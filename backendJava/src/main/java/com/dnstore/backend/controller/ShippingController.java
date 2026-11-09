package com.dnstore.backend.controller;

import com.dnstore.backend.model.User;
import com.dnstore.backend.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final OrderService orderService;

    @GetMapping("/quote")
    public List<OrderService.ShippingOption> quote(
            @RequestParam String zipCode,
            @AuthenticationPrincipal User user) {
        return orderService.quoteShipping(user, zipCode);
    }
}
