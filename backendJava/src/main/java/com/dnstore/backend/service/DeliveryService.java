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

    /**
     * Overload usado pelo OrderService — recebe peso total em double.
     * Cria um ShippingItem genérico com dimensões padrão de camiseta/acessório.
     */
    public ShippingQuote calculateShipping(String destinationZip, double totalWeightKg, String service) {
        BigDecimal weight = BigDecimal.valueOf(Math.max(totalWeightKg, 0.1));
        List<ShippingItem> items = List.of(new ShippingItem(
                weight,
                new BigDecimal("20"),
                new BigDecimal("5"),
                new BigDecimal("30"),
                1
        ));
        return calculateShipping(destinationZip, items, service);
    }

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
        validatePhysicalDimensions(items);

        List<ShippingQuote> quotes = shippingGateway.quote(
                new ShippingRequest(normalizedOrigin, normalizedDestination, items));
        if (quotes == null || quotes.isEmpty()) {
            throw new DeliveryException("Não há modalidades de entrega disponíveis para este CEP.");
        }
        boolean invalidQuote = quotes.stream().anyMatch(quote -> quote == null
                || quote.service() == null || quote.service().isBlank()
                || quote.price() == null || quote.price().signum() < 0
                || quote.deliveryDays() <= 0);
        if (invalidQuote) {
            throw new ShippingGatewayException("Resposta inválida do serviço de frete.");
        }
        return List.copyOf(quotes);
    }

    private void validatePhysicalDimensions(List<ShippingItem> items) {
        boolean invalidDimensions = items.stream().anyMatch(item -> item == null
                || item.width() == null || item.width().signum() <= 0
                || item.height() == null || item.height().signum() <= 0
                || item.length() == null || item.length().signum() <= 0);
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
