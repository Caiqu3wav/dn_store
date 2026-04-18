package com.dnstore.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

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
@PrimaryKeyJoinColumn(name = "id")
public class PhysicalProduct extends Product {
    private double weight;
    private double width;
    private double height;
    private double depth;

    public PhysicalProduct(UUID id, String name, String description, BigDecimal price, String imageUrl, double weight, double width, double height, double depth) {
        super(id, name, description, price, imageUrl);
        this.weight = weight;
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    @Override
    public double getShippingWeight() {
        return weight;
    }
}
