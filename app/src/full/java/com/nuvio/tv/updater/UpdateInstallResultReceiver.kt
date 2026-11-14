package com.nuvio.tv.updater

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "UpdateInstall"

/**
 * Receives the outcome of an update install session. A confirmation request is shown only for installs the user
 * started; a background install that still needs confirmation is dropped and the downloaded APK waits for the banner.
 * A failed session is retried without dex metadata, then falls back to the system installer screen.
 *
 * Successful package replacement is handled by [UpdatePackageReplacedReceiver]; this receiver clears the pending
 * install marker when a session is abandoned or definitively fails (so a cancelled system-installer attempt is
 * cleaned up on the next launch if replace never arrives).
 */
class UpdateInstallResultReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PrefsEntryPoint {
        fun updatePreferences(): UpdatePreferences
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val background = intent.getBooleanExtra(EXTRA_BACKGROUND, false)
        val withDexMetadata = intent.getBooleanExtra(EXTRA_WITH_DEX_METADATA, false)
        val apkFile = intent.getStringExtra(EXTRA_APK_PATH)?.let(::File)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i(TAG, "Session $sessionId status=$status background=$background dm=$withDexMetadata message=$message")

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (background) {
                    abandonSession(context, sessionId)
                    clearPending(context)
                } else {
                    showConfirmationOrSettle(context, intent, sessionId, apkFile)
                }
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> clearPending(context)
            else -> retryAfterFailure(context, apkFile, background, withDexMetadata)
        }
    }

    /**
     * The session stays pending until it is abandoned or the system confirmation activity takes it.
     * A missing [Intent.EXTRA_INTENT] or a failed start is logged, then the session is abandoned.
     * [ApkInstaller.launchSystemInstaller] runs only after that abandon succeeds.
     */
    private fun showConfirmationOrSettle(context: Context, intent: Intent, sessionId: Int, apkFile: File?) {
        val confirm = confirmationIntent(intent)
        if (confirm == null) {
            Log.w(TAG, "STATUS_PENDING_USER_ACTION without EXTRA_INTENT for session $sessionId")
            fallbackAfterSessionSettled(context, sessionId, apkFile)
            return
        }
        val started = runCatching {
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        if (started.isSuccess) return
        Log.w(TAG, "Install confirmation screen unavailable for session $sessionId", started.exceptionOrNull())
        fallbackAfterSessionSettled(context, sessionId, apkFile)
    }

    private fun fallbackAfterSessionSettled(context: Context, sessionId: Int, apkFile: File?) {
        if (!abandonSession(context, sessionId)) {
            Log.w(TAG, "Session $sessionId still pending; not starting a second installer")
            return
        }
        if (apkFile == null || !apkFile.exists()) {
            clearPending(context)
            return
        }
        runCatching { ApkInstaller.launchSystemInstaller(context, apkFile) }
            .onFailure {
                Log.w(TAG, "System installer fallback failed for session $sessionId", it)
                clearPending(context)
            }
    }

    private fun abandonSession(context: Context, sessionId: Int): Boolean {
        if (sessionId < 0) return true
        return runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
            .onFailure { Log.w(TAG, "Could not abandon session $sessionId", it) }
            .isSuccess
    }

    private fun clearPending(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                EntryPointAccessors.fromApplication(context.applicationContext, PrefsEntryPoint::class.java)
                    .updatePreferences()
                    .clearPendingInstall()
            }
        }
    }

    private fun retryAfterFailure(context: Context, apkFile: File?, background: Boolean, withDexMetadata: Boolean) {
        if (apkFile == null || !apkFile.exists()) {
            clearPending(context)
            return
        }
        runCatching {
            when {
                withDexMetadata -> ApkInstaller.install(context, apkFile, background, withDexMetadata = false)
                !background -> ApkInstaller.launchSystemInstaller(context, apkFile)
                else -> clearPending(context)
            }
        }.onFailure {
            Log.w(TAG, "Update install retry failed", it)
            clearPending(context)
        }
    }

    private fun confirmationIntent(intent: Intent): Intent? {
        // getParcelableExtra(key, Class) exists only on API 33. minSdk is 24.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
    }

    companion object {
        private const val EXTRA_APK_PATH = "com.nuvio.tv.updater.APK_PATH"
        private const val EXTRA_BACKGROUND = "com.nuvio.tv.updater.BACKGROUND"
        private const val EXTRA_WITH_DEX_METADATA = "com.nuvio.tv.updater.WITH_DEX_METADATA"

        fun statusReceiver(
            context: Context,
            sessionId: Int,
            apkFile: File,
            background: Boolean,
            withDexMetadata: Boolean
        ): PendingIntent {
            val intent = Intent(context, UpdateInstallResultReceiver::class.java)
                .putExtra(EXTRA_APK_PATH, apkFile.absolutePath)
                .putExtra(EXTRA_BACKGROUND, background)
                .putExtra(EXTRA_WITH_DEX_METADATA, withDexMetadata)
            // The installer adds the status extras, so the intent has to stay mutable.
            val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
            )
        }
    }
}
