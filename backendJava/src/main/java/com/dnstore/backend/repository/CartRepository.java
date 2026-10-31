package com.dnstore.backend.repository;

import com.dnstore.backend.model.Cart;
import com.dnstore.backend.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {

    @EntityGraph(attributePaths = {"items", "items.productVariant", "items.productVariant.product"})
    Optional<Cart> findByUser(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.user = :user")
    @EntityGraph(attributePaths = {"items", "items.productVariant", "items.productVariant.product"})
    Optional<Cart> findByUserForUpdate(@Param("user") User user);

    Optional<Cart> findByUserId(UUID userId);
}