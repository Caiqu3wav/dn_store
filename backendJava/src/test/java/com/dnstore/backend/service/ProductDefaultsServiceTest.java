package com.dnstore.backend.service;

import com.dnstore.backend.dto.ProductDefaultRuleRequest;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.Product;
import com.dnstore.backend.model.ProductDefaultRule;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.repository.CategoryRepository;
import com.dnstore.backend.repository.ProductDefaultRuleRepository;
import com.dnstore.backend.repository.ProductRepository;
import com.dnstore.backend.repository.ProductVariantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductDefaultsServiceTest {

    @Mock
    private ProductDefaultRuleRepository ruleRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private ProductDefaultsService service;

    @BeforeEach
    void setUp() {
        service = new ProductDefaultsService(
                ruleRepository, productRepository, productVariantRepository, categoryRepository, new ObjectMapper());
    }

    @Test
    void createRule_shouldUpdateMatchesAndPreserveExcludedProductsAndInventory() {
        UUID excludedId = UUID.randomUUID();
        PhysicalProduct included = product(UUID.randomUUID(), "Camisa DN Ciclismo Preta");
        PhysicalProduct excluded = product(excludedId, "Camisa DN Ciclismo Azul");
        ProductVariant existingVariant = new ProductVariant();
        existingVariant.setProduct(included);
        existingVariant.setSize("P");
        existingVariant.setStock(7);
        included.setVariants(new ArrayList<>(List.of(existingVariant)));
        when(productRepository.findAll(any(Specification.class), eq(Sort.unsorted())))
                .thenReturn(List.of(included, excluded));
        when(productRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ruleRepository.save(any(ProductDefaultRule.class))).thenAnswer(invocation -> {
            ProductDefaultRule rule = invocation.getArgument(0);
            rule.setId(UUID.randomUUID());
            rule.setCreatedAt(LocalDateTime.now());
            return rule;
        });

        var response = service.createRule(new ProductDefaultRuleRequest(
                "Camisas de ciclismo",
                new ProductDefaultRuleRequest.Criteria("Camisa DN Ciclismo", null, null, null, null),
                new ProductDefaultRuleRequest.Values(
                        null, null, new BigDecimal("125.00"), null, false, null, null, false, null, false,
                        0.5, 30.0, 40.0, 60.0, List.of("P", "M", "G")),
                List.of(excludedId)));

        assertThat(response.updatedProducts()).isEqualTo(1);
        assertThat(response.excludedProductIds()).containsExactly(excludedId);
        assertThat(included.getPrice()).isEqualByComparingTo("125.00");
        assertThat(included.getWeight()).isEqualTo(0.5);
        assertThat(included.getWidth()).isEqualTo(30.0);
        assertThat(included.getHeight()).isEqualTo(40.0);
        assertThat(included.getDepth()).isEqualTo(60.0);
        assertThat(included.getVariants()).extracting(ProductVariant::getSize).containsExactly("P", "M", "G");
        assertThat(included.getVariants().get(0).getStock()).isEqualTo(7);
        assertThat(included.getVariants().get(1).getStock()).isZero();
        assertThat(excluded.getPrice()).isEqualByComparingTo("100.00");
        verify(productRepository).saveAll(argThat(products -> {
            var iterator = products.iterator();
            return iterator.hasNext() && iterator.next() == included && !iterator.hasNext();
        }));
    }

    @Test
    void applyRulesToNewProduct_shouldApplyMatchingDefaultsAndAddMissingSizesAtZeroStock() {
        ProductDefaultRule rule = new ProductDefaultRule();
        rule.setCriteriaJson("{\"nameContains\":\"Camisa DN Ciclismo\"}");
        rule.setValuesJson("{\"price\":125.00,\"sizes\":[\"P\",\"M\"]}");
        rule.setExcludedProductIdsJson("[]");
        when(ruleRepository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(List.of(rule));
        PhysicalProduct product = product(null, "Camisa DN Ciclismo Azul");
        ProductVariant existingVariant = new ProductVariant();
        existingVariant.setProduct(product);
        existingVariant.setSize("P");
        existingVariant.setStock(4);
        product.setVariants(new ArrayList<>(List.of(existingVariant)));

        service.applyRulesToNewProduct(product);

        assertThat(product.getPrice()).isEqualByComparingTo("125.00");
        assertThat(product.getVariants()).extracting(ProductVariant::getSize).containsExactly("P", "M");
        assertThat(product.getVariants().get(0).getStock()).isEqualTo(4);
        assertThat(product.getVariants().get(1).getStock()).isZero();
    }

    @Test
    void applyRulesToNewProduct_shouldPreserveLegacyStockWhenAddingVariants() {
        ProductDefaultRule rule = new ProductDefaultRule();
        rule.setCriteriaJson("{\"nameContains\":\"Camisa DN Ciclismo\"}");
        rule.setValuesJson("{\"sizes\":[\"M\"]}");
        rule.setExcludedProductIdsJson("[]");
        when(ruleRepository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(List.of(rule));
        PhysicalProduct product = product(null, "Camisa DN Ciclismo");
        product.setStock(9);

        service.applyRulesToNewProduct(product);

        assertThat(product.getVariants()).extracting(ProductVariant::getSize).containsExactly("Único", "M");
        assertThat(product.getVariants().get(0).getStock()).isEqualTo(9);
        assertThat(product.getVariants().get(1).getStock()).isZero();
    }

    @Test
    void createRule_shouldRejectRulesWithoutAFilterOrEditableField() {
        assertThatThrownBy(() -> service.createRule(new ProductDefaultRuleRequest(
                "Padrão", new ProductDefaultRuleRequest.Criteria(null, null, null, null, null),
                new ProductDefaultRuleRequest.Values(null, null, new BigDecimal("10"), null, false,
                        null, null, false, null, false, null, null, null, null, null),
                List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filtro");

        verifyNoInteractions(productRepository, ruleRepository);
    }

    private PhysicalProduct product(UUID id, String name) {
        PhysicalProduct product = new PhysicalProduct();
        product.setId(id);
        product.setName(name);
        product.setPrice(new BigDecimal("100.00"));
        product.setVariants(new ArrayList<>());
        return product;
    }
}
