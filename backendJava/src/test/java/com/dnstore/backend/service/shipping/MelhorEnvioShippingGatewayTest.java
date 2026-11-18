package com.dnstore.backend.service.shipping;

import com.dnstore.backend.config.MelhorEnvioProperties;
import com.dnstore.backend.exception.NoShippingOptionsException;
import com.dnstore.backend.exception.ShippingGatewayException;
import com.dnstore.backend.exception.ShippingQuoteRejectedException;
import com.dnstore.backend.exception.ShippingRateLimitedException;
import com.dnstore.backend.service.shipping.ShippingGateway.ShippingItem;
import com.dnstore.backend.service.shipping.ShippingGateway.ShippingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
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
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
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
    private final List<Long> sleeps = new ArrayList<>();

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
        gateway = new MelhorEnvioShippingGateway(restClientBuilder, tokenStore, properties, sleeps::add);
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
    void quote_whenProviderReturnsAnEmptyList_reportsNoServicesInsteadOfAnOutage() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.quote(validRequest()))
                .isInstanceOf(NoShippingOptionsException.class)
                .isNotInstanceOf(ShippingGatewayException.class);
        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void quote_whenEveryServiceIsExplicitlyUnavailable_reportsNoServicesWithoutRetrying() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withSuccess("""
                        [
                          {"id": 1, "name": "PAC", "error": "Serviço indisponível para o trecho",
                           "company": {"id": 1, "name": "Correios"}},
                          {"id": 3, "name": ".Package", "error": "Dimensões não atendidas",
                           "company": {"id": 2, "name": "Jadlog"}}
                        ]
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.quote(validRequest())).isInstanceOf(NoShippingOptionsException.class);
        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void quote_keepsAvailableServicesAndIgnoresTheOnesThatReportedAnError() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withSuccess("""
                        [
                          {"id": 1, "name": "PAC", "error": "Serviço indisponível",
                           "company": {"id": 1, "name": "Correios"}},
                          {"id": 2, "name": "SEDEX", "price": "31.00", "delivery_time": 2,
                           "company": {"id": 1, "name": "Correios"}}
                        ]
                        """, MediaType.APPLICATION_JSON));

        assertThat(gateway.quote(validRequest())).containsExactly(
                new ShippingQuote("1:2", "Correios SEDEX", new BigDecimal("31.00"), 2, "MELHOR_ENVIO"));
        server.verify();
    }

    @Test
    void quote_whenOptionsAreMalformedWithoutAReason_staysATechnicalFailure() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withSuccess("""
                        [{"id": 9, "name": "PAC", "delivery_time": 4}]
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.quote(validRequest()))
                .isInstanceOf(ShippingGatewayException.class)
                .hasMessageContaining("no valid shipping options");
        server.verify();
    }

    @Test
    void quote_mixOfUnavailableAndMalformedOptionsIsStillATechnicalFailure() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withSuccess("""
                        [{"id": 1, "name": "PAC", "error": "indisponivel"},
                         {"id": 9, "name": "SEDEX", "delivery_time": 4}]
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.quote(validRequest())).isInstanceOf(ShippingGatewayException.class);
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
    void quote_providerErrorDoesNotExposeAccessToken(CapturedOutput output) {
        server.expect(times(3), requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"request rejected\"}"));

        assertThatThrownBy(() -> gateway.quote(validRequest()))
                .isInstanceOf(ShippingGatewayException.class)
                .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain(ACCESS_TOKEN));
        server.verify();
        assertThat(output.getAll()).doesNotContain(ACCESS_TOKEN).doesNotContain("request rejected");
    }

    @Test
    void quote_retriesServerErrorsWithBackoffUntilSuccess(CapturedOutput output) {
        server.expect(times(2), requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withSuccess(oneOption(), MediaType.APPLICATION_JSON));

        assertThat(gateway.quote(validRequest())).hasSize(1);

        server.verify();
        assertThat(sleeps).hasSize(2);
        assertThat(sleeps.get(0)).isBetween(300L, 375L);
        assertThat(sleeps.get(1)).isBetween(600L, 750L);
        assertThat(output.getAll()).contains("melhor_envio.quote.retry")
                .contains("melhor_envio.quote.ok options=1 attempts=3");
    }

    @Test
    void quote_retriesNetworkFailures() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withException(new IOException("connection reset")));
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withSuccess(oneOption(), MediaType.APPLICATION_JSON));

        assertThat(gateway.quote(validRequest())).hasSize(1);

        server.verify();
        assertThat(sleeps).hasSize(1);
    }

    @Test
    void quote_honorsShortRetryAfterOnTooManyRequests() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "2"));
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withSuccess(oneOption(), MediaType.APPLICATION_JSON));

        assertThat(gateway.quote(validRequest())).hasSize(1);

        server.verify();
        assertThat(sleeps).singleElement().satisfies(wait -> assertThat(wait).isGreaterThanOrEqualTo(2000L));
    }

    @Test
    void quote_doesNotWaitForLongRetryAfterAndReportsRateLimit() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "120"));

        assertThatThrownBy(() -> gateway.quote(validRequest()))
                .isInstanceOfSatisfying(ShippingRateLimitedException.class,
                        exception -> assertThat(exception.getRetryAfterSeconds()).isEqualTo(120L));

        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void quote_stopsRetryingWhenTimeBudgetIsExhausted() {
        properties.getRetry().setTotalBudget(Duration.ofMillis(100));
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> gateway.quote(validRequest())).isInstanceOf(ShippingGatewayException.class);

        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void quote_unprocessableDestinationPostalCodeIsRejectedWithoutRetry() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"invalid\",\"errors\":{\"to.postal_code\":[\"CEP invalido\"]}}"));

        assertThatThrownBy(() -> gateway.quote(validRequest()))
                .isInstanceOf(ShippingQuoteRejectedException.class)
                .hasMessageContaining("CEP");

        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void quote_unprocessableProductOrOriginIsOurFailureNotTheCustomers() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":{\"to.postal_code\":[\"x\"],\"products.0.width\":[\"y\"]}}"));

        assertThatThrownBy(() -> gateway.quote(validRequest()))
                .isInstanceOf(ShippingGatewayException.class)
                .isNotInstanceOf(ShippingQuoteRejectedException.class);
        server.verify();
    }

    @Test
    void quote_forbiddenIsNotRetried(CapturedOutput output) {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> gateway.quote(validRequest())).isInstanceOf(ShippingGatewayException.class);

        server.verify();
        assertThat(sleeps).isEmpty();
        assertThat(output.getAll()).contains("category=forbidden status=403");
    }

    @Test
    void quote_secondUnauthorizedAfterRefreshFailsWithoutLooping() {
        when(tokenStore.refreshAfterUnauthorized(ACCESS_TOKEN)).thenReturn(REFRESHED_TOKEN);
        server.expect(times(2), requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> gateway.quote(validRequest())).isInstanceOf(ShippingGatewayException.class);

        server.verify();
    }

    private String oneOption() {
        return """
                [{"id": 1, "name": "PAC", "price": "19.90", "delivery_time": 4}]
                """;
    }

    @Test
    void quote_sendsCatalogPackageMeasurementsForEachProduct() {
        server.expect(requestTo(BASE_URL + "/api/v2/me/shipment/calculate"))
                .andExpect(content().json("""
                        {
                          "from": {"postal_code": "01001000"},
                          "to": {"postal_code": "20040002"},
                          "products": [
                            {"id": "bone", "height": 12, "width": 20, "length": 25,
                             "weight": 0.20, "insurance_value": 29.99, "quantity": 1},
                            {"id": "camisa-ciclismo", "height": 4, "width": 22, "length": 30,
                             "weight": 0.25, "insurance_value": 209.99, "quantity": 1},
                            {"id": "camisa-poliamida", "height": 3, "width": 22, "length": 28,
                             "weight": 0.20, "insurance_value": 59.99, "quantity": 2},
                            {"id": "meia", "height": 3, "width": 10, "length": 20,
                             "weight": 0.08, "insurance_value": 39.99, "quantity": 1}
                          ]
                        }
                        """, true))
                .andRespond(withSuccess("""
                        [{
                          "id": 1,
                          "name": "PAC",
                          "price": "20.00",
                          "currency": "R$",
                          "delivery_time": 5,
                          "company": {"id": 1, "name": "Correios"}
                        }]
                        """, MediaType.APPLICATION_JSON));

        gateway.quote(new ShippingRequest("01001000", "20040002", List.of(
                new ShippingItem("bone", new BigDecimal("0.20"), new BigDecimal("20"),
                        new BigDecimal("12"), new BigDecimal("25"), new BigDecimal("29.99"), 1),
                new ShippingItem("camisa-ciclismo", new BigDecimal("0.25"), new BigDecimal("22"),
                        new BigDecimal("4"), new BigDecimal("30"), new BigDecimal("209.99"), 1),
                new ShippingItem("camisa-poliamida", new BigDecimal("0.20"), new BigDecimal("22"),
                        new BigDecimal("3"), new BigDecimal("28"), new BigDecimal("59.99"), 2),
                new ShippingItem("meia", new BigDecimal("0.08"), new BigDecimal("10"),
                        new BigDecimal("3"), new BigDecimal("20"), new BigDecimal("39.99"), 1))));

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
