package com.dnstore.backend.service.shipping;

import com.dnstore.backend.config.MelhorEnvioProperties;
import com.dnstore.backend.model.MelhorEnvioOAuthState;
import com.dnstore.backend.repository.MelhorEnvioOAuthStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

@Service
@ConditionalOnProperty(name = "shipping.gateway", havingValue = "melhorEnvio")
@RequiredArgsConstructor
public class MelhorEnvioOAuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MelhorEnvioProperties properties;
    private final MelhorEnvioOAuthStateRepository stateRepository;
    private final MelhorEnvioOAuthClient oauthClient;
    private final MelhorEnvioTokenStore tokenStore;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public String createAuthorizationUrl() {
        byte[] random = new byte[32];
        SECURE_RANDOM.nextBytes(random);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

        MelhorEnvioOAuthState stateRecord = new MelhorEnvioOAuthState();
        stateRecord.setStateHash(hashState(state));
        stateRecord.setEnvironment(properties.getEnvironment());
        stateRecord.setExpiresAt(now().plus(properties.getStateTtl()));
        stateRepository.save(stateRecord);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getAuthorizationUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("state", state);
        if (properties.getScopes() != null && !properties.getScopes().isBlank()) {
            builder.queryParam("scope", properties.getScopes());
        }
        return builder.build().encode().toUriString();
    }

    @Transactional
    public boolean consumeState(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        return stateRepository.consume(
                hashState(state),
                properties.getEnvironment(),
                now()) == 1;
    }

    public boolean completeAuthorization(String code, String providerError) {
        if (providerError != null && !providerError.isBlank()) {
            return false;
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Melhor Envio authorization code is required.");
        }

        MelhorEnvioOAuthClient.TokenResponse tokens;
        try {
            tokens = oauthClient.exchangeCode(code);
        } catch (MelhorEnvioOAuthClient.OAuthTokenException exception) {
            if (exception.isInvalidAuthorizationCode()) {
                return false;
            }
            throw exception;
        }
        tokenStore.save(tokens);
        return true;
    }

    public String frontendRedirect(String result) {
        return UriComponentsBuilder.fromUriString(frontendUrl)
                .replacePath("/admin")
                .replaceQuery(null)
                .queryParam("integration", "melhor-envio")
                .queryParam("status", result)
                .build()
                .encode()
                .toUriString();
    }

    private String hashState(String state) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(state.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
