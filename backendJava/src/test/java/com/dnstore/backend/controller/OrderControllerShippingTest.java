package com.dnstore.backend.controller;

import com.dnstore.backend.exception.ShippingGatewayException;
import com.dnstore.backend.model.User;
import com.dnstore.backend.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderControllerShippingTest {

    @Test
    void quoteShipping_whenGatewayUnavailable_shouldReturnServiceUnavailable() {
        OrderService orderService = mock(OrderService.class);
        OrderController controller = new OrderController(orderService);
        User user = new User();
        OrderController.ShippingQuoteRequest request = new OrderController.ShippingQuoteRequest();
        request.setZipCode("12500-000");
        when(orderService.quoteShipping(user, "12500-000"))
                .thenThrow(new ShippingGatewayException("provider unavailable"));

        ResponseEntity<?> response = controller.quoteShipping(user, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}