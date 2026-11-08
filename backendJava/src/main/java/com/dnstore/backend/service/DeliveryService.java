package com.dnstore.backend.service;

import com.dnstore.backend.exception.DeliveryException;
import com.dnstore.backend.exception.ShippingGatewayException;
import com.dnstore.backend.service.shipping.ShippingGateway;
import com.dnstore.backend.service.shipping.ShippingGateway.ShippingItem;
import com.dnstore.backend.service.shipping.ShippingGateway.ShippingRequest;
import com.dnstore.backend.service.shipping.ShippingQuote;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final ShippingGateway shippingGateway;

    @Value("${shipping.origin-zip:}")
    private String originZip;

    public ShippingQuote calculateShipping(String destinationZip, List<ShippingItem> items, String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new DeliveryException("Identificador da modalidade de entrega é obrigatório.");
        }
        return calculateOptions(destinationZip, items).stream()
                .filter(option -> option.serviceId().equals(serviceId.trim()))
                .findFirst()
                .orElseThrow(() -> new DeliveryException("Modalidade de entrega indisponível."));
    }

    public List<ShippingQuote> calculateOptions(String destinationZip, List<ShippingItem> items) {
        if (originZip == null || originZip.isBlank()) {
            throw new ShippingGatewayException("CEP de origem não configurado.");
        }
        String normalizedOrigin;
        try {
            normalizedOrigin = normalizeZip(originZip, "origem");
        } catch (DeliveryException e) {
            throw new ShippingGatewayException("CEP de origem não configurado corretamente.");
        }
        String normalizedDestination = normalizeZip(destinationZip, "destino");
        if (items == null || items.isEmpty()) {
            throw new DeliveryException("O carrinho não possui itens para cotação.");
        }
        validatePhysicalDimensions(items);

        List<ShippingQuote> quotes = shippingGateway.quote(
                new ShippingRequest(normalizedOrigin, normalizedDestination, items));
        if (quotes == null || quotes.isEmpty()) {
            throw new DeliveryException("Não há modalidades de entrega disponíveis para este CEP.");
        }
        boolean invalidQuote = quotes.stream().anyMatch(quote -> quote == null
                || quote.serviceId() == null || quote.serviceId().isBlank()
                || quote.service() == null || quote.service().isBlank()
                || quote.price() == null || quote.price().signum() < 0
                || quote.deliveryDays() <= 0);
        if (invalidQuote) {
            throw new ShippingGatewayException("Resposta inválida do serviço de frete.");
        }
        return List.copyOf(quotes);
    }

    public String normalizedOriginZip() {
        if (originZip == null || originZip.isBlank()) {
            throw new ShippingGatewayException("CEP de origem não configurado.");
        }
        try {
            return normalizeZip(originZip, "origem");
        } catch (DeliveryException exception) {
            throw new ShippingGatewayException("CEP de origem não configurado corretamente.");
        }
    }

    private void validatePhysicalDimensions(List<ShippingItem> items) {
        boolean invalidDimensions = items.stream().anyMatch(item -> item == null
                || item.id() == null || item.id().isBlank()
                || item.width() == null || item.width().signum() <= 0
                || item.height() == null || item.height().signum() <= 0
                || item.length() == null || item.length().signum() <= 0
                || item.weight() == null || item.weight().signum() <= 0
                || item.insuranceValue() == null || item.insuranceValue().signum() < 0
                || item.quantity() <= 0);
        if (invalidDimensions) {
            throw new DeliveryException("As dimensões físicas dos itens devem ser maiores que zero.");
        }
    }

    private String normalizeZip(String zipCode, String role) {
        if (zipCode == null || !zipCode.trim().replaceAll("-", "").matches("\\d{8}")) {
            throw new DeliveryException("CEP de " + role + " inválido.");
        }
        return zipCode.replace("-", "").trim();
    }
}
