package com.dnstore.backend.model;

import com.dnstore.backend.model.enums.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_reset_token", columnList = "reset_token"),
        @Index(name = "idx_users_mfa_challenge_token_hash", columnList = "mfa_challenge_token_hash")
})
public class User implements UserDetails {

    @JsonIgnore
    @Column(length = 64)
    private String resetToken;

    @JsonIgnore
    @Column(name = "reset_token_expiration")
    private LocalDateTime resetTokenExpiration;

    @JsonIgnore
    @Column(name = "reset_token_last_sent_at")
    private LocalDateTime resetTokenLastSentAt;

    @JsonIgnore
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = true;

    @JsonIgnore
    @Column(name = "email_verification_code_hash", length = 64)
    private String emailVerificationCodeHash;

    @JsonIgnore
    @Column(name = "email_verification_expiration")
    private LocalDateTime emailVerificationExpiration;

    @JsonIgnore
    @Column(name = "email_verification_last_sent_at")
    private LocalDateTime emailVerificationLastSentAt;

    @JsonIgnore
    @Column(name = "email_verification_attempts", nullable = false)
    private int emailVerificationAttempts;

    @JsonIgnore
    @Column(name = "mfa_challenge_token_hash", length = 64)
    private String mfaChallengeTokenHash;

    @JsonIgnore
    @Column(name = "mfa_code_hash", length = 64)
    private String mfaCodeHash;

    @JsonIgnore
    @Column(name = "mfa_code_expiration")
    private LocalDateTime mfaCodeExpiration;

    @JsonIgnore
    @Column(name = "mfa_last_sent_at")
    private LocalDateTime mfaLastSentAt;

    @JsonIgnore
    @Column(name = "mfa_attempts", nullable = false)
    private int mfaAttempts;

    @JsonIgnore
    @Column(name = "email_mfa_enabled", nullable = false)
    private boolean emailMfaEnabled;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @JsonIgnore
    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 20)
    private String phone;

    @JsonIgnore
    @Column(length = 11, unique = true)
    private String cpf;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // UserDetails implementation for Spring Security
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return emailVerified;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public LocalDateTime getResetTokenExpiration() {
        return resetTokenExpiration;
    }

    public void setResetTokenExpiration(LocalDateTime resetTokenExpiration) {
        this.resetTokenExpiration = resetTokenExpiration;
    }

}
