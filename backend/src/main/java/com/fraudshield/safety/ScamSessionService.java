package com.fraudshield.safety;

import com.fraudshield.domain.Enums.Severity;
import com.fraudshield.domain.Enums.ThreatType;
import com.fraudshield.safety.SafetyRepositories.ScamSignalRepository;
import com.fraudshield.safety.ScamSignal.Kind;
import com.fraudshield.service.ThreatService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 15.1: live-scam ("screen-share") correlation, the same idea as 7.2 {@code TakeoverService}.
 * Each signal alone is everyday life: long calls, AnyDesk for IT support, banking apps, OTPs. All four together within
 * a few minutes is how remote-access bank fraud runs: the "bank officer" keeps you on the phone, has you install a
 * screen-sharing app, open your banking app and read out the OTP. Then ONE CRITICAL "Possible scam in progress"
 * threat is raised (one OPEN at a time) and pushed to the user's phones.
 * Privacy: the server receives and keeps only which KIND of signal happened and when (deleted after 24 h) - no
 * phone number, no app name, no notification text.
 */
@Service
public class ScamSessionService {
    public enum Level { NONE, WARNING, CRITICAL }
    public record Result(boolean counted, Level level, List<Kind> signals, long windowMinutes, Long threatId,
                         Map<String, String> advice) {}

    static final Map<String, String> ADVICE = Map.of(
            "en", "This looks like a screen-sharing scam. Hang up now. Do not read out or share any OTP. Close the screen-sharing app "
                    + "and do not open your bank app while the call is on. If money has gone, call 1930 at once.",
            "hi", "यह स्क्रीन-शेयरिंग धोखा लगता है। अभी कॉल काट दें। कोई OTP न बताएँ, न साझा करें। स्क्रीन-शेयरिंग ऐप बंद करें "
                    + "और कॉल चलते समय बैंक ऐप न खोलें। अगर पैसे कट गए हैं तो तुरंत 1930 पर कॉल करें।");

    private final ScamSignalRepository signals;
    private final ThreatService threats;
    private final Duration window;
    final int longCallSeconds;
    final List<String> remoteAccessPackages;
    private final Clock clock;

    @Autowired
    public ScamSessionService(ScamSignalRepository signals, ThreatService threats,
                              @Value("${fraudshield.scam-session.window-minutes:10}") long windowMinutes,
                              @Value("${fraudshield.scam-session.long-call-seconds:180}") int longCallSeconds,
                              // well-known remote-access apps (package names to re-check on Google Play before release)
                              @Value("${fraudshield.scam-session.remote-access-packages:com.anydesk.anydeskandroid,"
                                      + "com.teamviewer.quicksupport.market,com.teamviewer.teamviewer.market.mobile,"
                                      + "com.carriez.flutter_hbb,com.sand.airdroid,com.google.chromeremotedesktop,"
                                      + "com.microsoft.rdc.androidx,com.realvnc.viewer.android}") String[] remoteAccessPackages) {
        this(signals, threats, Duration.ofMinutes(windowMinutes), longCallSeconds, List.of(remoteAccessPackages), Clock.systemUTC());
    }

    ScamSessionService(ScamSignalRepository signals, ThreatService threats, Duration window, int longCallSeconds,
                       List<String> remoteAccessPackages, Clock clock) {
        this.signals = signals; this.threats = threats; this.window = window; this.longCallSeconds = longCallSeconds;
        this.remoteAccessPackages = remoteAccessPackages; this.clock = clock;
    }

    public Duration window() { return window; }

    /**
     * One signal from the phone. A call only counts when it is from an unknown number and has lasted at least
     * {@code long-call-seconds}; the phone may send it repeatedly while the call goes on.
     */
    public Result signal(Long userId, Kind kind, Boolean unknownCaller, Integer callSeconds) {
        Instant now = Instant.now(clock);
        signals.deleteOlderThan(now.minus(Duration.ofHours(24)));
        boolean counts = kind != Kind.LONG_UNKNOWN_CALL
                || (!Boolean.FALSE.equals(unknownCaller) && callSeconds != null && callSeconds >= longCallSeconds);
        if (counts) signals.save(new ScamSignal(userId, kind, now));
        return evaluate(userId, counts, now);
    }

    Result evaluate(Long userId, boolean counted, Instant now) {
        EnumSet<Kind> seen = EnumSet.noneOf(Kind.class);
        for (ScamSignal s : signals.findByUserIdAndCreatedAtAfter(userId, now.minus(window))) seen.add(s.getSignal());
        List<Kind> list = List.copyOf(seen);
        if (seen.size() == Kind.values().length) {
            String detail = "Within " + window.toMinutes() + " minutes: a long call from an unknown number, a screen-sharing app "
                    + "was open, a banking / UPI app was opened and a one-time code arrived. This is how screen-sharing bank "
                    + "scams work. Hang up, share no code, close the screen-sharing app.";
            var r = threats.report(userId, ThreatType.LIVE_SCAM, Severity.CRITICAL, "Possible scam in progress", detail, "correlation");
            return new Result(counted, Level.CRITICAL, list, window.toMinutes(), r.threat().id(), ADVICE);
        }
        // three of four with the call AND the screen-sharing app: warn on the phone now, no threat record yet
        if (seen.size() == 3 && seen.contains(Kind.LONG_UNKNOWN_CALL) && seen.contains(Kind.REMOTE_ACCESS_APP))
            return new Result(counted, Level.WARNING, list, window.toMinutes(), null, ADVICE);
        return new Result(counted, Level.NONE, list, window.toMinutes(), null, null);
    }
}
