package com.fraudshield.app

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.nio.file.Files

/**
 * 17.4 safe delete. Only runs after the user ticked items and confirmed in the app; then:
 *  - every path is checked again (LeftoverRules.refuseReason): shared storage only, never Android/, never a standard
 *    folder itself, never a symlink, never a file MediaStore says belongs to another installed app
 *  - Android 11+: photos / videos / audio go to the system trash (MediaStore.createTrashRequest - Android shows its own
 *    confirm, the user can restore for 30 days in Files / Gallery "Recently deleted")
 *  - files Android's trash does not take (APK, PDF, cache...) and Android 10 and older: deleted after the same confirm
 */
object LeftoverCleaner {
    data class Plan(val trash: List<Uri>, val direct: List<File>, val refused: Map<String, String>)

    private data class Media(val id: Long, val type: Int, val owner: String?)

    private fun mediaOf(ctx: Context, path: String): Media? {
        val cols = mutableListOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE)
        if (Build.VERSION.SDK_INT >= 29) cols.add(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        return try {
            ctx.contentResolver.query(MediaStore.Files.getContentUri("external"), cols.toTypedArray(),
                "${MediaStore.MediaColumns.DATA}=?", arrayOf(path), null)?.use { c ->
                if (c.moveToFirst()) Media(c.getLong(0), c.getInt(1), if (cols.size > 2) c.getString(2) else null) else null
            }
        } catch (e: Exception) { null }
    }

    private fun uriOf(m: Media): Uri? = when (m.type) {
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, m.id)
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, m.id)
        MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, m.id)
        else -> null
    }

    /** Builds the plan; [removedPkg] = whose leftovers (null for orphans / APKs). */
    fun plan(ctx: Context, paths: List<String>, removedPkg: String?): Plan {
        val root = LeftoverIndex.root().canonicalPath
        val trash = mutableListOf<Uri>(); val direct = mutableListOf<File>(); val refused = linkedMapOf<String, String>()
        val installed = HashMap<String, Boolean>()
        fun isInstalled(p: String) = installed.getOrPut(p) { LeftoverFinder.isInstalled(ctx, p) }
        for (p in paths) {
            val f = File(p)
            if (!f.exists()) { refused[p] = "GONE"; continue }
            if (Files.isSymbolicLink(f.toPath())) { refused[p] = "SYMLINK"; continue }
            val canon = f.canonicalPath
            LeftoverRules.refuseReason(canon, root, null, removedPkg, ::isInstalled)?.let { refused[p] = it; continue }
            val files = if (f.isDirectory) f.walkTopDown().onEnter { !Files.isSymbolicLink(it.toPath()) }.filter { it.isFile }.take(20_000).toList() else listOf(f)
            val planned = mutableListOf<Pair<File, Uri?>>()
            var bad: String? = null
            for (x in files) {
                val m = mediaOf(ctx, x.absolutePath)
                LeftoverRules.refuseReason(x.canonicalPath, root, m?.owner, removedPkg, ::isInstalled)?.let { bad = it }
                if (bad != null) break
                planned.add(x to m?.let(::uriOf))
            }
            if (bad != null) { refused[p] = bad!!; continue } // one file of another installed app -> the whole folder is left alone
            for ((x, u) in planned) if (u != null && Build.VERSION.SDK_INT >= 30) trash.add(u) else direct.add(x)
            if (f.isDirectory) direct.add(f) // removed at the end only if empty
        }
        return Plan(trash, direct, refused)
    }

    /** Android 11+: the system "Move to trash?" dialog. Returns false when nothing needs the trash. */
    fun requestTrash(activity: Activity, plan: Plan, requestCode: Int): Boolean {
        if (Build.VERSION.SDK_INT < 30 || plan.trash.isEmpty()) return false
        val pi = MediaStore.createTrashRequest(activity.contentResolver, plan.trash, true)
        @Suppress("DEPRECATION")
        activity.startIntentSenderForResult(pi.intentSender, requestCode, null, 0, 0, 0)
        return true
    }

    /** Deletes the files the trash cannot take; folders only when empty (trashed files stay hidden in them for 30 days). */
    fun deleteDirect(plan: Plan): Int {
        var n = 0
        for (f in plan.direct.filter { it.isFile }) if (f.delete()) n++
        for (d in plan.direct.filter { it.isDirectory }.sortedByDescending { it.absolutePath.length }) {
            d.walkBottomUp().filter { it.isDirectory && (it.list()?.isEmpty() == true) }.forEach { it.delete() }
        }
        return n
    }
}
