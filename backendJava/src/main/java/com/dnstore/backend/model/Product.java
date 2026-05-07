package com.dnstore.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 📦 Classe Product
 *
 * Representa um produto genérico no sistema.
 * Esta classe é uma entidade JPA e serve como base para produtos concretos (Físicos, Digitais).
 *
 * Conceitos de POO:
 * - Abstração: Define o modelo base.
 * - Encapsulamento: Atributos privados com Getters/Setters (via Lombok).
 */
@Entity
@Table(name = "products")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "product_type")
@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private boolean active = true;
    /**
     * Método abstrato que força as subclasses a definir como calcular o peso para frete.
     * Isso demonstra Polimorfismo: cada tipo de produto se comporta de um jeito.
     */
    public abstract double getShippingWeight();
}
