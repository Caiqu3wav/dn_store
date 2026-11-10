package com.dnstore.backend.service;

import com.dnstore.backend.dto.ProductDefaultRuleRequest;
import com.dnstore.backend.dto.ProductDefaultRuleResponse;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.Category;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.Product;
import com.dnstore.backend.model.ProductDefaultRule;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.model.ProductVariantSizes;
import com.dnstore.backend.repository.CategoryRepository;
import com.dnstore.backend.repository.ProductDefaultRuleRepository;
import com.dnstore.backend.repository.ProductRepository;
import com.dnstore.backend.repository.ProductVariantRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductDefaultsService {

    private final ProductDefaultRuleRepository ruleRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ProductDefaultRuleResponse> listRules() {
        return ruleRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductDefaultRuleResponse createRule(ProductDefaultRuleRequest request) {
        ProductDefaultRuleRequest normalized = validateAndNormalize(request);
        Set<UUID> excludedIds = new HashSet<>(
                normalized.excludedProductIds() == null ? List.of() : normalized.excludedProductIds());
        List<Product> matches = findMatches(normalized.criteria());
        List<Product> toUpdate = matches.stream()
                .filter(product -> !excludedIds.contains(product.getId()))
                .toList();

        for (Product product : toUpdate) {
            applyValues(product, normalized.values());
        }

        List<Product> savedProducts = productRepository.saveAll(toUpdate);
        savedProducts.forEach(this::persistMissingVariantSkus);

        ProductDefaultRule rule = new ProductDefaultRule();
        rule.setName(normalized.name());
        rule.setCriteriaJson(writeJson(normalized.criteria()));
        rule.setValuesJson(writeJson(normalized.values()));
        rule.setExcludedProductIdsJson(writeJson(List.copyOf(excludedIds)));
        rule.setUpdatedProducts(savedProducts.size());
        return toResponse(ruleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(UUID id) {
        if (!ruleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Padrão de produto não encontrado.");
        }
        ruleRepository.deleteById(id);
    }

    @Transactional
    public void applyRulesToNewProduct(Product product) {
        List<CompiledRule> matchingRules = ruleRepository.findAllByOrderByCreatedAtAscIdAsc().stream()
                .map(this::compile)
                .filter(rule -> matches(product, rule.criteria()))
                .filter(rule -> product.getId() == null || !rule.excludedProductIds().contains(product.getId()))
                .toList();

        for (CompiledRule rule : matchingRules) {
            applyValues(product, rule.values());
        }
    }

    private ProductDefaultRuleRequest validateAndNormalize(ProductDefaultRuleRequest request) {
        if (request == null || request.criteria() == null || request.values() == null) {
            throw new IllegalArgumentException("Informe os filtros e os valores do padrão.");
        }
        if (request.excludedProductIds() != null
                && request.excludedProductIds().stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("A lista de produtos excluídos contém um ID inválido.");
        }
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank() || name.length() > 150) {
            throw new IllegalArgumentException("Informe um nome de até 150 caracteres para o padrão.");
        }

        ProductDefaultRuleRequest.Criteria criteria = normalizeCriteria(request.criteria());
        ProductDefaultRuleRequest.Values values = normalizeValues(request.values());
        if (criteria.nameContains() == null && criteria.categoryId() == null && criteria.color() == null
                && criteria.minPrice() == null && criteria.maxPrice() == null) {
            throw new IllegalArgumentException("Defina ao menos um filtro para o padrão.");
        }
        if (!hasValues(values)) {
            throw new IllegalArgumentException("Selecione ao menos um campo para editar.");
        }
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new IllegalArgumentException("O preço mínimo não pode superar o preço máximo.");
        }
        if (values.categoryId() != null) {
            categoryRepository.findById(values.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
        }
        return new ProductDefaultRuleRequest(
                name,
                criteria,
                values,
                request.excludedProductIds() == null ? List.of() : List.copyOf(request.excludedProductIds()));
    }

    private ProductDefaultRuleRequest.Criteria normalizeCriteria(ProductDefaultRuleRequest.Criteria criteria) {
        String nameContains = trimToNull(criteria.nameContains());
        String color = normalizeColor(criteria.color());
        BigDecimal minPrice = criteria.minPrice();
        BigDecimal maxPrice = criteria.maxPrice();
        validateNonNegative(minPrice, "Preço mínimo");
        validateNonNegative(maxPrice, "Preço máximo");
        return new ProductDefaultRuleRequest.Criteria(
                nameContains, criteria.categoryId(), color, minPrice, maxPrice);
    }

    private ProductDefaultRuleRequest.Values normalizeValues(ProductDefaultRuleRequest.Values values) {
        String name = trimToNull(values.name());
        if (values.name() != null && (name == null || name.length() > 150)) {
            throw new IllegalArgumentException("O nome padrão deve ter entre 1 e 150 caracteres.");
        }
        validateNonNegative(values.price(), "Preço");
        validateNonNegative(values.promotionalPrice(), "Preço promocional");
        if (Boolean.TRUE.equals(values.clearPromotionalPrice()) && values.promotionalPrice() != null) {
            throw new IllegalArgumentException("Escolha entre definir ou remover o preço promocional.");
        }
        if (Boolean.TRUE.equals(values.clearCategory()) && values.categoryId() != null) {
            throw new IllegalArgumentException("Escolha entre definir ou remover a categoria.");
        }
        if (Boolean.TRUE.equals(values.clearColor()) && values.color() != null) {
            throw new IllegalArgumentException("Escolha entre definir ou remover a cor.");
        }

        Double weight = positiveOrNull(values.weight(), "Peso");
        Double width = positiveOrNull(values.width(), "Largura");
        Double height = positiveOrNull(values.height(), "Altura");
        Double depth = positiveOrNull(values.depth(), "Profundidade");
        List<String> sizes = values.sizes() == null ? null : List.copyOf(values.sizes());
        if (sizes != null) {
            Set<String> uniqueSizes = new HashSet<>();
            for (String size : sizes) {
                if (!ProductVariantSizes.ALLOWED.contains(size) || !uniqueSizes.add(size)) {
                    throw new IllegalArgumentException("A lista de tamanhos possui valores inválidos ou repetidos.");
                }
            }
            if (sizes.isEmpty()) {
                sizes = null;
            }
        }
        return new ProductDefaultRuleRequest.Values(
                name,
                values.description(),
                values.price(),
                values.promotionalPrice(),
                Boolean.TRUE.equals(values.clearPromotionalPrice()),
                values.active(),
                normalizeColor(values.color()),
                Boolean.TRUE.equals(values.clearColor()),
                values.categoryId(),
                Boolean.TRUE.equals(values.clearCategory()),
                weight,
                width,
                height,
                depth,
                sizes);
    }

    private boolean hasValues(ProductDefaultRuleRequest.Values values) {
        return values.name() != null || values.description() != null || values.price() != null
                || values.promotionalPrice() != null || Boolean.TRUE.equals(values.clearPromotionalPrice())
                || values.active() != null || values.color() != null
                || Boolean.TRUE.equals(values.clearColor()) || values.categoryId() != null
                || Boolean.TRUE.equals(values.clearCategory()) || values.weight() != null
                || values.width() != null || values.height() != null || values.depth() != null
                || values.sizes() != null;
    }

    private void validateNonNegative(BigDecimal value, String label) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(label + " não pode ser negativo.");
        }
    }

    private Double positiveOrNull(Double value, String label) {
        if (value != null && (!Double.isFinite(value) || value <= 0)) {
            throw new IllegalArgumentException(label + " deve ser maior que zero.");
        }
        return value;
    }

    private String normalizeColor(String color) {
        if (color == null || color.isBlank()) {
            return null;
        }
        try {
            return ProductColorNormalizer.normalize(color);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Selecione uma cor válida da lista.", exception);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private List<Product> findMatches(ProductDefaultRuleRequest.Criteria criteria) {
        return productRepository.findAll(criteriaFor(criteria), Sort.unsorted());
    }

    private Specification<Product> criteriaFor(ProductDefaultRuleRequest.Criteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.nameContains() != null) {
                predicates.add(builder.like(
                        builder.lower(root.get("name")),
                        "%" + escapeLike(criteria.nameContains().toLowerCase(Locale.ROOT)) + "%",
                        '\\'));
            }
            if (criteria.categoryId() != null) {
                predicates.add(builder.equal(root.get("category").get("id"), criteria.categoryId()));
            }
            if (criteria.color() != null) {
                predicates.add(builder.equal(builder.lower(root.get("color")),
                        criteria.color().toLowerCase(Locale.ROOT)));
            }
            if (criteria.minPrice() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("price"), criteria.minPrice()));
            }
            if (criteria.maxPrice() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("price"), criteria.maxPrice()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private boolean matches(Product product, ProductDefaultRuleRequest.Criteria criteria) {
        if (criteria.nameContains() != null
                && (product.getName() == null
                || !product.getName().toLowerCase(Locale.ROOT)
                        .contains(criteria.nameContains().toLowerCase(Locale.ROOT)))) {
            return false;
        }
        if (criteria.categoryId() != null
                && (product.getCategory() == null
                || !criteria.categoryId().equals(product.getCategory().getId()))) {
            return false;
        }
        if (criteria.color() != null
                && (product.getColor() == null
                || !criteria.color().equalsIgnoreCase(product.getColor()))) {
            return false;
        }
        if (criteria.minPrice() != null
                && (product.getPrice() == null || product.getPrice().compareTo(criteria.minPrice()) < 0)) {
            return false;
        }
        return criteria.maxPrice() == null
                || product.getPrice() != null && product.getPrice().compareTo(criteria.maxPrice()) <= 0;
    }

    private void applyValues(Product product, ProductDefaultRuleRequest.Values values) {
        if (values.name() != null) product.setName(values.name());
        if (values.description() != null) product.setDescription(values.description());
        if (values.price() != null) product.setPrice(values.price());
        if (Boolean.TRUE.equals(values.clearPromotionalPrice())) {
            product.setPromotionalPrice(null);
        } else if (values.promotionalPrice() != null) {
            product.setPromotionalPrice(values.promotionalPrice());
        }
        if (values.active() != null) product.setActive(values.active());
        if (Boolean.TRUE.equals(values.clearColor())) {
            product.setColor(null);
        } else if (values.color() != null) {
            product.setColor(values.color());
        }
        if (Boolean.TRUE.equals(values.clearCategory())) {
            product.setCategory(null);
        } else if (values.categoryId() != null) {
            Category category = categoryRepository.findById(values.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
            product.setCategory(category);
        }

        if (product instanceof PhysicalProduct physical) {
            if (values.weight() != null) physical.setWeight(values.weight());
            if (values.width() != null) physical.setWidth(values.width());
            if (values.height() != null) physical.setHeight(values.height());
            if (values.depth() != null) physical.setDepth(values.depth());
        }

        if (values.sizes() != null) {
            addMissingVariants(product, values.sizes());
        }
    }

    private void addMissingVariants(Product product, List<String> sizes) {
        List<ProductVariant> variants = product.getVariants() == null
                ? new ArrayList<>()
                : new ArrayList<>(product.getVariants());
        if (variants.isEmpty() && product instanceof PhysicalProduct physical && physical.getStock() > 0) {
            ProductVariant legacyVariant = new ProductVariant();
            legacyVariant.setProduct(product);
            legacyVariant.setSize("Único");
            legacyVariant.setColor(product.getColor());
            legacyVariant.setStock(physical.getStock());
            legacyVariant.setActive(true);
            variants.add(legacyVariant);
        }
        Set<String> existingSizes = variants.stream()
                .map(ProductVariant::getSize)
                .collect(java.util.stream.Collectors.toSet());
        for (String size : sizes) {
            if (existingSizes.add(size)) {
                ProductVariant variant = new ProductVariant();
                variant.setProduct(product);
                variant.setSize(size);
                variant.setColor(product.getColor());
                variant.setStock(0);
                variant.setActive(true);
                variants.add(variant);
            }
        }
        product.setVariants(variants);
    }

    private void persistMissingVariantSkus(Product product) {
        if (product.getVariants() == null || product.getId() == null) {
            return;
        }
        for (ProductVariant variant : product.getVariants()) {
            if (variant.getSku() == null || variant.getSku().isBlank()) {
                variant.setSku("DN-" + product.getId() + "-" + variant.getSize().replace(' ', '-'));
                productVariantRepository.save(variant);
            }
        }
    }

    private ProductDefaultRuleResponse toResponse(ProductDefaultRule rule) {
        return new ProductDefaultRuleResponse(
                rule.getId(),
                rule.getName(),
                readJson(rule.getCriteriaJson(), ProductDefaultRuleRequest.Criteria.class),
                readJson(rule.getValuesJson(), ProductDefaultRuleRequest.Values.class),
                readJsonList(rule.getExcludedProductIdsJson()),
                rule.getUpdatedProducts(),
                rule.getCreatedAt());
    }

    private CompiledRule compile(ProductDefaultRule rule) {
        return new CompiledRule(
                readJson(rule.getCriteriaJson(), ProductDefaultRuleRequest.Criteria.class),
                readJson(rule.getValuesJson(), ProductDefaultRuleRequest.Values.class),
                Set.copyOf(readJsonList(rule.getExcludedProductIdsJson())));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível salvar o padrão de produto.", exception);
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível ler um padrão de produto salvo.", exception);
        }
    }

    private List<UUID> readJsonList(String value) {
        try {
            return objectMapper.readerForListOf(UUID.class).readValue(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível ler as exceções do padrão de produto.", exception);
        }
    }

    private record CompiledRule(
            ProductDefaultRuleRequest.Criteria criteria,
            ProductDefaultRuleRequest.Values values,
            Set<UUID> excludedProductIds) {
    }
}
