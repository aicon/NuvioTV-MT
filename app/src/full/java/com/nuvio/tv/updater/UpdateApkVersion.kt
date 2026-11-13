package com.nuvio.tv.updater

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

internal object UpdateApkVersion {
    fun versionCodeOf(context: Context, apkFile: File): Int? {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
        } ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }

    fun matchesInstalled(pendingTag: String, pendingVersionCode: Int?): Boolean {
        if (pendingVersionCode != null && pendingVersionCode == com.nuvio.tv.BuildConfig.VERSION_CODE) {
            return true
        }
        val local = com.nuvio.tv.BuildConfig.VERSION_NAME
        if (VersionUtils.normalize(pendingTag) == VersionUtils.normalize(local)) {
            return true
        }
        val remote = VersionUtils.parse(pendingTag) ?: return false
        val installed = VersionUtils.parse(local) ?: return false
        return remote == installed
    }
}
