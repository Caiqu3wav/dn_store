package com.dnstore.backend.controller;

import com.dnstore.backend.service.shipping.MelhorEnvioTokenStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShippingIntegrationStatusControllerTest {

    @SuppressWarnings("unchecked")
    private ShippingIntegrationStatusController controller(MelhorEnvioTokenStore store, String gateway) {
        ObjectProvider<MelhorEnvioTokenStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(store);
        ShippingIntegrationStatusController controller = new ShippingIntegrationStatusController(provider);
        ReflectionTestUtils.setField(controller, "gateway", gateway);
        return controller;
    }

    @Test
    void whenMelhorEnvioGatewayIsNotActiveReportsDisabledWithoutTouchingTokens() {
        var response = controller(null, "simulated").status();

        assertThat(response.gateway()).isEqualTo("simulated");
        assertThat(response.state()).isEqualTo("DISABLED");
        assertThat(response.accessTokenExpiresAt()).isNull();
    }

    @Test
    void reportsConnectionStateExpiryAndLastRenewalFailure() {
        MelhorEnvioTokenStore store = mock(MelhorEnvioTokenStore.class);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 12, 18, 12, 0);
        LocalDateTime failedAt = LocalDateTime.of(2026, 12, 12, 9, 0);
        when(store.status()).thenReturn(new MelhorEnvioTokenStore.ConnectionStatus(
                MelhorEnvioTokenStore.ConnectionState.RENEWAL_FAILING,
                "production",
                expiresAt,
                expiresAt.minusDays(7),
                new MelhorEnvioTokenStore.RefreshFailure(failedAt, "temporary", 503, null),
                true));

        var response = controller(store, "melhorEnvio").status();

        assertThat(response.state()).isEqualTo("RENEWAL_FAILING");
        assertThat(response.environment()).isEqualTo("production");
        assertThat(response.accessTokenExpiresAt()).isEqualTo(expiresAt);
        assertThat(response.lastRefreshFailure().httpStatus()).isEqualTo(503);
        assertThat(response.persistencePending()).isTrue();
        assertThat(response.toString()).doesNotContainIgnoringCase("token=");
    }
}
