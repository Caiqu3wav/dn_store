package com.dnstore.backend.controller;

import com.dnstore.backend.dto.FavoriteResponse;
import com.dnstore.backend.model.User;
import com.dnstore.backend.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public ResponseEntity<List<FavoriteResponse>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(favoriteService.findResponsesByUser(user.getId()));
    }

    @PostMapping("/{productId}")
    public ResponseEntity<FavoriteResponse> add(
            @AuthenticationPrincipal User user,
            @PathVariable UUID productId) {
        return ResponseEntity.ok(favoriteService.addResponse(user.getId(), productId));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal User user,
            @PathVariable UUID productId) {
        favoriteService.remove(user.getId(), productId);
        return ResponseEntity.noContent().build();
    }
}