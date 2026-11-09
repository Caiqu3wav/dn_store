package com.dnstore.backend.service;

import com.dnstore.backend.model.PaymentWebhookEvent;
import com.dnstore.backend.repository.PaymentWebhookEventRepository;
import com.dnstore.backend.service.payment.PaymentGateway;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookIngestionService {

    static final int MAX_PAYLOAD_BYTES = 256 * 1024;
    private final PaymentGateway gateway;
    private final PaymentWebhookEventRepository repository;

    public boolean ingest(String rawPayload, String signatureHeader) {
        if (rawPayload == null || rawPayload.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Webhook payload excede o limite permitido.");
        }
        PaymentGateway.WebhookResult parsed = gateway.processWebhook(rawPayload, signatureHeader);
        if (parsed.eventId() == null || parsed.externalId() == null || parsed.eventType() == null
                || parsed.eventId().length() > 100 || parsed.externalId().length() > 100
                || parsed.eventType().length() > 100) {
            throw new IllegalArgumentException("Identificador do webhook excede o limite permitido.");
        }
        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setEventId(parsed.eventId());
        event.setExternalPaymentId(parsed.externalId());
        event.setEventType(parsed.eventType() == null ? "UNKNOWN" : parsed.eventType());
        event.setPayload(rawPayload);
        event.setReceivedAt(LocalDateTime.now());
        event.setProcessingStatus("PENDING");
        event.setNextAttemptAt(null);
        try {
            repository.saveAndFlush(event);
            log.info("payment.webhook.received eventId={} eventType={}", parsed.eventId(), event.getEventType());
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            log.info("payment.webhook.duplicate eventId={}", parsed.eventId());
            return false;
        }
    }
}
