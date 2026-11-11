package com.nuvio.tv.core.addon

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Stable install id + default label for MediaTrail Now Watching strips. */
@Singleton
class PlayerDeviceIdentity @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun deviceId(): String {
        val existing = preferences.getString(DEVICE_ID_KEY, null)?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing
        val created = UUID.randomUUID().toString()
        preferences.edit().putString(DEVICE_ID_KEY, created).apply()
        return created
    }

    fun deviceName(): String = "TV"

    companion object {
        private const val PREFERENCES_NAME = "nuvio_player_device"
        private const val DEVICE_ID_KEY = "device_id"
    }
}
