package com.dnstore.backend.service.shipping;

import com.dnstore.backend.config.MelhorEnvioProperties;
import com.dnstore.backend.exception.NoShippingOptionsException;
import com.dnstore.backend.exception.ShippingGatewayException;
import com.dnstore.backend.exception.ShippingQuoteRejectedException;
import com.dnstore.backend.exception.ShippingRateLimitedException;
import com.dnstore.backend.service.shipping.ShippingGateway.ShippingItem;
import com.dnstore.backend.service.shipping.ShippingGateway.ShippingRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cotação de frete no Melhor Envio.
 *
 * <p>Tratamento de erros: 401 renova o token e repete uma vez; 422 com erro no CEP de destino vira
 * {@link ShippingQuoteRejectedException} (400 para o cliente); 429, 5xx e falhas de rede são
 * repetidos com backoff exponencial, respeitando {@code Retry-After} dentro de um orçamento de
 * tempo; 403 e demais 4xx não são repetidos e indicam problema de configuração ou de dados nossos.
 * Os logs trazem só categoria, status HTTP e contagem de tentativas, nunca corpo, token ou CEP.
 */
@Slf4j
@Component("melhorEnvioShippingGateway")
@ConditionalOnProperty(name = "shipping.gateway", havingValue = "melhorEnvio")
public class MelhorEnvioShippingGateway implements ShippingGateway {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String GENERIC_FAILURE = "Could not retrieve shipping options from Melhor Envio.";

    private final RestClient.Builder restClientBuilder;
    private final MelhorEnvioTokenStore tokenStore;
    private final MelhorEnvioProperties properties;
    private final Sleeper sleeper;

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    @Autowired
    public MelhorEnvioShippingGateway(
            RestClient.Builder restClientBuilder,
            MelhorEnvioTokenStore tokenStore,
            MelhorEnvioProperties properties) {
        this(restClientBuilder, tokenStore, properties, Thread::sleep);
    }

    MelhorEnvioShippingGateway(
            RestClient.Builder restClientBuilder,
            MelhorEnvioTokenStore tokenStore,
            MelhorEnvioProperties properties,
            Sleeper sleeper) {
        this.restClientBuilder = restClientBuilder;
        this.tokenStore = tokenStore;
        this.properties = properties;
        this.sleeper = sleeper;
    }

    @Override
    public List<ShippingQuote> quote(ShippingRequest request) {
        String accessToken = tokenStore.getValidAccessToken();
        try {
            return quoteWithRetry(request, accessToken);
        } catch (UnauthorizedException unauthorized) {
            log.warn("melhor_envio.quote.unauthorized action=refresh_and_retry_once");
            String refreshedToken = tokenStore.refreshAfterUnauthorized(accessToken);
            try {
                return quoteWithRetry(request, refreshedToken);
            } catch (UnauthorizedException stillUnauthorized) {
                log.error("melhor_envio.quote.failed category=unauthorized_after_refresh status=401");
                throw new ShippingGatewayException(GENERIC_FAILURE);
            }
        }
    }

    private List<ShippingQuote> quoteWithRetry(ShippingRequest request, String accessToken) {
        MelhorEnvioProperties.Retry retry = properties.getRetry();
        long startedNanos = System.nanoTime();
        int attempt = 0;
        while (true) {
            attempt++;
            Integer status = null;
            Long retryAfterSeconds = null;
            String category;
            try {
                List<ShippingQuote> quotes = requestQuote(request, accessToken);
                log.info("melhor_envio.quote.ok options={} attempts={} durationMs={}",
                        quotes.size(), attempt, elapsedMillis(startedNanos));
                return quotes;
            } catch (RestClientResponseException exception) {
                status = exception.getStatusCode().value();
                if (status == 401) {
                    throw new UnauthorizedException();
                }
                if (status == 422) {
                    throw unprocessable(exception, attempt, startedNanos);
                }
                retryAfterSeconds = parseRetryAfterSeconds(exception.getResponseHeaders());
                if (status == 429) {
                    category = "rate_limited";
                } else if (status >= 500) {
                    category = "provider_error";
                } else {
                    // 403 (escopo/conta) e outros 4xx: repetir não adianta, o ajuste é nosso.
                    log.error("melhor_envio.quote.failed category={} status={} attempts={} durationMs={}",
                            status == 403 ? "forbidden" : "client_error", status, attempt,
                            elapsedMillis(startedNanos));
                    throw new ShippingGatewayException(GENERIC_FAILURE);
                }
            } catch (ResourceAccessException exception) {
                category = "network";
            } catch (RestClientException exception) {
                // Resposta ilegível: não é transitório.
                log.error("melhor_envio.quote.failed category=unreadable_response attempts={} durationMs={}",
                        attempt, elapsedMillis(startedNanos));
                throw new ShippingGatewayException(GENERIC_FAILURE);
            }

            long waitMillis = nextWaitMillis(retry, attempt, retryAfterSeconds, startedNanos);
            if (attempt >= retry.getMaxAttempts() || waitMillis < 0) {
                log.warn("melhor_envio.quote.failed category={} status={} attempts={} durationMs={}",
                        category, status, attempt, elapsedMillis(startedNanos));
                if (status != null && status == 429) {
                    throw new ShippingRateLimitedException(GENERIC_FAILURE, retryAfterSeconds);
                }
                throw new ShippingGatewayException(GENERIC_FAILURE);
            }
            log.warn("melhor_envio.quote.retry category={} status={} attempt={} waitMs={}",
                    category, status, attempt, waitMillis);
            pause(waitMillis);
        }
    }

