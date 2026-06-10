package com.dnstore.backend.service;

import com.dnstore.backend.model.Product;
import com.dnstore.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    public List<Product> search(
            String search,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sortBy) {

        List<Product> products = productRepository.findAll();

        if (search != null && !search.isBlank()) {
            products = products.stream()
                    .filter(p -> p.getName() != null &&
                            p.getName().toLowerCase().contains(search.toLowerCase()))
                    .toList();
        }

        if (minPrice != null) {
            products = products.stream()
                    .filter(p -> p.getPrice().compareTo(minPrice) >= 0)
                    .toList();
        }

        if (maxPrice != null) {
            products = products.stream()
                    .filter(p -> p.getPrice().compareTo(maxPrice) <= 0)
                    .toList();
        }

        if ("priceAsc".equalsIgnoreCase(sortBy)) {
            products = products.stream()
                    .sorted((a, b) -> a.getPrice().compareTo(b.getPrice()))
                    .toList();
        }

        if ("priceDesc".equalsIgnoreCase(sortBy)) {
            products = products.stream()
                    .sorted((a, b) -> b.getPrice().compareTo(a.getPrice()))
                    .toList();
        }

        if ("name".equalsIgnoreCase(sortBy)) {
            products = products.stream()
                    .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                    .toList();
        }

        return products;
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
