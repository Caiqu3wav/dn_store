package com.dnstore.backend.service;

import com.dnstore.backend.model.Category;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.Product;
import com.dnstore.backend.model.ProductImage;
import com.dnstore.backend.repository.CategoryRepository;
import com.dnstore.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 🥫 ProductService
 *
 * Gerencia o catálogo de produtos.
 * Utiliza MySQL via JPA Repository.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    // --- R: Read ---
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Optional<Product> findById(UUID id) {
        return productRepository.findById(id);
    }

    // --- C: Create ---
    public Product create(Product product) {
        if (product.getCategory() != null && product.getCategory().getId() != null) {
            Category cat = categoryRepository.findById(product.getCategory().getId())
                .orElseThrow(() -> new RuntimeException("Category not found"));
            product.setCategory(cat);
        }
        if (product.getImages() != null) {
            for (ProductImage img : product.getImages()) {
                img.setProduct(product);
            }
        }
        return productRepository.save(product);
    }

    // --- U: Update ---
    public Optional<Product> update(UUID id, Product updatedData) {
        return productRepository.findById(id).map(existing -> {
            existing.setName(updatedData.getName());
            existing.setPrice(updatedData.getPrice());
            existing.setDescription(updatedData.getDescription());
            existing.setPromotionalPrice(updatedData.getPromotionalPrice());
            existing.setActive(updatedData.isActive());
            existing.setColor(updatedData.getColor());

            if (updatedData.getCategory() != null && updatedData.getCategory().getId() != null) {
                Category cat = categoryRepository.findById(updatedData.getCategory().getId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
                existing.setCategory(cat);
            } else {
                existing.setCategory(null);
            }

            // Sync images
            if (updatedData.getImages() != null) {
                existing.getImages().clear();
                for (ProductImage img : updatedData.getImages()) {
                    img.setProduct(existing);
                    existing.getImages().add(img);
                }
            }

            if (existing instanceof PhysicalProduct && updatedData instanceof PhysicalProduct) {
                PhysicalProduct existingPhysical = (PhysicalProduct) existing;
                PhysicalProduct updatedPhysical = (PhysicalProduct) updatedData;
                existingPhysical.setWeight(updatedPhysical.getWeight());
                existingPhysical.setWidth(updatedPhysical.getWidth());
                existingPhysical.setHeight(updatedPhysical.getHeight());
                existingPhysical.setDepth(updatedPhysical.getDepth());
            }
            return productRepository.save(existing);
        });
    }

    public List<Product> search(
            String search,
            UUID categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sortBy) {

        Specification<Product> spec = Specification.where(null);

        if (search != null && !search.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase(java.util.Locale.ROOT) + "%")
            );
        }

        if (categoryId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("category").get("id"), categoryId)
            );
        }

        if (minPrice != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("price"), minPrice)
            );
        }

        if (maxPrice != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("price"), maxPrice)
            );
        }

        Sort sort = Sort.unsorted();
        if ("priceAsc".equalsIgnoreCase(sortBy)) {
            sort = Sort.by("price").ascending();
        } else if ("priceDesc".equalsIgnoreCase(sortBy)) {
            sort = Sort.by("price").descending();
        } else if ("name".equalsIgnoreCase(sortBy)) {
            sort = Sort.by("name").ascending();
        }

        return productRepository.findAll(spec, sort);
    }

    // --- D: Delete ---
    public boolean delete(UUID id) {
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
