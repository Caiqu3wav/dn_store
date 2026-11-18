package com.dnstore.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResendEmailServiceTest {

    private final ResendEmailService withoutApiKey =
            new ResendEmailService("", "DN Store <no-reply@example.com>", "https://loja.example.com/");

    @Test
    void sendingWithoutApiKey_shouldFailWith503InsteadOfSilentlySkipping() {
        for (Runnable send : new Runnable[] {
                () -> withoutApiKey.sendVerificationCode("cliente@example.com", "123456"),
                () -> withoutApiKey.sendMfaCode("cliente@example.com", "123456"),
                () -> withoutApiKey.sendPasswordResetLink("cliente@example.com", "token")}) {
            assertThatThrownBy(send::run)
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        }
    }
}
