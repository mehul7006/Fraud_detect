package com.fraudshield.app

import com.fraudshield.app.LeftoverRules.ApkStatus
import com.fraudshield.app.LeftoverRules.Group
import com.fraudshield.app.LeftoverRules.State
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Phase 17: the leftover cleaner's decisions (pure logic; the Android parts are checked on a real phone, 17.8). */
class LeftoverRulesTest {
    private val root = "/storage/emulated/0"

    @Test fun groups_and_safe_defaults() {
        assertEquals(Group.INSTALLER, LeftoverRules.group("Download/old-app.apk"))
        assertEquals(Group.INSTALLER, LeftoverRules.group("Download/Game.XAPK"))
        assertEquals(Group.CACHE, LeftoverRules.group("SHAREit/.cache/a.jpg"))
        assertEquals(Group.CACHE, LeftoverRules.group("Telegram/.nomedia"))
        assertEquals(Group.CACHE, LeftoverRules.group("Xender/log/run.txt"))
        assertEquals(Group.MEDIA, LeftoverRules.group("Pictures/Telegram/IMG_1.jpg"))
        assertEquals(Group.MEDIA, LeftoverRules.group("Movies/InShot/v.MP4"))
        assertEquals(Group.DOCUMENT, LeftoverRules.group("Documents/CamScanner/scan.pdf"))
        assertEquals(Group.OTHER, LeftoverRules.group("SHAREit/data.bin"))
        assertTrue(LeftoverRules.tickedByDefault(Group.INSTALLER))
        assertTrue(LeftoverRules.tickedByDefault(Group.CACHE))
        assertFalse("photos never pre-ticked", LeftoverRules.tickedByDefault(Group.MEDIA))
        assertFalse("documents never pre-ticked", LeftoverRules.tickedByDefault(Group.DOCUMENT))
        assertFalse(LeftoverRules.tickedByDefault(Group.OTHER))
    }

    @Test fun a_folder_with_any_photo_or_document_is_personal() {
        assertEquals(Group.MEDIA, LeftoverRules.folderGroup(mapOf(Group.CACHE to 900_000_000L, Group.MEDIA to 10L)))
        assertEquals(Group.DOCUMENT, LeftoverRules.folderGroup(mapOf(Group.CACHE to 900L, Group.DOCUMENT to 1L)))
        assertEquals(Group.CACHE, LeftoverRules.folderGroup(mapOf(Group.CACHE to 900L, Group.OTHER to 10L)))
        assertEquals(Group.CACHE, LeftoverRules.folderGroup(emptyMap()))
    }

    @Test fun folder_names_match_exactly_and_never_on_generic_words() {
        assertEquals(setOf("telegram", "messenger").minus("messenger"), LeftoverRules.nameKeys("org.telegram.messenger", "Telegram"))
        assertEquals(setOf("shareit", "lenovo", "anyshare"), LeftoverRules.nameKeys("com.lenovo.anyshare.gps", "SHAREit"))
        assertTrue("generic label never a key", LeftoverRules.nameKeys("com.miui.gallery", "Gallery").none { it == "gallery" })
        assertTrue(LeftoverRules.nameKeys("com.google.android.apps.photos", "Photos").isEmpty())
        val keys = LeftoverRules.nameKeys("org.telegram.messenger", "Telegram")
        assertTrue(LeftoverRules.folderMatches("Telegram", keys))
        assertTrue(LeftoverRules.folderMatches("telegram", keys))
        assertFalse("no fuzzy match", LeftoverRules.folderMatches("TelegramX", keys))
        assertFalse(LeftoverRules.folderMatches("Tele", keys))
    }

    private val known by lazy {
        val f = listOf("../../assets/leftovers/known-folders.json", "../assets/leftovers/known-folders.json", "assets/leftovers/known-folders.json")
            .map(::File).firstOrNull { it.exists() } ?: error("known-folders.json not found from ${File(".").absolutePath}")
        LeftoverRules.parseKnown(f.readText())
    }

    @Test fun known_folders_file_parses() {
        assertTrue(known.size >= 20)
        assertTrue(known.any { it.app == "SHAREit" && it.folder == "SHAREit" && "com.lenovo.anyshare.gps" in it.packages })
        assertTrue(known.any { it.app == "Telegram" && it.folder == "Pictures/Telegram" })
    }

    @Test fun orphans_only_for_apps_that_are_not_installed() {
        val existing = listOf("SHAREit", "Telegram", "Pictures/Telegram", "Pictures/Instagram", "Xender", "MyOwnFolder", "DCIM/Camera")
        val installed = setOf("org.telegram.messenger", "com.instagram.lite")
        val installedKeys = setOf("telegram", "instagram", "xender") // e.g. a "Xender" clone with another package
        val o = LeftoverRules.orphans(existing, known, installed, installedKeys).map { it.first }
        assertEquals(listOf("SHAREit"), o)
        // nothing installed: every known folder that exists is listed, unknown folders never
        val all = LeftoverRules.orphans(existing, known, emptySet(), emptySet()).map { it.first }.toSet()
        assertEquals(setOf("SHAREit", "Telegram", "Pictures/Telegram", "Pictures/Instagram", "Xender"), all)
    }

