package com.dnstore.backend.controller;

import com.dnstore.backend.model.Category;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.ProductImage;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.repository.CategoryRepository;
import com.dnstore.backend.repository.ProductRepository;
import com.dnstore.backend.repository.ProductVariantRepository;
import com.dnstore.backend.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerSerializationTest {

    private ProductRepository productRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        ProductService productService = new ProductService(
                productRepository,
                mock(CategoryRepository.class),
                mock(ProductVariantRepository.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService)).build();
    }

    @Test
    void listProducts_serializesCategoryImagesAndVariantsWithoutHibernateProxies() throws Exception {
        UUID productId = UUID.randomUUID();
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Ciclismo");
        category.setSlug("ciclismo");

        PhysicalProduct product = new PhysicalProduct();
        product.setId(productId);
        product.setName("Camisa DN");
        product.setDescription("Camisa oficial");
        product.setPrice(new BigDecimal("129.90"));
        product.setActive(true);
        product.setCategory(category);
        product.setImages(new ArrayList<>(List.of(new ProductImage(
                UUID.randomUUID(), product, "/assets/Images/products/camisa.jpeg", true))));
        product.setVariants(new ArrayList<>(List.of(new ProductVariant(
                UUID.randomUUID(), product, "M", "Tradicional", null, 4, true, "DN-CAMISA-M", null))));
        product.setWeight(0.5);

        when(productRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(product));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(productId.toString()))
                .andExpect(jsonPath("$[0].category.name").value("Ciclismo"))
                .andExpect(jsonPath("$[0].images[0].imageUrl")
                        .value("/assets/Images/products/camisa.jpeg"))
                .andExpect(jsonPath("$[0].variants[0].size").value("M"))
                .andExpect(jsonPath("$[0].variants[0].stock").value(4))
                .andExpect(jsonPath("$[0].shippingWeight").value(0.5))
                .andExpect(jsonPath("$[0].category.hibernateLazyInitializer").doesNotExist());
    }
}
