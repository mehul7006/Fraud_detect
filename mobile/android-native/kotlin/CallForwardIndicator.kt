package com.fraudshield.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager

/**
 * 3.3: Android's own "unconditional call forwarding is on" flag (the forwarding icon in the status bar).
 *
 * Needed because the *#21# reply text is not always enough: when the network answers with service class 0,
 * Android's reply is only "Call forwarding" for BOTH on and off (seen on the emulator's modem, see
 * docs/evidence/3.3-3.4-results.md). The flag is public API (READ_PHONE_STATE) and is updated by the network and
 * by every **21* / ##21# / ##002#. Listeners get the current value right after registering.
 * Answers true / false, or null when unknown (no permission, no answer within 2 s).
 */
object CallForwardIndicator {
    fun read(ctx: Context, cb: (Boolean?) -> Unit) {
        if (ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            cb(null); return
        }
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val main = Handler(Looper.getMainLooper())
        var done = false
        var stop: () -> Unit = {}
        val answer: (Boolean?) -> Unit = { v -> if (!done) { done = true; stop(); cb(v) } }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val listener = object : TelephonyCallback(), TelephonyCallback.CallForwardingIndicatorListener {
                    override fun onCallForwardingIndicatorChanged(cfi: Boolean) { main.post { answer(cfi) } }
                }
                stop = { tm.unregisterTelephonyCallback(listener) }
                tm.registerTelephonyCallback(ctx.mainExecutor, listener)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() { // called on the main thread (method channel) -> main looper
                    @Deprecated("old API, used below Android 12")
                    override fun onCallForwardingIndicatorChanged(cfi: Boolean) = answer(cfi)
                }
                @Suppress("DEPRECATION")
                stop = { tm.listen(listener, PhoneStateListener.LISTEN_NONE) }
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR)
            }
        } catch (e: Exception) {
            answer(null); return
        }
        main.postDelayed({ answer(null) }, 2000)
    }
}
