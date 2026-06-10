package com.dnstore.backend.service;

import com.dnstore.backend.model.Favorite;
import com.dnstore.backend.repository.FavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;

    public List<Favorite> findByUser(UUID userId) {
        return favoriteRepository.findByUserId(userId);
    }

    public void remove(UUID userId, UUID productId) {
        favoriteRepository.deleteByUserIdAndProductId(userId, productId);
    }
}