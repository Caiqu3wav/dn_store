package com.dnstore.backend.service.shipping;

import com.dnstore.backend.config.MelhorEnvioProperties;
import com.dnstore.backend.exception.ShippingGatewayException;
import com.dnstore.backend.service.shipping.ShippingGateway.ShippingItem;
import com.dnstore.backend.service.shipping.ShippingGateway.ShippingRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component("melhorEnvioShippingGateway")
@ConditionalOnProperty(name = "shipping.gateway", havingValue = "melhorEnvio")
@RequiredArgsConstructor
public class MelhorEnvioShippingGateway implements ShippingGateway {

    private final RestClient.Builder restClientBuilder;
    private final MelhorEnvioTokenStore tokenStore;
    private final MelhorEnvioProperties properties;

    @Override
    public List<ShippingQuote> quote(ShippingRequest request) {
        String accessToken = tokenStore.getValidAccessToken();
        try {
            return requestQuote(request, accessToken);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 401) {
                throw new ShippingGatewayException(
                        "Could not retrieve shipping options from Melhor Envio.");
            }
            String refreshedToken = tokenStore.refreshAfterUnauthorized(accessToken);
            try {
                return requestQuote(request, refreshedToken);
            } catch (RestClientException retryException) {
                throw new ShippingGatewayException(
                        "Could not retrieve shipping options from Melhor Envio.");
            }
        } catch (RestClientException exception) {
            throw new ShippingGatewayException(
                    "Could not retrieve shipping options from Melhor Envio.");
        }
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
            throw new ShippingGatewayException("Melhor Envio returned no shipping options.");
        }

        List<ShippingQuote> quotes = new ArrayList<>();
        for (MelhorEnvioQuoteResponse option : response) {
            ShippingQuote quote = toQuote(option);
            if (quote != null) {
                quotes.add(quote);
            }
        }
        if (quotes.isEmpty()) {
            throw new ShippingGatewayException("Melhor Envio returned no valid shipping options.");
        }
        return List.copyOf(quotes);
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

    private record MelhorEnvioQuoteResponse(
            String id,
            String name,
            BigDecimal price,
            @JsonProperty("custom_price") BigDecimal customPrice,
            String currency,
            @JsonProperty("delivery_time") Integer deliveryTime,
            @JsonProperty("custom_delivery_time") Integer customDeliveryTime,
            MelhorEnvioCompany company) {
    }

    private record MelhorEnvioCompany(String id, String name) {
    }
}
