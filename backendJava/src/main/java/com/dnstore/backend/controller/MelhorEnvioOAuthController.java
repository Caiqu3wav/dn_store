package com.dnstore.backend.controller;

import com.dnstore.backend.service.shipping.MelhorEnvioOAuthService;
import com.dnstore.backend.exception.ShippingGatewayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/integrations/melhor-envio")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "shipping.gateway", havingValue = "melhorEnvio")
public class MelhorEnvioOAuthController {

    private final MelhorEnvioOAuthService oauthService;

    @GetMapping("/authorize")
    @PreAuthorize("hasRole('ADMIN')")
    public AuthorizationResponse authorize() {
        URI authorizationUri = URI.create(oauthService.createAuthorizationUrl());
        return new AuthorizationResponse(authorizationUri.toString());
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        if (!oauthService.consumeState(state)) {
            log.warn("melhor_envio.oauth.invalid_state");
            return ResponseEntity.badRequest().build();
        }
        if (error != null && !error.isBlank()) {
            log.info("melhor_envio.oauth.authorization_denied");
            return redirect(oauthService.frontendRedirect("denied"));
        }
        if (code == null || code.isBlank()) {
            return redirect(oauthService.frontendRedirect("failed"));
        }

        try {
            if (!oauthService.completeAuthorization(code, null)) {
                return redirect(oauthService.frontendRedirect("failed"));
            }
        } catch (ShippingGatewayException exception) {
            log.warn("melhor_envio.oauth.authorization_failed");
            return redirect(oauthService.frontendRedirect("failed"));
        }
        log.info("melhor_envio.oauth.authorized environment-bound");
        return redirect(oauthService.frontendRedirect("connected"));
    }

    private ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }

    public record AuthorizationResponse(String authorizationUrl) {
    }
}
