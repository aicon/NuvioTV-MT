package com.nuvio.tv.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nuvio.tv.BuildConfig
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Observes [Intent.ACTION_MY_PACKAGE_REPLACED]. Suitable for detecting a finished self-update on
 * both PackageInstaller and system-installer paths; does not by itself grant background launch rights.
 */
class UpdatePackageReplacedReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PrefsEntryPoint {
        fun updatePreferences(): UpdatePreferences
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handleReplace(app)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleReplace(context: Context) {
        val prefs = EntryPointAccessors.fromApplication(context, PrefsEntryPoint::class.java)
            .updatePreferences()
        val pending = prefs.pendingInstall.first()
        Log.i(
            TAG,
            "MY_PACKAGE_REPLACED installed=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) " +
                "pending=${pending?.tag}/${pending?.versionCode} source=${pending?.source}"
        )
        if (pending == null) {
            // Older updaters left no marker. Do not surprise-launch or show a success banner.
            prefs.setAwaitForegroundConfirm(false)
            prefs.setUpdateReadyNoticeTag(null)
            Log.i(TAG, "No pending install marker; leaving silent (no relaunch, no banner)")
            return
        }

        if (!UpdateApkVersion.matchesInstalled(pending.tag, pending.versionCode)) {
            Log.w(TAG, "Installed version does not match pending target; clearing stale pending install")
            prefs.clearPendingInstall()
            return
        }

        prefs.clearPendingInstall()
        when (pending.source) {
            PendingUpdateInstall.Source.IDLE -> {
                // Never surprise-launch after idle auto-install; optional notice on next open.
                prefs.setAutoInstalledTag(pending.tag)
                Log.i(TAG, "Idle update ${pending.tag} recorded for next-launch notice")
            }
            PendingUpdateInstall.Source.MANUAL -> {
                UpdatePostInstallContinuation.onManualUpdateInstalled(context, pending.tag)
            }
        }
    }

    companion object {
        private const val TAG = "UpdatePackageReplaced"
    }
}
