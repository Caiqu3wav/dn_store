package com.dnstore.backend.repository;

import com.dnstore.backend.model.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByCodeIgnoreCase(String code);

    /**
     * Consome uma utilização de forma atômica: o banco só incrementa se ainda houver vaga, e o lock de linha
     * do UPDATE serializa checkouts simultâneos. Evita o "ler, comparar, gravar" que deixava duas compras
     * passarem pelo último uso. Retorna 1 se consumiu, 0 se o limite já foi atingido.
     */
    @Modifying
    @Query("UPDATE Coupon c SET c.currentUsage = COALESCE(c.currentUsage, 0) + 1 "
            + "WHERE c.id = :id AND (c.maxUsage IS NULL OR COALESCE(c.currentUsage, 0) < c.maxUsage)")
    int consumeUsage(@Param("id") UUID id);

    /** Devolve uma utilização (nunca abaixo de zero). Retorna 0 se não havia o que devolver. */
    @Modifying
    @Query("UPDATE Coupon c SET c.currentUsage = c.currentUsage - 1 "
            + "WHERE c.id = :id AND c.currentUsage IS NOT NULL AND c.currentUsage > 0")
    int releaseUsage(@Param("id") UUID id);

    /** Reaplica uma utilização já devolvida (pedido reativado), sem verificar o limite. */
    @Modifying
    @Query("UPDATE Coupon c SET c.currentUsage = COALESCE(c.currentUsage, 0) + 1 WHERE c.id = :id")
    int reapplyUsage(@Param("id") UUID id);
}
