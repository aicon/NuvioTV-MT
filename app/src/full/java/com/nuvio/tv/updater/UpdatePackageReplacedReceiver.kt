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
            // Older builds (e.g. 1.1.16) did not record install source before handing off to the
            // system installer. Do not guess MANUAL and surprise-launch; only arm the next-open banner.
            recordLegacyUpgradeReady(prefs)
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
                // Never surprise-launch after idle auto-install; banner on next open only.
                prefs.setAutoInstalledTag(pending.tag)
                Log.i(TAG, "Idle update ${pending.tag} recorded for next-launch notice")
            }
            PendingUpdateInstall.Source.MANUAL -> {
                UpdatePostInstallContinuation.onManualUpdateInstalled(context, pending.tag)
            }
        }
    }

    /**
     * First upgrade onto a build that understands post-install continuation, from an older updater
     * that left no pending marker. Prefer the downloaded release tag when it matches this install.
     */
    private suspend fun recordLegacyUpgradeReady(prefs: UpdatePreferences) {
        val downloadedTag = prefs.downloadedUpdate.first()?.first
        val tag = when {
            !downloadedTag.isNullOrBlank() &&
                UpdateApkVersion.matchesInstalled(downloadedTag, pendingVersionCode = null) -> downloadedTag
            else -> BuildConfig.VERSION_NAME
        }
        prefs.setUpdateReadyNoticeTag(tag)
        prefs.setAwaitForegroundConfirm(false)
        Log.i(TAG, "No pending install marker; stored next-launch update-ready notice for $tag")
    }

    companion object {
        private const val TAG = "UpdatePackageReplaced"
    }
}
