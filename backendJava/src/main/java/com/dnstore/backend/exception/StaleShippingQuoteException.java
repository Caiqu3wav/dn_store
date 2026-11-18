package com.dnstore.backend.exception;

/**
 * A cotação de frete escolhida não vale mais (expirou, o CEP ou o carrinho mudaram, ou a
 * configuração do provedor mudou). O cliente deve cotar de novo; o frontend reconhece o código
 * {@link #CODE} e refaz a cotação sem reiniciar o checkout.
 */
public class StaleShippingQuoteException extends ConflictException {

    public static final String CODE = "SHIPPING_QUOTE_STALE";

    public StaleShippingQuoteException(String message) {
        super(message);
    }
}
