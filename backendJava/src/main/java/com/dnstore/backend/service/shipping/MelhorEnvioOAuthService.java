package com.dnstore.backend.service.shipping;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class MelhorEnvioOAuthService {

    private final RestClient.Builder restClientBuilder;

    @Value("${melhor-envio.client-id}")
    private String clientId;

    @Value("${melhor-envio.client-secret}")
    private String clientSecret;

    @Value("${melhor-envio.redirect-uri}")
    private String redirectUri;

    @Value("${melhor-envio.base-url}")
    private String baseUrl;

    public TokenResponse exchangeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(
                    "Código de autorização do Melhor Envio é obrigatório.");
        }

        RestClient client = restClientBuilder
                .baseUrl(baseUrl)
                .build();

        Map<String, String> body = Map.of(
                "grant_type", "authorization_code",
                "client_id", clientId,
                "client_secret", clientSecret,
                "redirect_uri", redirectUri,
                "code", code);

        TokenResponse response = client.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException(
                    "Melhor Envio não retornou um access token.");
        }

        return response;
    }

    public record TokenResponse(
            @JsonProperty("token_type") String tokenType,

            @JsonProperty("expires_in") Long expiresIn,

            @JsonProperty("access_token") String accessToken,

            @JsonProperty("refresh_token") String refreshToken) {
    }
}