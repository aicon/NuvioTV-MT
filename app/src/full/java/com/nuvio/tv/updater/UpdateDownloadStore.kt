package com.nuvio.tv.updater

import android.content.Context
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.updater.model.AppUpdate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the downloaded update APK in app storage and remembers which release it belongs to, so a download made in the
 * background can be installed later, from the banner or by the idle install job.
 */
@Singleton
class UpdateDownloadStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updatePreferences: UpdatePreferences,
    private val apkDownloader: ApkDownloader
) {
    // Files storage instead of cache, which the system may clear before the update is installed.
    private val updatesDir: File get() = File(context.filesDir, "updates")

    // The banner and the background job can ask for the same download; the second one waits and reuses the file.
    private val downloadMutex = Mutex()

    fun destinationFor(update: AppUpdate): File {
        val safeName = update.assetName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(updatesDir, safeName)
    }

    /** Returns the APK only after it passes the integrity check for this release. */
    suspend fun downloadedFileFor(update: AppUpdate): File? {
        val saved = updatePreferences.downloadedUpdate.first() ?: return null
        if (saved.tag != update.tag) return null
        val file = File(saved.path)
        val digest = update.assetDigest ?: saved.digest
        if (!ApkIntegrity.isIntact(context, file, update.assetSizeBytes, digest)) {
            if (file.exists()) file.delete()
            updatePreferences.setDownloadedUpdate(null, null, null)
            return null
        }
        return file
    }

    /** Returns the downloaded APK when it is newer than the installed one and still intact. */
    suspend fun pendingInstall(): Pair<String, File>? {
        val saved = updatePreferences.downloadedUpdate.first() ?: return null
        val file = File(saved.path)
        if (!VersionUtils.isRemoteNewer(saved.tag, BuildConfig.VERSION_NAME)) return null
        if (!ApkIntegrity.isIntact(context, file, expectedSize = null, digest = saved.digest)) {
            if (file.exists()) file.delete()
            updatePreferences.setDownloadedUpdate(null, null, null)
            return null
        }
        return saved.tag to file
    }

    suspend fun download(
        update: AppUpdate,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): Result<File> = downloadMutex.withLock {
        downloadedFileFor(update)?.let { return@withLock Result.success(it) }
        val destination = destinationFor(update)
        apkDownloader.download(
            url = update.assetUrl,
            destinationFile = destination,
            knownTotalBytes = update.assetSizeBytes,
            onProgress = onProgress
        ).mapCatching { file ->
            if (!ApkIntegrity.isIntact(context, file, update.assetSizeBytes, update.assetDigest)) {
                file.delete()
                error("Downloaded APK failed integrity check")
            }
            file
        }.onSuccess { file ->
            updatesDir.listFiles()?.filter { it != file }?.forEach { it.delete() }
            updatePreferences.setDownloadedUpdate(update.tag, file.absolutePath, update.assetDigest)
        }
    }

    /** Drops the downloaded APK once it is installed, outdated, or no longer wanted. */
    suspend fun clear() {
        updatesDir.deleteRecursively()
        updatePreferences.setDownloadedUpdate(null, null)
    }

    suspend fun clearIfInstalled() {
        val saved = updatePreferences.downloadedUpdate.first() ?: return
        if (!VersionUtils.isRemoteNewer(saved.tag, BuildConfig.VERSION_NAME)) clear()
    }
}
