package com.nuvio.tv.updater

import android.content.Context
import android.util.Log

/**
 * Manual installs do not relaunch the app from the background. [onMainActivityForeground] stays so
 * [com.nuvio.tv.MainActivity] and the Play Store stub keep the same call.
 */
object UpdatePostInstallContinuation {
    private const val TAG = "UpdatePostInstall"

    fun onManualUpdateInstalled(context: Context, tag: String) {
        Log.i(TAG, "Manual update $tag for ${context.packageName} installed; not relaunching from the background")
    }

    fun onMainActivityForeground(context: Context) = Unit
}
