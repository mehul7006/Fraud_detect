package com.fraudshield.service;

import com.fraudshield.domain.Device;
import com.fraudshield.dto.Dtos.DeviceRequest;
import com.fraudshield.dto.Dtos.DeviceResponse;
import com.fraudshield.repo.Repositories.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Cloning cannot be proven from inside a phone; we score observable signals.
 */
@Service
public class CloneRiskService {
    static final Set<String> REMOTE_APPS = Set.of("com.anydesk.anydeskandroid", "com.teamviewer.quicksupport.market",
            "com.teamviewer.host.market", "com.sand.airdroid", "com.airdroid.kids", "com.rustdesk.rustdesk",
            "com.microsoft.rdc.androidx", "net.christianbeier.droidvnc_ng");

    private final DeviceRepository devices;

    public CloneRiskService(DeviceRepository devices) { this.devices = devices; }

    @Transactional
    public DeviceResponse register(Long userId, DeviceRequest r) {
        Device d = devices.findByUserIdAndDeviceId(userId, r.deviceId()).orElseGet(() -> new Device(userId, r.deviceId()));
        List<String> reasons = new ArrayList<>();
        int score = 0;

        if (d.getId() != null && d.getSimHash() != null && r.simHash() != null && !d.getSimHash().equals(r.simHash())) {
            score += 30; reasons.add("SIM card changed since last check");
            d.markSimChanged();
        }
        if (d.getId() != null && d.getFingerprint() != null && r.fingerprint() != null
                && !d.getFingerprint().equals(r.fingerprint())) {
            score += 20; reasons.add("Device hardware/OS fingerprint changed");
        }
        // Same account active on another device with identical model+fingerprint = strong clone sign
        Instant recent = Instant.now().minus(Duration.ofDays(1));
        for (Device other : devices.findByUserId(userId)) {
            if (other.getDeviceId().equals(r.deviceId())) continue;
            if (other.getLastSeen().isAfter(recent) && Objects.equals(other.getFingerprint(), r.fingerprint())
                    && r.fingerprint() != null) {
                score += 50; reasons.add("Another device with identical fingerprint is active (possible clone)");
            }
        }
        if (r.rooted()) { score += 20; reasons.add("Device is rooted"); }
        if (r.emulator()) { score += 25; reasons.add("Running in emulator/virtual environment"); }
        if (!r.playProtectOn()) { score += 10; reasons.add("Google Play Protect is off"); }
        if (r.remoteAppsInstalled() != null) {
            for (String p : r.remoteAppsInstalled()) {
                if (REMOTE_APPS.contains(p)) { score += 25; reasons.add("Remote-access app installed: " + p); }
            }
        }
        score = Math.min(100, score);

        d.setModel(r.model()); d.setOsVersion(r.osVersion());
        d.setSimHash(r.simHash()); d.setFingerprint(r.fingerprint());
        if (r.pushToken() != null && !r.pushToken().isBlank()) d.setPushToken(r.pushToken());
        d.setCloneRiskScore(score); d.touch();
        devices.save(d);
        return new DeviceResponse(r.deviceId(), score, reasons);
    }
}
