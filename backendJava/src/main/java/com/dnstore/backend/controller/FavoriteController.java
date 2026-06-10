package com.dnstore.backend.controller;

import com.dnstore.backend.model.Favorite;
import com.dnstore.backend.model.Product;
import com.dnstore.backend.model.User;
import com.dnstore.backend.repository.FavoriteRepository;
import com.dnstore.backend.repository.ProductRepository;
import com.dnstore.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @GetMapping
    public ResponseEntity<List<Favorite>> list(@RequestParam UUID userId) {
        return ResponseEntity.ok(favoriteRepository.findByUserId(userId));
    }

    @PostMapping("/{productId}")
    public ResponseEntity<?> add(
            @RequestParam UUID userId,
            @PathVariable UUID productId) {
        if (favoriteRepository.findByUserIdAndProductId(userId, productId).isPresent()) {
            return ResponseEntity.ok().build();
        }

        User user = userRepository.findById(userId).orElseThrow();
        Product product = productRepository.findById(productId).orElseThrow();

        Favorite favorite = new Favorite();
        favorite.setUser(user);
        favorite.setProduct(product);

        return ResponseEntity.ok(favoriteRepository.save(favorite));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(
            @RequestParam UUID userId,
            @PathVariable UUID productId) {
        favoriteRepository.deleteByUserIdAndProductId(userId, productId);
        return ResponseEntity.noContent().build();
    }
}