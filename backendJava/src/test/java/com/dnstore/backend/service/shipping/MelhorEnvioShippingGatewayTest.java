package com.dnstore.backend.service.shipping;

import com.dnstore.backend.exception.ShippingGatewayException;
import com.dnstore.backend.service.shipping.ShippingGateway.ShippingItem;
import com.dnstore.backend.service.shipping.ShippingGateway.ShippingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class MelhorEnvioShippingGatewayTest {

    private static final String BASE_URL = "https://melhor-envio.test";
    private static final String ACCESS_TOKEN = "test-access-token";

    private MockRestServiceServer server;
    private RestClient.Builder restClientBuilder;
    private MelhorEnvioTokenStore tokenStore;
    private MelhorEnvioShippingGateway gateway;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        tokenStore = new MelhorEnvioTokenStore();
        tokenStore.save(ACCESS_TOKEN);
        gateway = new MelhorEnvioShippingGateway(restClientBuilder, tokenStore);
        ReflectionTestUtils.setField(gateway, "baseUrl", BASE_URL);
    }

    @Test
    void quote_shouldSendConfiguredUrlHeadersPostalCodesAndProductUnits() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("""
                        {
                          "from": {"postal_code": "01001000"},
                          "to": {"postal_code": "20040002"},
                          "products": [{
                            "id": "item-1",
                            "weight": 0.45,
                            "width": 22,
                            "height": 15,
                            "length": 31,
                            "insurance_value": 0,
                            "quantity": 3
                          }]
                        }
                        """))
                .andRespond(withSuccess("""
                        [{"name":"PAC","price":19.90,"delivery_time":4}]
                        """, MediaType.APPLICATION_JSON));

        List<ShippingQuote> quotes = gateway.quote(new ShippingRequest(
                "01001000",
                "20040002",
                List.of(new ShippingItem(
                        new BigDecimal("0.45"),
                        new BigDecimal("22"),
                        new BigDecimal("15"),
                        new BigDecimal("31"),
                        3))));

        assertThat(quotes).containsExactly(new ShippingQuote("PAC", new BigDecimal("19.90"), 4));
        server.verify();
    }

    @Test
    void quote_shouldKeepValidOptionsWhenResponseContainsInvalidOptions() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withSuccess("""
                        [
                          null,
                          {"name":"", "price":8.50, "delivery_time":2,
                           "company":{"name":"Correios"}},
                          {"name":"PAC","price":19.90,"delivery_time":4,
                           "company":{"name":"Correios"}}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<ShippingQuote> quotes = gateway.quote(validRequest());

        assertThat(quotes).containsExactly(new ShippingQuote("Correios PAC", new BigDecimal("19.90"), 4));
        server.verify();
    }

    @Test
    void quote_shouldMapCarrierAndServiceNamesWithoutChangingPriceOrDeliveryTime() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withSuccess("""
                        [
                          {"name":".Package","price":53.58,"delivery_time":5,
                           "company":{"name":"Jadlog"}},
                          {"name":".Com","price":64.24,"delivery_time":4,
                           "company":{"name":"Jadlog"}},
                          {"name":"PAC","price":22.10,"delivery_time":3,
                           "company":{"name":"Correios"}},
                          {"name":"SEDEX","price":31.75,"delivery_time":1,
                           "company":{"name":"Correios"}}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<ShippingQuote> quotes = gateway.quote(validRequest());

        assertThat(quotes).containsExactly(
                new ShippingQuote("Jadlog .Package", new BigDecimal("53.58"), 5),
                new ShippingQuote("Jadlog .Com", new BigDecimal("64.24"), 4),
                new ShippingQuote("Correios PAC", new BigDecimal("22.10"), 3),
                new ShippingQuote("Correios SEDEX", new BigDecimal("31.75"), 1));
        server.verify();
    }

    @Test
    void quote_whenResponseContainsNoValidOptions_shouldThrowGatewayException() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.quote(validRequest()))
                .isInstanceOf(ShippingGatewayException.class)
                .hasMessageContaining("opções de frete válidas");

        server.verify();
    }

    @Test
    void quote_withoutAccessToken_shouldThrowControlledGatewayException() {
        MelhorEnvioShippingGateway unauthorizedGateway =
                new MelhorEnvioShippingGateway(restClientBuilder, new MelhorEnvioTokenStore());
        ReflectionTestUtils.setField(unauthorizedGateway, "baseUrl", BASE_URL);

        assertThatThrownBy(() -> unauthorizedGateway.quote(validRequest()))
                .isInstanceOf(ShippingGatewayException.class)
                .hasMessageContaining("ainda não foi autorizado");

        server.verify();
    }

    @Test
    void quote_whenProviderReturnsHttpError_shouldNotExposeAccessToken() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"request rejected\"}"));

        assertThatThrownBy(() -> gateway.quote(validRequest()))
                .isInstanceOf(ShippingGatewayException.class)
                .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain(ACCESS_TOKEN));

        server.verify();
    }

    private ShippingRequest validRequest() {
        return new ShippingRequest("01001000", "20040002", List.of(
                new ShippingItem(
                        new BigDecimal("0.45"),
                        new BigDecimal("22"),
                        new BigDecimal("15"),
                        new BigDecimal("31"),
                        1)));
    }
}
