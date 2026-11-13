package com.nuvio.tv.updater

import android.content.Context

/** Play Store builds have no sideloaded updater; foreground confirm is a no-op. */
object UpdatePostInstallContinuation {
    fun onMainActivityForeground(context: Context) = Unit
}
