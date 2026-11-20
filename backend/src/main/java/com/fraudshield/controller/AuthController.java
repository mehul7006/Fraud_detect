package com.fraudshield.controller;

import com.fraudshield.domain.User;
import com.fraudshield.dto.Dtos.AuthRequest;
import com.fraudshield.dto.Dtos.TokenResponse;
import com.fraudshield.repo.Repositories.UserRepository;
import com.fraudshield.security.JwtService;
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
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users; this.encoder = encoder; this.jwt = jwt;
    }

    @PostMapping("/register")
    public TokenResponse register(@Valid @RequestBody AuthRequest r) {
        String email = r.email().toLowerCase();
        if (users.findByEmail(email).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already used");
        User u = users.save(new User(email, encoder.encode(r.password())));
        return new TokenResponse(jwt.create(u.getId()));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody AuthRequest r) {
        User u = users.findByEmail(r.email().toLowerCase())
                .filter(x -> encoder.matches(r.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        return new TokenResponse(jwt.create(u.getId()));
    }
}
