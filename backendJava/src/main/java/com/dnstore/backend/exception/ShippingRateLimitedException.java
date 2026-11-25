package com.dnstore.backend.exception;

/** O provedor de frete respondeu 429 e as tentativas se esgotaram. */
public class ShippingRateLimitedException extends ShippingGatewayException {

    private final Long retryAfterSeconds;

    public ShippingRateLimitedException(String message, Long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
