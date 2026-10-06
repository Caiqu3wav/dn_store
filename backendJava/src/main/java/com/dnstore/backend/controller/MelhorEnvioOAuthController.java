package com.dnstore.backend.controller;

import com.dnstore.backend.service.shipping.MelhorEnvioOAuthService;
import com.dnstore.backend.service.shipping.MelhorEnvioTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/melhor-envio")
@RequiredArgsConstructor
public class MelhorEnvioOAuthController {

    private final MelhorEnvioOAuthService oauthService;
    private final MelhorEnvioTokenStore tokenStore;

    @GetMapping("/callback")
    public ResponseEntity<String> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error) {

        if (error != null) {
            return ResponseEntity.badRequest()
                    .body("Erro na autorização do Melhor Envio.");
        }

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Código de autorização não informado.");
        }

        MelhorEnvioOAuthService.TokenResponse token = oauthService.exchangeCode(code);

        tokenStore.save(token.accessToken());

        return ResponseEntity.ok(
                "Integração com o Melhor Envio autorizada com sucesso.");
    }
}