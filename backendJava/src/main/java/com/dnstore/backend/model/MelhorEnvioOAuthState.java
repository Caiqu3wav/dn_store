package com.dnstore.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "melhor_envio_oauth_states")
public class MelhorEnvioOAuthState {

    @Id
    @Column(name = "state_hash", length = 64, nullable = false)
    private String stateHash;

    @Column(name = "environment", length = 16, nullable = false)
    private String environment;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
