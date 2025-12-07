package com.dnstore.backend.service;

import com.dnstore.backend.model.PhysicalProduct;
import com.dnstore.backend.model.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 🥫 ProductService
 * 
 * Gerencia o catálogo de produtos.
 * Agora utiliza uma lista mutável para permitir operações CRUD completas.
 */
@Service
public class ProductService {

    private final List<Product> products = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(5); // IDs 1-4 já usados

    public ProductService() {
        // Mock de Dados Inicial (lista mutável)
        products.add(new PhysicalProduct(1L, "Notebook Gamer", new BigDecimal("4500.00"), "url-note", 2.5));
        products.add(new PhysicalProduct(2L, "Mouse Sem Fio", new BigDecimal("120.00"), "url-mouse", 0.2));
        products.add(new PhysicalProduct(3L, "Teclado Mecânico", new BigDecimal("350.00"), "url-kb", 1.0));
        products.add(new PhysicalProduct(4L, "Monitor 144Hz", new BigDecimal("1200.00"), "url-monitor", 5.0));
    }

    // --- R: Read ---
    public List<Product> findAll() {
        return products;
    }

    public Optional<Product> findById(Long id) {
        return products.stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    // --- C: Create ---
    public Product create(Product product) {
        product.setId(idGenerator.getAndIncrement());
        products.add(product);
        return product;
    }

    // --- U: Update ---
    public Optional<Product> update(Long id, Product updatedData) {
        return findById(id).map(existing -> {
            existing.setName(updatedData.getName());
            existing.setPrice(updatedData.getPrice());
            existing.setImageUrl(updatedData.getImageUrl());
            // Em uma app real, checaríamos cast para PhysicalProduct para atualizar peso
            return existing;
        });
    }

    // --- D: Delete ---
    public boolean delete(Long id) {
        return products.removeIf(p -> p.getId().equals(id));
    }
}
