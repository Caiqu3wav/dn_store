package com.dnstore.backend.service.shipping;

import java.math.BigDecimal;
import java.util.List;

public interface ShippingGateway {

    List<ShippingQuote> quote(ShippingRequest request);

    record ShippingRequest(String originZip, String destinationZip, List<ShippingItem> items) {
        public ShippingRequest {
            items = List.copyOf(items);
        }
    }

    record ShippingItem(
            String id,
            BigDecimal weight,
            BigDecimal width,
            BigDecimal height,
            BigDecimal length,
            BigDecimal insuranceValue,
            int quantity) {

        public ShippingItem(
                BigDecimal weight,
                BigDecimal width,
                BigDecimal height,
                BigDecimal length,
                int quantity) {
            this("item", weight, width, height, length, BigDecimal.ZERO, quantity);
        }

        public BigDecimal totalWeight() {
            return weight.multiply(BigDecimal.valueOf(quantity));
        }
    }
}