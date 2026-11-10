package com.dnstore.backend.repository;

import com.dnstore.backend.model.MelhorEnvioToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MelhorEnvioTokenRepository extends JpaRepository<MelhorEnvioToken, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT token FROM MelhorEnvioToken token WHERE token.environment = :environment")
    Optional<MelhorEnvioToken> findByEnvironmentForUpdate(@Param("environment") String environment);
}
