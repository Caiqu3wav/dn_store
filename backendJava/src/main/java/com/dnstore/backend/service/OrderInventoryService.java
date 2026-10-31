package com.dnstore.backend.service;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.OrderItem;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderInventoryService {

    private final ProductVariantRepository productVariantRepository;

    public void releaseReservedStock(Order order) {
        if (order.isStockReleased()) {
            return;
        }
        Map<UUID, Integer> quantities = quantitiesByVariant(order);
        Map<UUID, ProductVariant> variants = lockVariants(quantities.keySet());
        quantities.forEach((variantId, quantity) -> {
            ProductVariant variant = variants.get(variantId);
            variant.setStock(Math.addExact(variant.getStock(), quantity));
            productVariantRepository.save(variant);
        });
        order.setStockReleased(true);
    }

    public void reserveReleasedStock(Order order) {
        if (!order.isStockReleased()) {
            return;
        }
        Map<UUID, Integer> quantities = quantitiesByVariant(order);
        Map<UUID, ProductVariant> variants = lockVariants(quantities.keySet());
        quantities.forEach((variantId, quantity) -> {
            ProductVariant variant = variants.get(variantId);
            if (variant.getStock() < quantity) {
                throw new ConflictException(
                        "Estoque insuficiente para reativar o pedido pago: "
                                + variant.getProduct().getName() + " (" + variant.getSize() + ").");
            }
        });
        quantities.forEach((variantId, quantity) -> {
            ProductVariant variant = variants.get(variantId);
            variant.setStock(variant.getStock() - quantity);
            productVariantRepository.save(variant);
        });
        order.setStockReleased(false);
    }

    private Map<UUID, Integer> quantitiesByVariant(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new ConflictException("Não foi possível reconciliar o estoque do pedido.");
        }
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            if (item == null || item.getProductVariant() == null || item.getQuantity() <= 0) {
                throw new ConflictException("O pedido contém um item inválido para reconciliar o estoque.");
            }
            UUID variantId = item.getProductVariant().getId();
            quantities.merge(variantId, item.getQuantity(), Math::addExact);
        }
        return quantities;
    }

    private Map<UUID, ProductVariant> lockVariants(java.util.Set<UUID> variantIds) {
        List<UUID> orderedIds = variantIds.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        Map<UUID, ProductVariant> variants = new LinkedHashMap<>();
        for (ProductVariant variant : productVariantRepository.findAllByIdInForUpdate(orderedIds)) {
            variants.put(variant.getId(), variant);
        }
        if (variants.size() != orderedIds.size()) {
            throw new ResourceNotFoundException("Uma variação do pedido não foi encontrada.");
        }
        return variants;
    }
}
