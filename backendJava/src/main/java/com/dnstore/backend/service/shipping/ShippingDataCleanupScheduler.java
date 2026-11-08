package com.dnstore.backend.service.shipping;

import com.dnstore.backend.repository.MelhorEnvioOAuthStateRepository;
import com.dnstore.backend.repository.ShippingQuoteOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class ShippingDataCleanupScheduler {

    private final MelhorEnvioOAuthStateRepository stateRepository;
    private final ShippingQuoteOptionRepository quoteOptionRepository;

    @Scheduled(fixedDelayString = "${shipping.cleanup-interval-ms:3600000}")
    public void cleanupExpiredData() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        stateRepository.deleteExpired(now);
        quoteOptionRepository.deleteExpiredUnconsumed(now);
    }
}
