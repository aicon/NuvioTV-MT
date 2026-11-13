package com.nuvio.tv.updater

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * After a successful manual self-update: try to bring the app forward, confirm via MainActivity,
 * otherwise fall back to a user-activated notification or a next-launch banner.
 */
object UpdatePostInstallContinuation {
    private const val TAG = "UpdatePostInstall"
    private const val FOREGROUND_CONFIRM_TIMEOUT_MS = 4_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var timeoutRunnable: Runnable? = null
    @Volatile private var pendingTag: String? = null

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PrefsEntryPoint {
        fun updatePreferences(): UpdatePreferences
    }

    private fun prefs(context: Context): UpdatePreferences =
        EntryPointAccessors.fromApplication(context.applicationContext, PrefsEntryPoint::class.java)
            .updatePreferences()

    fun onManualUpdateInstalled(context: Context, tag: String) {
        val app = context.applicationContext
        pendingTag = tag
        scope.launch {
            prefs(app).setAwaitForegroundConfirm(true)
        }
        val launched = runCatching {
            val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)
                ?: return@runCatching false
            intent.addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            app.startActivity(intent)
            true
        }.getOrDefault(false)
        Log.i(TAG, "Manual update $tag installed; launchAttempted=$launched")
        scheduleTimeout(app, tag)
    }

    /** MainActivity reached the foreground — only this counts as a successful auto-bring-forward. */
    fun onMainActivityForeground(context: Context) {
        val app = context.applicationContext
        val awaiting = runBlocking { prefs(app).awaitForegroundConfirm.first() }
        if (!awaiting && pendingTag == null) return
        Log.i(TAG, "Foreground confirmed for pending update ${pendingTag.orEmpty()}")
        cancelTimeout()
        pendingTag = null
        scope.launch {
            prefs(app).setAwaitForegroundConfirm(false)
            prefs(app).setUpdateReadyNoticeTag(null)
        }
        UpdateReadyNotifier.cancel(app)
    }

    private fun scheduleTimeout(context: Context, tag: String) {
        cancelTimeout()
        val runnable = Runnable { onForegroundUnconfirmed(context, tag) }
        timeoutRunnable = runnable
        mainHandler.postDelayed(runnable, FOREGROUND_CONFIRM_TIMEOUT_MS)
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun onForegroundUnconfirmed(context: Context, tag: String) {
        val app = context.applicationContext
        pendingTag = null
        Log.i(TAG, "Foreground not confirmed for $tag; using fallback")
        scope.launch {
            prefs(app).setAwaitForegroundConfirm(false)
            val notified = UpdateReadyNotifier.showOpenApp(app, tag)
            if (!notified) {
                prefs(app).setUpdateReadyNoticeTag(tag)
                Log.i(TAG, "Notification unavailable; stored next-launch notice for $tag")
            }
        }
    }
}
