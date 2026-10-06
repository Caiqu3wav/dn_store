package com.dnstore.backend.service.shipping;

import org.springframework.stereotype.Component;

@Component
public class MelhorEnvioTokenStore {

    private volatile String accessToken;

    public void save(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException(
                    "Access token do Melhor Envio não pode ser vazio.");
        }

        this.accessToken = accessToken;
    }

    public String get() {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "Melhor Envio ainda não foi autorizado.");
        }

        return accessToken;
    }

    public boolean isAvailable() {
        return accessToken != null && !accessToken.isBlank();
    }
}