package com.nuvio.tv.core.addon

import android.util.Log
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.WatchedItem
import com.nuvio.tv.domain.model.enabledAddons
import com.nuvio.tv.domain.repository.AddonRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Best-effort library mark/unmark to MediaTrail via Bearer POST /nuvio/watched.json.
 * Failures never roll back the local Nuvio mark. Token stays in the Authorization header.
 */
@Singleton
class MediaTrailWatchedSync @Inject constructor(
    private val addonRepository: AddonRepository,
    @Named("addonPermissive") okHttpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestClient: OkHttpClient = okHttpClient.newBuilder()
        .callTimeout(WATCHED_SYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .connectTimeout(WATCHED_SYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(WATCHED_SYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    fun pushMarks(items: Collection<WatchedItem>, played: Boolean) {
        if (items.isEmpty()) return
        val payloads = items.mapNotNull { item -> watchedPayloadJson(item, played = played) }
        if (payloads.isEmpty()) return
        scope.launch {
            runCatching {
                val addons = withTimeoutOrNull(2_000L) {
                    addonRepository.getInstalledAddons().first()
                }?.enabledAddons().orEmpty() ?: return@launch
                val targets = resolveMediaTrailWatchedTargets(addons)
                if (targets.isEmpty()) return@launch
                for (target in targets) {
                    for (body in payloads) {
                        runCatching {
                            withTimeoutOrNull(WATCHED_SYNC_TIMEOUT_MS) {
                                val request = Request.Builder()
                                    .url(target.watchedUrl)
                                    .header("Authorization", "Bearer ${target.bearerToken}")
                                    .header("Accept", "application/json")
                                    .post(body.toRequestBody(JSON_MEDIA_TYPE))
                                    .build()
                                requestClient.newCall(request).execute().use { response ->
                                    Log.d(
                                        TAG,
                                        "watched sync played=$played code=${response.code} url=${target.watchedUrl}",
                                    )
                                }
                            }
                        }.onFailure { err ->
                            Log.d(TAG, "watched sync failed: ${err.message}")
                        }
                    }
                }
            }.onFailure { err ->
                Log.d(TAG, "watched sync aborted: ${err.message}")
            }
        }
    }

    fun pushUnmark(
        contentId: String,
        contentType: String?,
        season: Int?,
        episode: Int?,
    ) {
        val type = when {
            !contentType.isNullOrBlank() -> contentType
            season != null && episode != null -> "series"
            else -> "movie"
        }
        pushMarks(
            items = listOf(
                WatchedItem(
                    contentId = contentId,
                    contentType = type,
                    title = "",
                    season = season,
                    episode = episode,
                    watchedAt = 0L,
                ),
            ),
            played = false,
        )
    }

    companion object {
        private const val TAG = "MediaTrailWatched"
        private const val WATCHED_SYNC_TIMEOUT_MS = 4_000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

data class MediaTrailWatchedTarget(
    val watchedUrl: String,
    val bearerToken: String,
)

fun resolveMediaTrailWatchedTargets(addons: List<Addon>): List<MediaTrailWatchedTarget> =
    addons.mapNotNull { addon ->
        parseMediaTrailWatchedEndpoint(addon.baseUrl)
    }.distinctBy { it.watchedUrl to it.bearerToken }

/**
 * Addon installs look like `https://host/nuvio/<token>/manifest.json` (baseUrl may omit manifest).
 * Watched sync uses the host origin + Bearer token — never the path token form.
 */
fun parseMediaTrailWatchedEndpoint(manifestOrBaseUrl: String): MediaTrailWatchedTarget? {
    val path = manifestOrBaseUrl.substringBefore("?").trim().trimEnd('/')
    if (path.isEmpty()) return null
    val match = MEDIA_TRAIL_URL.matchEntire(path) ?: return null
    val origin = match.groupValues[1]
    val token = match.groupValues[2].trim()
    if (token.isEmpty()) return null
    // Reject legacy …/nuvio/manifest.json (token slot would be "manifest[.json]").
    if (token.equals("manifest", ignoreCase = true) || token.equals("manifest.json", ignoreCase = true)) {
        return null
    }
    return MediaTrailWatchedTarget(
        watchedUrl = "$origin/nuvio/watched.json",
        bearerToken = token,
    )
}

fun watchedPayloadJson(item: WatchedItem, played: Boolean): String? {
    val contentId = item.contentId.trim()
    if (contentId.isEmpty()) return null
    val type = AddonPlayerEventDispatcher.normalizeStremioType(item.contentType)
    val action = if (played) "watched" else "unwatched"
    if (type == "series") {
        val season = item.season ?: return null
        val episode = item.episode ?: return null
        return buildString {
            append('{')
            appendJsonField("action", action)
            append(',')
            appendJsonField("type", "series")
            append(',')
            appendJsonField("id", contentId)
            append(',')
            append("\"season\":").append(season)
            append(',')
            append("\"episode\":").append(episode)
            if (played && item.watchedAt > 0L) {
                append(',')
                append("\"watched_at\":").append(item.watchedAt)
            }
            append('}')
        }
    }
    return buildString {
        append('{')
        appendJsonField("action", action)
        append(',')
        appendJsonField("type", "movie")
        append(',')
        appendJsonField("id", contentId)
        if (played && item.watchedAt > 0L) {
            append(',')
            append("\"watched_at\":").append(item.watchedAt)
        }
        append('}')
    }
}

private fun StringBuilder.appendJsonField(name: String, value: String) {
    append('"').append(name).append("\":\"")
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
    append('"')
}

private val MEDIA_TRAIL_URL = Regex(
    """^(https?://[^/]+)/nuvio/([^/]+)(?:/manifest(?:\.json)?)?$""",
    RegexOption.IGNORE_CASE,
)
