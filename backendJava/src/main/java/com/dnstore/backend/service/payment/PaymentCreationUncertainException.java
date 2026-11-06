package com.dnstore.backend.service.payment;

public class PaymentCreationUncertainException extends PaymentGatewayException {
    public PaymentCreationUncertainException(String message, Throwable cause) {
        super(message, cause);
    }
}
