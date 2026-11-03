package com.dnstore.backend.controller;

import com.dnstore.backend.model.Order;
import com.dnstore.backend.model.OrderItem;
import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.ProductVariant;
import com.dnstore.backend.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerSerializationTest {

    private OrderService orderService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(null, null, null, null, null, null, null, null) {
            @Override
            public List<Order> findAll() {
                return List.of(createOrder());
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService)).build();
    }

    @Test
    void listAll_serializesOrderItemPriceTotalAndProductName() throws Exception {
        mockMvc.perform(get("/api/orders/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].items[0].quantity").value(2))
                .andExpect(jsonPath("$[0].items[0].price").value(14.99))
                .andExpect(jsonPath("$[0].items[0].totalPrice").value(29.98))
                .andExpect(jsonPath("$[0].items[0].productName").value("Camiseta DN"))
                .andExpect(jsonPath("$[0].items[0].subtotal").doesNotExist());
    }

    private Order createOrder() {
        PhysicalProduct product = new PhysicalProduct();
        product.setId(UUID.randomUUID());
        product.setName("Camiseta DN");
        product.setPrice(new BigDecimal("14.99"));

        ProductVariant variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        variant.setSize("M");

        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus("PENDING_PAYMENT");
        order.setItems(List.of(new OrderItem(order, variant, 2, new BigDecimal("14.99"))));
        return order;
    }
}
