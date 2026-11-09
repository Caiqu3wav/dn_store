
package com.dnstore.backend.service.shipping;

import com.dnstore.backend.config.MelhorEnvioProperties;
import com.dnstore.backend.exception.ShippingGatewayException;
import com.dnstore.backend.model.MelhorEnvioToken;
import com.dnstore.backend.repository.MelhorEnvioTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@ConditionalOnProperty(name = "shipping.gateway", havingValue = "melhorEnvio")
@RequiredArgsConstructor
public class MelhorEnvioTokenStore {

    private static final String AUTHORIZED = "AUTHORIZED";
    private static final String REAUTHORIZATION_REQUIRED = "REAUTH_REQUIRED";

    private final MelhorEnvioTokenRepository tokenRepository;
    private final MelhorEnvioOAuthClient oauthClient;
    private final TokenCipher tokenCipher;
    private final MelhorEnvioProperties properties;

    @Transactional
    public void save(MelhorEnvioOAuthClient.TokenResponse tokens) {
        validateTokens(tokens);

        String environment = properties.getEnvironment();

        MelhorEnvioToken token = tokenRepository.findById(environment)
                .orElseGet(MelhorEnvioToken::new);

        token.setEnvironment(environment);
        token.setAccessTokenCiphertext(
                tokenCipher.encrypt(tokens.accessToken(), aad(environment, "access")));
        token.setRefreshTokenCiphertext(
                tokenCipher.encrypt(tokens.refreshToken(), aad(environment, "refresh")));
        token.setExpiresAt(now().plusSeconds(tokens.expiresIn()));
        token.setRefreshExpiresAt(now().plus(properties.getRefreshTokenTtl()));
        token.setStatus(AUTHORIZED);

        tokenRepository.save(token);
    }

    @Transactional(noRollbackFor = ShippingGatewayException.class)
    public String getValidAccessToken() {
        MelhorEnvioToken token = lockedToken();

        if (REAUTHORIZATION_REQUIRED.equals(token.getStatus())) {
            throw new ShippingGatewayException(
                    "Melhor Envio authorization must be renewed.");
        }

        if (!token.getRefreshExpiresAt().isAfter(now())) {
            token.setStatus(REAUTHORIZATION_REQUIRED);
            tokenRepository.save(token);

            throw new ShippingGatewayException(
                    "Melhor Envio authorization must be renewed.");
        }

        if (token.getExpiresAt().isAfter(now().plus(properties.getRefreshSkew()))) {
            return decryptAccessToken(token);
        }

        return refresh(token, decryptRefreshToken(token));
    }

    @Transactional(noRollbackFor = ShippingGatewayException.class)
    public String refreshAfterUnauthorized(String rejectedAccessToken) {
        MelhorEnvioToken token = lockedToken();

        if (REAUTHORIZATION_REQUIRED.equals(token.getStatus())) {
            throw new ShippingGatewayException(
                    "Melhor Envio authorization must be renewed.");
        }

        if (!token.getRefreshExpiresAt().isAfter(now())) {
            token.setStatus(REAUTHORIZATION_REQUIRED);
            tokenRepository.save(token);

            throw new ShippingGatewayException(
                    "Melhor Envio authorization must be renewed.");
        }

        String currentAccessToken = decryptAccessToken(token);

        if (!currentAccessToken.equals(rejectedAccessToken)
                && token.getExpiresAt().isAfter(now())) {
            return currentAccessToken;
        }

        return refresh(token, decryptRefreshToken(token));
    }

    public String get() {
        return getValidAccessToken();
    }

    private MelhorEnvioToken lockedToken() {
        return tokenRepository.findByEnvironmentForUpdate(properties.getEnvironment())
                .orElseThrow(() -> new ShippingGatewayException(
                        "Melhor Envio has not been authorized."));
    }

    private String refresh(MelhorEnvioToken token, String refreshToken) {
        MelhorEnvioOAuthClient.TokenResponse refreshed;

        try {
            refreshed = oauthClient.refreshToken(refreshToken);

        } catch (MelhorEnvioOAuthClient.OAuthTokenException exception) {

            if (exception.isReauthorizationRequired()) {
                token.setStatus(REAUTHORIZATION_REQUIRED);
                tokenRepository.save(token);

                throw new ShippingGatewayException(
                        "Melhor Envio authorization was revoked or expired.");
            }

            throw new ShippingGatewayException(
                    "Melhor Envio token refresh failed temporarily. Please try again.");
        }

        validateTokens(refreshed);

        String environment = properties.getEnvironment();

        token.setAccessTokenCiphertext(
                tokenCipher.encrypt(refreshed.accessToken(), aad(environment, "access")));
        token.setRefreshTokenCiphertext(
                tokenCipher.encrypt(refreshed.refreshToken(), aad(environment, "refresh")));
        token.setExpiresAt(now().plusSeconds(refreshed.expiresIn()));
        token.setRefreshExpiresAt(now().plus(properties.getRefreshTokenTtl()));
        token.setStatus(AUTHORIZED);

        tokenRepository.save(token);

        return refreshed.accessToken();
    }

    private String decryptAccessToken(MelhorEnvioToken token) {
        return tokenCipher.decrypt(
                token.getAccessTokenCiphertext(),
                aad(properties.getEnvironment(), "access"));
    }

    private String decryptRefreshToken(MelhorEnvioToken token) {
        return tokenCipher.decrypt(
                token.getRefreshTokenCiphertext(),
                aad(properties.getEnvironment(), "refresh"));
    }

    private String aad(String environment, String tokenType) {
        return "melhor-envio:" + environment + ":" + tokenType;
    }

    private void validateTokens(MelhorEnvioOAuthClient.TokenResponse tokens) {
        if (tokens == null
                || tokens.accessToken() == null || tokens.accessToken().isBlank()
                || tokens.refreshToken() == null || tokens.refreshToken().isBlank()
                || tokens.expiresIn() == null || tokens.expiresIn() <= 0) {
            throw new ShippingGatewayException(
                    "Melhor Envio returned incomplete OAuth credentials.");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
