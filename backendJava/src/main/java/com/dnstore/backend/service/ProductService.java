package com.dnstore.backend.service;

import com.dnstore.backend.model.Product;
import com.dnstore.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 🥫 ProductService
 *
 * Gerencia o catálogo de produtos.
 * Utiliza MySQL via JPA Repository.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    // --- R: Read ---
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Optional<Product> findById(UUID id) {
        return productRepository.findById(id);
    }

    // --- C: Create ---
    public Product create(Product product) {
        return productRepository.save(product);
    }

    // --- U: Update ---
    public Optional<Product> update(UUID id, Product updatedData) {
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
    public boolean delete(UUID id) {
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
