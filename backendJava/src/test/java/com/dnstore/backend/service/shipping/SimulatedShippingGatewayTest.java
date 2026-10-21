package com.dnstore.backend.service.shipping;

import com.dnstore.backend.service.shipping.ShippingGateway.ShippingItem;
import com.dnstore.backend.service.shipping.ShippingGateway.ShippingRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedShippingGatewayTest {

    private final SimulatedShippingGateway gateway = new SimulatedShippingGateway();

    @Test
    void quote_shouldReturnEstimatedPacAndSedexPricesBasedOnCartWeight() {
        ShippingRequest request = new ShippingRequest(
                "12600000",
                "12500000",
                List.of(new ShippingItem(
                        new BigDecimal("0.5"),
                        new BigDecimal("20"),
                        new BigDecimal("10"),
                        new BigDecimal("30"),
                        4)));

        List<ShippingQuote> quotes = gateway.quote(request);

        assertThat(quotes).containsExactly(
                new ShippingQuote("PAC", new BigDecimal("27.90"), 5),
                new ShippingQuote("SEDEX", new BigDecimal("45.50"), 2));
    }

    @Test
    void quote_shouldUseBasePricesForCartUpToOneKilogram() {
        ShippingRequest request = new ShippingRequest(
                "12600000",
                "12500000",
                List.of(new ShippingItem(
                        new BigDecimal("0.5"),
                        new BigDecimal("20"),
                        new BigDecimal("10"),
                        new BigDecimal("30"),
                        2)));

        List<ShippingQuote> quotes = gateway.quote(request);

        assertThat(quotes).containsExactly(
                new ShippingQuote("PAC", new BigDecimal("25.90"), 5),
                new ShippingQuote("SEDEX", new BigDecimal("42.50"), 2));
    }
}
