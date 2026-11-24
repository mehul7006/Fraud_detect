package com.fraudshield.service;

import com.fraudshield.domain.Enums.Verdict;
import com.fraudshield.dto.Dtos.AppInfo;
import com.fraudshield.repo.Repositories.AppReputationRepository;
import com.fraudshield.service.CategoryRules.Category;
import com.fraudshield.service.CategoryRules.Group;
import com.fraudshield.siteintel.Finding;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 13.3 + 13.7: leaky / fraud app score from what the phone reports - permissions vs what the app is for, side-loading,
 * tracker SDKs, domains it talks to, fake copies of official apps. One explainable score with stable reason codes.
 * Genuine known-good and official apps are never flagged (13.1); the ad-abuse score (6.x) is separate.
 */
@Service
public class AppPrivacyService {
    public record Result(int score, Verdict verdict, List<String> codes, List<String> reasons, String impersonatedBrand,
                         Category category) {}

    /** App stores whose apps are reviewed (not side-loaded). */
    static final Set<String> STORES = Set.of("com.android.vending", "com.sec.android.app.samsungapps", "com.xiaomi.mipicks",
            "com.xiaomi.market", "com.heytap.market", "com.oppo.market", "com.bbk.appstore", "com.huawei.appmarket",
            "com.amazon.venezia",
            // found on a real POCO (device test): Xiaomi's own update channel and India's Indus Appstore (PhonePe)
            "com.xiaomi.discover", "com.indus.appstore", "com.vivo.appstore", "com.samsung.android.app.galaxystore");

    /**
     * Phone makers' own apps arrive preinstalled (no installer) or through the maker's channel - not "sideloaded".
     * Device test: MIUI Calculator / Gallery Editor / Wallpaper Carousel were flagged before this.
     * Weakness: a fake could copy such a package name; checking the maker's signing certificate is the follow-up.
     */
    static final List<String> MAKER_PREFIXES = List.of("com.miui.", "com.xiaomi.", "com.mi.", "com.samsung.", "com.sec.",
            "com.coloros.", "com.oplus.", "com.heytap.", "com.oppo.", "com.vivo.", "com.bbk.", "com.iqoo.", "com.oneplus.",
            "com.realme.", "com.huawei.", "com.hihonor.", "com.motorola.", "com.google.android.", "com.android.");

    static boolean makerApp(String pkg, String installer) {
        if (installer != null && !STORES.contains(installer)) return false;
        for (String p : MAKER_PREFIXES) if (pkg.startsWith(p)) return true;
        return false;
    }

    private static final Map<Group, Integer> WEIGHT = new EnumMap<>(Group.class);
    private static final Map<Group, String> NAME = new EnumMap<>(Group.class);
    static {
        w(Group.SMS, 25, "SMS"); w(Group.CALL_LOG, 20, "call history"); w(Group.CONTACTS, 15, "contacts");
        w(Group.LOCATION, 10, "location"); w(Group.BACKGROUND_LOCATION, 20, "location all the time"); w(Group.CAMERA, 10, "camera");
        w(Group.MICROPHONE, 15, "microphone"); w(Group.PHONE, 10, "phone calls / number"); w(Group.ACCOUNTS, 10, "accounts");
        w(Group.CALENDAR, 5, "calendar"); w(Group.STORAGE, 5, "files"); w(Group.INSTALL_APPS, 20, "install other apps");
        w(Group.OVERLAY, 15, "draw over other apps"); w(Group.ACCESSIBILITY, 20, "accessibility (read and control the screen)");
        w(Group.NOTIFICATIONS_READ, 15, "read your notifications"); w(Group.DEVICE_ADMIN, 20, "device admin");
        w(Group.QUERY_ALL_APPS, 5, "list of your apps"); w(Group.BODY_SENSORS, 10, "body sensors");
    }
    private static void w(Group g, int points, String name) { WEIGHT.put(g, points); NAME.put(g, name); }

    private final OfficialAppService official;
    private final DomainCategoryService domains;
    private final AppReputationRepository reputation; // null in plain unit tests

    @Autowired
    public AppPrivacyService(OfficialAppService official, DomainCategoryService domains, AppReputationRepository reputation) {
        this.official = official; this.domains = domains; this.reputation = reputation;
    }

    /** Plain unit tests: seed files only, no reputation table. */
    public AppPrivacyService() { this(new OfficialAppService(), new DomainCategoryService(), null); }

