package com.fraudshield.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecurityFilterChain chain(HttpSecurity http, JwtService jwt,
                              com.fraudshield.repo.Repositories.UserRepository users,
                              @org.springframework.beans.factory.annotation.Value("${fraudshield.ratelimit.enabled:true}") boolean rlOn,
                              @org.springframework.beans.factory.annotation.Value("${fraudshield.ratelimit.per-minute:100}") long rlPerMin,
                              @org.springframework.beans.factory.annotation.Value("${fraudshield.ratelimit.auth-per-minute:10}") long rlAuthPerMin,
                              @org.springframework.beans.factory.annotation.Value("${fraudshield.cors.allowed-origins:}") List<String> corsOrigins)
            throws Exception {
        // 8.3: security headers on every response. The API returns JSON only, so the strictest CSP fits; the Swagger UI
        // pages (dev only, disabled in prod) need their scripts, so they are left out of the CSP.
        var notDocs = new org.springframework.security.web.util.matcher.NegatedRequestMatcher(
                new org.springframework.security.web.util.matcher.OrRequestMatcher(
                        new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/swagger-ui/**"),
                        new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/swagger-ui.html"),
                        new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/v3/api-docs/**")));
        http.headers(h -> h
                .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())
                .frameOptions(f -> f.deny())
                .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .httpStrictTransportSecurity(t -> t.includeSubDomains(true).maxAgeInSeconds(31_536_000)) // only on HTTPS
                .addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter(
                        "Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                .addHeaderWriter(new org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter(notDocs,
                        new org.springframework.security.web.header.writers.StaticHeadersWriter(
                                "Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))));
        // 8.3: CORS closed unless web origins are named (the Android app sends no Origin, so it is unaffected)
        http.cors(c -> c.configurationSource(req -> {
            var cfg = new org.springframework.web.cors.CorsConfiguration();
            cfg.setAllowedOrigins(corsOrigins.stream().map(String::trim).filter(o -> !o.isEmpty()).toList());
            cfg.setAllowedMethods(List.of("GET", "POST", "DELETE"));
            cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
            cfg.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
            cfg.setMaxAge(3600L);
            return cfg;
        }));
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/health", "/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> com.fraudshield.error.ErrorBody.write(
                    res, org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication required", req.getRequestURI()))
                .accessDeniedHandler((req, res, ex) -> com.fraudshield.error.ErrorBody.write(
                    res, org.springframework.http.HttpStatus.FORBIDDEN, "Access denied", req.getRequestURI())))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                        throws ServletException, IOException {
                    String h = req.getHeader("Authorization");
                    if (h != null && h.startsWith("Bearer ")) {
                        JwtService.Access acc = jwt.verify(h.substring(7));
                        // a validly signed token is not enough: the account must still exist (not deleted / DB reset)
                        // and the token must be newer than the user's last logout (8.2). Otherwise 401, never a 500.
                        var user = acc == null ? null : users.findById(acc.userId()).orElse(null);
                        boolean ok = user != null && (user.getTokensValidAfter() == null
                                || acc.issuedAt().isAfter(user.getTokensValidAfter()));
                        if (ok) {
                            Long uid = acc.userId();
                            String role = acc.role();
                            var authorities = "ADMIN".equals(role)
                                ? List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
                                : List.<org.springframework.security.core.authority.SimpleGrantedAuthority>of();
                            SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(uid, null, authorities));
                        }
                    }
                    chain.doFilter(req, res);
                }
            }, UsernamePasswordAuthenticationFilter.class)
            // 8.1: after the JWT filter, so limits are per user when logged in, per IP otherwise
            .addFilterAfter(new RateLimitFilter(rlOn, rlPerMin, rlAuthPerMin), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
