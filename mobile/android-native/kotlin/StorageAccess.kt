package com.fraudshield.app

/**
 * 5.8: which storage permission file protection needs, per Android version (pure logic, unit tested).
 *
 *  - Android 11+ (API 30+): "All files access" (MANAGE_EXTERNAL_STORAGE), granted only in system Settings.
 *  - Android 8-10 (API 26-29): the old runtime permissions READ/WRITE_EXTERNAL_STORAGE (manifest has
 *    maxSdkVersion 29) plus requestLegacyExternalStorage so Android 10 still allows plain File access.
 *
 * Before this fix Android 8-10 always reported "granted" although nothing was asked, so the tile said ON while
 * scanning could not read the Download folder.
 */
object StorageAccess {
    const val ALL_FILES_SDK = 30

    /** A real dialog takes the user at least this long to answer; a faster "denied" means none was shown. */
    const val NO_DIALOG_MS = 400L

    enum class Request { ALL_FILES_SETTINGS, RUNTIME_PROMPT }

    fun hasAccess(sdk: Int, isExternalStorageManager: Boolean, readGranted: Boolean, writeGranted: Boolean): Boolean =
        if (sdk >= ALL_FILES_SDK) isExternalStorageManager else readGranted && writeGranted

    /** What tapping "Allow" opens first. On Android 8-10 the system dialog is always tried first. */
    fun request(sdk: Int): Request = if (sdk >= ALL_FILES_SDK) Request.ALL_FILES_SETTINGS else Request.RUNTIME_PROMPT

    /**
     * After the runtime request returns: true when Android refused without showing a dialog (the user chose
     * "Don't ask again" earlier). Then the app opens App info so the user is never stuck on a button that does
     * nothing. A normal "Deny" in the dialog is never followed by a Settings page.
     */
    fun openAppSettingsInstead(granted: Boolean, elapsedMs: Long): Boolean = !granted && elapsedMs < NO_DIALOG_MS
}
