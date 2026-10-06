package com.dnstore.backend.service;

import com.dnstore.backend.model.Category;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.Product;
import com.dnstore.backend.model.ProductImage;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.repository.CategoryRepository;
import com.dnstore.backend.repository.ProductRepository;
import com.dnstore.backend.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
    private final ProductVariantRepository productVariantRepository;

    // --- R: Read ---
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Optional<Product> findById(UUID id) {
        return productRepository.findById(id);
    }

    // --- C: Create ---
    public Product create(Product product) {
        normalizeProductColor(product);
        validateProduct(product);

        if (product.getCategory() != null && product.getCategory().getId() != null) {
            Category cat = categoryRepository.findById(product.getCategory().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
            product.setCategory(cat);
        }
        if (product.getImages() != null) {
            for (ProductImage img : product.getImages()) {
                img.setProduct(product);
            }
        }
        Product saved = productRepository.save(product);
        ensureLegacyDefaultVariant(saved);
        return saved;
    }

    // --- Batch Create ---
    public List<Product> createAll(List<PhysicalProduct> products) {
        for (Product product : products) {
            normalizeProductColor(product);
            validateProduct(product);
            if (product.getCategory() != null && product.getCategory().getId() != null) {
                Category cat = categoryRepository.findById(product.getCategory().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
                product.setCategory(cat);
            }
            if (product.getImages() != null) {
                for (ProductImage img : product.getImages()) {
                    img.setProduct(product);
                }
            }
        }
        List<Product> saved = new java.util.ArrayList<>();
        for (PhysicalProduct p : products) {
            Product savedProduct = productRepository.save(p);
            ensureLegacyDefaultVariant(savedProduct);
            saved.add(savedProduct);
        }
        return saved;
    }

    // --- U: Update ---
    public Optional<Product> update(UUID id, Product updatedData) {
        validateProduct(updatedData);

        return productRepository.findById(id).map(existing -> {
            existing.setName(updatedData.getName());
            existing.setPrice(updatedData.getPrice());
            existing.setDescription(updatedData.getDescription());
            existing.setPromotionalPrice(updatedData.getPromotionalPrice());
            existing.setActive(updatedData.isActive());
            normalizeProductColor(updatedData);
            existing.setColor(updatedData.getColor());

            if (updatedData.getCategory() != null && updatedData.getCategory().getId() != null) {
                Category cat = categoryRepository.findById(updatedData.getCategory().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
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
                existingPhysical.setStock(updatedPhysical.getStock());
                existingPhysical.setWeight(updatedPhysical.getWeight());
                existingPhysical.setWidth(updatedPhysical.getWidth());
                existingPhysical.setHeight(updatedPhysical.getHeight());
                existingPhysical.setDepth(updatedPhysical.getDepth());
                existingPhysical.setStock(updatedPhysical.getStock());
            }
            Product saved = productRepository.save(existing);
            ensureLegacyDefaultVariant(saved);
            return saved;
        });
    }

    private void normalizeProductColor(Product product) {
        try {
            product.setColor(ProductColorNormalizer.normalize(product.getColor()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    public List<Product> search(
            String search,
            UUID categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sortBy) {

        Specification<Product> spec = Specification.where(null);

        if (search != null && !search.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")),
                    "%" + search.toLowerCase(java.util.Locale.ROOT) + "%"));
        }

        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }

        if (minPrice != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice));
        }

        if (maxPrice != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice));
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

    private void validateProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("Produto é obrigatório.");
        }
        if (product.getName() == null || product.getName().isBlank()) {
            throw new IllegalArgumentException("Nome do produto é obrigatório.");
        }
        if (product.getPrice() == null) {
            throw new IllegalArgumentException("Preço do produto é obrigatório.");
        }
        if (product.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Preço do produto não pode ser negativo.");
        }
        if (product instanceof PhysicalProduct physicalProduct && physicalProduct.getStock() < 0) {
            throw new IllegalArgumentException("Estoque do produto não pode ser negativo.");
        }
        if (product.getCategory() != null && product.getCategory().getId() != null) {
            categoryRepository.findById(product.getCategory().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
        }
    }

    // --- D: Delete ---
    private void ensureLegacyDefaultVariant(Product product) {
        if (!(product instanceof PhysicalProduct physicalProduct)) {
            return;
        }

        List<ProductVariant> existingVariants = productVariantRepository.findByProductId(product.getId());
        ProductVariant defaultVariant = existingVariants.stream()
                .filter(variant -> variant.getSize() != null && variant.getSize().equalsIgnoreCase("Único"))
                .findFirst()
                .orElse(null);

        if (defaultVariant != null) {
            defaultVariant.setColor(product.getColor());
            defaultVariant.setProduct(product);
            defaultVariant.setStock(Math.max(0, physicalProduct.getStock()));
            productVariantRepository.save(defaultVariant);
            physicalProduct.setStock(defaultVariant.getStock());
            productRepository.save(physicalProduct);
            return;
        }

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSize("Único");
        variant.setColor(product.getColor());
        variant.setStock(Math.max(0, physicalProduct.getStock()));
        productVariantRepository.save(variant);
    }

    public boolean delete(UUID id) {
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
