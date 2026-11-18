package com.dnstore.backend.service;

import com.dnstore.backend.model.Coupon;
import com.dnstore.backend.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
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

    /**
     * Consome uma utilização do cupom dentro da transação do checkout. A checagem do limite e o incremento
     * são um único UPDATE atômico no banco, então compras simultâneas não ultrapassam {@code maxUsage}.
     * Se o checkout falhar depois, o rollback devolve a utilização junto.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registerUsage(Coupon coupon) {
        if (couponRepository.consumeUsage(coupon.getId()) == 0) {
            throw new IllegalArgumentException("Este cupom já atingiu o limite máximo de uso.");
        }
    }

    /** Devolve a utilização de um pedido cancelado/expirado. Idempotência é garantida por quem chama. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseUsage(Coupon coupon) {
        if (couponRepository.releaseUsage(coupon.getId()) == 0) {
            log.warn("coupon.release_skipped couponId={} reason=usage_already_zero", coupon.getId());
        }
    }

    /** Reaplica a utilização quando um pedido com estoque devolvido volta a reservar estoque. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reapplyUsage(Coupon coupon) {
        couponRepository.reapplyUsage(coupon.getId());
    }
}