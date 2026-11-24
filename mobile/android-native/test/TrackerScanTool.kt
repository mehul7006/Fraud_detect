package com.fraudshield.app

import java.io.File
import org.junit.Assume
import org.junit.Test

/**
 * 13.4 device check, run on the PC: APK_DIR=<folder with one sub-folder per app, holding its APKs copied from a
 * phone with `adb pull`> -> prints the SDKs the production scanner finds in each. Skipped without APK_DIR.
 */
class TrackerScanTool {
    @Test fun scan_real_apks() {
        val dir = System.getenv("APK_DIR")
        Assume.assumeTrue(dir != null && File(dir).isDirectory)
        val out = StringBuilder("# 13.4 tracker SDKs found in real apps (APKs from a test phone)\n\n| App | SDKs found |\n|---|---|\n")
        for (app in File(dir!!).listFiles()!!.filter { it.isDirectory }.sortedBy { it.name }) {
            val t0 = System.nanoTime()
            val found = TrackerScanner.match(app.listFiles()!!.filter { it.name.endsWith(".apk") }.asSequence()
                .flatMap { TrackerScanner.dexBytes(it.path) })
            val ms = (System.nanoTime() - t0) / 1_000_000
            val names = found.sortedBy { it.category + it.name }.joinToString(", ") { "${it.name} (${it.category})" }
            out.append("| ${app.name} | ${names.ifEmpty { "none" }} - ${ms} ms |\n")
        }
        File(dir, "trackers-report.md").writeText(out.toString())
        println(out)
    }
}
