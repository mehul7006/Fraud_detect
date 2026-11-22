package com.fraudshield.service;

import com.fraudshield.config.AdAbuseProperties;
import com.fraudshield.domain.Enums.Verdict;
import com.fraudshield.dto.Dtos.AppInfo;
import com.fraudshield.dto.Dtos.AppResult;
import com.fraudshield.repo.Repositories.AppReputationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/** Ad-abuse / risky-app score. Weights come from {@link AdAbuseProperties} (6.1); reputation overrides scoring (6.2). */
@Service
public class AdAbuseService {
    private final AdAbuseProperties w;
    private final AppReputationRepository reputation; // null in plain unit tests

    @Autowired
    public AdAbuseService(AdAbuseProperties w, AppReputationRepository reputation) { this.w = w; this.reputation = reputation; }

    /** Default weights, no reputation table (unit tests and the accuracy test). */
    public AdAbuseService() { this(new AdAbuseProperties(), null); }

    public AdAbuseService(AdAbuseProperties w) { this(w, null); }

    public AppResult score(AppInfo a) {
        if (a.isSystem()) return new AppResult(a.packageName(), 0, Verdict.SAFE, List.of());
        if (reputation != null) {
            var rep = reputation.findByPackageName(a.packageName());
            if (rep.isPresent()) {
                var r = rep.get();
                if ("BAD".equals(r.getVerdict()))
                    return new AppResult(a.packageName(), 100, Verdict.MALICIOUS, List.of("Known harmful app"));
                // GOOD: only the genuine app is trusted (6.2); a look-alike using the same package name is not
                switch (AppTrust.check(r.getCertSha256(), a.certSha256(), a.installer())) {
                    case GENUINE -> { return new AppResult(a.packageName(), 0, Verdict.SAFE, List.of()); }
                    case FAKE -> {
                        String name = r.getLabel() != null ? r.getLabel() : a.packageName();
                        return new AppResult(a.packageName(), 100, Verdict.MALICIOUS,
                                List.of("Fake copy of " + name + ": not signed by the real developer"));
                    }
                    case UNVERIFIED -> { /* not proven genuine: score it like any other app */ }
                }
            }
        }
        List<String> reasons = new ArrayList<>();
        int score = 0;
        if (a.notifPerHour() >= w.getNotifHigh()) { score += w.getNotifHighWeight(); reasons.add(a.notifPerHour() + " notifications/hour"); }
        else if (a.notifPerHour() >= w.getNotifMid()) { score += w.getNotifMidWeight(); reasons.add(a.notifPerHour() + " notifications/hour"); }
        else if (a.notifPerHour() >= w.getNotifLow()) { score += w.getNotifLowWeight(); reasons.add("Frequent notifications"); }
        if (a.hasOverlayPermission()) { score += w.getOverlayWeight(); reasons.add("Can draw over other apps"); }
        if (a.hasFullScreenIntent()) { score += w.getFullScreenWeight(); reasons.add("Can launch full-screen pop-ups"); }
        if (a.hasAccessibility()) { score += w.getAccessibilityWeight(); reasons.add("Uses accessibility service"); }
        if (a.hasSmsPermission()) { score += w.getSmsWeight(); reasons.add("Can read/receive SMS"); }
        if (a.hasNotificationListener()) { score += w.getListenerWeight(); reasons.add("Can read your notifications"); }
        if (a.selfLaunchCount() >= w.getSelfLaunchHigh()) { score += w.getSelfLaunchHighWeight(); reasons.add("Opened itself " + a.selfLaunchCount() + " times in 24 h"); }
        else if (a.selfLaunchCount() >= w.getSelfLaunchLow()) { score += w.getSelfLaunchLowWeight(); reasons.add("Opened itself without you"); }
        score = Math.min(100, score);
        return new AppResult(a.packageName(), score, Verdict.fromScore(score), reasons);
    }

    public List<AppResult> scanAll(List<AppInfo> apps) { return apps.stream().map(this::score).toList(); }
}
