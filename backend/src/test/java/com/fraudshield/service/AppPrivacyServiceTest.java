package com.fraudshield.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fraudshield.domain.Enums.Verdict;
import com.fraudshield.dto.Dtos.AppInfo;
import com.fraudshield.explain.ExplainService;
import com.fraudshield.service.CategoryRules.Category;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 13.1 / 13.3 / 13.6 / 13.7 / 13.13 without a database (seed files only). */
class AppPrivacyServiceTest {
    static final AppPrivacyService P = new AppPrivacyService();
    static final String PLAY = "com.android.vending";

    static AppInfo app(String pkg, String label, String installer, Integer targetSdk, List<String> perms, List<String> trackers,
                       List<String> domains) {
        return new AppInfo(pkg, label, 0, false, false, false, false, false, false, 0, null, installer,
                perms, perms, targetSdk, trackers, domains, null);
    }

    static List<String> perms(String csv) { return csv.isBlank() ? List.of() : List.of(csv.split(";")); }

    /** 13.3: 20 sample apps scored as expected (verdict + the main reason code). */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = '|', value = {
        // package | label | installer | perms | expected verdict | expected code (or -)
        "com.example.torch | Super Torch LED | PLAY | CAMERA | SAFE | -",
        "com.example.torch2 | Bright Flashlight | PLAY | CAMERA;READ_CONTACTS;READ_SMS;ACCESS_FINE_LOCATION | SUSPICIOUS | PERMISSION_NOT_NEEDED",
        "com.example.calc | Calculator Plus | PLAY | - | SAFE | -",
        "com.example.calc2 | Smart Calculator | PLAY | READ_CONTACTS;READ_CALL_LOG;RECORD_AUDIO | SUSPICIOUS | PERMISSION_NOT_NEEDED",
        "com.example.wall | HD Wallpapers 4K | PLAY | READ_MEDIA_IMAGES | SAFE | -",
        "com.example.wall2 | Live Wallpaper Themes | NONE | READ_SMS;SYSTEM_ALERT_WINDOW;BIND_ACCESSIBILITY_SERVICE | MALICIOUS | BANKING_TROJAN_PATTERN",
        "com.example.qr | QR & Barcode Scanner | PLAY | CAMERA | SAFE | -",
        "com.example.qr2 | QR Scanner Pro | PLAY | CAMERA;ACCESS_BACKGROUND_LOCATION;READ_CONTACTS | SUSPICIOUS | PERMISSION_NOT_NEEDED",
        "com.example.clean | Phone Cleaner Booster | PLAY | QUERY_ALL_PACKAGES;READ_EXTERNAL_STORAGE | SAFE | -",
        "com.quickcash.loan | Quick Cash Instant Loan | PLAY | READ_CONTACTS;READ_SMS;READ_CALL_LOG;CAMERA | MALICIOUS | LOAN_APP_DATA_GRAB",
        "com.goodbank.app | Good Bank Mobile Banking | PLAY | READ_SMS;CAMERA;ACCESS_FINE_LOCATION | SAFE | -",
        "com.example.chat | Hello Chat Messenger | PLAY | READ_CONTACTS;CAMERA;RECORD_AUDIO;READ_SMS | SAFE | -",
        "com.example.game | Racing Cars 3D Game | PLAY | - | SAFE | -",
        "com.example.game2 | Ludo Game Star | PLAY | READ_SMS;READ_CONTACTS;READ_CALL_LOG | SUSPICIOUS | PERMISSION_NOT_NEEDED",
        "com.example.kb | Fancy Keyboard Fonts | PLAY | RECORD_AUDIO | SAFE | -",
        "com.example.apk | Free Recharge 2026 | NONE | READ_SMS;RECEIVE_SMS;BIND_ACCESSIBILITY_SERVICE;REQUEST_INSTALL_PACKAGES | MALICIOUS | BANKING_TROJAN_PATTERN",
        "com.phonepe.app.secure | PhonePe | NONE | READ_SMS | MALICIOUS | FAKE_APP",
        "com.example.sbi.yono | YONO SBI | NONE | - | MALICIOUS | FAKE_APP",
        "com.status.saver | Status Saver for WhatsApp | PLAY | READ_EXTERNAL_STORAGE | SAFE | -",
        "com.example.oldapp | Torch Old | PLAY | CAMERA | SUSPICIOUS | OLD_TARGET_SDK",
    })
    void twenty_sample_apps(String pkg, String label, String inst, String ps, String verdict, String code) {
        Integer sdk = label.contains("Old") ? 19 : 34;
        var r = P.score(app(pkg, label, inst.equals("PLAY") ? PLAY : null, sdk, perms(ps.equals("-") ? "" : ps), null, null));
        if (label.equals("Torch Old")) {
            // old target SDK alone (+15) is not enough for a warning; with a sideload it is
            assertEquals(Verdict.SAFE, r.verdict());
            r = P.score(app(pkg, label, null, sdk, List.of("CAMERA", "READ_CONTACTS"), null, null));
        }
        assertEquals(Verdict.valueOf(verdict), r.verdict(), pkg + " " + r);
        if (!code.equals("-")) assertTrue(r.codes().contains(code), pkg + " " + r.codes());
    }

    @Test
    void categories_from_name_package_and_android() {
        assertEquals(Category.TORCH, CategoryRules.infer("com.x.flashlight", "Flashlight", null));
        assertEquals(Category.LOAN, CategoryRules.infer("com.x", "Instant Loan in 5 min", null));
        assertEquals(Category.FINANCE, CategoryRules.infer("com.sbi.lotusintouch", "YONO SBI: Banking - UPI & Pay", null));
        assertEquals(Category.GAME, CategoryRules.infer("com.x.y", "Zorg", "GAME"));
        assertEquals(Category.UNKNOWN, CategoryRules.infer("com.x.y", "Zorg", null));
        assertEquals(CategoryRules.Group.SMS, CategoryRules.group("android.permission.READ_SMS"));
        assertNull(CategoryRules.group("android.permission.INTERNET"));
    }

    /** 13.13: ≥100 official apps; lookup by package; fake names caught; third-party helpers on Play are not fakes. */
    @Test
    void official_registry_and_fake_names() {
        var o = new OfficialAppService();
        assertTrue(o.size() >= 100, "official apps: " + o.size());
        assertEquals("PhonePe", o.byPackage("com.phonepe.app").orElseThrow().brand());
        assertNull(o.byPackage("com.phonepe.app").orElseThrow().certSha256(), "certificates not verified yet");
        assertTrue(o.byPackage("com.phonepe.app.fake").isEmpty());
        assertEquals("PhonePe", o.fakeOf("PhonePe", "com.phonepe.app.fake").orElseThrow());
        assertEquals("PhonePe", o.fakeOf("PhonePe UPI Payments", "com.pay.phonepay").orElseThrow());
        assertEquals("State Bank of India", o.fakeOf("YONO SBI", "com.yono.sbi.kyc").orElseThrow());
        assertTrue(o.fakeOf("PhonePe", "com.phonepe.app").isEmpty(), "the real one");
        assertTrue(o.fakeOf("Status Saver for WhatsApp", "com.status.saver").isEmpty());
        assertTrue(o.fakeOf("WhatsApp Web Scanner", "com.wa.web").isEmpty());
        assertTrue(o.fakeOf("Calculator", "com.calc").isEmpty());
        assertTrue(o.fakeOf("x", "com.some.app").isEmpty(), "found by Phase6IT: 'X (Twitter)' must not claim every app named x");
        assertTrue(o.fakeOf("Vi", "com.some.app").isEmpty());
    }

    /** 13.6: categories from the seed (AdAway ads, own analytics/tracker list, URLhaus malware); parents match. */
    @Test
    void domain_categories() {
        var d = new DomainCategoryService();
        assertTrue(d.size() > 5000, "domains: " + d.size());
        assertEquals("ads", d.category("doubleclick.net"));
        assertEquals("ads", d.category("ad.doubleclick.net"));
        assertEquals("analytics", d.category("app-measurement.com"));
        assertEquals("tracker", d.category("t.appsflyer.com"));
        assertNull(d.category("www.flipkart.com"));
        assertNull(d.category("com"));
        assertEquals("fraudshield-domains-v1\n7\ntrue\na.com:ads,b.com:tracker\nc.com",
                DomainCategoryService.canonical(7, true, List.of(new DomainCategoryService.Entry("a.com", "ads"),
                        new DomainCategoryService.Entry("b.com", "tracker")), List.of("c.com")));
    }

    /** 13.4 / 13.5 data from the phone: trackers and talkative ad apps. */
    @Test
    void trackers_and_domains() {
        var ads10 = List.of("doubleclick.net", "googlesyndication.com", "applovin.com", "unityads.unity3d.com", "inmobi.com",
                "vungle.com", "chartboost.com", "adcolony.com", "mintegral.com", "startapp.com", "www.example.org");
        var r = P.score(app("com.x.notes", "Simple Notes", PLAY, 34, List.of(), List.of("AppsFlyer", "Facebook Ads", "AdMob", "Adjust"), ads10));
        assertTrue(r.codes().containsAll(List.of("TRACKERS", "MANY_AD_DOMAINS")), r.toString());
        assertEquals(Verdict.SUSPICIOUS, r.verdict());
        var normal = P.score(app("com.x.notes2", "Simple Notes", PLAY, 34, List.of(), List.of(), List.of("www.example.org", "api.notes.example")));
        assertEquals(Verdict.SAFE, normal.verdict(), "normal app is not flagged");
        var bad = P.score(app("com.x.y", "Zorg", PLAY, 34, List.of(), List.of(), List.of("beneon.hypstok.info")));
        assertTrue(bad.codes().contains("MALWARE_DOMAIN"), bad.toString());
    }

    /** 13.1: official / known-good apps proven genuine are never flagged, even with many permissions and trackers. */
    @Test
    void genuine_official_apps_are_never_flagged() {
        var r = P.score(app("com.whatsapp", "WhatsApp", PLAY, 34,
                List.of("READ_CONTACTS", "READ_SMS", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "READ_CALL_LOG"), List.of("Facebook Analytics"), null));
        assertEquals(Verdict.SAFE, r.verdict());
        assertEquals(0, r.score());
        var side = P.score(app("com.whatsapp", "WhatsApp", null, 34, List.of("READ_SMS", "BIND_ACCESSIBILITY_SERVICE", "SYSTEM_ALERT_WINDOW"), null, null));
        assertNotEquals(Verdict.SAFE, side.verdict(), "a side-loaded 'com.whatsapp' is not proven genuine: scored");
    }

    /** 13.7: combined answer with codes + en/hi explanation; old clients get the 6.x answer unchanged. */
    @Test
    void combined_result_and_old_clients() {
        var scan = new AppScanService(new AdAbuseService(), P, new ExplainService(Set.of("PhonePe"), null));
        var fake = scan.scan(app("com.phonepe.app.secure", "PhonePe", null, 34, List.of("READ_SMS"), null, null));
        assertEquals(Verdict.MALICIOUS, fake.verdict());
        assertEquals("PhonePe", fake.impersonatedBrand());
        assertTrue(fake.reasonCodes().contains("FAKE_APP"));
        assertTrue(fake.explanation().get("en").startsWith("This app pretends to be PhonePe"), fake.explanation().toString());
        assertTrue(fake.explanation().get("hi").contains("PhonePe"));
        var old = new AppInfo("com.spam.app", "Spam", 30, true, false, false, false, true, false);
        var r = scan.scan(old);
        assertNull(r.privacyScore(), "older client: no privacy fields");
        assertEquals(new AdAbuseService().score(old).verdict(), r.verdict());
    }

    /** Found on a real POCO M6 Pro (Android 15): phone-maker apps and app stores must not be flagged. */
    @org.junit.jupiter.api.Test
    void phone_maker_apps_and_app_stores_are_not_sideloaded_or_leaky() {
        org.junit.jupiter.api.Assertions.assertTrue(AppPrivacyService.makerApp("com.miui.calculator", null));
        org.junit.jupiter.api.Assertions.assertTrue(AppPrivacyService.makerApp("com.miui.mediaeditor", "com.xiaomi.discover"));
        org.junit.jupiter.api.Assertions.assertFalse(AppPrivacyService.makerApp("com.miui.calculator", "com.miui.packageinstaller"),
                "installed by hand from an APK file is still sideloaded");
        org.junit.jupiter.api.Assertions.assertFalse(AppPrivacyService.makerApp("com.torch.pro", null));
        org.junit.jupiter.api.Assertions.assertEquals(CategoryRules.Category.STORE, CategoryRules.infer("com.indus.appstore", "PhonePe Appstore", null));
        org.junit.jupiter.api.Assertions.assertEquals(CategoryRules.Category.FINANCE, CategoryRules.infer("com.phonepe.app", "PhonePe", null));
    }
}
