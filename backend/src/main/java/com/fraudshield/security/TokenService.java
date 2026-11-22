package com.fraudshield.security;

import com.fraudshield.domain.RefreshToken;
import com.fraudshield.domain.User;
import com.fraudshield.dto.Dtos.TokenResponse;
import com.fraudshield.repo.Repositories.RefreshTokenRepository;
import com.fraudshield.repo.Repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 8.2: issues access + refresh tokens, rotates refresh tokens, detects re-use, and logs out.
 * Refresh tokens are random 256-bit values; only their SHA-256 is stored.
 */
@Service
public class TokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JwtService jwt;
    private final RefreshTokenRepository refresh;
    private final UserRepository users;
    private final Duration refreshTtl;

    public TokenService(JwtService jwt, RefreshTokenRepository refresh, UserRepository users,
                        @Value("${fraudshield.jwt.refresh-ttl-days:30}") long refreshDays) {
        this.jwt = jwt; this.refresh = refresh; this.users = users; this.refreshTtl = Duration.ofDays(refreshDays);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private TokenResponse issue(User u, String family, String client) {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        refresh.save(new RefreshToken(u.getId(), family, hash(raw), client, Instant.now().plus(refreshTtl)));
        return new TokenResponse(jwt.create(u.getId(), u.getRole()), raw, jwt.ttlSeconds());
    }

    /** New login (register / login): a new family. */
    @Transactional
    public TokenResponse login(User u) { return issue(u, UUID.randomUUID().toString(), "app"); }

    /** Extra, independent session for another part of the app (the background scanner, 7.11). */
    @Transactional
    public TokenResponse extraSession(Long userId, String client) {
        User u = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        return issue(u, UUID.randomUUID().toString(), client);
    }

    /** Swap a refresh token for a new pair. A token that was already swapped is a stolen copy: revoke its family. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public TokenResponse refresh(String raw) {
        RefreshToken t = refresh.findByTokenHash(hash(raw))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (t.isRevoked()) {
            refresh.findByFamily(t.getFamily()).forEach(RefreshToken::revoke); // re-use detected
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token already used - please log in again");
        }
        if (t.getExpiresAt().isBefore(Instant.now())) {
            t.revoke();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired - please log in again");
        }
        User u = users.findById(t.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        t.revoke(); // used once; logout already revoked every refresh token of the user
        return issue(u, t.getFamily(), t.getClient());
    }

    /** Logout everywhere: every refresh token revoked, every access token issued until now rejected. */
    @Transactional
    public void logout(Long userId) {
        refresh.findByUserIdAndRevokedAtIsNull(userId).forEach(RefreshToken::revoke);
        users.findById(userId).ifPresent(u -> u.invalidateTokensIssuedBefore(Instant.now()));
    }
}
