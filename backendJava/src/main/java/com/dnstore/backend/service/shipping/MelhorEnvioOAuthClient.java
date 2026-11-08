package com.dnstore.backend.service.shipping;

import com.dnstore.backend.config.MelhorEnvioProperties;
import com.dnstore.backend.exception.ShippingGatewayException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "shipping.gateway", havingValue = "melhorEnvio")
public class MelhorEnvioOAuthClient {

    private final RestClient.Builder restClientBuilder;
    private final MelhorEnvioProperties properties;
    private final ObjectMapper objectMapper;

    public MelhorEnvioOAuthClient(
            RestClient.Builder restClientBuilder,
            MelhorEnvioProperties properties) {
        this(restClientBuilder, properties, new ObjectMapper());
    }

    public MelhorEnvioOAuthClient(
            RestClient.Builder restClientBuilder,
            MelhorEnvioProperties properties,
            ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public TokenResponse exchangeCode(String code) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("grant_type", "authorization_code");
        body.put("client_id", properties.getClientId());
        body.put("client_secret", properties.getClientSecret());
        body.put("redirect_uri", properties.getRedirectUri());
        body.put("code", code);
        return requestToken(body, false);
    }

    public TokenResponse refreshToken(String refreshToken) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("grant_type", "refresh_token");
        body.put("client_id", properties.getClientId());
        body.put("client_secret", properties.getClientSecret());
        body.put("refresh_token", refreshToken);
        return requestToken(body, true);
    }

    private TokenResponse requestToken(Map<String, String> body, boolean refresh) {
        try {
            TokenResponse response = restClientBuilder.clone()
                    .build()
                    .post()
                    .uri(properties.getTokenUrl())
                    .header(HttpHeaders.USER_AGENT, properties.getUserAgent())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(TokenResponse.class);

            if (response == null
                    || response.accessToken() == null
                    || response.accessToken().isBlank()
                    || response.refreshToken() == null
                    || response.refreshToken().isBlank()
                    || response.expiresIn() == null
                    || response.expiresIn() <= 0) {
                if (refresh) {
                    throw new OAuthTokenException(false, false);
                }
                throw new ShippingGatewayException("Melhor Envio returned an incomplete OAuth response.");
            }
            return response;
        } catch (RestClientResponseException exception) {
            String errorCode = oauthErrorCode(exception.getResponseBodyAsString());
            boolean invalidGrant = "invalid_grant".equals(errorCode);
            throw new OAuthTokenException(
                    refresh && invalidGrant,
                    !refresh && invalidGrant,
                    errorCode,
                    exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new OAuthTokenException(false, false, null, null);
        }
    }

    private String oauthErrorCode(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode error = objectMapper.readTree(responseBody).get("error");
            return error == null || !error.isTextual() ? null : error.textValue();
        } catch (Exception ignored) {
            return null;
        }
    }

    public record TokenResponse(
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken) {
    }

    public static class OAuthTokenException extends ShippingGatewayException {
        private final boolean reauthorizationRequired;
        private final boolean invalidAuthorizationCode;
        private final String errorCode;
        private final Integer httpStatus;

        public OAuthTokenException(boolean reauthorizationRequired, boolean invalidAuthorizationCode) {
            this(reauthorizationRequired, invalidAuthorizationCode, null, null);
        }

        public OAuthTokenException(
                boolean reauthorizationRequired,
                boolean invalidAuthorizationCode,
                String errorCode,
                Integer httpStatus) {
            super("Melhor Envio OAuth request failed.");
            this.reauthorizationRequired = reauthorizationRequired;
            this.invalidAuthorizationCode = invalidAuthorizationCode;
            this.errorCode = errorCode;
            this.httpStatus = httpStatus;
        }

        public boolean isReauthorizationRequired() {
            return reauthorizationRequired;
        }

        public boolean isInvalidAuthorizationCode() {
            return invalidAuthorizationCode;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public Integer getHttpStatus() {
            return httpStatus;
        }
    }
}
