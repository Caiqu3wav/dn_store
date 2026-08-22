package com.dnstore.backend.controller;

import com.dnstore.backend.model.Favorite;
import com.dnstore.backend.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public ResponseEntity<List<Favorite>> list(@RequestParam UUID userId) {
        return ResponseEntity.ok(favoriteService.findByUser(userId));
    }

    @PostMapping("/{productId}")
    public ResponseEntity<Favorite> add(
            @RequestParam UUID userId,
            @PathVariable UUID productId) {
        Favorite favorite = favoriteService.add(userId, productId);
        return ResponseEntity.ok(favorite);
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(
            @RequestParam UUID userId,
            @PathVariable UUID productId) {
        favoriteService.remove(userId, productId);
        return ResponseEntity.noContent().build();
    }
}