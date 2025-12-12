```java
package com.dnstore.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

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
@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class Product {
    private Long id;
    private String name;
    private BigDecimal price; // BigDecimal é ideal para valores monetários pela precisão
    private String imageUrl;
    
    /**
     * Método abstrato que força as subclasses a definir como calcular o peso para frete.
     * Isso demonstra Polimorfismo: cada tipo de produto se comporta de um jeito.
     */
    public abstract double getShippingWeight();
}
