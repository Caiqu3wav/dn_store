package com.dnstore.backend.service;

import com.dnstore.backend.dto.ProductResponse;
import com.dnstore.backend.model.Category;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.Product;
import com.dnstore.backend.model.ProductImage;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.ProductVariantSizes;
import com.dnstore.backend.model.enums.ProductRemovalOutcome;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.repository.CategoryRepository;
import com.dnstore.backend.repository.ProductRepository;
import com.dnstore.backend.repository.ProductVariantRepository;
import com.dnstore.backend.repository.StockMovementRepository;
import com.dnstore.backend.model.enums.StockMovementType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private final ProductDefaultsService productDefaultsService;
    private final StockLedgerService stockLedger;
    private final StockMovementRepository stockMovementRepository;

    // --- R: Read ---
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Optional<Product> findById(UUID id) {
        return productRepository.findById(id);
    }

    public Optional<Product> findActiveById(UUID id) {
        return productRepository.findByIdAndActiveTrue(id);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> searchResponses(
            String search,
            UUID categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sortBy) {
        return search(search, categoryId, minPrice, maxPrice, sortBy, null, true)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> searchForAdminResponses(
            String search,
            UUID categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sortBy) {
        return searchForAdminResponses(search, categoryId, minPrice, maxPrice, sortBy, null);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> searchForAdminResponses(
            String search,
            UUID categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sortBy,
            String color) {
        return search(search, categoryId, minPrice, maxPrice, sortBy, color, false)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ProductResponse> findActiveResponseById(UUID id) {
        return productRepository.findByIdAndActiveTrue(id).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public Optional<ProductResponse> findResponseById(UUID id) {
        return productRepository.findById(id).map(ProductResponse::from);
    }

    // --- C: Create ---
    @Transactional
    public Product create(Product product) {
        normalizeProductColor(product);
        validateProduct(product);

        if (product.getCategory() != null && product.getCategory().getId() != null) {
            Category cat = categoryRepository.findById(product.getCategory().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
            product.setCategory(cat);
        }
        productDefaultsService.applyRulesToNewProduct(product);
        validateProduct(product);
        prepareVariants(product);
        if (product.getImages() != null) {
            for (ProductImage img : product.getImages()) {
                img.setProduct(product);
            }
        }
        Product saved = productRepository.save(product);
        if (saved.getVariants() == null || saved.getVariants().isEmpty()) {
            ensureLegacyDefaultVariant(saved);
        }
        persistMissingVariantSkus(saved);
        recordInitialStock(saved);
        return saved;
    }

    // --- Batch Create ---
    @Transactional
    public List<Product> createAll(List<PhysicalProduct> products) {
        for (Product product : products) {
            normalizeProductColor(product);
            validateProduct(product);
            if (product.getCategory() != null && product.getCategory().getId() != null) {
                Category cat = categoryRepository.findById(product.getCategory().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
                product.setCategory(cat);
            }
            productDefaultsService.applyRulesToNewProduct(product);
            validateProduct(product);
            prepareVariants(product);
            if (product.getImages() != null) {
                for (ProductImage img : product.getImages()) {
                    img.setProduct(product);
                }
            }
        }
        List<Product> saved = new java.util.ArrayList<>();
        for (PhysicalProduct p : products) {
            Product savedProduct = productRepository.save(p);
            if (savedProduct.getVariants() == null || savedProduct.getVariants().isEmpty()) {
                ensureLegacyDefaultVariant(savedProduct);
            }
            persistMissingVariantSkus(savedProduct);
            recordInitialStock(savedProduct);
            saved.add(savedProduct);
        }
        return saved;
    }

    // --- U: Update ---
    @Transactional
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
                existingPhysical.setWeight(updatedPhysical.getWeight());
                existingPhysical.setWidth(updatedPhysical.getWidth());
                existingPhysical.setHeight(updatedPhysical.getHeight());
                existingPhysical.setDepth(updatedPhysical.getDepth());
            }
            List<ProductVariant> addedVariants = List.of();
            if (updatedData.getVariants() != null && !updatedData.getVariants().isEmpty()) {
                addedVariants = mergeVariants(existing, updatedData.getVariants());
            }
            synchronizeAggregateStock(existing);
            Product saved = productRepository.save(existing);
            persistMissingVariantSkus(saved);
            recordInitialStock(addedVariants);
            return saved;
        });
    }

    private void prepareVariants(Product product) {
        List<ProductVariant> variants = product.getVariants();
        if (variants == null || variants.isEmpty()) {
            return;
        }
        validateVariants(variants);
        for (ProductVariant variant : variants) {
            variant.setProduct(product);
        }
        synchronizeAggregateStock(product);
    }

    private void validateVariants(List<ProductVariant> variants) {
        Set<String> sizes = new HashSet<>();
        for (ProductVariant variant : variants) {
            if (variant == null || variant.getSize() == null
                    || !ProductVariantSizes.ALLOWED.contains(variant.getSize())) {
                throw new IllegalArgumentException("Tamanho de variação inválido.");
            }
            if (!sizes.add(variant.getSize())) {
                throw new IllegalArgumentException("O produto não pode ter tamanhos duplicados.");
            }
            if (variant.getStock() < 0) {
                throw new IllegalArgumentException("Estoque da variação não pode ser negativo.");
            }
        }
    }

    /** @return as variações novas criadas por este merge (para registrar o estoque inicial no livro). */
    private List<ProductVariant> mergeVariants(Product product, List<ProductVariant> incomingVariants) {
        validateVariants(incomingVariants);
        List<ProductVariant> currentVariants = product.getVariants() == null
                ? new ArrayList<>()
                : product.getVariants();
        List<UUID> currentIds = currentVariants.stream()
                .map(ProductVariant::getId)
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(UUID::toString))
                .toList();
        if (!currentIds.isEmpty()) {
            // Trava e RE-LÊ as variações do banco (refresh com lock): o saldo anterior registrado no livro precisa
            // ser o real, não o da coleção carregada antes do lock. O valor absoluto do formulário continua
            // sobrescrevendo (comportamento anterior); o livro registra a diferença real.
            stockLedger.lockVariants(currentIds);
        }
        List<ProductVariant> added = new ArrayList<>();
        for (ProductVariant incoming : incomingVariants) {
            ProductVariant current = currentVariants.stream()
                    .filter(variant -> incoming.getSize().equals(variant.getSize()))
                    .findFirst()
                    .orElse(null);
            if (current == null) {
                incoming.setProduct(product);
                currentVariants.add(incoming);
                added.add(incoming);
            } else {
                int stockBefore = current.getStock();
                current.setStock(incoming.getStock());
                stockLedger.recordApplied(current, stockBefore, StockLedgerService.MovementSpec
                        .of(StockMovementType.EDICAO_CADASTRO)
                        .withReason(null, "Alteração de estoque pelo cadastro do produto"));
                current.setActive(incoming.isActive());
                current.setColor(incoming.getColor());
                current.setType(incoming.getType());
                if (incoming.getSku() != null && !incoming.getSku().isBlank()) {
                    current.setSku(incoming.getSku());
                }
            }
        }
        product.setVariants(currentVariants);
        synchronizeAggregateStock(product);
        return added;
    }

    private void recordInitialStock(Product product) {
        if (product.getVariants() != null) {
            recordInitialStock(product.getVariants());
        }
    }

    /** Variações recém-criadas com estoque > 0 entram no livro como saldo vindo do cadastro (antes = 0). */
    private void recordInitialStock(List<ProductVariant> newVariants) {
        for (ProductVariant variant : newVariants) {
            if (variant.getStock() <= 0) {
                continue;
            }
            ProductVariant persisted = variant.getId() == null ? productVariantRepository.save(variant) : variant;
            stockLedger.recordApplied(persisted, 0, StockLedgerService.MovementSpec
                    .of(StockMovementType.EDICAO_CADASTRO)
                    .withReason(null, "Estoque inicial informado no cadastro do produto"));
        }
    }

    private void synchronizeAggregateStock(Product product) {
        if (product instanceof PhysicalProduct physicalProduct && product.getVariants() != null) {
            int totalStock = product.getVariants().stream()
                    .filter(ProductVariant::isActive)
                    .mapToInt(ProductVariant::getStock)
                    .reduce(0, Math::addExact);
            physicalProduct.setStock(totalStock);
        }
    }

    private void persistMissingVariantSkus(Product product) {
        if (product.getVariants() == null) {
            return;
        }
        for (ProductVariant variant : product.getVariants()) {
            if (variant.getSku() == null || variant.getSku().isBlank()) {
                variant.setSku("DN-" + product.getId() + "-" + variant.getSize().replace(' ', '-'));
                productVariantRepository.save(variant);
            }
        }
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
        return search(search, categoryId, minPrice, maxPrice, sortBy, null, true);
    }

    public List<Product> searchForAdmin(
            String search,
            UUID categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sortBy) {
        return search(search, categoryId, minPrice, maxPrice, sortBy, null, false);
    }

    private List<Product> search(
            String search,
            UUID categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sortBy,
            String color,
            boolean activeOnly) {
        Specification<Product> spec = Specification.where(null);
        if (activeOnly) {
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("active")));
        }

        if (search != null && !search.isBlank()) {
            String escapedSearch = search.toLowerCase(java.util.Locale.ROOT)
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")),
                    "%" + escapedSearch + "%", '\\'));
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

        if (color != null && !color.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(
                    cb.lower(root.get("color")), color.trim().toLowerCase(java.util.Locale.ROOT)));
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

        List<ProductVariant> existingVariants = new ArrayList<>(
                productVariantRepository.findByProductId(product.getId()));
        product.setVariants(existingVariants);
        boolean hasDefaultVariant = existingVariants.stream()
                .anyMatch(variant -> "Único".equals(variant.getSize()));
        if (hasDefaultVariant) {
            return;
        }

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSize("Único");
        variant.setColor(product.getColor());
        variant.setStock(Math.max(0, physicalProduct.getStock()));
        variant.setSku("DN-" + product.getId() + "-Único");
        ProductVariant savedVariant = productVariantRepository.save(variant);
        if (savedVariant != null) {
            product.getVariants().add(savedVariant);
        } else {
            product.getVariants().add(variant);
        }
    }

    /**
     * Remove o produto. Se já houver pedidos ou carrinhos que o referenciam, o banco impede o
     * DELETE (FK em order_items/cart_items/stock_movement); nesse caso ele é apenas arquivado
     * (active=false), preservando o histórico de pedidos e do livro de estoque.
     *
     * @return o resultado da remoção, ou vazio se o produto não existe
     */
    @Transactional
    public Optional<ProductRemovalOutcome> delete(UUID id) {
        return productRepository.findById(id).map(product -> {
            if (productRepository.isReferencedByOrders(id) || productRepository.isReferencedByCarts(id)
                    || stockMovementRepository.isProductReferenced(id)) {
                product.setActive(false);
                productRepository.save(product);
                return ProductRemovalOutcome.ARCHIVED;
            }
            productRepository.delete(product);
            return ProductRemovalOutcome.DELETED;
        });
    }
}
