package com.dnstore.backend.service.shipping;

import com.dnstore.backend.config.MelhorEnvioProperties;
import com.dnstore.backend.exception.ShippingGatewayException;
import com.dnstore.backend.service.shipping.ShippingGateway.ShippingItem;
import com.dnstore.backend.service.shipping.ShippingGateway.ShippingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MelhorEnvioShippingGatewayTest {

    private static final String BASE_URL = "https://melhor-envio.test";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String REFRESHED_TOKEN = "refreshed-access-token";
    private static final String USER_AGENT = "DN Store/1.0 (support@example.com)";

    private MockRestServiceServer server;
    private RestClient.Builder restClientBuilder;
    private MelhorEnvioTokenStore tokenStore;
    private MelhorEnvioProperties properties;
    private MelhorEnvioShippingGateway gateway;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        tokenStore = mock(MelhorEnvioTokenStore.class);
        when(tokenStore.getValidAccessToken()).thenReturn(ACCESS_TOKEN);
        properties = new MelhorEnvioProperties();
        properties.setApiBaseUrl(BASE_URL);
        properties.setUserAgent(USER_AGENT);
        properties.setEnvironment("sandbox");
        gateway = new MelhorEnvioShippingGateway(restClientBuilder, tokenStore, properties);
    }

    @Test
    void quote_usesBackendProductDataCustomPriceAndRequiredHeaders() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("""
                        {
                          "from": {"postal_code": "01001000"},
                          "to": {"postal_code": "20040002"},
                          "products": [{
                            "id": "variant-1",
                            "weight": 0.45,
                            "width": 22,
                            "height": 15,
                            "length": 31,
                            "insurance_value": 100.5,
                            "quantity": 3
                          }]
                        }
                        """))
                .andRespond(withSuccess("""
                        [{
                          "id": 12,
                          "name": "PAC",
                          "price": "19.90",
                          "custom_price": "17.50",
                          "currency": "R$",
                          "delivery_time": 4,
                          "custom_delivery_time": 6,
                          "company": {"id": 1, "name": "Correios"}
                        }]
                        """, MediaType.APPLICATION_JSON));

        List<ShippingQuote> quotes = gateway.quote(validRequest());

        assertThat(quotes).containsExactly(new ShippingQuote(
                "1:12", "Correios PAC", new BigDecimal("17.50"), 6, "MELHOR_ENVIO"));
        server.verify();
    }

    @Test
    void quote_shouldDropInvalidOptionsAndKeepStableProviderServiceId() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withSuccess("""
                        [
                          null,
                          {"id": 3, "name": "", "price": "8.50", "delivery_time": 2},
                          {"id": 9, "name": "PAC", "price": "19.90", "delivery_time": 4,
                           "company": {"id": 4, "name": "Correios"}}
                        ]
                        """, MediaType.APPLICATION_JSON));

        assertThat(gateway.quote(validRequest())).containsExactly(new ShippingQuote(
                "4:9", "Correios PAC", new BigDecimal("19.90"), 4, "MELHOR_ENVIO"));
        server.verify();
    }

    @Test
    void quote_mapsMultipleCarriersAndServiceIds() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withSuccess("""
                        [
                          {"id": 3, "name": ".Package", "price": "53.58", "delivery_time": 5,
                           "company": {"id": 2, "name": "Jadlog"}},
                          {"id": 1, "name": "PAC", "price": "22.10", "delivery_time": 3,
                           "company": {"id": 1, "name": "Correios"}}
                        ]
                        """, MediaType.APPLICATION_JSON));

        assertThat(gateway.quote(validRequest())).containsExactly(
                new ShippingQuote("2:3", "Jadlog .Package", new BigDecimal("53.58"), 5, "MELHOR_ENVIO"),
                new ShippingQuote("1:1", "Correios PAC", new BigDecimal("22.10"), 3, "MELHOR_ENVIO"));
        server.verify();
    }

    @Test
    void quote_whenProviderReturnsNoValidOptions_shouldFailClosed() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.quote(validRequest()))
                .isInstanceOf(ShippingGatewayException.class)
                .hasMessageContaining("no valid shipping options");
        server.verify();
    }

    @Test
    void quote_withoutAuthorizedAccount_shouldNotCallProvider() {
        when(tokenStore.getValidAccessToken())
                .thenThrow(new ShippingGatewayException("not authorized"));

        assertThatThrownBy(() -> gateway.quote(validRequest()))
                .isInstanceOf(ShippingGatewayException.class);
        server.verify();
    }

    @Test
    void quote_retriesOnlyOnceAfterUnauthorizedResponseWithRefreshedToken() {
        when(tokenStore.refreshAfterUnauthorized(ACCESS_TOKEN)).thenReturn(REFRESHED_TOKEN);
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + REFRESHED_TOKEN))
                .andRespond(withSuccess("""
                        [{"id": 1, "name": "PAC", "price": "19.90", "delivery_time": 4}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(gateway.quote(validRequest()))
                .containsExactly(new ShippingQuote(
                        "1", "PAC", new BigDecimal("19.90"), 4, "MELHOR_ENVIO"));
        server.verify();
    }

    @Test
    void quote_providerErrorDoesNotExposeAccessToken() {
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
                        "variant-1",
                        new BigDecimal("0.45"),
                        new BigDecimal("22"),
                        new BigDecimal("15"),
                        new BigDecimal("31"),
                        new BigDecimal("100.5"),
                        3)));
    }
}
