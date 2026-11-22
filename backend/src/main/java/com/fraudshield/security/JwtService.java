package com.fraudshield.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Access tokens (JWT, HS256). 8.2: short-lived (default 15 min) - the app keeps a refresh token for new ones.
 * "iatms" = issue time in milliseconds, so logout (users.tokens_valid_after) can reject tokens issued even a few
 * milliseconds before it while a login right after it still works (the standard "iat" only has whole seconds).
 */
@Service
public class JwtService {
    public record Access(Long userId, String role, Instant issuedAt) {}

    private final SecretKey key;
    private final long ttlMillis;

    public JwtService(@Value("${fraudshield.jwt.secret}") String secret,
                      @Value("${fraudshield.jwt.ttl-minutes}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlMinutes * 60_000;
    }

    public long ttlSeconds() { return ttlMillis / 1000; }

    public String create(Long userId) { return create(userId, "USER"); }

    public String create(Long userId, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder().subject(String.valueOf(userId)).claim("role", role).claim("iatms", now)
                .issuedAt(new Date(now)).expiration(new Date(now + ttlMillis)).signWith(key).compact();
    }

    /** @return the verified token, or null when invalid / expired */
    public Access verify(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Object r = c.get("role"), ms = c.get("iatms");
            Instant iat = ms instanceof Number n ? Instant.ofEpochMilli(n.longValue())
                    : c.getIssuedAt() == null ? Instant.EPOCH : c.getIssuedAt().toInstant();
            return new Access(Long.valueOf(c.getSubject()), r == null ? "USER" : r.toString(), iat);
        } catch (Exception e) {
            return null;
        }
    }

    /** @return role claim ("USER" when missing) or null when token invalid/expired */
    public String parseRole(String token) {
        Access a = verify(token);
        return a == null ? null : a.role();
    }

    /** @return userId or null when token invalid/expired */
    public Long parse(String token) {
        Access a = verify(token);
        return a == null ? null : a.userId();
    }
}
