package com.dnstore.backend.repository;

import com.dnstore.backend.model.MelhorEnvioOAuthState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface MelhorEnvioOAuthStateRepository extends JpaRepository<MelhorEnvioOAuthState, String> {

    @Modifying
    @Query("""
            DELETE FROM MelhorEnvioOAuthState state
            WHERE state.stateHash = :stateHash
              AND state.environment = :environment
              AND state.expiresAt > :now
            """)
    int consume(
            @Param("stateHash") String stateHash,
            @Param("environment") String environment,
            @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("DELETE FROM MelhorEnvioOAuthState state WHERE state.expiresAt <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
