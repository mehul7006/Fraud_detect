package com.fraudshield.app

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * Phase 16 MethodChannel methods (MainActivity forwards unknown methods here).
 *   callGuardStatus -> {supported, sdk, roleHeld, mode, allow, block, scamListSize, scamListVersion, rulesVersion,
 *                       contactsPermission, callLogPermission, counts}
 *   callGuardRequestRole -> bool (system "Caller ID & spam app" dialog; Android 10+)
 *   callGuardSetMode{mode 1-4}, callGuardAllow{number, allow}, callGuardBlock{number, block} -> canonical or null
 *   callGuardNotSpam{number} -> canonical (allow list + log rows marked undone)
 *   callGuardLog{days} -> [{id, at, number, action, kind, label, rule, category, undone}]
 *   callGuardSync -> {rules, numbers}; callGuardRequestContacts -> asks READ_CONTACTS + READ_CALL_LOG
 *   callGuardCheck{number} -> {canonical, protected, rule, label} (report screen: 1600 / 140 cannot be reported)
 *   callGuardTakeOpenLog -> true once after the user tapped a call notification
 */
object CallGuardBridge {
    private const val ROLE_REQUEST = 4161
    private const val CONTACTS_REQUEST = 4162
    private var pendingRole: MethodChannel.Result? = null
    @Volatile private var openLog = false

    fun captureIntent(i: Intent?) { if (i?.getBooleanExtra(CallNotifier.EXTRA_OPEN_LOG, false) == true) openLog = true }

    fun roleHeld(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val rm = ctx.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    private fun granted(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun bg(a: Activity, result: MethodChannel.Result, work: () -> Any?) = Thread {
        val r = try { Result.success(work()) } catch (e: Throwable) { Result.failure(e) }
        a.runOnUiThread { r.fold({ result.success(it) }, { result.error("CALL_GUARD", it.javaClass.simpleName, null) }) }
    }.start()

    fun handle(a: Activity, call: MethodCall, result: MethodChannel.Result): Boolean {
        when (call.method) {
            "callGuardStatus" -> bg(a, result) {
                CallGuardStore.load(a)
                mapOf("supported" to (Build.VERSION.SDK_INT >= 29), "sdk" to Build.VERSION.SDK_INT, "roleHeld" to roleHeld(a),
                    "mode" to CallGuardStore.mode(a).n, "allow" to CallGuardStore.allow(a).sorted(), "block" to CallGuardStore.block(a).sorted(),
                    "scamListSize" to CallGuardStore.scam.size(), "scamListVersion" to CallGuardStore.scam.version,
                    "rulesVersion" to CallGuardStore.rules.version,
                    "contactsPermission" to granted(a, Manifest.permission.READ_CONTACTS),
                    "callLogPermission" to granted(a, Manifest.permission.READ_CALL_LOG),
                    "counts" to CallLogStore.get(a).counts())
            }
            "callGuardRequestRole" -> {
                if (Build.VERSION.SDK_INT < 29) { result.success(false); return true }
                if (roleHeld(a)) { result.success(true); return true }
                val rm = a.getSystemService(RoleManager::class.java)
                if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) { result.success(false); return true }
                pendingRole?.success(false)
                pendingRole = result
                @Suppress("DEPRECATION") a.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), ROLE_REQUEST)
            }
            "callGuardSetMode" -> { CallGuardStore.setMode(a, call.argument<Int>("mode") ?: 2); result.success(true) }
            "callGuardAllow" -> result.success(CallGuardStore.setAllowed(a, call.argument<String>("number") ?: "", call.argument<Boolean>("allow") != false))
            "callGuardBlock" -> { CallGuardStore.load(a); result.success(CallGuardStore.setBlocked(a, call.argument<String>("number") ?: "", call.argument<Boolean>("block") != false)) }
            "callGuardNotSpam" -> bg(a, result) {
                val c = CallGuardStore.setAllowed(a, call.argument<String>("number") ?: "", true)
                if (c != null) CallLogStore.get(a).markUndone(c)
                c
            }
            "callGuardLog" -> bg(a, result) { CallLogStore.get(a).recent(call.argument<Int>("days") ?: 30) }
            "callGuardSync" -> bg(a, result) { CallGuardStore.sync(a) }
            "callGuardRequestContacts" -> {
                a.requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG), CONTACTS_REQUEST)
                result.success(true)
            }
            "callGuardCheck" -> {
                CallGuardStore.load(a)
                val raw = call.argument<String>("number") ?: ""
                val r = CallGuardStore.rules.classify(raw)
                result.success(mapOf("canonical" to CallNumbers.canonical(raw), "protected" to (r?.action == "NEVER_BLOCK"),
                    "rule" to r?.id, "label" to r?.let { CallGuardStore.rules.label(it, raw, CallNotifier.hindi()) }))
            }
            "callGuardTakeOpenLog" -> { val o = openLog; openLog = false; result.success(o) }
            else -> return false
        }
        return true
    }

    fun onActivityResult(a: Activity, requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != ROLE_REQUEST) return false
        pendingRole?.success(roleHeld(a)); pendingRole = null
        return true
    }
}
