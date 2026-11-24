package com.fraudshield.app

import android.app.Activity
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/** 18.4 / 18.5: the app's calls for ad / tracker blocking (one branch in MainActivity routes here). */
object BlockChannel {
    val METHODS = setOf("blockSettings", "blockSetCategory", "blockPause", "blockAllowApp", "blockAllowSite",
        "blockAppBroke", "blockStats", "blockSync")

    fun handle(a: Activity, call: MethodCall, result: MethodChannel.Result) {
        AdBlocker.init(a, BuildConfig.FS_FLAVOUR)
        Thread {
            val r: Any? = try {
                when (call.method) {
                    "blockSettings" -> AdBlocker.settings(a)
                    "blockSetCategory" -> { AdBlocker.setCategory(a, call.argument<String>("category") ?: "", call.argument<Boolean>("on") == true); AdBlocker.settings(a) }
                    "blockPause" -> { AdBlocker.pause(a, call.argument<Int>("minutes") ?: 0); AdBlocker.settings(a) }
                    "blockAllowApp" -> { AdBlocker.allowApp(a, call.argument<String>("package") ?: "", call.argument<Boolean>("allow") != false); AdBlocker.settings(a) }
                    "blockAllowSite" -> { AdBlocker.allowSite(a, call.argument<String>("domain") ?: "", call.argument<Boolean>("allow") != false); AdBlocker.settings(a) }
                    "blockAppBroke" -> { AdBlocker.appBroke(a, call.argument<String>("package") ?: ""); AdBlocker.settings(a) }
                    "blockStats" -> BlockStats.get(a).summary(call.argument<Int>("days") ?: 7)
                    "blockSync" -> AdBlocker.sync(a)
                    else -> null
                }
            } catch (e: Exception) {
                android.util.Log.w("FraudShield", "${call.method} failed: ${e.javaClass.simpleName}"); null
            }
            a.runOnUiThread { if (r == null && call.method != "blockSync") result.error("BLOCK", "not available", null) else result.success(r ?: false) }
        }.start()
    }
}
