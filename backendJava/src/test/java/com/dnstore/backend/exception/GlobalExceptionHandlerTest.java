package com.dnstore.backend.exception;

import com.dnstore.backend.service.payment.PaymentGatewayException;
import com.dnstore.backend.service.payment.WebhookAuthenticationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExceptionTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void resourceNotFound_shouldReturnStandard404() throws Exception {
        mockMvc.perform(get("/test-errors/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Pedido não encontrado."))
                .andExpect(jsonPath("$.path").value("/test-errors/missing"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void validation_shouldReturnFieldErrorsAs400() throws Exception {
        mockMvc.perform(post("/test-errors/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.quantity").exists())
                .andExpect(jsonPath("$.path").value("/test-errors/validation"));
    }

    @Test
    void malformedBody_shouldNotEchoRequestContent() throws Exception {
        mockMvc.perform(post("/test-errors/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("not-a-json-secret-value"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request parameters or body."))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret-value"))));
    }

    @Test
    void conflict_shouldReturn409() throws Exception {
        mockMvc.perform(get("/test-errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("State conflict."));
    }

    @Test
    void paymentGateway_shouldReturn502WithoutProviderDetails() throws Exception {
        mockMvc.perform(get("/test-errors/payment-gateway"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("Payment provider could not process the request."))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private-provider-detail"))));
    }

    @Test
    void shippingGateway_shouldReturn503() throws Exception {
        mockMvc.perform(get("/test-errors/shipping-gateway"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void webhookAuthentication_shouldReturn401() throws Exception {
        mockMvc.perform(get("/test-errors/webhook-auth"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void unexpectedException_shouldReturnSanitized500WithoutStackTrace() throws Exception {
        mockMvc.perform(get("/test-errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.path").value("/test-errors/unexpected"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("database password"))));
    }

    @RestController
    static class ExceptionTestController {

        @GetMapping("/test-errors/missing")
        void missing() {
            throw new ResourceNotFoundException("Pedido não encontrado.");
        }

        @PostMapping("/test-errors/validation")
        void validation(@Valid @RequestBody ValidationRequest request) {
        }

        @GetMapping("/test-errors/conflict")
        void conflict() {
            throw new ConflictException("State conflict.");
        }

        @GetMapping("/test-errors/payment-gateway")
        void paymentGateway() {
            throw new PaymentGatewayException("private-provider-detail");
        }

        @GetMapping("/test-errors/shipping-gateway")
        void shippingGateway() {
            throw new ShippingGatewayException("private-shipping-detail");
        }

        @GetMapping("/test-errors/webhook-auth")
        void webhookAuth() {
            throw new WebhookAuthenticationException();
        }

        @GetMapping("/test-errors/unexpected")
        void unexpected() {
            throw new RuntimeException("database password: internal-secret");
        }
    }

    record ValidationRequest(@NotBlank @Email String email, @Positive int quantity) {
    }
}