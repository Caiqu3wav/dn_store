package com.dnstore.backend.service.shipping;

import java.math.BigDecimal;

public record ShippingQuote(
        String serviceId,
        String service,
        BigDecimal price,
        int deliveryDays,
        String source) {

    public ShippingQuote(String service, BigDecimal price, int deliveryDays) {
        this(service, service, price, deliveryDays, "SHIPPING_GATEWAY");
    }
}