package com.dnstore.backend.repository;

import com.dnstore.backend.model.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {
    List<Favorite> findByUserId(UUID userId);

    Optional<Favorite> findByUserIdAndProductId(UUID userId, UUID productId);

    void deleteByUserIdAndProductId(UUID userId, UUID productId);
}