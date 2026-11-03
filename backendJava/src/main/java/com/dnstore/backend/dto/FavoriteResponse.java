package com.dnstore.backend.dto;

import java.util.UUID;

public record FavoriteResponse(UUID id, ProductResponse product) {
}
