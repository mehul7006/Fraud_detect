package com.fraudshield.controller;

import com.fraudshield.domain.UrlFeedback;
import com.fraudshield.dto.Dtos.FeedbackRequest;
import com.fraudshield.repo.Repositories.UrlFeedbackRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/scan/url/feedback")
public class FeedbackController {
    private final UrlFeedbackRepository repo;
    public FeedbackController(UrlFeedbackRepository repo) { this.repo = repo; }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@AuthenticationPrincipal Long uid, @Valid @RequestBody FeedbackRequest r) {
        String host = hostOf(r.url());
        if (repo.existsByUserIdAndHostAndStatus(uid, host, "OPEN"))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already reported this site");
        try {
            repo.save(new UrlFeedback(uid, host, r.kind(), r.note()));
        } catch (org.springframework.dao.DataIntegrityViolationException e) { // concurrent duplicate
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already reported this site");
        }
    }

    static String hostOf(String url) {
        try {
            String u = url.trim();
            String host = URI.create(u.contains("://") ? u : "http://" + u).getHost();
            if (host != null && !host.isBlank()) return host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) { }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a valid URL");
    }
}
