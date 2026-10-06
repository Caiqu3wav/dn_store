package com.dnstore.backend.service.shipping;

import com.dnstore.backend.exception.ShippingGatewayException;

import java.util.List;

public class UnavailableShippingGateway implements ShippingGateway {

    @Override
    public List<ShippingQuote> quote(ShippingRequest request) {
        throw new ShippingGatewayException(
                "Cotação de frete temporariamente indisponível: integração oficial não configurada.");
    }
}