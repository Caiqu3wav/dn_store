package com.dnstore.backend.service;

import com.dnstore.backend.dto.FavoriteResponse;
import com.dnstore.backend.dto.ProductResponse;
import com.dnstore.backend.model.Favorite;
import com.dnstore.backend.model.Product;
import com.dnstore.backend.model.User;
import com.dnstore.backend.exception.ResourceNotFoundException;
import com.dnstore.backend.repository.FavoriteRepository;
import com.dnstore.backend.repository.ProductRepository;
import com.dnstore.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public List<Favorite> findByUser(UUID userId) {
        return favoriteRepository.findActiveProductsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<FavoriteResponse> findResponsesByUser(UUID userId) {
        return favoriteRepository.findActiveProductsByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public Favorite add(UUID userId, UUID productId) {
        Product product = productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));

        Optional<Favorite> existing = favoriteRepository.findByUserIdAndProductId(userId, productId);
        if (existing.isPresent()) {
            return existing.get();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        Favorite favorite = new Favorite();
        favorite.setUser(user);
        favorite.setProduct(product);

        return favoriteRepository.save(favorite);
    }

    @Transactional
    public FavoriteResponse addResponse(UUID userId, UUID productId) {
        return toResponse(add(userId, productId));
    }

    @Transactional
    public void remove(UUID userId, UUID productId) {
        favoriteRepository.deleteByUserIdAndProductId(userId, productId);
    }

    private FavoriteResponse toResponse(Favorite favorite) {
        return new FavoriteResponse(favorite.getId(), ProductResponse.from(favorite.getProduct()));
    }
}