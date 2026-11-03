package com.dnstore.backend.repository;

import com.dnstore.backend.model.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {
    List<Favorite> findByUserId(UUID userId);

    @Query("SELECT f FROM Favorite f JOIN FETCH f.product p WHERE f.user.id = :userId AND p.active = true")
    List<Favorite> findActiveProductsByUserId(@Param("userId") UUID userId);

    Optional<Favorite> findByUserIdAndProductId(UUID userId, UUID productId);

    void deleteByUserIdAndProductId(UUID userId, UUID productId);
}