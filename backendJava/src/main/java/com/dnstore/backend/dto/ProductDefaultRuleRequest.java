package com.dnstore.backend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductDefaultRuleRequest(
        String name,
        Criteria criteria,
        Values values,
        List<UUID> excludedProductIds) {

    public record Criteria(
            String nameContains,
            UUID categoryId,
            String color,
            BigDecimal minPrice,
            BigDecimal maxPrice) {
    }

    public record Values(
            String name,
            String description,
            BigDecimal price,
            BigDecimal promotionalPrice,
            Boolean clearPromotionalPrice,
            Boolean active,
            String color,
            Boolean clearColor,
            UUID categoryId,
            Boolean clearCategory,
            Double weight,
            Double width,
            Double height,
            Double depth,
            List<String> sizes) {
    }
}
