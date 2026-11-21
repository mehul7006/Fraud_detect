package com.fraudshield.app

import java.io.File
import java.io.FileInputStream

/**
 * Looks at the first 64 KB of a PDF or Office file and returns FLAG NAMES only (never any content):
 * PDF_JS, PDF_LAUNCH, PDF_OPENACTION, OFFICE_MACRO. Pure Kotlin so it can be unit-tested.
 *
 * Limits (by design, documented for 5.6.3):
 *  - only the first 64 KB are read, so a macro/JavaScript that sits later in a large file is not seen;
 *  - PDF keys inside compressed object streams are not seen;
 *  - docx/xlsx/pptx are ZIP files: the entry name "vbaProject.bin" appears in the local file header, which is
 *    inside 64 KB for normal documents; old .doc/.xls (OLE) are detected by the UTF-16 stream name "VBA".
 */
object ContentFlagger {
    const val LIMIT = 64 * 1024
    private val JS = Regex("/(JavaScript|JS)(?![A-Za-z0-9])")
    private val LAUNCH = Regex("/Launch(?![A-Za-z0-9])")
    private val OPEN_ACTION = Regex("/OpenAction(?![A-Za-z0-9])")

    fun flags(f: File): List<String> {
        val buf = ByteArray(LIMIT)
        val n = FileInputStream(f).use { s ->
            var total = 0
            while (total < LIMIT) { val r = s.read(buf, total, LIMIT - total); if (r < 0) break; total += r }
            total
        }
        return flagsOf(buf.copyOf(n))
    }

    fun flagsOf(b: ByteArray): List<String> {
        val out = mutableListOf<String>()
        if (isPdf(b)) {
            val t = String(b, Charsets.ISO_8859_1)
            if (JS.containsMatchIn(t)) out.add("PDF_JS")
            if (LAUNCH.containsMatchIn(t)) out.add("PDF_LAUNCH")
            if (OPEN_ACTION.containsMatchIn(t)) out.add("PDF_OPENACTION")
        } else if (startsWith(b, byteArrayOf(0x50, 0x4B, 0x03, 0x04))) {
            if (indexOf(b, "vbaProject.bin".toByteArray(Charsets.ISO_8859_1)) >= 0) out.add("OFFICE_MACRO")
        } else if (startsWith(b, byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()))) {
            if (indexOf(b, "VBA".toByteArray(Charsets.UTF_16LE)) >= 0) out.add("OFFICE_MACRO")
        }
        return out
    }

    private fun isPdf(b: ByteArray): Boolean {
        val head = b.copyOf(minOf(b.size, 1024))
        return indexOf(head, "%PDF-".toByteArray(Charsets.ISO_8859_1)) >= 0
    }

    private fun startsWith(b: ByteArray, p: ByteArray): Boolean =
        b.size >= p.size && p.indices.all { b[it] == p[it] }

    internal fun indexOf(b: ByteArray, p: ByteArray): Int {
        if (p.isEmpty() || b.size < p.size) return -1
        outer@ for (i in 0..b.size - p.size) {
            for (j in p.indices) if (b[i + j] != p[j]) continue@outer
            return i
        }
        return -1
    }
}
