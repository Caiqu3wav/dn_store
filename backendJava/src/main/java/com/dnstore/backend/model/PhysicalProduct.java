package com.dnstore.backend.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

/**
 * 📦 Classe ProdutoFisico (PhysicalProduct)
 * 
 * Estende a classe Product, herdando seus atributos (nome, preço, etc).
 * Adiciona características específicas de itens tangíveis, como peso.
 * 
 * Conceitos de POO:
 * - Herança: 'extends Product'
 * - Polimorfismo: Sobrescrita (@Override) do método getShippingWeight().
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Entity
@PrimaryKeyJoinColumn(name = "product_id")
public class PhysicalProduct extends Product {
    private double weight;
    private double width;
    private double height;
    private double depth;

    public PhysicalProduct(Long id, String name, String description, BigDecimal price, String imageUrl, double weight, double width, double height, double depth) {
        super(id, name, description, price, imageUrl);
        this.weight = weight;
        this.width = width;
        this.height = height;
        this.depth = depth;
    }
}
