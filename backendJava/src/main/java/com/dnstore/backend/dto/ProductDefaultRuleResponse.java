package com.dnstore.backend.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProductDefaultRuleResponse(
        UUID id,
        String name,
        ProductDefaultRuleRequest.Criteria criteria,
        ProductDefaultRuleRequest.Values values,
        List<UUID> excludedProductIds,
        int updatedProducts,
        LocalDateTime createdAt) {
}
