package com.fraudshield.service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 13.1 / 13.3: what an app is for (from its name, package and Android's own category) and which sensitive
 * permissions such an app normally needs. A torch app asking for SMS and contacts is the classic leaky / fraud app.
 */
public final class CategoryRules {
    public enum Category {
        FINANCE, LOAN, MESSAGING, SOCIAL, SHOPPING, FOOD, TRAVEL, NAVIGATION, BROWSER, CAMERA, PHOTO_VIDEO, MUSIC, VIDEO,
        GAME, KEYBOARD, LAUNCHER, TORCH, CALCULATOR, WALLPAPER, CLEANER, QR_SCANNER, VPN, FILE_MANAGER, HEALTH, EDUCATION,
        NEWS, PRODUCTIVITY, SECURITY, DIALER, STORE, UNKNOWN
    }

    /** Sensitive permission groups (Android permission names map onto these). */
    public enum Group { SMS, CALL_LOG, CONTACTS, LOCATION, BACKGROUND_LOCATION, CAMERA, MICROPHONE, PHONE, ACCOUNTS, CALENDAR,
        STORAGE, INSTALL_APPS, OVERLAY, ACCESSIBILITY, NOTIFICATIONS_READ, DEVICE_ADMIN, QUERY_ALL_APPS, BODY_SENSORS }

    private static final Map<String, Group> PERMISSION_GROUP = new HashMap<>();
    static {
        for (String p : List.of("READ_SMS", "RECEIVE_SMS", "SEND_SMS", "RECEIVE_MMS", "RECEIVE_WAP_PUSH")) PERMISSION_GROUP.put(p, Group.SMS);
        for (String p : List.of("READ_CALL_LOG", "WRITE_CALL_LOG", "PROCESS_OUTGOING_CALLS")) PERMISSION_GROUP.put(p, Group.CALL_LOG);
        for (String p : List.of("READ_CONTACTS", "WRITE_CONTACTS")) PERMISSION_GROUP.put(p, Group.CONTACTS);
        for (String p : List.of("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION")) PERMISSION_GROUP.put(p, Group.LOCATION);
        PERMISSION_GROUP.put("ACCESS_BACKGROUND_LOCATION", Group.BACKGROUND_LOCATION);
        PERMISSION_GROUP.put("CAMERA", Group.CAMERA);
        PERMISSION_GROUP.put("RECORD_AUDIO", Group.MICROPHONE);
        for (String p : List.of("READ_PHONE_STATE", "READ_PHONE_NUMBERS", "CALL_PHONE", "ANSWER_PHONE_CALLS")) PERMISSION_GROUP.put(p, Group.PHONE);
        PERMISSION_GROUP.put("GET_ACCOUNTS", Group.ACCOUNTS);
        for (String p : List.of("READ_CALENDAR", "WRITE_CALENDAR")) PERMISSION_GROUP.put(p, Group.CALENDAR);
        for (String p : List.of("READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES",
                "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO")) PERMISSION_GROUP.put(p, Group.STORAGE);
        PERMISSION_GROUP.put("REQUEST_INSTALL_PACKAGES", Group.INSTALL_APPS);
        PERMISSION_GROUP.put("SYSTEM_ALERT_WINDOW", Group.OVERLAY);
        PERMISSION_GROUP.put("BIND_ACCESSIBILITY_SERVICE", Group.ACCESSIBILITY);
        PERMISSION_GROUP.put("BIND_NOTIFICATION_LISTENER_SERVICE", Group.NOTIFICATIONS_READ);
        PERMISSION_GROUP.put("BIND_DEVICE_ADMIN", Group.DEVICE_ADMIN);
        PERMISSION_GROUP.put("QUERY_ALL_PACKAGES", Group.QUERY_ALL_APPS);
        for (String p : List.of("BODY_SENSORS", "BODY_SENSORS_BACKGROUND")) PERMISSION_GROUP.put(p, Group.BODY_SENSORS);
    }

    /** "android.permission.READ_SMS" or "READ_SMS" -> SMS; null for non-sensitive permissions. */
    public static Group group(String permission) {
        if (permission == null) return null;
        String p = permission.substring(permission.lastIndexOf('.') + 1).trim().toUpperCase(Locale.ROOT);
        return PERMISSION_GROUP.get(p);
    }

    /** Groups an app of this category may reasonably need. Anything else is "not needed for what this app does". */
    private static final Map<Category, Set<Group>> EXPECTED = new EnumMap<>(Category.class);
    static {
        Set<Group> base = EnumSet.of(Group.STORAGE);
        EXPECTED.put(Category.FINANCE, EnumSet.of(Group.SMS, Group.PHONE, Group.LOCATION, Group.CAMERA, Group.CONTACTS, Group.STORAGE, Group.ACCOUNTS));
        EXPECTED.put(Category.LOAN, EnumSet.of(Group.CAMERA, Group.LOCATION, Group.STORAGE, Group.PHONE));
        EXPECTED.put(Category.MESSAGING, EnumSet.of(Group.SMS, Group.CONTACTS, Group.CAMERA, Group.MICROPHONE, Group.LOCATION, Group.STORAGE,
                Group.PHONE, Group.CALL_LOG, Group.ACCOUNTS, Group.OVERLAY, Group.CALENDAR));
        EXPECTED.put(Category.SOCIAL, EnumSet.of(Group.CONTACTS, Group.CAMERA, Group.MICROPHONE, Group.LOCATION, Group.STORAGE, Group.ACCOUNTS, Group.PHONE));
        EXPECTED.put(Category.SHOPPING, EnumSet.of(Group.LOCATION, Group.CAMERA, Group.STORAGE, Group.SMS, Group.PHONE, Group.MICROPHONE));
        EXPECTED.put(Category.FOOD, EnumSet.of(Group.LOCATION, Group.STORAGE, Group.PHONE, Group.SMS));
        EXPECTED.put(Category.TRAVEL, EnumSet.of(Group.LOCATION, Group.BACKGROUND_LOCATION, Group.STORAGE, Group.PHONE, Group.SMS, Group.CAMERA, Group.CALENDAR, Group.CONTACTS));
        EXPECTED.put(Category.NAVIGATION, EnumSet.of(Group.LOCATION, Group.BACKGROUND_LOCATION, Group.MICROPHONE, Group.STORAGE, Group.OVERLAY));
        EXPECTED.put(Category.BROWSER, EnumSet.of(Group.LOCATION, Group.CAMERA, Group.MICROPHONE, Group.STORAGE));
        EXPECTED.put(Category.CAMERA, EnumSet.of(Group.CAMERA, Group.MICROPHONE, Group.LOCATION, Group.STORAGE));
        EXPECTED.put(Category.PHOTO_VIDEO, EnumSet.of(Group.CAMERA, Group.MICROPHONE, Group.STORAGE));
        EXPECTED.put(Category.MUSIC, EnumSet.of(Group.STORAGE, Group.MICROPHONE, Group.PHONE));
        EXPECTED.put(Category.VIDEO, EnumSet.of(Group.STORAGE, Group.MICROPHONE, Group.PHONE, Group.OVERLAY));
        EXPECTED.put(Category.GAME, EnumSet.of(Group.STORAGE, Group.MICROPHONE, Group.ACCOUNTS));
        EXPECTED.put(Category.KEYBOARD, EnumSet.of(Group.MICROPHONE, Group.CONTACTS, Group.STORAGE));
        EXPECTED.put(Category.LAUNCHER, EnumSet.of(Group.STORAGE, Group.QUERY_ALL_APPS, Group.CONTACTS, Group.CALENDAR, Group.LOCATION, Group.NOTIFICATIONS_READ));
        EXPECTED.put(Category.TORCH, EnumSet.of(Group.CAMERA));
        EXPECTED.put(Category.CALCULATOR, EnumSet.noneOf(Group.class));
        EXPECTED.put(Category.WALLPAPER, EnumSet.copyOf(base));
        EXPECTED.put(Category.CLEANER, EnumSet.of(Group.STORAGE, Group.QUERY_ALL_APPS));
        EXPECTED.put(Category.QR_SCANNER, EnumSet.of(Group.CAMERA, Group.STORAGE));
        EXPECTED.put(Category.VPN, EnumSet.of(Group.QUERY_ALL_APPS));
        EXPECTED.put(Category.FILE_MANAGER, EnumSet.of(Group.STORAGE, Group.INSTALL_APPS, Group.QUERY_ALL_APPS));
        EXPECTED.put(Category.HEALTH, EnumSet.of(Group.BODY_SENSORS, Group.LOCATION, Group.CAMERA, Group.STORAGE, Group.PHONE));
        EXPECTED.put(Category.EDUCATION, EnumSet.of(Group.CAMERA, Group.MICROPHONE, Group.STORAGE, Group.PHONE));
        EXPECTED.put(Category.NEWS, EnumSet.of(Group.LOCATION, Group.STORAGE));
        EXPECTED.put(Category.PRODUCTIVITY, EnumSet.of(Group.STORAGE, Group.CALENDAR, Group.CONTACTS, Group.CAMERA, Group.MICROPHONE, Group.ACCOUNTS));
        EXPECTED.put(Category.SECURITY, EnumSet.allOf(Group.class));
        EXPECTED.put(Category.STORE, EnumSet.allOf(Group.class)); // app stores install / manage other apps
        EXPECTED.put(Category.DIALER, EnumSet.of(Group.PHONE, Group.CALL_LOG, Group.CONTACTS, Group.SMS, Group.MICROPHONE, Group.OVERLAY));
    }

    public static boolean expected(Category c, Group g) {
        Set<Group> s = EXPECTED.get(c);
        return s == null || s.contains(g); // UNKNOWN: nothing is "unexpected" (no guessing)
    }

    /** Keyword rules on the app name + package, then Android's own category. First match wins (order matters). */
    private static final List<Map.Entry<Pattern, Category>> RULES = List.of(
            // app stores first: "PhonePe Appstore" is a store, not a finance app (device test)
            rule("app ?store|\\bstore\\b.*apps|getapps|\\bapp ?market\\b|play store|galaxy store", Category.STORE),
            rule("flash ?light|torch|\\bled light", Category.TORCH),
            rule("calculator|\\bcalc\\b", Category.CALCULATOR),
            rule("wallpaper|\\btheme|ringtone|live wall", Category.WALLPAPER),
            rule("cleaner|booster|junk|ram clean|battery (saver|doctor)|speed ?up", Category.CLEANER),
            rule("qr|barcode|scanner(?!.*(document|pdf))", Category.QR_SCANNER),
            rule("\\bvpn\\b|proxy", Category.VPN),
            rule("instant loan|personal loan|loan app|\\bloans?\\b|credit line|cash ?advance|kreditbee|mpokket|moneyview", Category.LOAN),
            rule("bank|banking|\\bupi\\b|\\bpay\\b|payments?|wallet|paytm|phonepe|\\bbhim\\b|cred\\b|mobikwik|freecharge|stocks?|trading|invest|mutual fund|demat|crypto|bitcoin|zerodha|groww|insurance", Category.FINANCE),
            rule("whatsapp|telegram|messenger|\\bsms\\b|messages|chat\\b|signal|viber|\\bline\\b|imo", Category.MESSAGING),
            rule("dialer|caller ?id|truecaller|phone by|contacts", Category.DIALER),
            rule("keyboard|gboard|swiftkey", Category.KEYBOARD),
            rule("launcher|home screen", Category.LAUNCHER),
            rule("browser|chrome|firefox|opera|brave|duckduckgo", Category.BROWSER),
            rule("antivirus|mobile security|password manager|authenticator|bitwarden|lastpass", Category.SECURITY),
            rule("file manager|files by|file transfer|my files|explorer", Category.FILE_MANAGER),
            rule("maps|navigation|gps|waze", Category.NAVIGATION),
            rule("camera|selfie", Category.CAMERA),
            rule("photo|video editor|collage|editor|gallery|lightroom|snapseed|picsart|canva", Category.PHOTO_VIDEO),
            rule("music|songs|podcast|radio|mp3|spotify|gaana|wynk|jiosaavn", Category.MUSIC),
            rule("video|movies|\\btv\\b|ott|netflix|hotstar|youtube|player|stream", Category.VIDEO),
            rule("shop|shopping|mart|store|fashion|grocer|meesho|flipkart|amazon|myntra|ajio|olx", Category.SHOPPING),
            rule("food|swiggy|zomato|restaurant|pizza|delivery", Category.FOOD),
            rule("flight|hotel|train|bus ticket|cab|taxi|ride|travel|irctc|redbus|uber|ola\\b|rapido|trip", Category.TRAVEL),
            rule("health|fitness|doctor|medicine|period|pregnan|diet|yoga|meditat", Category.HEALTH),
            rule("learn|exam|course|study|class|school|tutor|byju|unacademy|duolingo", Category.EDUCATION),
            rule("news|epaper|headlines", Category.NEWS),
            rule("social|facebook|instagram|snapchat|twitter|sharechat|\\bmoj\\b|reddit|linkedin|dating|matrimony", Category.SOCIAL),
            rule("game|puzzle|cricket|ludo|candy|racing|clash|battle|craft|solitaire|chess|rummy|casino", Category.GAME),
            rule("notes|docs|office|pdf|calendar|mail|email|todo|task|drive|scanner", Category.PRODUCTIVITY));

    private static Map.Entry<Pattern, Category> rule(String regex, Category c) {
        return Map.entry(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), c);
    }

    public static Category infer(String packageName, String label, String androidCategory) {
        String text = ((label == null ? "" : label) + " " + (packageName == null ? "" : packageName.replace('.', ' ').replace('_', ' ')));
        for (var r : RULES) if (r.getKey().matcher(text).find()) return r.getValue();
        if (androidCategory != null) {
            switch (androidCategory.toUpperCase(Locale.ROOT)) {
                case "GAME", "CATEGORY_GAME": return Category.GAME;
                case "AUDIO", "CATEGORY_AUDIO": return Category.MUSIC;
                case "VIDEO", "CATEGORY_VIDEO": return Category.VIDEO;
                case "IMAGE", "CATEGORY_IMAGE": return Category.PHOTO_VIDEO;
                case "SOCIAL", "CATEGORY_SOCIAL": return Category.SOCIAL;
                case "NEWS", "CATEGORY_NEWS": return Category.NEWS;
                case "MAPS", "CATEGORY_MAPS": return Category.NAVIGATION;
                case "PRODUCTIVITY", "CATEGORY_PRODUCTIVITY": return Category.PRODUCTIVITY;
                default: break;
            }
        }
        return Category.UNKNOWN;
    }

    private CategoryRules() {}
}
