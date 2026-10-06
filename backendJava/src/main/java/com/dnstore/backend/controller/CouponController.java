package com.dnstore.backend.controller;

import com.dnstore.backend.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @GetMapping("/validate")
    public ResponseEntity<?> validate(@RequestParam String code) {
        return couponService.validateCoupon(code)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseThrow(() -> new IllegalArgumentException("Cupom inválido ou expirado."));
    }
}