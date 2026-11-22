package com.fraudshield.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 8.2: one refresh token (only its SHA-256 is stored). See V10__refresh_tokens.sql. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false)
    private String family;
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;
    @Column(nullable = false)
    private String client;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshToken() {}
    public RefreshToken(Long userId, String family, String tokenHash, String client, Instant expiresAt) {
        this.userId = userId; this.family = family; this.tokenHash = tokenHash; this.client = client; this.expiresAt = expiresAt;
    }

    public Long getUserId() { return userId; }
    public String getFamily() { return family; }
    public String getClient() { return client; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public boolean isRevoked() { return revokedAt != null; }
    public void revoke() { if (revokedAt == null) revokedAt = Instant.now(); }
}