    public Result score(AppInfo a) {
        Category cat = reputation == null ? null : reputation.findByPackageName(a.packageName())
                .map(r -> r.getCategory() == null ? null : Category.valueOf(r.getCategory())).orElse(null);
        var off = official.byPackage(a.packageName());
        if (cat == null && off.isPresent()) cat = CategoryRules.infer(a.packageName(), off.get().brand() + " " + off.get().category(), a.appCategory());
        if (cat == null) cat = CategoryRules.infer(a.packageName(), a.label(), a.appCategory());
        if (a.isSystem() || trustedGenuine(a)) return new Result(0, Verdict.SAFE, List.of(), List.of(), null, cat);

        boolean sideloaded = (a.installer() == null || !STORES.contains(a.installer())) && !makerApp(a.packageName(), a.installer());
        List<Finding> f = new ArrayList<>();

        // fake copy: the name claims a brand, the package is not one of that brand's apps (13.12 / 13.13)
        String fakeOf = official.fakeOf(a.label(), a.packageName()).orElse(null);
        if (fakeOf != null)
            f.add(new Finding(sideloaded ? 80 : 45, "FAKE_APP", "Calls itself " + fakeOf + " but is not the official " + fakeOf + " app"));

        // permissions vs purpose (granted = full points, only requested = half)
        Set<Group> granted = groups(a.grantedPermissions());
        Set<Group> requested = groups(a.requestedPermissions());
        requested.addAll(granted);
        if (a.hasSmsPermission()) { requested.add(Group.SMS); granted.add(Group.SMS); }
        if (a.hasOverlayPermission()) { requested.add(Group.OVERLAY); granted.add(Group.OVERLAY); }
        if (a.hasAccessibility()) { requested.add(Group.ACCESSIBILITY); granted.add(Group.ACCESSIBILITY); }
        if (a.hasNotificationListener()) { requested.add(Group.NOTIFICATIONS_READ); granted.add(Group.NOTIFICATIONS_READ); }
        int perm = 0;
        List<String> extra = new ArrayList<>();
        for (Group g : requested) {
            if (CategoryRules.expected(cat, g)) continue;
            perm += granted.contains(g) ? WEIGHT.get(g) : WEIGHT.get(g) / 2;
            extra.add(NAME.get(g));
        }
        if (perm > 0)
            f.add(new Finding(Math.min(50, perm), "PERMISSION_NOT_NEEDED",
                    (cat == Category.UNKNOWN ? "Asks for" : "A " + label(cat) + " app asks for") + ": " + String.join(", ", extra)));

        if (sideloaded) f.add(new Finding(15, "SIDELOADED", "Installed from outside the app store"));
        if (sideloaded && requested.contains(Group.SMS) && (requested.contains(Group.OVERLAY) || requested.contains(Group.ACCESSIBILITY)))
            f.add(new Finding(60, "BANKING_TROJAN_PATTERN",
                    "Installed from outside the app store and can read your SMS and control the screen - how banking trojans steal OTPs"));
        if (cat == Category.LOAN && (requested.contains(Group.CONTACTS) || requested.contains(Group.SMS) || requested.contains(Group.CALL_LOG)))
            f.add(new Finding(40, "LOAN_APP_DATA_GRAB", "Loan app that wants your contacts / SMS / call history - used to harass borrowers"));
        if (a.targetSdk() != null && a.targetSdk() > 0 && a.targetSdk() < 23)
            f.add(new Finding(15, "OLD_TARGET_SDK", "Built for very old Android, so it gets permissions without asking"));

        List<String> trackers = a.trackers() == null ? List.of() : a.trackers().stream().filter(t -> t != null && !t.isBlank()).distinct().toList();
        if (!trackers.isEmpty())
            f.add(new Finding(Math.min(20, 4 * trackers.size()), "TRACKERS",
                    "Contains " + trackers.size() + " tracking SDK(s): " + String.join(", ", trackers.subList(0, Math.min(5, trackers.size())))));

        if (a.domainsContacted() != null && !a.domainsContacted().isEmpty()) {
            Set<String> ad = new TreeSet<>(), bad = new TreeSet<>();
            for (String d : a.domainsContacted()) {
                String c = domains.category(d);
                if ("malware".equals(c)) bad.add(d);
                else if (c != null) ad.add(d);
            }
            if (!bad.isEmpty()) f.add(new Finding(60, "MALWARE_DOMAIN", "Talks to known malware servers: " + String.join(", ", first(bad, 3))));
            if (ad.size() >= 10) f.add(new Finding(20, "MANY_AD_DOMAINS", "Talks to " + ad.size() + " advertising / tracking servers"));
            else if (ad.size() >= 5) f.add(new Finding(10, "MANY_AD_DOMAINS", "Talks to " + ad.size() + " advertising / tracking servers"));
        }

        int score = Math.min(100, f.stream().mapToInt(Finding::points).sum());
        return new Result(score, Verdict.fromScore(score), f.stream().map(Finding::code).toList(), f.stream().map(Finding::reason).toList(),
                fakeOf, cat);
    }

    /** Known-good or official app that is provably the real one (Play-installed, or certificate matches). */
    private boolean trustedGenuine(AppInfo a) {
        var off = official.byPackage(a.packageName());
        if (off.isPresent() && AppTrust.check(off.get().certSha256(), a.certSha256(), a.installer()) == AppTrust.Result.GENUINE) return true;
        if (reputation == null) return false;
        return reputation.findByPackageName(a.packageName())
                .filter(r -> "GOOD".equals(r.getVerdict()))
                .map(r -> AppTrust.check(r.getCertSha256(), a.certSha256(), a.installer()) == AppTrust.Result.GENUINE).orElse(false);
    }

    private static Set<Group> groups(List<String> perms) {
        Set<Group> s = EnumSet.noneOf(Group.class);
        if (perms != null) for (String p : perms) { Group g = CategoryRules.group(p); if (g != null) s.add(g); }
        return s;
    }

    private static List<String> first(Set<String> s, int n) { return s.stream().limit(n).toList(); }

    private static String label(Category c) { return c.name().toLowerCase(Locale.ROOT).replace('_', ' '); }
}
