package com.dnstore.backend.exception;

/**
 * O provedor de frete respondeu normalmente, mas não há nenhum serviço disponível para esta rota
 * (CEP sem cobertura ou itens que nenhuma transportadora atende). Não é indisponibilidade técnica:
 * o cliente deve ver uma mensagem de "sem entrega", não "tente mais tarde".
 */
public class NoShippingOptionsException extends DeliveryException {

    public static final String CODE = "SHIPPING_NOT_AVAILABLE";

    public NoShippingOptionsException() {
        super("Não há modalidades de entrega disponíveis para este CEP.");
    }
}
