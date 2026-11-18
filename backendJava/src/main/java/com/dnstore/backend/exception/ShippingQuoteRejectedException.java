package com.dnstore.backend.exception;

/**
 * O provedor de frete recusou a consulta por causa do CEP de destino informado (HTTP 422 com erro
 * no campo de destino). É um erro do cliente, não uma indisponibilidade do serviço.
 */
public class ShippingQuoteRejectedException extends RuntimeException {
    public ShippingQuoteRejectedException(String message) {
        super(message);
    }
}
