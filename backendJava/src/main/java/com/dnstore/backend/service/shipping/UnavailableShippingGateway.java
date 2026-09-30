package com.dnstore.backend.service.shipping;

import com.dnstore.backend.exception.ShippingGatewayException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnMissingBean(ShippingGateway.class)
public class UnavailableShippingGateway implements ShippingGateway {

    @Override
    public List<ShippingQuote> quote(ShippingRequest request) {
        throw new ShippingGatewayException(
                "Cotação de frete temporariamente indisponível: integração oficial não configurada.");
    }
}