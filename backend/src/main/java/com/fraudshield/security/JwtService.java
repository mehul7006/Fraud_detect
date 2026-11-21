package com.fraudshield.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final long ttlMillis;

    public JwtService(@Value("${fraudshield.jwt.secret}") String secret,
                      @Value("${fraudshield.jwt.ttl-minutes}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlMinutes * 60_000;
    }

    public String create(Long userId) { return create(userId, "USER"); }

    public String create(Long userId, String role) {
        Date now = new Date();
        return Jwts.builder().subject(String.valueOf(userId)).claim("role", role).issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis)).signWith(key).compact();
    }

    /** @return role claim ("USER" when missing) or null when token invalid/expired */
    public String parseRole(String token) {
        try {
            Object r = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().get("role");
            return r == null ? "USER" : r.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** @return userId or null when token invalid/expired */
    public Long parse(String token) {
        try {
            String sub = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
            return Long.valueOf(sub);
        } catch (Exception e) {
            return null;
        }
    }
}
