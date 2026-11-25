package com.dnstore.backend.controller;

import com.dnstore.backend.service.shipping.MelhorEnvioTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Estado da conexão com o Melhor Envio para o painel administrativo. Existe em qualquer gateway,
 * para o painel distinguir "frete simulado" de "Melhor Envio não conectado". Nunca devolve tokens.
 */
@RestController
@RequestMapping("/api/integrations/melhor-envio")
@RequiredArgsConstructor
public class ShippingIntegrationStatusController {

    private final ObjectProvider<MelhorEnvioTokenStore> tokenStore;

    @Value("${shipping.gateway:simulated}")
    private String gateway;

    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public StatusResponse status() {
        MelhorEnvioTokenStore store = tokenStore.getIfAvailable();
        if (store == null) {
            return new StatusResponse(gateway, "DISABLED", null, null, null, null, false);
        }
        MelhorEnvioTokenStore.ConnectionStatus status = store.status();
        MelhorEnvioTokenStore.RefreshFailure failure = status.lastRefreshFailure();
        return new StatusResponse(
                gateway,
                status.state().name(),
                status.environment(),
                status.accessTokenExpiresAt(),
                status.renewalStartsAt(),
                failure == null ? null : new FailureResponse(
                        failure.at(), failure.category(), failure.httpStatus(), failure.errorCode()),
                status.persistencePending());
    }

    /** Horários em UTC, sem fuso no JSON (padrão do restante da API). */
    public record StatusResponse(
            String gateway,
            String state,
            String environment,
            LocalDateTime accessTokenExpiresAt,
            LocalDateTime renewalStartsAt,
            FailureResponse lastRefreshFailure,
            boolean persistencePending) {
    }

    public record FailureResponse(
            LocalDateTime at, String category, Integer httpStatus, String errorCode) {
    }
}
