package com.nuvio.tv.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.nuvio.tv.BuildConfig
import java.io.File

object ApkInstaller {
    private const val TAG = "ApkInstaller"

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildUnknownSourcesSettingsIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            null
        }
    }

    /**
     * Installs [apkFile] as an update of this app through a PackageInstaller session, adding the APK's own dex
     * metadata so the new version is compiled at install time. On Android 12+ self-updates request
     * [PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED] (permission declared); the system may still
     * require confirmation. Status callbacks go to [UpdateInstallResultReceiver] — not used as a relaunch path.
     */
    fun install(
        context: Context,
        apkFile: File,
        background: Boolean,
        withDexMetadata: Boolean = true
    ) {
        // Best-effort: register relaunch PI while we may still be foreground (manual path).
        UpdateLaunchIntents.prepareRelaunchPendingIntent(context)

        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Sessions default to internal-only, which the system rejects for an app the user moved to adopted
            // storage. Auto keeps the update on the volume the app is already on.
            setInstallLocation(PackageInfo.INSTALL_LOCATION_AUTO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Self-update UX: skip confirm when the OS allows it. Does not keep the process alive.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val dexMetadata = if (withDexMetadata) {
            UpdateDexMetadata.extract(apkFile, Build.VERSION.SDK_INT, File(apkFile.parentFile, "update.dm"))
        } else {
            null
        }
        val sessionId = packageInstaller.createSession(params)
        try {
            packageInstaller.openSession(sessionId).use { session ->
                session.writeFile("base.apk", apkFile)
                dexMetadata?.let { session.writeFile("base.dm", it) }
                session.commit(
                    UpdateInstallResultReceiver.statusReceiver(
                        context = context,
                        sessionId = sessionId,
                        apkFile = apkFile,
                        background = background,
                        withDexMetadata = dexMetadata != null
                    ).intentSender
                )
            }
            Log.i(TAG, "Committed session $sessionId background=$background")
        } catch (e: Exception) {
            packageInstaller.abandonSession(sessionId)
            throw e
        } finally {
            dexMetadata?.delete()
        }
    }

    /** Hands the APK to the system installer screen, used when the session install cannot be completed. */
    fun launchSystemInstaller(context: Context, apkFile: File) {
        UpdateLaunchIntents.prepareRelaunchPendingIntent(context)
        val authority = "${BuildConfig.APPLICATION_ID}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, apkFile)

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    private fun PackageInstaller.Session.writeFile(name: String, file: File) {
        file.inputStream().use { input ->
            openWrite(name, 0, file.length()).use { output ->
                input.copyTo(output)
                fsync(output)
            }
        }
    }
}
