package com.dnstore.backend.service;

import com.dnstore.backend.model.Coupon;
import com.dnstore.backend.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    public Optional<Coupon> validateCoupon(String code) {

        Optional<Coupon> couponOpt = couponRepository.findByCodeIgnoreCase(code);

        if (couponOpt.isEmpty()) {
            return Optional.empty();
        }

        Coupon coupon = couponOpt.get();

        if (!Boolean.TRUE.equals(coupon.getActive())) {
            return Optional.empty();
        }

        if (coupon.getExpiresAt() != null &&
                coupon.getExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }

        return Optional.of(coupon);
    }
}