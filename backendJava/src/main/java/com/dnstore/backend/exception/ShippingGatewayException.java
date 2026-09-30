package com.dnstore.backend.exception;

public class ShippingGatewayException extends RuntimeException {
    public ShippingGatewayException(String message) {
        super(message);
    }

    public ShippingGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}