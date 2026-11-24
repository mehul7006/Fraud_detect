package com.fraudshield.app

import android.content.Context
import android.content.pm.PackageManager
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * 13.4: which tracking / advertising SDKs an app contains. Reads the class names compiled into the app's DEX files
 * (the string table - no code is run, nothing leaves the phone) and matches package prefixes against [SIGNATURES].
 * Our own list of well-known SDK packages (not copied from AGPL lists). Results cached per app version.
 */
object TrackerScanner {
    data class Tracker(val name: String, val category: String)

    /** Package prefix (dots) -> SDK. Categories: ads, analytics, attribution, engagement, social, crash. */
    val SIGNATURES: Map<String, Tracker> = listOf(
        "com.facebook.ads" to Tracker("Facebook Audience Network", "ads"),
        "com.facebook.appevents" to Tracker("Facebook Analytics", "analytics"),
        "com.facebook.login" to Tracker("Facebook Login", "social"),
        "com.facebook.share" to Tracker("Facebook Share", "social"),
        "com.google.android.gms.ads" to Tracker("Google AdMob", "ads"),
        "com.google.ads" to Tracker("Google Ads", "ads"),
        "com.google.android.gms.measurement" to Tracker("Google Analytics for Firebase", "analytics"),
        "com.google.firebase.analytics" to Tracker("Google Analytics for Firebase", "analytics"),
        "com.google.android.gms.analytics" to Tracker("Google Analytics", "analytics"),
        "com.google.firebase.crashlytics" to Tracker("Firebase Crashlytics", "crash"),
        "com.crashlytics" to Tracker("Crashlytics", "crash"),
        "com.google.android.gms.tagmanager" to Tracker("Google Tag Manager", "analytics"),
        "com.appsflyer" to Tracker("AppsFlyer", "attribution"),
        "com.adjust.sdk" to Tracker("Adjust", "attribution"),
        "com.kochava" to Tracker("Kochava", "attribution"),
        "com.singular.sdk" to Tracker("Singular", "attribution"),
        "io.branch" to Tracker("Branch", "attribution"),
        "com.tenjin" to Tracker("Tenjin", "attribution"),
        "com.mixpanel" to Tracker("Mixpanel", "analytics"),
        "com.amplitude" to Tracker("Amplitude", "analytics"),
        "com.segment.analytics" to Tracker("Segment", "analytics"),
        "com.flurry" to Tracker("Flurry", "analytics"),
        "com.yandex.metrica" to Tracker("Yandex AppMetrica", "analytics"),
        "io.appmetrica" to Tracker("Yandex AppMetrica", "analytics"),
        "com.microsoft.appcenter.analytics" to Tracker("App Center Analytics", "analytics"),
        "com.newrelic" to Tracker("New Relic", "analytics"),
        "com.heapanalytics" to Tracker("Heap", "analytics"),
        "com.uxcam" to Tracker("UXCam", "analytics"),
        "com.smartlook" to Tracker("Smartlook", "analytics"),
        "com.instabug" to Tracker("Instabug", "crash"),
        "io.sentry" to Tracker("Sentry", "crash"),
        "com.bugsnag" to Tracker("Bugsnag", "crash"),
        "com.clevertap" to Tracker("CleverTap", "engagement"),
        "com.moengage" to Tracker("MoEngage", "engagement"),
        "com.webengage" to Tracker("WebEngage", "engagement"),
        "com.netcore" to Tracker("Netcore Smartech", "engagement"),
        "com.onesignal" to Tracker("OneSignal", "engagement"),
        "com.braze" to Tracker("Braze", "engagement"),
        "com.appboy" to Tracker("Braze (Appboy)", "engagement"),
        "com.leanplum" to Tracker("Leanplum", "engagement"),
        "com.batch.android" to Tracker("Batch", "engagement"),
        "com.urbanairship" to Tracker("Airship", "engagement"),
        "com.pushwoosh" to Tracker("Pushwoosh", "engagement"),
        "io.intercom" to Tracker("Intercom", "engagement"),
        "com.freshchat" to Tracker("Freshchat", "engagement"),
        "com.unity3d.ads" to Tracker("Unity Ads", "ads"),
        "com.unity3d.services" to Tracker("Unity Ads", "ads"),
        "com.applovin" to Tracker("AppLovin", "ads"),
        "com.ironsource" to Tracker("ironSource", "ads"),
        "com.vungle" to Tracker("Vungle (Liftoff)", "ads"),
        "com.chartboost" to Tracker("Chartboost", "ads"),
        "com.inmobi" to Tracker("InMobi", "ads"),
        "com.startapp" to Tracker("Start.io", "ads"),
        "com.mopub" to Tracker("MoPub", "ads"),
        "com.mbridge" to Tracker("Mintegral", "ads"),
        "com.mintegral" to Tracker("Mintegral", "ads"),
        "com.bytedance.sdk.openadsdk" to Tracker("Pangle (ByteDance)", "ads"),
        "com.tapjoy" to Tracker("Tapjoy", "ads"),
        "com.fyber" to Tracker("Fyber", "ads"),
        "com.adcolony" to Tracker("AdColony", "ads"),
        "com.my.target" to Tracker("myTarget", "ads"),
        "com.huawei.hms.ads" to Tracker("Huawei Ads", "ads"),
        "com.amazon.device.ads" to Tracker("Amazon Ads", "ads"),
        "com.smaato" to Tracker("Smaato", "ads"),
        "net.pubnative" to Tracker("PubNative", "ads"),
        "com.criteo" to Tracker("Criteo", "ads"),
        "com.taboola" to Tracker("Taboola", "ads"),
        "com.outbrain" to Tracker("Outbrain", "ads"),
        "com.google.android.gms.ads.identifier" to Tracker("Advertising ID access", "ads"),
        "com.miui.zeus" to Tracker("Xiaomi Ads (MIUI)", "ads"),
        "com.xiaomi.ad" to Tracker("Xiaomi Ads", "ads"),
        "com.tencent.bugly" to Tracker("Tencent Bugly", "crash"),
        "com.umeng" to Tracker("Umeng", "analytics"),
    ).toMap()

