package com.fraudshield.service;

import com.fraudshield.domain.Device;
import com.fraudshield.domain.Enums.Severity;
import com.fraudshield.domain.Enums.ThreatType;
import com.fraudshield.domain.ThreatEvent;
import com.fraudshield.repo.Repositories.DeviceRepository;
import com.fraudshield.repo.Repositories.ThreatEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 7.2: account-takeover correlation. One signal alone is common (people get OTPs, buy phones, swap SIMs).
 * A login code / linked-device notice AND a new device or SIM change for the same account within a few minutes
 * is the classic takeover pattern (scammer asks for the OTP, registers the account on their phone / swapped SIM).
 * Both together raise ONE CRITICAL "Account takeover in progress" threat (one OPEN at a time).
 */
@Service
public class TakeoverService {
    static final Set<ThreatType> CODE_SIGNALS =
            EnumSet.of(ThreatType.UNEXPECTED_OTP, ThreatType.WHATSAPP_TAKEOVER, ThreatType.TELEGRAM_TAKEOVER);

    private final DeviceRepository devices;
    private final ThreatEventRepository events;
    private final ThreatService threats;
    private final Duration window;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public TakeoverService(DeviceRepository devices, ThreatEventRepository events, ThreatService threats,
                           @Value("${fraudshield.takeover.window-minutes:10}") long windowMinutes) {
        this(devices, events, threats, Duration.ofMinutes(windowMinutes), Clock.systemUTC());
    }

    TakeoverService(DeviceRepository devices, ThreatEventRepository events, ThreatService threats,
                    Duration window, Clock clock) {
        this.devices = devices; this.events = events; this.threats = threats; this.window = window; this.clock = clock;
    }

    /** Device-side signals in the window: a NEW device (not the account's first one) or a SIM change. */
    List<String> deviceSignals(Long userId, Instant since) {
        List<Device> all = devices.findByUserId(userId);
        Instant oldest = all.stream().map(Device::getFirstSeen).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        List<String> out = new ArrayList<>();
        for (Device d : all) {
            boolean isNew = d.getFirstSeen() != null && d.getFirstSeen().isAfter(since) && oldest != null
                    && d.getFirstSeen().isAfter(oldest); // the account's very first phone is not "new"
            if (isNew) out.add("New device signed in: " + label(d));
            if (d.getSimChangedAt() != null && d.getSimChangedAt().isAfter(since)) out.add("SIM card changed on " + label(d));
        }
        return out;
    }

    /** Run after every device check-in and every code / linked-device report. Returns the takeover threat if raised. */
    public Optional<ThreatService.Reported> check(Long userId) {
        Instant since = Instant.now(clock).minus(window);
        List<String> deviceSide = deviceSignals(userId, since);
        if (deviceSide.isEmpty()) return Optional.empty();
        List<ThreatEvent> codes = events.findByUserIdAndTypeInAndCreatedAtAfter(userId, CODE_SIGNALS, since);
        if (codes.isEmpty()) return Optional.empty();
        List<String> parts = new ArrayList<>(deviceSide);
        codes.stream().map(e -> e.getTitle() == null ? e.getType().name() : e.getTitle()).distinct().forEach(parts::add);
        String detail = "Within " + window.toMinutes() + " minutes: " + String.join("; ", parts)
                + ". Someone may be moving your account to their phone. Do not share any code; follow the recovery steps.";
        return Optional.of(threats.report(userId, ThreatType.ACCOUNT_TAKEOVER, Severity.CRITICAL,
                "Account takeover in progress", detail, "correlation"));
    }

    private static String label(Device d) {
        return d.getModel() == null || d.getModel().isBlank() ? "another phone" : d.getModel();
    }
}