    @Test fun three_test_apks_are_classified() {
        assertEquals(ApkStatus.SAME_INSTALLED, LeftoverRules.apkStatus("org.telegram.messenger", 5000, 5000, false))
        assertEquals(ApkStatus.OLDER_THAN_INSTALLED, LeftoverRules.apkStatus("com.whatsapp", 100, 250, false))
        assertEquals(ApkStatus.FLAGGED, LeftoverRules.apkStatus("com.fake.bank", 1, null, true))
        assertEquals(ApkStatus.NOT_INSTALLED, LeftoverRules.apkStatus("com.some.game", 1, null, false))
        assertEquals(ApkStatus.NEWER_THAN_INSTALLED, LeftoverRules.apkStatus("com.whatsapp", 300, 250, false))
        assertEquals(ApkStatus.UNREADABLE, LeftoverRules.apkStatus(null, 0, null, false))
        assertTrue(LeftoverRules.apkTicked(ApkStatus.SAME_INSTALLED) && LeftoverRules.apkTicked(ApkStatus.OLDER_THAN_INSTALLED) && LeftoverRules.apkTicked(ApkStatus.FLAGGED))
        assertFalse("an installer for an app you do not have is listed, not ticked", LeftoverRules.apkTicked(ApkStatus.NOT_INSTALLED))
        assertFalse("an update you have not installed yet is kept", LeftoverRules.apkTicked(ApkStatus.NEWER_THAN_INSTALLED))
    }

    @Test fun never_outside_shared_storage_never_another_installed_apps_files() {
        val installed = setOf("com.whatsapp")
        fun r(path: String, owner: String? = null, removed: String? = "org.telegram.messenger") =
            LeftoverRules.refuseReason(path, root, owner, removed) { it in installed }
        assertNull(r("$root/Telegram"))
        assertNull(r("$root/Pictures/Telegram/a.jpg", owner = "org.telegram.messenger"))
        assertNull("owner already uninstalled", r("$root/Pictures/x.jpg", owner = "com.gone.app"))
        assertEquals("OWNED_BY_INSTALLED_APP", r("$root/Pictures/Telegram/wa.jpg", owner = "com.whatsapp"))
        assertEquals("OUTSIDE_SHARED_STORAGE", r("/data/data/com.whatsapp/x"))
        assertEquals("OUTSIDE_SHARED_STORAGE", r("/storage/emulated/01/x"))
        assertEquals("OUTSIDE_SHARED_STORAGE", r(root))
        assertEquals("APP_PRIVATE_AREA", r("$root/Android/data/org.telegram.messenger"))
        assertEquals("APP_PRIVATE_AREA", r("$root/Android/media/com.whatsapp/WhatsApp"))
        assertEquals("STANDARD_FOLDER", r("$root/Download"))
        assertEquals("STANDARD_FOLDER", r("$root/Pictures/"))
        assertEquals("STANDARD_FOLDER", r("$root/DCIM/Camera"))
        assertNull(r("$root/Download/old.apk", removed = null))
    }

    @Test fun settings_residue() {
        val pkg = "com.bad.app"
        val c = LeftoverRules.settingsResidue(pkg,
            accessibility = "com.google.android.marvin.talkback/.TalkBackService",
            listeners = "com.fraudshield.app/com.fraudshield.app.AdNotificationListener:com.bad.app/com.bad.app.Listener",
            admins = emptyList(), alwaysOnVpn = null, alwaysOnReadable = false,
            defaults = mapOf("SMS" to "com.google.android.apps.messaging", "HOME" to pkg))
        val m = c.associate { it.kind to it.state }
        assertEquals(State.CLEAR, m["ACCESSIBILITY"])
        assertEquals(State.LEFTOVER, m["NOTIFICATION_ACCESS"])
        assertEquals(State.CLEAR, m["DEVICE_ADMIN"])
        assertEquals("unreadable is never shown as clear", State.UNKNOWN, m["ALWAYS_ON_VPN"])
        assertEquals(State.CLEAR, m["DEFAULT_SMS"])
        assertEquals(State.LEFTOVER, m["DEFAULT_HOME"])
        val clean = LeftoverRules.settingsResidue(pkg, "", "", null, pkg, true, emptyMap()).associate { it.kind to it.state }
        assertEquals(State.UNKNOWN, clean["DEVICE_ADMIN"])
        assertEquals(State.LEFTOVER, clean["ALWAYS_ON_VPN"])
        assertEquals(listOf("a.b", "c"), LeftoverRules.packagesOf("a.b/.X:c/c.Y"))
        assertEquals(emptyList<String>(), LeftoverRules.packagesOf(""))
    }

    @Test fun sizes() {
        assertEquals("512 B", LeftoverRules.humanSize(512))
        assertEquals("2 KB", LeftoverRules.humanSize(2048))
        assertEquals("1.5 MB", LeftoverRules.humanSize(1_572_864))
        assertEquals("2.0 GB", LeftoverRules.humanSize(2L shl 30))
    }
}