    /** Espera antes da próxima tentativa, ou -1 se o Retry-After ou o orçamento de tempo não permitem. */
    private long nextWaitMillis(
            MelhorEnvioProperties.Retry retry, int attempt, Long retryAfterSeconds, long startedNanos) {
        long backoff = Math.min(
                retry.getMaxBackoff().toMillis(),
                retry.getInitialBackoff().toMillis() * (1L << Math.min(attempt - 1, 10)));
        long jitter = (long) (backoff * 0.25 * ThreadLocalRandom.current().nextDouble());
        long wait = backoff + jitter;
        if (retryAfterSeconds != null) {
            long retryAfterMillis = Duration.ofSeconds(retryAfterSeconds).toMillis();
            if (retryAfterMillis > retry.getMaxRetryAfter().toMillis()) {
                return -1;
            }
            wait = Math.max(wait, retryAfterMillis);
        }
        if (elapsedMillis(startedNanos) + wait > retry.getTotalBudget().toMillis()) {
            return -1;
        }
        return wait;
    }

    private void pause(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ShippingGatewayException(GENERIC_FAILURE);
        }
    }

    /** Retry-After em segundos ou em data HTTP; ausente ou ilegível vira null. */
    private Long parseRetryAfterSeconds(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Math.max(0, Long.parseLong(value.trim()));
        } catch (NumberFormatException notSeconds) {
            try {
                ZonedDateTime date = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
                return Math.max(0, Duration.between(ZonedDateTime.now(date.getZone()), date).toSeconds());
            } catch (RuntimeException notDate) {
                return null;
            }
        }
    }

    /** 422: erro no CEP de destino é do cliente; qualquer outro campo é problema nosso (503). */
    private RuntimeException unprocessable(
            RestClientResponseException exception, int attempt, long startedNanos) {
        List<String> fields = new ArrayList<>();
        try {
            JsonNode errors = JSON.readTree(exception.getResponseBodyAsString()).get("errors");
            if (errors != null && errors.isObject()) {
                errors.fieldNames().forEachRemaining(name -> {
                    if (name.matches("[A-Za-z0-9_.\\[\\]-]{1,60}")) {
                        fields.add(name);
                    }
                });
            }
        } catch (Exception ignored) {
            // corpo ilegível: tratado como falha nossa abaixo
        }
        boolean onlyDestination = !fields.isEmpty()
                && fields.stream().allMatch(name -> name.equals("to") || name.startsWith("to."));
        if (onlyDestination) {
            log.info("melhor_envio.quote.rejected category=destination status=422 attempts={} durationMs={}",
                    attempt, elapsedMillis(startedNanos));
            return new ShippingQuoteRejectedException(
                    "Não foi possível calcular o frete para este CEP. Confira o CEP informado.");
        }
        log.error("melhor_envio.quote.failed category=unprocessable status=422 fields={} attempts={} durationMs={}",
                fields, attempt, elapsedMillis(startedNanos));
        return new ShippingGatewayException(GENERIC_FAILURE);
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private List<ShippingQuote> requestQuote(ShippingRequest request, String accessToken) {
        List<Map<String, Object>> products = new ArrayList<>();
        for (ShippingItem item : request.items()) {
            products.add(Map.of(
                    "id", item.id(),
                    "width", item.width(),
                    "height", item.height(),
                    "length", item.length(),
                    "weight", item.weight(),
                    "insurance_value", item.insuranceValue(),
                    "quantity", item.quantity()));
        }

        Map<String, Object> body = Map.of(
                "from", Map.of("postal_code", request.originZip()),
                "to", Map.of("postal_code", request.destinationZip()),
                "products", products);

        MelhorEnvioQuoteResponse[] response = restClientBuilder.clone()
                .baseUrl(properties.getApiBaseUrl())
                .build()
                .post()
                .uri("/api/v2/me/shipment/calculate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.USER_AGENT, properties.getUserAgent())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MelhorEnvioQuoteResponse[].class);

        if (response == null) {
            log.error("melhor_envio.quote.failed category=empty_body");
            throw new ShippingGatewayException("Melhor Envio returned no shipping options.");
        }

        List<ShippingQuote> quotes = new ArrayList<>();
        int explicitlyUnavailable = 0;
        int unusable = 0;
        for (MelhorEnvioQuoteResponse option : response) {
            if (reportsError(option)) {
                // O serviço respondeu que não atende esta rota: ausência de serviço, não falha.
                explicitlyUnavailable++;
                continue;
            }
            ShippingQuote quote = toQuote(option);
            if (quote != null) {
                quotes.add(quote);
            } else {
                unusable++;
            }
        }
        if (quotes.isEmpty()) {
            if (unusable == 0) {
                // Resposta válida sem nenhum serviço (lista vazia ou todos "indisponíveis" para o CEP).
                // WARN: um CEP sem cobertura é normal, mas uma série deles para CEPs diferentes indica
                // origem ou medidas mal configuradas escondidas atrás do "sem entrega".
                log.warn("melhor_envio.quote.no_services returned={} unavailable={}",
                        response.length, explicitlyUnavailable);
                throw new NoShippingOptionsException();
            }
            // Itens sem preço/prazo/id e sem motivo: o provedor respondeu algo que não entendemos.
            log.warn("melhor_envio.quote.no_valid_options returned={} unusable={} unavailable={}",
                    response.length, unusable, explicitlyUnavailable);
            throw new ShippingGatewayException("Melhor Envio returned no valid shipping options.");
        }
        return List.copyOf(quotes);
    }

    /** O campo {@code error} não é documentado; aceita texto, objeto ou booleano sem quebrar a leitura. */
    private boolean reportsError(MelhorEnvioQuoteResponse option) {
        if (option == null || option.error() == null || option.error().isNull()) {
            return false;
        }
        JsonNode error = option.error();
        if (error.isTextual()) {
            return !error.asText().isBlank();
        }
        if (error.isBoolean()) {
            return error.asBoolean();
        }
        return !error.isEmpty();
    }

    private ShippingQuote toQuote(MelhorEnvioQuoteResponse option) {
        if (option == null || option.id() == null || option.id().isBlank()
                || option.name() == null || option.name().isBlank()) {
            return null;
        }
        BigDecimal price = option.customPrice() != null ? option.customPrice() : option.price();
        Integer deliveryDays = option.customDeliveryTime() != null
                ? option.customDeliveryTime()
                : option.deliveryTime();
        if (price == null || price.signum() < 0 || deliveryDays == null || deliveryDays <= 0) {
            return null;
        }
        if (option.currency() != null && !option.currency().equalsIgnoreCase("R$")) {
            return null;
        }
        String serviceId = option.company() != null && option.company().id() != null
                ? option.company().id() + ":" + option.id()
                : option.id();
        String serviceName = option.company() == null
                || option.company().name() == null
                || option.company().name().isBlank()
                ? option.name()
                : option.company().name() + " " + option.name();
        return new ShippingQuote(
                serviceId,
                serviceName,
                price.setScale(2, RoundingMode.HALF_UP),
                deliveryDays,
                "MELHOR_ENVIO");
    }

    /** Sinaliza 401 para o fluxo de renovação de token; nunca sai do gateway. */
    private static final class UnauthorizedException extends RuntimeException {
        UnauthorizedException() {
            super(null, null, false, false);
        }
    }

    private record MelhorEnvioQuoteResponse(
            String id,
            String name,
            BigDecimal price,
            @JsonProperty("custom_price") BigDecimal customPrice,
            String currency,
            @JsonProperty("delivery_time") Integer deliveryTime,
            @JsonProperty("custom_delivery_time") Integer customDeliveryTime,
            JsonNode error,
            MelhorEnvioCompany company) {
    }

    private record MelhorEnvioCompany(String id, String name) {
    }
}
