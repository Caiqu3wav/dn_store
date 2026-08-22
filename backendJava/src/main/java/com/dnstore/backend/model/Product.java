package com.dnstore.backend.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import java.util.ArrayList;
import java.util.List;

/**
 * 📦 Classe Product
 *
 * Representa um produto genérico no sistema.
 * Esta classe é uma entidade JPA e serve como base para produtos concretos
 * (Físicos, Digitais).
 *
 * Conceitos de POO:
 * - Abstração: Define o modelo base.
 * - Encapsulamento: Atributos privados com Getters/Setters (via Lombok).
 */
@Setter
@Getter
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
    @JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;


    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "promotional_price", precision = 10, scale = 2)
    private BigDecimal promotionalPrice;

    @Column(length = 50)
    private String color;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @JsonManagedReference
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    /**
     * Método abstrato que força as subclasses a definir como calcular o peso para
     * frete.
     * Isso demonstra Polimorfismo: cada tipo de produto se comporta de um jeito.
     */
    public abstract double getShippingWeight();
}
