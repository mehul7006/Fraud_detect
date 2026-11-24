package com.fraudshield.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private String role = "USER";
    /** 8.2: access tokens issued before this moment are rejected (set by logout). */
    @Column(name = "tokens_valid_after")
    private Instant tokensValidAfter;

    protected User() {}
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public User(String email, String passwordHash) { this.email = email; this.passwordHash = passwordHash; }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public Instant getCreatedAt() { return createdAt; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getTokensValidAfter() { return tokensValidAfter; }
    public void invalidateTokensIssuedBefore(Instant t) { this.tokensValidAfter = t; }
}
