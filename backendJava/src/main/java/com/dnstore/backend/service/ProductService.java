package com.dnstore.backend.service;

import com.dnstore.backend.model.Product;
import com.dnstore.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 🥫 ProductService
 * 
 * Gerencia o catálogo de produtos.
 * Utiliza Postgres via JPA Repository.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    // --- R: Read ---
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    // --- C: Create ---
    public Product create(Product product) {
        return productRepository.save(product);
    }

    // --- U: Update ---
    public Optional<Product> update(Long id, Product updatedData) {
        return productRepository.findById(id).map(existing -> {
            existing.setName(updatedData.getName());
            existing.setPrice(updatedData.getPrice());
            existing.setImageUrl(updatedData.getImageUrl());
            existing.setDescription(updatedData.getDescription());
            // JPA faz o update automaticamente ao salvar/alterar entidade gerenciada
            return productRepository.save(existing);
        });
    }

    // --- D: Delete ---
    public boolean delete(Long id) {
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
            return true;
        }
        return false;
    }
}

    // --- D: Delete ---
    public boolean delete(Long id) {
        return products.removeIf(p -> p.getId().equals(id));
    }
}
