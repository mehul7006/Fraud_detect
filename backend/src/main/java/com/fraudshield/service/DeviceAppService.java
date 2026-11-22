package com.fraudshield.service;

import com.fraudshield.domain.DeviceApp;
import com.fraudshield.domain.Enums.Verdict;
import com.fraudshield.dto.Dtos.AppResult;
import com.fraudshield.repo.Repositories.DeviceAppRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 6.3: remembers each device's apps so the response can say which risky apps are new since the last scan. */
@Service
public class DeviceAppService {
    /** results with newSinceLastScan filled in, plus the apps that just BECAME malicious on this device. */
    public record Outcome(List<AppResult> results, List<AppResult> newlyMalicious) {}

    private final DeviceAppRepository repo;
    public DeviceAppService(DeviceAppRepository repo) { this.repo = repo; }

    @Transactional
    public Outcome track(Long userId, String deviceId, List<AppResult> scored) {
        Map<String, DeviceApp> known = repo.findByUserIdAndDeviceId(userId, deviceId).stream()
                .collect(Collectors.toMap(DeviceApp::getPackageName, Function.identity(), (a, b) -> a));
        boolean firstScan = known.isEmpty();
        List<AppResult> out = new ArrayList<>();
        List<AppResult> newlyBad = new ArrayList<>();
        for (AppResult r : scored) {
            DeviceApp row = known.get(r.packageName());
            boolean isNew = row == null && !firstScan;
            boolean wasBad = row != null && Verdict.MALICIOUS.name().equals(row.getLastVerdict());
            if (row == null) row = new DeviceApp(userId, deviceId, r.packageName());
            row.seen(r.adAbuseScore(), r.verdict().name());
            repo.save(row);
            AppResult withFlag = new AppResult(r.packageName(), r.adAbuseScore(), r.verdict(), r.reasons(), isNew);
            out.add(withFlag);
            if (r.verdict() == Verdict.MALICIOUS && !wasBad) newlyBad.add(withFlag);
        }
        return new Outcome(out, newlyBad);
    }
}
