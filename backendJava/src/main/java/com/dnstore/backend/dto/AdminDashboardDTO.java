package com.dnstore.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class AdminDashboardDTO {

    private long totalOrders;
    private long totalUsers;
    private long totalProducts;
    private BigDecimal totalRevenue;
    private BigDecimal averageTicket;
    /** Pedidos com pagamento confirmado: base da receita e do ticket médio. */
    private long paidOrders;
}