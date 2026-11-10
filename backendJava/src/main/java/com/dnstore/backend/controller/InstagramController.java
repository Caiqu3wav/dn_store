package com.dnstore.backend.controller;

import com.dnstore.backend.dto.InstagramPostResponse;
import com.dnstore.backend.service.InstagramMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/instagram")
@RequiredArgsConstructor
public class InstagramController {

    private final InstagramMediaService instagramMediaService;

    @GetMapping("/mtb")
    public List<InstagramPostResponse> getMtbPosts() {
        return instagramMediaService.getMtbPosts();
    }

    @GetMapping("/store")
    public List<InstagramPostResponse> getStorePosts() {
        return instagramMediaService.getStorePosts();
    }
}
