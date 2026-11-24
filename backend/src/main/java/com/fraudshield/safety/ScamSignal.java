package com.fraudshield.safety;

import jakarta.persistence.*;
import java.time.Instant;

/** 15.1: one live-scam signal from the phone. Only the kind and the time - never a number, app name or text. */
@Entity
@Table(name = "scam_signals")
public class ScamSignal {
    public enum Kind {
        /** a call from a number that is not a saved contact has been going on for a long time */
        LONG_UNKNOWN_CALL,
        /** a screen-share / remote-access app (AnyDesk, TeamViewer ...) is in the foreground */
        REMOTE_ACCESS_APP,
        /** a banking or UPI app is in the foreground */
        BANKING_APP,
        /** an OTP / login-code notification arrived */
        OTP_SEEN
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private Kind signal;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ScamSignal() {}
    public ScamSignal(Long userId, Kind signal, Instant at) { this.userId = userId; this.signal = signal; this.createdAt = at; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Kind getSignal() { return signal; }
    public Instant getCreatedAt() { return createdAt; }
}
