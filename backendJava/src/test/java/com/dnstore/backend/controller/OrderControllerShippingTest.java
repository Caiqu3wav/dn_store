package com.dnstore.backend.controller;

import com.dnstore.backend.exception.ShippingGatewayException;
import com.dnstore.backend.exception.GlobalExceptionHandler;
import com.dnstore.backend.model.User;
import com.dnstore.backend.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderControllerShippingTest {

    @Test
    void quoteShipping_whenGatewayUnavailable_shouldReturnServiceUnavailable() {
        OrderService orderService = mock(OrderService.class);
        OrderController controller = new OrderController(orderService);
        when(orderService.quoteShipping(nullable(User.class), eq("12500-000")))
                .thenThrow(new ShippingGatewayException("provider unavailable"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        try {
            mockMvc.perform(post("/api/orders/shipping-quotes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"zipCode\":\"12500-000\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(HttpStatus.SERVICE_UNAVAILABLE.value()))
                    .andExpect(jsonPath("$.message").value("Shipping quote is temporarily unavailable."));
        } catch (Exception exception) {
            throw new AssertionError("Shipping quote endpoint did not use the global error handler", exception);
        }
    }
}