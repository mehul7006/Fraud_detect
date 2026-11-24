package com.fraudshield.service;

import com.fraudshield.domain.Enums.Verdict;
import com.fraudshield.dto.Dtos.AppInfo;
import com.fraudshield.dto.Dtos.AppResult;
import com.fraudshield.explain.ExplainService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 13.7: one result per app = ad-abuse score (6.x) + privacy / fraud score (13.3), with the worse verdict, stable
 * reason codes and a plain explanation (en / hi). Apps from older clients (no privacy data, no brand-like name) get
 * exactly the 6.x answer.
 */
@Service
public class AppScanService {
    private final AdAbuseService ads;
    private final AppPrivacyService privacy;
    private final ExplainService explain;

    @Autowired
    public AppScanService(AdAbuseService ads, AppPrivacyService privacy, ExplainService explain) {
        this.ads = ads; this.privacy = privacy; this.explain = explain;
    }

    public AppResult scan(AppInfo a) {
        AppResult ad = ads.score(a);
        if (!a.hasPrivacyData() && a.label() == null) return ad;
        var p = privacy.score(a);
        if (!a.hasPrivacyData() && p.impersonatedBrand() == null) return ad; // old client: unchanged answer
        Verdict v = worse(ad.verdict(), p.verdict());
        List<String> codes = new ArrayList<>(p.codes());
        if (ad.verdict() != Verdict.SAFE && !codes.contains("AD_ABUSE")) codes.add("AD_ABUSE");
        var e = v == Verdict.SAFE ? null : explain.explain(v.name(), codes, p.impersonatedBrand());
        return new AppResult(a.packageName(), ad.adAbuseScore(), v, ad.reasons(), ad.newSinceLastScan(), p.score(), p.reasons(),
                codes, p.impersonatedBrand(), e);
    }

    public List<AppResult> scanAll(List<AppInfo> apps) { return apps.stream().map(this::scan).toList(); }

    static Verdict worse(Verdict a, Verdict b) { return a.ordinal() >= b.ordinal() ? a : b; }
}