    private val bySlashPrefix: Map<String, Tracker> = SIGNATURES.mapKeys { "L" + it.key.replace('.', '/') + "/" }

    /** Distinct SDKs found in the given DEX files' class names. Pure - unit tested with built DEX tables. */
    fun match(dexFiles: Sequence<ByteArray>): Set<Tracker> {
        val found = mutableSetOf<Tracker>()
        for (dex in dexFiles) for (s in DexStrings.classDescriptors(dex)) {
            // try the descriptor's package prefixes: Lcom/appsflyer/, Lcom/facebook/ads/, ...
            var i = s.indexOf('/')
            var depth = 0
            while (i > 0 && depth < 6) {
                bySlashPrefix[s.substring(0, i + 1)]?.let { found += it }
                i = s.indexOf('/', i + 1); depth++
            }
        }
        // a specific match ("Facebook Login") already says Facebook: drop nothing, list them all, sorted
        return found
    }

    /** Scans an installed app (its APK and split APKs). Cached by package + last update time. */
    fun scan(ctx: Context, pkg: String): List<Tracker> {
        val pm = ctx.packageManager
        val info = try { pm.getPackageInfo(pkg, 0) } catch (e: PackageManager.NameNotFoundException) { return emptyList() }
        val key = "trk:$pkg:${info.lastUpdateTime}"
        val prefs = ctx.getSharedPreferences("fraudshield_trackers", Context.MODE_PRIVATE)
        prefs.getString(key, null)?.let { cached ->
            return if (cached.isEmpty()) emptyList() else cached.split("|").map { val (n, c) = it.split("~"); Tracker(n, c) }
        }
        val ai = info.applicationInfo ?: return emptyList()
        val apks = listOfNotNull(ai.sourceDir) + (ai.splitSourceDirs?.toList() ?: emptyList())
        val result = try {
            match(apks.asSequence().flatMap { path -> dexBytes(path) }).sortedBy { it.name }
        } catch (e: Exception) { emptyList() }
        prefs.edit().apply {
            prefs.all.keys.filter { it.startsWith("trk:$pkg:") }.forEach { remove(it) }
            putString(key, result.joinToString("|") { "${it.name}~${it.category}" })
        }.apply()
        return result
    }

    /** All DEX files of one APK file (used for installed apps, and by the PC tool on APKs copied from a phone). */
    fun dexBytes(apk: String): Sequence<ByteArray> = sequence {
        ZipFile(apk).use { z ->
            for (e in z.entries().asSequence().filter { it.name.matches(Regex("classes\\d*\\.dex")) }) {
                if (e.size > 64L * 1024 * 1024) continue
                yield(z.getInputStream(e).use(InputStream::readBytes))
            }
        }
    }
}

/** Minimal DEX string-table reader: just the class descriptors ("Lcom/x/Y;"), no code is loaded. */
object DexStrings {
    fun classDescriptors(dex: ByteArray): Sequence<String> = sequence {
        if (dex.size < 0x70 || dex[0] != 'd'.code.toByte() || dex[1] != 'e'.code.toByte() || dex[2] != 'x'.code.toByte()) return@sequence
        val count = u32(dex, 0x38)
        val idsOff = u32(dex, 0x3C)
        if (count <= 0 || idsOff <= 0 || idsOff.toLong() + count * 4L > dex.size) return@sequence
        for (k in 0 until count) {
            var p = u32(dex, idsOff + k * 4)
            if (p <= 0 || p >= dex.size) continue
            var len = 0; var shift = 0
            while (p < dex.size) { val b = dex[p++].toInt() and 0xFF; len = len or ((b and 0x7F) shl shift); if (b and 0x80 == 0) break; shift += 7 }
            if (p >= dex.size || dex[p] != 'L'.code.toByte() || len < 3) continue
            val end = minOf(dex.size, p + len * 3)
            var q = p
            while (q < end && dex[q] != 0.toByte()) q++
            yield(String(dex, p, q - p, Charsets.UTF_8))
        }
    }

    private fun u32(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) or
        ((b[i + 2].toInt() and 0xFF) shl 16) or ((b[i + 3].toInt() and 0xFF) shl 24)
}
