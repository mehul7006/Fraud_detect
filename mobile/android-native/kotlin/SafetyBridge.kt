package com.fraudshield.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.speech.tts.TextToSpeech
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.Locale

/**
 * Phase 15 MethodChannel methods (MainActivity forwards unknown methods here).
 *   scamGuardStatus -> {enabled, otpGuard, running, phoneState, callLog, contacts, usageAccess, notificationAccess, remoteAccessApps}
 *   scamGuardSet{enabled}, scamGuardOtp{enabled}, scamGuardPermissions (asks READ_PHONE_STATE + READ_CALL_LOG + READ_CONTACTS)
 *   scamGuardSimulate (debug builds only: four test signals -> STOP path)
 *   speak{text, lang 'en'|'hi'} -> bool (false = no voice for that language), stopSpeaking
 *   dialNumber{number} -> opens the dialler filled in (user presses Call); shareText{text, subject} -> share sheet
 */
object SafetyBridge {
    private const val PERMS = 4151
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending: (() -> Unit)? = null

    fun handle(a: Activity, call: MethodCall, result: MethodChannel.Result): Boolean {
        when (call.method) {
            "scamGuardStatus" -> result.success(ScamSignalCollector.status(a))
            "scamGuardSet" -> { ScamSignalCollector.setEnabled(a, call.argument<Boolean>("enabled") == true); result.success(true) }
            "scamGuardOtp" -> { ScamSignalCollector.setOtpGuard(a, call.argument<Boolean>("enabled") == true); result.success(true) }
            "scamGuardPermissions" -> {
                a.requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.READ_CONTACTS), PERMS)
                result.success(true)
            }
            "scamGuardSimulate" -> {
                val debug = (a.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                if (debug) ScamSignalCollector.simulate(a)
                result.success(debug)
            }
            "speak" -> speak(a, call.argument<String>("text") ?: "", call.argument<String>("lang") ?: "en") { result.success(it) }
            "stopSpeaking" -> { tts?.stop(); result.success(true) }
            // 15.6: opens the dialler with the number filled in - the user presses Call (no CALL_PHONE use)
            "dialNumber" -> {
                val n = call.argument<String>("number") ?: ""
                if (!Regex("^\\+?[0-9 -]{3,20}$").matches(n)) result.success(false)
                else { a.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + android.net.Uri.encode(n)))); result.success(true) }
            }
            // 15.6: evidence pack leaves the phone only through the share sheet the user picks
            "shareText" -> {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, call.argument<String>("text") ?: "")
                    .putExtra(android.content.Intent.EXTRA_SUBJECT, call.argument<String>("subject") ?: "FraudShield")
                a.startActivity(android.content.Intent.createChooser(send, null))
                result.success(true)
            }
            else -> return false
        }
        return true
    }

    /** 15.3: Android's own text-to-speech (no extra library). Hindi needs a Hindi voice on the phone. */
    private fun speak(ctx: Context, text: String, lang: String, done: (Boolean) -> Unit) {
        val go = {
            val t = tts!!
            val loc = if (lang == "hi") Locale("hi", "IN") else Locale("en", "IN")
            val ok = t.setLanguage(loc) >= TextToSpeech.LANG_AVAILABLE
            if (ok) { t.setSpeechRate(0.9f); t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fs-stop") }
            done(ok)
        }
        if (tts != null && ttsReady) { go(); return }
        pending = go
        if (tts == null) tts = TextToSpeech(ctx.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            val p = pending; pending = null
            if (ttsReady) p?.invoke() else done(false)
        }
    }
}
