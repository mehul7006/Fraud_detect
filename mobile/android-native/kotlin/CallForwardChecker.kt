package com.fraudshield.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager

object CallForwardChecker {
    /**
     * Sends a USSD/MMI code to the carrier and returns the carrier response text.
     * Returns "NO_PERMISSION", "UNSUPPORTED" or "ERROR:<code>" when it can't. Flutter then falls back to dialing.
     */
    fun run(ctx: Context, code: String, cb: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { cb("UNSUPPORTED"); return }
        if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            cb("NO_PERMISSION"); return
        }
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        var replied = false
        val reply: (String) -> Unit = { if (!replied) { replied = true; cb(it) } }
        tm.sendUssdRequest(code, object : TelephonyManager.UssdResponseCallback() {
            override fun onReceiveUssdResponse(t: TelephonyManager, req: String, response: CharSequence) =
                reply(response.toString())
            override fun onReceiveUssdResponseFailed(t: TelephonyManager, req: String, failureCode: Int) =
                reply("ERROR:$failureCode")
        }, Handler(Looper.getMainLooper()))
    }
}
