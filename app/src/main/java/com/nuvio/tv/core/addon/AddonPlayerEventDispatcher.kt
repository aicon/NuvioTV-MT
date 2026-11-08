package com.nuvio.tv.core.addon

import android.util.Log
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.enabledAddons
import com.nuvio.tv.domain.repository.AddonRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Fire-and-forget Stremio-style player events to installed addons that declare
 * the `player` resource (see NuvioTV #4320 / Yamtrack scrobbler).
 *
 * Failures never propagate to callers — playback must stay unaffected.
 */
@Singleton
class AddonPlayerEventDispatcher @Inject constructor(
    private val addonRepository: AddonRepository,
    @Named("addonPermissive") private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestClient: OkHttpClient = okHttpClient.newBuilder()
        .callTimeout(PLAYER_EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .connectTimeout(PLAYER_EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(PLAYER_EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    fun emit(
        action: String,
        mediaType: String,
        videoId: String,
        currentTimeMs: Long?,
        durationMs: Long?,
        enabled: Boolean
    ) {
        if (!enabled) return
        if (videoId.isBlank()) return
        val normalizedAction = action.trim().lowercase()
        if (normalizedAction.isEmpty()) return
        val type = normalizeStremioType(mediaType)
        scope.launch {
            runCatching {
                val addons = withTimeoutOrNull(2_000L) {
                    addonRepository.getInstalledAddons().first()
                }?.enabledAddons().orEmpty() ?: return@launch

                val targets = addons.filter { addon ->
                    addonSupportsPlayer(addon, type, videoId)
                }
                if (targets.isEmpty()) return@launch

                for (addon in targets) {
                    val url = buildPlayerEventUrl(
                        baseUrl = addon.baseUrl,
                        type = type,
                        videoId = videoId,
                        action = normalizedAction,
                        currentTimeMs = currentTimeMs,
                        durationMs = durationMs
                    ) ?: continue
                    runCatching {
                        val request = Request.Builder().url(url).get().build()
                        requestClient.newCall(request).execute().use { response ->
                            Log.d(
                                TAG,
                                "player event action=$normalizedAction addon=${addon.id} code=${response.code}"
                            )
                        }
                    }.onFailure { err ->
                        Log.d(TAG, "player event failed addon=${addon.id} action=$normalizedAction: ${err.message}")
                    }
                }
            }.onFailure { err ->
                Log.d(TAG, "player event dispatch aborted: ${err.message}")
            }
        }
    }

    companion object {
        private const val TAG = "AddonPlayerEvents"
        private const val PLAYER_EVENT_TIMEOUT_MS = 2_500L

        fun normalizeStremioType(contentType: String?): String {
            val raw = contentType?.trim()?.lowercase().orEmpty()
            return when {
                raw == "movie" || raw == "movies" -> "movie"
                raw == "series" || raw == "tv" || raw == "show" || raw == "shows" -> "series"
                else -> if (raw.isBlank()) "movie" else raw
            }
        }

        fun addonSupportsPlayer(addon: Addon, type: String, videoId: String): Boolean {
            val playerResources = addon.resources.filter { it.name.equals("player", ignoreCase = true) }
            if (playerResources.isEmpty()) return false
            val typeOk = playerResources.any { res ->
                res.types.isEmpty() || res.types.any { it.equals(type, ignoreCase = true) }
            }
            if (!typeOk) return false
            val prefixes = playerResources.flatMap { it.idPrefixes.orEmpty() }
                .ifEmpty { addon.idPrefixes }
            if (prefixes.isEmpty()) return true
            return prefixes.any { prefix -> videoId.startsWith(prefix) }
        }

        fun buildPlayerEventUrl(
            baseUrl: String,
            type: String,
            videoId: String,
            action: String,
            currentTimeMs: Long?,
            durationMs: Long?
        ): String? {
            val base = baseUrl.trim().trimEnd('/')
            if (base.isEmpty()) return null
            val encodedId = urlEncode(videoId)
            val extras = buildString {
                append("action=").append(urlEncode(action))
                if (currentTimeMs != null) append("&currentTime=").append(currentTimeMs)
                if (durationMs != null) append("&duration=").append(durationMs)
            }
            return "$base/player/$type/$encodedId/$extras.json"
        }

        private fun urlEncode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }
}
