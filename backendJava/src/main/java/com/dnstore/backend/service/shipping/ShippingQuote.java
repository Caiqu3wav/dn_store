package com.dnstore.backend.service.shipping;

import java.math.BigDecimal;

public record ShippingQuote(String service, BigDecimal price, int deliveryDays) {
}