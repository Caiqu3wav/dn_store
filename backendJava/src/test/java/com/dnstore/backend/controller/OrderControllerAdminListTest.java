package com.dnstore.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dnstore.backend.exception.GlobalExceptionHandler;
import com.dnstore.backend.model.Order;
import com.dnstore.backend.service.OrderService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** GET /api/orders/all: sem parâmetros continua devolvendo a lista completa; limit/excludeStatus são opcionais. */
class OrderControllerAdminListTest {

    private final List<String> calls = new ArrayList<>();
    private Integer lastLimit;
    private Collection<String> lastExcluded;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderService orderService = new OrderService(null, null, null, null, null, null, null, null, null, null, null) {
            @Override
            public List<Order> findAll() {
                calls.add("findAll");
                return List.of(order());
            }

            @Override
            public List<Order> findRecent(Integer limit, Collection<String> excludedStatuses) {
                calls.add("findRecent");
                lastLimit = limit;
                lastExcluded = excludedStatuses;
                return List.of(order());
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void withoutParametersReturnsTheFullListLikeBefore() throws Exception {
        mockMvc.perform(get("/api/orders/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PAID"));

        assertThat(calls).containsExactly("findAll");
    }

    @Test
    void limitAndRepeatedExcludeStatusAreForwardedToTheService() throws Exception {
        mockMvc.perform(get("/api/orders/all")
                        .param("limit", "5")
                        .param("excludeStatus", "PENDING_PAYMENT", "PAYMENT_FAILED", "EXPIRED"))
                .andExpect(status().isOk());

        assertThat(calls).containsExactly("findRecent");
        assertThat(lastLimit).isEqualTo(5);
        assertThat(lastExcluded).containsExactly("PENDING_PAYMENT", "PAYMENT_FAILED", "EXPIRED");
    }

    @Test
    void excludeStatusAloneHasNoLimit() throws Exception {
        mockMvc.perform(get("/api/orders/all").param("excludeStatus", "EXPIRED"))
                .andExpect(status().isOk());

        assertThat(calls).containsExactly("findRecent");
        assertThat(lastLimit).isNull();
    }

    @Test
    void limitOutsideTheAllowedRangeIsRejected() throws Exception {
        mockMvc.perform(get("/api/orders/all").param("limit", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/orders/all").param("limit", "101")).andExpect(status().isBadRequest());

        assertThat(calls).isEmpty();
    }

    private static Order order() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus("PAID");
        return order;
    }
}
