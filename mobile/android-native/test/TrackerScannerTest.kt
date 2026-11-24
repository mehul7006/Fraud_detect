package com.fraudshield.app

import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

/** 13.4: SDK detection from a DEX string table (built in the test, like a tiny real classes.dex). */
class TrackerScannerTest {
    /** A DEX file with only a header and a string table holding [strings]. */
    private fun dex(vararg strings: String): ByteArray {
        val header = 0x70
        val ids = header
        val dataStart = ids + strings.size * 4
        val data = ByteArrayOutputStream()
        val offsets = IntArray(strings.size)
        strings.forEachIndexed { i, s ->
            offsets[i] = dataStart + data.size()
            var len = s.length
            do { var b = len and 0x7F; len = len ushr 7; if (len != 0) b = b or 0x80; data.write(b) } while (len != 0)
            data.write(s.toByteArray()); data.write(0)
        }
        val out = ByteArray(dataStart + data.size())
        "dex\n035".toByteArray().copyInto(out)
        fun put(at: Int, v: Int) { out[at] = v.toByte(); out[at + 1] = (v shr 8).toByte(); out[at + 2] = (v shr 16).toByte(); out[at + 3] = (v shr 24).toByte() }
        put(0x38, strings.size); put(0x3C, ids)
        offsets.forEachIndexed { i, o -> put(ids + i * 4, o) }
        data.toByteArray().copyInto(out, dataStart)
        return out
    }

    @Test fun reads_class_descriptors_only() {
        val d = dex("Lcom/appsflyer/AppsFlyerLib;", "hello world", "Ljava/lang/String;", "")
        assertEquals(listOf("Lcom/appsflyer/AppsFlyerLib;", "Ljava/lang/String;"), DexStrings.classDescriptors(d).toList())
        assertTrue(DexStrings.classDescriptors(ByteArray(10)).toList().isEmpty())
        assertTrue("not a dex", DexStrings.classDescriptors(ByteArray(200)).toList().isEmpty())
    }

    @Test fun finds_facebook_and_appsflyer_by_name() {
        val app = dex("Lcom/facebook/ads/AdView;", "Lcom/facebook/appevents/AppEventsLogger;", "Lcom/appsflyer/AppsFlyerLib;",
            "Lcom/myshop/MainActivity;", "Landroidx/core/app/NotificationCompat;")
        val names = TrackerScanner.match(sequenceOf(app)).map { it.name }.toSet()
        assertEquals(setOf("Facebook Audience Network", "Facebook Analytics", "AppsFlyer"), names)
    }

    @Test fun clean_app_has_none_and_lookalike_packages_do_not_match() {
        val app = dex("Lcom/myshop/MainActivity;", "Lcom/appsflyerclone/X;", "Lcom/facebookish/ads/Y;", "Landroidx/core/A;")
        assertTrue(TrackerScanner.match(sequenceOf(app)).isEmpty())
    }

    @Test fun several_dex_files_are_combined() {
        val r = TrackerScanner.match(sequenceOf(dex("Lcom/unity3d/ads/UnityAds;"), dex("Lio/branch/referral/Branch;")))
        assertEquals(setOf("Unity Ads", "Branch"), r.map { it.name }.toSet())
        assertTrue(TrackerScanner.SIGNATURES.size >= 70)
    }
}
