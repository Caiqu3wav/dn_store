package com.dnstore.backend.model;

import com.dnstore.backend.service.strategy.DeliveryStrategy.DeliveryResult;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders") // "order" é palavra reservada em SQL
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private LocalDateTime createdAt;
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private List<OrderItem> items; // Mudança de CartItem para OrderItem
    
    private BigDecimal productsTotal;
    private BigDecimal entregaCusto;
    private Integer entregaDias;
    private String entregaTipo;
    private BigDecimal granTotal;
    private String status;
}
```
