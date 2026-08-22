package com.dnstore.backend.service;

import com.dnstore.backend.model.Category;
import com.dnstore.backend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    public Optional<Category> findById(UUID id) {
        return categoryRepository.findById(id);
    }

    public Category create(Category category) {
        // Prevent duplicate category names
        categoryRepository.findByNameIgnoreCase(category.getName()).ifPresent(c -> {
            throw new RuntimeException("Category already exists with name: " + category.getName());
        });
        if (category.getSlug() == null || category.getSlug().isBlank()) {
            category.setSlug(category.getName().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", "")
                .trim()
                .replaceAll("\\s+", "-"));
        }
        return categoryRepository.save(category);
    }

    public Optional<Category> update(UUID id, Category updatedData) {
        return categoryRepository.findById(id).map(existing -> {
            // Check if name is changing and already exists
            if (!existing.getName().equalsIgnoreCase(updatedData.getName())) {
                categoryRepository.findByNameIgnoreCase(updatedData.getName()).ifPresent(c -> {
                    throw new RuntimeException("Category already exists with name: " + updatedData.getName());
                });
            }
            existing.setName(updatedData.getName());
            if (updatedData.getSlug() != null && !updatedData.getSlug().isBlank()) {
                existing.setSlug(updatedData.getSlug());
            } else {
                existing.setSlug(updatedData.getName().toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[^a-z0-9\\s]", "")
                    .trim()
                    .replaceAll("\\s+", "-"));
            }
            existing.setDescription(updatedData.getDescription());
            return categoryRepository.save(existing);
        });
    }

    public boolean delete(UUID id) {
        if (categoryRepository.existsById(id)) {
            categoryRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
