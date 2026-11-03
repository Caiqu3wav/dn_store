package com.dnstore.backend.dto;

import com.dnstore.backend.model.Category;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.Product;
import com.dnstore.backend.model.ProductImage;
import com.dnstore.backend.model.ProductVariant;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        boolean active,
        BigDecimal promotionalPrice,
        String color,
        CategoryResponse category,
        List<ImageResponse> images,
        List<VariantResponse> variants,
        Double shippingWeight,
        Integer stock,
        Double weight,
        Double width,
        Double height,
        Double depth) {

    public static ProductResponse from(Product product) {
        Category category = product.getCategory();
        PhysicalProduct physicalProduct = product instanceof PhysicalProduct physical
                ? physical
                : null;

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.isActive(),
                product.getPromotionalPrice(),
                product.getColor(),
                category == null ? null : new CategoryResponse(
                        category.getId(), category.getName(), category.getSlug(), category.getDescription()),
                product.getImages().stream().map(ImageResponse::from).toList(),
                product.getVariants().stream().map(VariantResponse::from).toList(),
                physicalProduct == null ? null : physicalProduct.getShippingWeight(),
                physicalProduct == null ? null : physicalProduct.getStock(),
                physicalProduct == null ? null : physicalProduct.getWeight(),
                physicalProduct == null ? null : physicalProduct.getWidth(),
                physicalProduct == null ? null : physicalProduct.getHeight(),
                physicalProduct == null ? null : physicalProduct.getDepth());
    }

    public record CategoryResponse(UUID id, String name, String slug, String description) {
    }

    public record ImageResponse(UUID id, String imageUrl, boolean main) {
        private static ImageResponse from(ProductImage image) {
            return new ImageResponse(image.getId(), image.getImageUrl(), image.isMain());
        }
    }

    public record VariantResponse(
            UUID id,
            String size,
            String type,
            String color,
            int stock,
            boolean active,
            String sku,
            LocalDateTime createdAt) {
        private static VariantResponse from(ProductVariant variant) {
            return new VariantResponse(
                    variant.getId(),
                    variant.getSize(),
                    variant.getType(),
                    variant.getColor(),
                    variant.getStock(),
                    variant.isActive(),
                    variant.getSku(),
                    variant.getCreatedAt());
        }
    }
}
