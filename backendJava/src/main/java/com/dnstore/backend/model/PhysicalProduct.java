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
public class PhysicalProduct extends Product {
    
    private double weightKg; // Peso em Quilos

    public PhysicalProduct(Long id, String name, BigDecimal price, String imageUrl, double weightKg) {
        super(id, name, price, imageUrl);
        this.weightKg = weightKg;
    }

    @Override
    public double getShippingWeight() {
        return this.weightKg;
    }
}
