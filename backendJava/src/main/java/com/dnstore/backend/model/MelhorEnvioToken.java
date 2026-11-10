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
@Table(name = "melhor_envio_tokens")
public class MelhorEnvioToken {

    @Id
    @Column(name = "environment", length = 16, nullable = false)
    private String environment;

    @Column(name = "access_token_ciphertext", nullable = false, length = 2048)
    private byte[] accessTokenCiphertext;

    @Column(name = "refresh_token_ciphertext", nullable = false, length = 2048)
    private byte[] refreshTokenCiphertext;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "refresh_expires_at", nullable = false)
    private LocalDateTime refreshExpiresAt;

    @Column(name = "token_status", nullable = false, length = 24)
    private String status;
}
