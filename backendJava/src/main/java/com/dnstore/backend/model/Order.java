package com.dnstore.backend.model;

import com.dnstore.backend.service.strategy.DeliveryStrategy.DeliveryResult;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 📦 Order (Pedido)
 * 
 * Representa o recibo final de uma compra.
 * É Imutável conceitualmente após criado (Snapshot).
 */
@Data
@Builder
public class Order {
    private Long id;
    private LocalDateTime createdAt;
    private List<CartItem> items; // Snapshot dos itens
    private BigDecimal productsTotal;
    private BigDecimal entregaCusto;
    private int entregaDias;
    private String entregaTipo;
    private BigDecimal granTotal;
    
    // Status do pedido (Enum seria ideal, string por simplicidade)
    @Builder.Default
    private String status = "CONFIRMED"; 
}
