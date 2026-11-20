package com.example.apkextractor

import android.content.ContentResolver
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes an installed app's APK(s) to a user-chosen location.
 *  - Single-APK apps -> the raw .apk
 *  - Split apps (App Bundles) -> a .apks zip holding base.apk + split_*.apk,
 *    which SAI (Split APKs Installer) or `adb install-multiple` can install.
 */
object ApkExporter {

    fun export(
        resolver: ContentResolver,
        entry: AppEntry,
        target: Uri,
        onProgress: (percent: Int) -> Unit,
    ) {
        val total = entry.totalBytes.coerceAtLeast(1L)
        var written = 0L
        var lastPercent = -1
        val report: (Int) -> Unit = { n ->
            written += n
            val pct = ((written * 100) / total).toInt()
            if (pct != lastPercent) {
                lastPercent = pct
                onProgress(pct)
            }
        }

        val raw = resolver.openOutputStream(target, "w")
            ?: throw IOException("Could not open the destination file")
        raw.use {
            val out = BufferedOutputStream(it, 256 * 1024)
            if (!entry.isSplit) {
                copy(File(entry.apkPaths[0]), out, report)
            } else {
                val zip = ZipOutputStream(out)
                for (path in entry.apkPaths) {
                    val f = File(path)
                    zip.putNextEntry(ZipEntry(f.name)) // base.apk, split_config.arm64_v8a.apk, ...
                    copy(f, zip, report)
                    zip.closeEntry()
                }
                zip.finish()
            }
            out.flush()
        }
    }

    private fun copy(src: File, out: OutputStream, report: (Int) -> Unit) {
        if (!src.canRead()) throw IOException("Cannot read ${src.path}")
        src.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                report(n)
            }
        }
    }
}
