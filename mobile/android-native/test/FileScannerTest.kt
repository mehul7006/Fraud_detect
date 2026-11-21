package com.fraudshield.app

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Roadmap 5.4 (sha256/header), 5.7 (delete rules). */
class FileScannerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}\$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!\$H+H*"

    @Test fun sha256OfEicarMatchesTheKnownHash() {
        val f = tmp.newFile("eicar.com"); f.writeText(eicar)
        assertEquals(68L, f.length())
        assertEquals("275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f", FileScanner.sha256(f))
    }

    @Test fun headerIsFirstEightBytesInHex() {
        val f = tmp.newFile("a.pdf"); f.writeBytes("%PDF-1.7 rest".toByteArray())
        assertEquals("255044462d312e37", FileScanner.header(f))
        val tiny = tmp.newFile("t.bin"); tiny.writeBytes(byteArrayOf(1, 2))
        assertEquals("0102", FileScanner.header(tiny))
    }

    @Test fun unfinishedDownloadsAndHiddenFilesAreSkipped() {
        assertTrue(FileScanner.looksUnfinished("movie.mp4.crdownload"))
        assertTrue(FileScanner.looksUnfinished("data.PART"))
        assertTrue(FileScanner.looksUnfinished(".nomedia"))
        assertFalse(FileScanner.looksUnfinished("invoice.pdf"))
    }

    @Test fun deleteRuleAcceptsFilesInsideTheRoot() {
        val root = tmp.newFolder("Download")
        val f = File(root, "a.apk"); f.writeText("x")
        assertTrue(FileScanner.isInsideRoot(root.canonicalPath, f.canonicalPath))
    }

    @Test fun deleteRuleRefusesSiblingFolderWithSamePrefix() {
        val root = tmp.newFolder("Download")
        val evil = tmp.newFolder("Download-evil")
        val f = File(evil, "a.apk"); f.writeText("x")
        assertFalse(FileScanner.isInsideRoot(root.canonicalPath, f.canonicalPath))
    }

    @Test fun deleteRuleRefusesTheRootItselfAndSystemPaths() {
        val root = tmp.newFolder("Download")
        assertFalse(FileScanner.isInsideRoot(root.canonicalPath, root.canonicalPath))
        assertFalse(FileScanner.isInsideRoot(root.canonicalPath, "/system/bin/sh"))
    }

    @Test fun deleteRefusesDotDotPaths() {
        assertFalse(FileScanner.delete("/sdcard/Download/../../system/build.prop"))
    }
}
