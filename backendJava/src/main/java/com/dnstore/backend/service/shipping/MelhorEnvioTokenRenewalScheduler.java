package com.dnstore.backend.service.shipping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Renova o token do Melhor Envio antes do vencimento, sem depender de haver uma cotação no momento
 * certo. A verificação roda várias vezes por dia e a janela de renovação é de dias, então uma falha
 * temporária do provedor é repetida muitas vezes antes de o access token realmente vencer.
 */
@Slf4j
@Component
@Profile("!readonly")
@ConditionalOnProperty(name = "shipping.gateway", havingValue = "melhorEnvio")
@RequiredArgsConstructor
public class MelhorEnvioTokenRenewalScheduler {

    private final MelhorEnvioTokenStore tokenStore;

    @Scheduled(
            fixedDelayString = "${melhor-envio.renewal-interval-ms:21600000}",
            initialDelayString = "${melhor-envio.renewal-initial-delay-ms:60000}")
    public void renewTokenIfDue() {
        try {
            MelhorEnvioTokenStore.RenewalOutcome outcome = tokenStore.renewIfDue();
            switch (outcome) {
                case RENEWED, NOT_DUE ->
                    log.debug("melhor_envio.token.renewal_check outcome={}", outcome);
                case NOT_CONNECTED, REAUTH_REQUIRED, FAILED ->
                    log.warn("melhor_envio.token.renewal_check outcome={}", outcome);
            }
        } catch (RuntimeException exception) {
            // Falha de banco ou de cifra; o próximo ciclo tenta novamente.
            log.error("melhor_envio.token.renewal_check outcome=ERROR type={}",
                    exception.getClass().getSimpleName());
        }
    }
}
