package com.nuvio.tv.updater

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * A matching byte size is not enough to treat an APK as ready. When the GitHub asset publishes a
 * SHA-256 digest, that must match. The file must also parse as a package archive.
 */
internal object ApkIntegrity {
    fun normalizeSha256(digest: String?): String? {
        val raw = digest?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val hex = if (raw.startsWith("sha256:", ignoreCase = true)) {
            raw.substringAfter(':').trim()
        } else {
            raw
        }.lowercase()
        if (hex.length != 64 || hex.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
        return hex
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun isIntact(context: Context, file: File, expectedSize: Long?, digest: String?): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        if (expectedSize != null && expectedSize > 0L && file.length() != expectedSize) return false
        val expectedHash = normalizeSha256(digest)
        if (expectedHash != null && sha256Hex(file) != expectedHash) return false
        return parsesAsApk(context, file)
    }

    private fun parsesAsApk(context: Context, file: File): Boolean {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        }
        return info != null
    }
}
