package com.dnstore.backend.service.shipping;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
@ConditionalOnProperty(name = "shipping.gateway", havingValue = "simulated", matchIfMissing = true)
public class SimulatedShippingGateway implements ShippingGateway {

    private static final BigDecimal BASE_WEIGHT = BigDecimal.ONE;
    private static final BigDecimal PAC_BASE_PRICE = new BigDecimal("25.90");
    private static final BigDecimal SEDEX_BASE_PRICE = new BigDecimal("42.50");
    private static final BigDecimal PAC_EXTRA_KG_PRICE = new BigDecimal("2.00");
    private static final BigDecimal SEDEX_EXTRA_KG_PRICE = new BigDecimal("3.00");

    @Override
    public List<ShippingQuote> quote(ShippingRequest request) {
        BigDecimal totalWeight = request.items().stream()
                .map(ShippingItem::totalWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal extraWeight = totalWeight.subtract(BASE_WEIGHT).max(BigDecimal.ZERO);

        return List.of(
                new ShippingQuote("PAC", calculatePrice(PAC_BASE_PRICE, PAC_EXTRA_KG_PRICE, extraWeight), 5),
                new ShippingQuote("SEDEX", calculatePrice(SEDEX_BASE_PRICE, SEDEX_EXTRA_KG_PRICE, extraWeight), 2));
    }

    private BigDecimal calculatePrice(BigDecimal basePrice, BigDecimal extraKgPrice, BigDecimal extraWeight) {
        return basePrice.add(extraKgPrice.multiply(extraWeight)).setScale(2, RoundingMode.HALF_UP);
    }
}
