package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dnstore.backend.model.Coupon;
import com.dnstore.backend.repository.CouponRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;
    @InjectMocks
    private CouponService couponService;

    private Coupon coupon() {
        Coupon coupon = new Coupon();
        coupon.setId(UUID.randomUUID());
        coupon.setActive(true);
        return coupon;
    }

    @Test
    void registerUsage_succeedsWhenTheAtomicUpdateTookTheLastSlot() {
        Coupon coupon = coupon();
        when(couponRepository.consumeUsage(coupon.getId())).thenReturn(1);

        couponService.registerUsage(coupon);

        verify(couponRepository).consumeUsage(coupon.getId());
    }

    @Test
    void registerUsage_failsWhenTheLimitWasAlreadyReached() {
        Coupon coupon = coupon();
        when(couponRepository.consumeUsage(coupon.getId())).thenReturn(0);

        assertThatThrownBy(() -> couponService.registerUsage(coupon))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limite máximo de uso");
    }

    @Test
    void releaseUsage_neverFailsWhenThereIsNothingToGiveBack() {
        Coupon coupon = coupon();
        when(couponRepository.releaseUsage(coupon.getId())).thenReturn(0);

        couponService.releaseUsage(coupon);

        verify(couponRepository).releaseUsage(coupon.getId());
    }

    @Test
    void validateCoupon_rejectsInactiveAndExpiredCoupons() {
        Coupon inactive = coupon();
        inactive.setActive(false);
        Coupon expired = coupon();
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(couponRepository.findByCodeIgnoreCase("OFF")).thenReturn(Optional.of(inactive));
        when(couponRepository.findByCodeIgnoreCase("OLD")).thenReturn(Optional.of(expired));

        assertThat(couponService.validateCoupon("OFF")).isEmpty();
        assertThat(couponService.validateCoupon("OLD")).isEmpty();
    }
}
