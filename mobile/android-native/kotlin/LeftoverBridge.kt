package com.fraudshield.app

import android.app.Activity
import android.content.Intent
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * Phase 17 MethodChannel methods (kept out of MainActivity, which only forwards unknown methods here).
 *   leftoverPending            -> [{package,label,at,files,bytes}]   removed apps that left files (last 30 days)
 *   leftoverForPackage{package}-> {package,label,items:[..],settings:[{kind,state,detail}]}
 *   leftoverScan{flagged:[path]} -> {orphans:[..], apks:[..], indexed:n}
 *   leftoverDelete{paths,package?} -> {trashed,deleted,refused:{path:reason},cancelled}
 *   leftoverDismiss{package}, leftoverTakeOpen -> package from a tapped notification, leftoverRefreshIndex -> n
 */
object LeftoverBridge {
    private const val TRASH_REQUEST = 4171
    private var pending: Triple<MethodChannel.Result, LeftoverCleaner.Plan, String?>? = null
    @Volatile private var openPkg: String? = null

    fun captureIntent(i: Intent?) { i?.getStringExtra(LeftoverFinder.EXTRA_OPEN)?.let { openPkg = it } }

    private fun bg(a: Activity, result: MethodChannel.Result, work: () -> Any?) = Thread {
        val r = try { Result.success(work()) } catch (e: Throwable) { Result.failure(e) }
        a.runOnUiThread { r.fold({ result.success(it) }, { result.error("LEFTOVER", it.message, null) }) }
    }.start()

    /** Returns false when the method is not ours. */
    fun handle(a: Activity, call: MethodCall, result: MethodChannel.Result): Boolean {
        val index = LeftoverIndex.get(a)
        when (call.method) {
            "leftoverPending" -> bg(a, result) {
                index.pending().map { mapOf("package" to it.pkg, "label" to it.label, "at" to it.at, "files" to it.files, "bytes" to it.bytes) }
            }
            "leftoverForPackage" -> bg(a, result) {
                val pkg = call.argument<String>("package") ?: ""
                val label = index.label(pkg)
                mapOf("package" to pkg, "label" to (label ?: pkg),
                    "items" to LeftoverFinder.find(a, pkg, label).map { it.toMap() },
                    "settings" to SettingsResidueCheck.check(a, pkg).map { mapOf("kind" to it.kind, "state" to it.state.name, "detail" to it.detail) })
            }
            "leftoverScan" -> bg(a, result) {
                val flagged = (call.argument<List<String>>("flagged") ?: emptyList()).toSet()
                val n = index.refreshIfStale(a)
                mapOf("orphans" to OrphanScanner.scan(a), "apks" to LeftoverFinder.oldApks(a, flagged), "indexed" to n)
            }
            "leftoverRefreshIndex" -> bg(a, result) { index.refresh(a) }
            "leftoverDismiss" -> { index.setState(call.argument<String>("package") ?: "", "DISMISSED"); result.success(true) }
            "leftoverTakeOpen" -> { val p = openPkg; openPkg = null; result.success(p) }
            "leftoverDelete" -> {
                val paths = call.argument<List<String>>("paths") ?: emptyList()
                val pkg = call.argument<String>("package")
                Thread {
                    val plan = try { LeftoverCleaner.plan(a, paths, pkg) } catch (e: Exception) { null }
                    a.runOnUiThread {
                        if (plan == null) { result.error("LEFTOVER", "could not prepare", null); return@runOnUiThread }
                        val asked = try { LeftoverCleaner.requestTrash(a, plan, TRASH_REQUEST) } catch (e: Exception) { false }
                        if (asked) pending = Triple(result, plan, pkg)
                        else finish(a, result, plan, pkg, trashed = 0)
                    }
                }.start()
            }
            else -> return false
        }
        return true
    }

    private fun finish(a: Activity, result: MethodChannel.Result, plan: LeftoverCleaner.Plan, pkg: String?, trashed: Int) = Thread {
        val deleted = LeftoverCleaner.deleteDirect(plan)
        if (pkg != null && plan.refused.isEmpty()) LeftoverIndex.get(a).setState(pkg, "DONE")
        a.runOnUiThread { result.success(mapOf("trashed" to trashed, "deleted" to deleted, "refused" to plan.refused, "cancelled" to false)) }
    }.start()

    /** MainActivity.onActivityResult forwards here. */
    fun onActivityResult(a: Activity, requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != TRASH_REQUEST) return false
        val (result, plan, pkg) = pending ?: return true
        pending = null
        if (resultCode == Activity.RESULT_OK) finish(a, result, plan, pkg, trashed = plan.trash.size)
        else result.success(mapOf("trashed" to 0, "deleted" to 0, "refused" to plan.refused, "cancelled" to true)) // user said no: nothing deleted
        return true
    }
}
