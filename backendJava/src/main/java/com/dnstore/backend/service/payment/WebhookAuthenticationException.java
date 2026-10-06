package com.dnstore.backend.service.payment;

public class WebhookAuthenticationException extends RuntimeException {
    public WebhookAuthenticationException() {
        super("Webhook não autorizado");
    }
}