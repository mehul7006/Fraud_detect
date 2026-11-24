package com.fraudshield.safety;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 15.1: the phone reports live-scam signals (kinds only, no content); 15.2 reads the rules from /config. */
@RestController
@RequestMapping("/api/v1/scam-signals")
public class ScamSessionController {
    public record SignalRequest(@NotNull ScamSignal.Kind signal, Boolean unknownCaller,
                                @Min(0) @Max(86_400) Integer callSeconds) {}
    public record Config(long windowMinutes, int longCallSeconds, List<String> remoteAccessPackages) {}

    private final ScamSessionService sessions;

    public ScamSessionController(ScamSessionService sessions) { this.sessions = sessions; }

    @PostMapping
    public ScamSessionService.Result signal(@AuthenticationPrincipal Long uid, @Valid @RequestBody SignalRequest r) {
        return sessions.signal(uid, r.signal(), r.unknownCaller(), r.callSeconds());
    }

    @GetMapping("/config")
    public Config config() {
        return new Config(sessions.window().toMinutes(), sessions.longCallSeconds, sessions.remoteAccessPackages);
    }
}
