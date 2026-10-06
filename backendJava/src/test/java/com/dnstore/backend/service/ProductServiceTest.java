package com.dnstore.backend.service;

import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.model.Category;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.repository.CategoryRepository;
import com.dnstore.backend.repository.ProductRepository;
import com.dnstore.backend.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private ProductService productService;

    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Roupas");
        category.setSlug("roupas");
    }

    @Test
    void create_validPhysicalProduct_shouldPersistAndCreateDefaultVariant() {
        PhysicalProduct product = new PhysicalProduct();
        product.setName("Camiseta");
        product.setDescription("Produto de teste");
        product.setPrice(new BigDecimal("99.90"));
        product.setStock(12);
        product.setActive(true);
        product.setCategory(category);

        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(productRepository.save(any(PhysicalProduct.class))).thenAnswer(invocation -> {
            PhysicalProduct saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(productVariantRepository.findByProductId(any(UUID.class))).thenReturn(List.of());

        PhysicalProduct saved = (PhysicalProduct) productService.create(product);

        assertThat(saved.getId()).isNotNull();
        verify(productVariantRepository)
                .save(argThat(variant -> "Único".equals(variant.getSize()) && variant.getStock() == 12));
    }

    @Test
    void create_whenPriceIsNegative_shouldFail() {
        PhysicalProduct product = new PhysicalProduct();
        product.setName("Camiseta");
        product.setPrice(new BigDecimal("-1.00"));
        product.setStock(5);
        product.setCategory(category);

        assertThatThrownBy(() -> productService.create(product))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Preço");
    }

    @Test
    void create_whenStockIsNegative_shouldFail() {
        PhysicalProduct product = new PhysicalProduct();
        product.setName("Camiseta");
        product.setPrice(new BigDecimal("50.00"));
        product.setStock(-1);
        product.setCategory(category);

        assertThatThrownBy(() -> productService.create(product))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Estoque");
    }

    @Test
    void create_whenCategoryDoesNotExist_shouldFail() {
        PhysicalProduct product = new PhysicalProduct();
        product.setName("Camiseta");
        product.setPrice(new BigDecimal("50.00"));
        product.setStock(5);
        product.setCategory(category);

        when(categoryRepository.findById(category.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(product))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Categoria");
    }

    @Test
    void create_whenDefaultVariantAlreadyExists_shouldReuseItWithoutDuplicate() {
        PhysicalProduct product = new PhysicalProduct();
        product.setId(UUID.randomUUID());
        product.setName("Camiseta");
        product.setDescription("Produto de teste");
        product.setPrice(new BigDecimal("80.00"));
        product.setStock(6);
        product.setCategory(category);

        ProductVariant existingVariant = new ProductVariant();
        existingVariant.setId(UUID.randomUUID());
        existingVariant.setProduct(product);
        existingVariant.setSize("Único");
        existingVariant.setStock(3);

        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(productRepository.save(any(PhysicalProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productVariantRepository.findByProductId(product.getId())).thenReturn(List.of(existingVariant));

        productService.create(product);

        verify(productVariantRepository).save(existingVariant);
        assertThat(existingVariant.getStock()).isEqualTo(6);
    }
}
