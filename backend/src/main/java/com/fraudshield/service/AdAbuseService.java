package com.fraudshield.service;

import com.fraudshield.domain.Enums.Verdict;
import com.fraudshield.dto.Dtos.AppInfo;
import com.fraudshield.dto.Dtos.AppResult;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdAbuseService {

    public AppResult score(AppInfo a) {
        if (a.isSystem()) return new AppResult(a.packageName(), 0, Verdict.SAFE, List.of());
        List<String> reasons = new ArrayList<>();
        int score = 0;
        if (a.notifPerHour() >= 20) { score += 50; reasons.add(a.notifPerHour() + " notifications/hour"); }
        else if (a.notifPerHour() >= 8) { score += 30; reasons.add(a.notifPerHour() + " notifications/hour"); }
        else if (a.notifPerHour() >= 4) { score += 10; reasons.add("Frequent notifications"); }
        if (a.hasOverlayPermission()) { score += 25; reasons.add("Can draw over other apps"); }
        if (a.hasFullScreenIntent()) { score += 15; reasons.add("Can launch full-screen pop-ups"); }
        if (a.hasAccessibility()) { score += 25; reasons.add("Uses accessibility service"); }
        if (a.hasSmsPermission()) { score += 20; reasons.add("Can read/receive SMS"); }
        if (a.hasNotificationListener()) { score += 15; reasons.add("Can read your notifications"); }
        score = Math.min(100, score);
        return new AppResult(a.packageName(), score, Verdict.fromScore(score), reasons);
    }

    public List<AppResult> scanAll(List<AppInfo> apps) { return apps.stream().map(this::score).toList(); }
}
