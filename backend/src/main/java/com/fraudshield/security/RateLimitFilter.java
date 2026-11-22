package com.fraudshield.security;

import com.fraudshield.error.ErrorBody;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;

/**
 * 8.1: token-bucket rate limit (Bucket4j). Key = logged-in user id, or the client IP for anonymous calls.
 * Register/login get their own, much smaller bucket per IP (password guessing). Over the limit: 429 in the standard
 * error shape plus a Retry-After header. Buckets live in memory (one instance); several instances would need a shared
 * store (Bucket4j has Redis/JDBC back-ends) - see docs/SECRETS.md go-live notes.
 * Runs AFTER the JWT filter so it knows who the caller is.
 */
public class RateLimitFilter extends OncePerRequestFilter {
    private final boolean enabled;
    private final long perMinute, authPerMinute;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10)).maximumSize(200_000).build();

    public RateLimitFilter(boolean enabled, long perMinute, long authPerMinute) {
        this.enabled = enabled; this.perMinute = perMinute; this.authPerMinute = authPerMinute;
    }

    static boolean isAuthCall(HttpServletRequest r) {
        return "POST".equals(r.getMethod()) && r.getRequestURI().startsWith("/api/v1/auth/");
    }

    /** Health checks and API docs are never limited (load balancers poll them). */
    static boolean exempt(HttpServletRequest r) {
        String u = r.getRequestURI();
        return u.startsWith("/actuator/health") || u.startsWith("/v3/api-docs") || u.startsWith("/swagger-ui");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled || exempt(req)) { chain.doFilter(req, res); return; }
        boolean auth = isAuthCall(req);
        var a = SecurityContextHolder.getContext().getAuthentication();
        String who = auth || a == null || !(a.getPrincipal() instanceof Long uid) ? "ip:" + req.getRemoteAddr() : "user:" + uid;
        String key = (auth ? "auth|" : "api|") + who;
        long limit = auth ? authPerMinute : perMinute;
        Bucket b = buckets.get(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(limit).refillGreedy(limit, Duration.ofMinutes(1)).build()).build());
        ConsumptionProbe p = b.tryConsumeAndReturnRemaining(1);
        res.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(p.getRemainingTokens()));
        if (p.isConsumed()) { chain.doFilter(req, res); return; }
        long waitSeconds = Math.max(1, Duration.ofNanos(p.getNanosToWaitForRefill()).toSeconds());
        res.setHeader("Retry-After", String.valueOf(waitSeconds));
        ErrorBody.write(res, HttpStatus.TOO_MANY_REQUESTS,
                auth ? "Too many login attempts. Try again in " + waitSeconds + " s" : "Too many requests. Try again in " + waitSeconds + " s",
                req.getRequestURI());
    }
}
