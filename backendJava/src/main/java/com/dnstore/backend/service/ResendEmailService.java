package com.dnstore.backend.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResendEmailService {

    private final RestClient resendClient;
    private final String from;
    private final String apiKey;
    private final String frontendUrl;

    public ResendEmailService(
            @Value("${resend.api-key}") String apiKey,
            @Value("${resend.from}") String from,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.apiKey = apiKey;
        this.from = from;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.resendClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public void sendVerificationCode(String email, String code) {
        send(email, "Confirme seu e-mail | DN Store",
                "Seu código de confirmação é " + code + ". Ele expira em 10 minutos.");
    }

    public void sendMfaCode(String email, String code) {
        send(email, "Código de acesso | DN Store",
                "Seu código de acesso é " + code + ". Ele expira em 10 minutos. Se não foi você, ignore este e-mail.");
    }

    public void sendPasswordResetLink(String email, String token) {
        String link = frontendUrl + "/auth/recover/reset?token=" + token;
        send(email, "Redefina sua senha | DN Store",
                "Use este link para criar uma nova senha: " + link + "\nO link expira em 30 minutos. Se não foi você, ignore este e-mail.");
    }

    private void send(String email, String subject, String text) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery is not configured");
        }

        try {
            resendClient.post()
                    .uri("/emails")
                    .body(Map.of("from", from, "to", List.of(email), "subject", subject, "text", text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Email delivery failed", exception);
        }
    }
}