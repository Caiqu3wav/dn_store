package com.dnstore.backend.model;

import com.dnstore.backend.model.enums.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
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
@Table(name = "users")
@Data
public class User implements UserDetails {

    @Column(length = 64)
    private String resetToken;

    @Column(name = "reset_token_expiration")
    private LocalDateTime resetTokenExpiration;

    @Column(name = "reset_token_last_sent_at")
    private LocalDateTime resetTokenLastSentAt;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = true;

    @Column(name = "email_verification_code_hash", length = 64)
    private String emailVerificationCodeHash;

    @Column(name = "email_verification_expiration")
    private LocalDateTime emailVerificationExpiration;

    @Column(name = "email_verification_last_sent_at")
    private LocalDateTime emailVerificationLastSentAt;

    @Column(name = "email_verification_attempts", nullable = false)
    private int emailVerificationAttempts;

    @Column(name = "mfa_challenge_token_hash", length = 64)
    private String mfaChallengeTokenHash;

    @Column(name = "mfa_code_hash", length = 64)
    private String mfaCodeHash;

    @Column(name = "mfa_code_expiration")
    private LocalDateTime mfaCodeExpiration;

    @Column(name = "mfa_last_sent_at")
    private LocalDateTime mfaLastSentAt;

    @Column(name = "mfa_attempts", nullable = false)
    private int mfaAttempts;

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

    @Column(length = 11, unique = true)
    private String cpf;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
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
