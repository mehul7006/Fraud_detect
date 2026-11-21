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
    SecurityFilterChain chain(HttpSecurity http, JwtService jwt) throws Exception {
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
                        Long uid = jwt.parse(h.substring(7));
                        if (uid != null) {
                            String role = jwt.parseRole(h.substring(7));
                            var authorities = "ADMIN".equals(role)
                                ? List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
                                : List.<org.springframework.security.core.authority.SimpleGrantedAuthority>of();
                            SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(uid, null, authorities));
                        }
                    }
                    chain.doFilter(req, res);
                }
            }, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
