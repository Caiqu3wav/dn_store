package com.dnstore.backend.service.shipping;

import com.dnstore.backend.exception.ShippingGatewayException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MelhorEnvioShippingGateway implements ShippingGateway {

    private final RestClient.Builder restClientBuilder;
    private final MelhorEnvioTokenStore tokenStore;

    @Value("${melhor-envio.base-url}")
    private String baseUrl;

    @Override
    public List<ShippingQuote> quote(ShippingRequest request) {
        String accessToken;
        try {
            accessToken = tokenStore.get();
        } catch (IllegalStateException e) {
            throw new ShippingGatewayException("Melhor Envio ainda não foi autorizado.", e);
        }

        try {
            RestClient client = restClientBuilder
                    .baseUrl(baseUrl)
                    .build();

            List<Map<String, Object>> products = new ArrayList<>();

            for (int i = 0; i < request.items().size(); i++) {
                ShippingItem item = request.items().get(i);

                products.add(Map.of(
                        "id", "item-" + (i + 1),
                        "width", item.width(),
                        "height", item.height(),
                        "length", item.length(),
                        "weight", item.weight(),
                        "insurance_value", BigDecimal.ZERO,
                        "quantity", item.quantity()));
            }

            Map<String, Object> body = Map.of(
                    "from", Map.of(
                            "postal_code", request.originZip()),
                    "to", Map.of(
                            "postal_code", request.destinationZip()),
                    "products", products);

            MelhorEnvioQuoteResponse[] response = client.post()
                    .uri("/api/v2/me/shipment/calculate")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MelhorEnvioQuoteResponse[].class);

            if (response == null) {
                throw new ShippingGatewayException(
                        "Melhor Envio não retornou opções de frete.");
            }

            List<ShippingQuote> quotes = new ArrayList<>();

            for (MelhorEnvioQuoteResponse option : response) {
                if (option == null
                        || option.name() == null
                        || option.name().isBlank()
                        || option.price() == null
                        || option.price().signum() < 0
                        || option.deliveryTime() == null
                        || option.deliveryTime() <= 0) {
                    continue;
                }

                quotes.add(new ShippingQuote(
                        displayServiceName(option),
                        option.price(),
                        option.deliveryTime()));
            }

            if (quotes.isEmpty()) {
                throw new ShippingGatewayException(
                        "Melhor Envio não retornou opções de frete válidas.");
            }

            return quotes;

        } catch (ShippingGatewayException e) {
            throw e;

        } catch (RestClientException e) {
            throw new ShippingGatewayException(
                    "Não foi possível consultar o frete no Melhor Envio.");
        }
    }

    private String displayServiceName(MelhorEnvioQuoteResponse option) {
        if (option.company() == null
                || option.company().name() == null
                || option.company().name().isBlank()) {
            return option.name();
        }
        return option.company().name() + " " + option.name();
    }

    private record MelhorEnvioQuoteResponse(
            String name,
            BigDecimal price,
            @com.fasterxml.jackson.annotation.JsonProperty("delivery_time") Integer deliveryTime,
            MelhorEnvioCompany company) {
    }

    private record MelhorEnvioCompany(String name) {
    }
}