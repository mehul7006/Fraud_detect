package com.fraudshield.controller;

import com.fraudshield.domain.User;
import com.fraudshield.dto.Dtos.AuthRequest;
import com.fraudshield.dto.Dtos.RefreshRequest;
import com.fraudshield.dto.Dtos.TokenResponse;
import com.fraudshield.repo.Repositories.UserRepository;
import com.fraudshield.security.TokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
@io.swagger.v3.oas.annotations.security.SecurityRequirements   // public endpoints: no token needed
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;

    public AuthController(UserRepository users, PasswordEncoder encoder, TokenService tokens) {
        this.users = users; this.encoder = encoder; this.tokens = tokens;
    }

    @PostMapping("/register")
    public TokenResponse register(@Valid @RequestBody AuthRequest r) {
        String email = r.email().toLowerCase();
        if (users.findByEmail(email).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already used");
        User u = users.save(new User(email, encoder.encode(r.password())));
        return tokens.login(u);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody AuthRequest r) {
        User u = users.findByEmail(r.email().toLowerCase())
                .filter(x -> encoder.matches(r.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        return tokens.login(u);
    }

    /** 8.2: swap a refresh token (single use) for a new access + refresh token. */
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest r) { return tokens.refresh(r.refreshToken()); }

    /** 8.2: log out everywhere - all refresh tokens revoked, every access token issued so far rejected. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@org.springframework.security.core.annotation.AuthenticationPrincipal Long uid) {
        if (uid == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        tokens.logout(uid);
    }

    /** 8.2 / 7.11: an independent session for the background scanner, so it can renew itself while the app is closed. */
    @PostMapping("/background-session")
    public TokenResponse backgroundSession(@org.springframework.security.core.annotation.AuthenticationPrincipal Long uid) {
        if (uid == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        return tokens.extraSession(uid, "background");
    }
}
