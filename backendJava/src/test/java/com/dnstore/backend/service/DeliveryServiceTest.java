package com.dnstore.backend.service;

import com.dnstore.backend.exception.DeliveryException;
import com.dnstore.backend.exception.ShippingGatewayException;
import com.dnstore.backend.service.shipping.ShippingGateway;
import com.dnstore.backend.service.shipping.ShippingGateway.ShippingItem;
import com.dnstore.backend.service.shipping.ShippingGateway.ShippingRequest;
import com.dnstore.backend.service.shipping.ShippingQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DeliveryServiceTest {

    private ShippingGateway gateway;
    private DeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        gateway = mock(ShippingGateway.class);
        deliveryService = new DeliveryService(gateway);
        ReflectionTestUtils.setField(deliveryService, "originZip", "12600-000");
    }

    @Test
    void calculateOptions_shouldNormalizeZipAndPreserveItemDimensionsAndQuantity() {
        List<ShippingItem> items = List.of(
                item("0.5", "20", "10", "30", 2),
                item("1.25", "40", "15", "25", 3));
        List<ShippingQuote> expected = List.of(
                new ShippingQuote("PAC", new BigDecimal("25.90"), 5),
                new ShippingQuote("SEDEX", new BigDecimal("42.50"), 2));
        when(gateway.quote(any(ShippingRequest.class))).thenReturn(expected);

        List<ShippingQuote> actual = deliveryService.calculateOptions("12500-000", items);

        assertThat(actual).containsExactlyElementsOf(expected);
        ArgumentCaptor<ShippingRequest> requestCaptor = ArgumentCaptor.forClass(ShippingRequest.class);
        verify(gateway).quote(requestCaptor.capture());
        ShippingRequest request = requestCaptor.getValue();
        assertThat(request.originZip()).isEqualTo("12600000");
        assertThat(request.destinationZip()).isEqualTo("12500000");
        assertThat(request.items()).hasSize(2);
        assertThat(request.items().get(0).totalWeight()).isEqualByComparingTo("1.0");
        assertThat(request.items().get(1).totalWeight()).isEqualByComparingTo("3.75");
        assertThat(request.items().stream().map(ShippingItem::totalWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("4.75");
    }

    @Test
    void calculateOptions_shouldRejectInvalidDestinationBeforeGatewayCall() {
        assertThatThrownBy(() -> deliveryService.calculateOptions("1250-000", List.of(item("1", "1", "1", "1", 1))))
                .isInstanceOf(DeliveryException.class)
                .hasMessageContaining("CEP de destino");

        verifyNoInteractions(gateway);
    }

    @Test
    void calculateOptions_shouldRejectInvalidOriginConfiguration() {
        ReflectionTestUtils.setField(deliveryService, "originZip", "1260-000");

        assertThatThrownBy(() -> deliveryService.calculateOptions("12500-000", List.of(item("1", "1", "1", "1", 1))))
                .isInstanceOf(ShippingGatewayException.class)
                .hasMessageContaining("CEP de origem");

        verifyNoInteractions(gateway);
    }

    @Test
    void calculateOptions_shouldRejectIncompletePhysicalDimensions() {
        assertThatThrownBy(() -> deliveryService.calculateOptions("12500-000",
                List.of(item("1", "0", "1", "1", 1))))
                .isInstanceOf(DeliveryException.class)
                .hasMessageContaining("dimensões");

        verifyNoInteractions(gateway);
    }

    @Test
    void calculateOptions_shouldRejectInvalidGatewayResponse() {
        when(gateway.quote(any(ShippingRequest.class)))
                .thenReturn(List.of(new ShippingQuote("PAC", null, 5)));

        assertThatThrownBy(() -> deliveryService.calculateOptions("12500-000", List.of(item("1", "1", "1", "1", 1))))
                .isInstanceOf(ShippingGatewayException.class)
                .hasMessageContaining("Resposta inválida");
    }

    @Test
    void calculateShipping_shouldSelectPacOrSedexFromGatewayQuotes() {
        when(gateway.quote(any(ShippingRequest.class))).thenReturn(List.of(
                new ShippingQuote("PAC", new BigDecimal("25.90"), 5),
                new ShippingQuote("SEDEX", new BigDecimal("42.50"), 2)));

        ShippingQuote pac = deliveryService.calculateShipping("12500000", List.of(item("1", "1", "1", "1", 1)), "pac");
        ShippingQuote sedex = deliveryService.calculateShipping("12500000", List.of(item("1", "1", "1", "1", 1)),
                "SEDEX");

        assertThat(pac.price()).isEqualByComparingTo("25.90");
        assertThat(pac.deliveryDays()).isEqualTo(5);
        assertThat(sedex.price()).isEqualByComparingTo("42.50");
        assertThat(sedex.deliveryDays()).isEqualTo(2);
    }

    @Test
    void calculateOptions_shouldPropagateGatewayUnavailabilityWithoutFallback() {
        when(gateway.quote(any(ShippingRequest.class)))
                .thenThrow(new ShippingGatewayException("provider unavailable"));

        assertThatThrownBy(() -> deliveryService.calculateOptions("12500-000", List.of(item("1", "1", "1", "1", 1))))
                .isInstanceOf(ShippingGatewayException.class)
                .hasMessage("provider unavailable");
    }

    @Test
    void unavailableGateway_shouldNeverReturnSimulatedPrices() {
        ShippingGateway unavailableGateway = new com.dnstore.backend.service.shipping.UnavailableShippingGateway();

        assertThatThrownBy(() -> unavailableGateway.quote(new ShippingRequest(
                "12600000", "12500000", List.of(item("1", "1", "1", "1", 1)))))
                .isInstanceOf(ShippingGatewayException.class);
    }

    private ShippingItem item(String weight, String width, String height, String length, int quantity) {
        return new ShippingItem(new BigDecimal(weight), new BigDecimal(width), new BigDecimal(height),
                new BigDecimal(length), quantity);
    }
}