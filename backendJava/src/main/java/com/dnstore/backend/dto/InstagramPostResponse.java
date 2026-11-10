package com.dnstore.backend.dto;

import java.util.List;

public record InstagramPostResponse(
        String id,
        String caption,
        String mediaType,
        String mediaUrl,
        String thumbnailUrl,
        String permalink,
        String timestamp,
        List<MediaItem> children
) {
    public record MediaItem(
            String id,
            String mediaType,
            String mediaUrl,
            String thumbnailUrl,
            String timestamp
    ) {}
}
