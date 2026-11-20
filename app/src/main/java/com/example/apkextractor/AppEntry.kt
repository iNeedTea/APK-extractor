package com.example.apkextractor

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

data class AppEntry(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    /** base.apk first, then any split_*.apk files. */
    val apkPaths: List<String>,
    val totalBytes: Long,
    val isSystem: Boolean,
) {
    val isSplit: Boolean get() = apkPaths.size > 1

    fun suggestedFileName(): String {
        val safeVersion = versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val ext = if (isSplit) "apks" else "apk"
        return "${packageName}_$safeVersion.$ext"
    }
}

object AppLoader {
    fun load(pm: PackageManager): List<AppEntry> {
        val packages: List<PackageInfo> =
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }

        return packages.mapNotNull { pi ->
            val ai: ApplicationInfo = pi.applicationInfo ?: return@mapNotNull null
            val base = ai.sourceDir ?: return@mapNotNull null
            val paths = buildList {
                add(base)
                ai.splitSourceDirs?.let { addAll(it) }
            }
            AppEntry(
                label = ai.loadLabel(pm).toString(),
                packageName = pi.packageName,
                versionName = pi.versionName ?: "unknown",
                versionCode = if (Build.VERSION.SDK_INT >= 28) {
                    pi.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pi.versionCode.toLong()
                },
                apkPaths = paths,
                totalBytes = paths.sumOf { File(it).length() },
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            )
        }.sortedBy { it.label.lowercase() }
    }
}
