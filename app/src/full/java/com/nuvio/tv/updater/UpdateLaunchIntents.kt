package com.nuvio.tv.updater

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Launch intents / PendingIntents for post-install continuation.
 * Creator BAL opt-in is best-effort on API 34+; not a guarantee the system will start the activity.
 */
internal object UpdateLaunchIntents {
    private const val TAG = "UpdateLaunch"
    private const val RELAUNCH_REQUEST_CODE = 71_043

    fun launchIntent(context: Context): Intent? {
        val pm = context.packageManager
        val leanback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            runCatching { pm.getLeanbackLaunchIntentForPackage(context.packageName) }.getOrNull()
        } else {
            null
        }
        val launch = leanback ?: pm.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return launch
    }

    /** Register an Activity PendingIntent while still in the foreground (before package replace). */
    fun prepareRelaunchPendingIntent(context: Context): PendingIntent? {
        val app = context.applicationContext
        val launch = launchIntent(app) ?: return null
        return runCatching {
            createRelaunchPendingIntent(app, launch)
        }.onFailure {
            Log.w(TAG, "Failed to prepare relaunch PendingIntent", it)
        }.getOrNull()
    }

    /**
     * Recreate and send the relaunch PendingIntent after package replace.
     * Relies on creator BAL opt-in stored on the PendingIntent when prepared before install.
     */
    fun sendPreparedRelaunch(context: Context): Boolean {
        val app = context.applicationContext
        val launch = launchIntent(app) ?: return false
        return runCatching {
            val pi = createRelaunchPendingIntent(app, launch)
            pi.send()
            true
        }.onFailure {
            Log.w(TAG, "Relaunch PendingIntent send failed", it)
        }.getOrDefault(false)
    }

    fun startLaunchActivity(context: Context): Boolean {
        val app = context.applicationContext
        val launch = launchIntent(app) ?: return false
        return runCatching {
            app.startActivity(launch)
            true
        }.getOrDefault(false)
    }

    private fun createRelaunchPendingIntent(context: Context, launch: Intent): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val optionsBundle = if (Build.VERSION.SDK_INT >= 35) {
            ActivityOptions.makeBasic().apply {
                // Creator opt-in required when targetSdk >= 35; without it BAL often blocks the later send.
                setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            }.toBundle()
        } else {
            null
        }
        return if (optionsBundle != null) {
            PendingIntent.getActivity(context, RELAUNCH_REQUEST_CODE, launch, flags, optionsBundle)
        } else {
            PendingIntent.getActivity(context, RELAUNCH_REQUEST_CODE, launch, flags)
        }
    }
}
