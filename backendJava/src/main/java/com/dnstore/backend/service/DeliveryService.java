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

import java.util.List;

/**
 * 🚚 DeliveryService (Serviço de Entregas)
 * 
 * Valida os dados de envio e delega cotações ao gateway configurado.
 */
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final ShippingGateway shippingGateway;

    @Value("${shipping.origin-zip:}")
    private String originZip;

    public ShippingQuote calculateShipping(String destinationZip, List<ShippingItem> items, String service) {
        if (service == null || service.isBlank()) {
            throw new DeliveryException("Modalidade de entrega é obrigatória.");
        }
        return calculateOptions(destinationZip, items).stream()
                .filter(option -> option.service().equalsIgnoreCase(service.trim()))
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
        for (ShippingItem item : items) {
            if (item == null || item.quantity() <= 0 || !isPositive(item.weight())
                    || !isPositive(item.width()) || !isPositive(item.height()) || !isPositive(item.length())) {
                throw new DeliveryException("Peso, dimensões e quantidade dos produtos devem ser válidos.");
            }
        }

        List<ShippingQuote> quotes = shippingGateway.quote(
                new ShippingRequest(normalizedOrigin, normalizedDestination, items));
        if (quotes == null) {
            throw new ShippingGatewayException("Resposta inválida recebida do serviço de frete.");
        }
        if (quotes.isEmpty()) {
            throw new DeliveryException("Não há modalidades de entrega disponíveis para este CEP.");
        }
        if (quotes.stream().anyMatch(quote -> quote == null || quote.service() == null || quote.service().isBlank()
                || quote.price() == null || quote.price().signum() < 0 || quote.deliveryDays() <= 0)) {
            throw new ShippingGatewayException("Resposta inválida recebida do serviço de frete.");
        }
        return List.copyOf(quotes);
    }

    private String normalizeZip(String zipCode, String role) {
        if (zipCode == null || !zipCode.trim().matches("\\d{5}-?\\d{3}")) {
            throw new DeliveryException("CEP de " + role + " inválido.");
        }
        return zipCode.replace("-", "").trim();
    }

    private boolean isPositive(java.math.BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
