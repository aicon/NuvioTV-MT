package com.nuvio.tv.ui.screens.player

import android.util.Log
import com.nuvio.tv.data.mediaserver.ServerItemRef
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal fun PlayerRuntimeController.emitAddonPlayerEvent(action: String) {
    val videoId = resolveAddonPlayerVideoId() ?: run {
        Log.d(PlayerRuntimeController.TAG, "addon player event skipped action=$action reason=no_imdb_id")
        return
    }
    val positionMs = currentPlaybackPositionMs()?.coerceAtLeast(0L)
        ?: _playbackTimeline.value.currentPosition
    val durationMs = currentPlaybackDurationMs().coerceAtLeast(0L).takeIf { it > 0L }
        ?: _playbackTimeline.value.duration.takeIf { it > 0L }
    addonPlayerEventDispatcher.emit(
        action = action,
        mediaType = contentType ?: "movie",
        videoId = videoId,
        currentTimeMs = positionMs,
        durationMs = durationMs,
        enabled = addonPlayerEventsEnabled
    )
}

/** Playback-driven: must not wait for Trakt/SIMKL scrobble success. */
internal fun PlayerRuntimeController.notifyAddonPlayerStarted() {
    emitAddonPlayerEvent("start")
    startAddonPlayerProgressTicker()
}

internal fun PlayerRuntimeController.notifyAddonPlayerPaused() {
    stopAddonPlayerProgressTicker()
    emitAddonPlayerEvent("pause")
}

internal fun PlayerRuntimeController.notifyAddonPlayerStopped() {
    stopAddonPlayerProgressTicker()
    emitAddonPlayerEvent("stop")
}

internal fun PlayerRuntimeController.startAddonPlayerProgressTicker() {
    addonPlayerProgressJob?.cancel()
    if (!addonPlayerEventsEnabled) return
    val intervalMs = addonPlayerProgressIntervalSeconds.coerceIn(5, 300) * 1_000L
    addonPlayerProgressJob = scope.launch {
        while (isActive) {
            delay(intervalMs)
            if (!addonPlayerEventsEnabled) continue
            if (!isPlaybackCurrentlyPlaying() || _uiState.value.isBuffering) continue
            emitAddonPlayerEvent("progress")
        }
    }
}

internal fun PlayerRuntimeController.stopAddonPlayerProgressTicker() {
    addonPlayerProgressJob?.cancel()
    addonPlayerProgressJob = null
}

internal fun PlayerRuntimeController.resolveAddonPlayerVideoId(): String? {
    val type = contentType?.lowercase()
    val season = currentSeason
    val episode = currentEpisode
    val imdb = resolveImdbIdForAddonPlayer() ?: return null
    return if (
        (type == "series" || type == "tv" || type == "show" || type == "shows") &&
        season != null &&
        episode != null
    ) {
        "$imdb:$season:$episode"
    } else {
        imdb
    }
}

private fun PlayerRuntimeController.resolveImdbIdForAddonPlayer(): String? {
    val direct = listOfNotNull(contentId, currentVideoId)
        .map { it.trim() }
        .firstNotNullOfOrNull { candidate ->
            candidate.substringBefore(':')
                .takeIf { it.matches(IMDB_ID_REGEX) }
        }
    if (direct != null) return direct

    val scrobbleImdb = currentScrobbleItem?.ids?.imdb
        ?.trim()
        ?.substringBefore(':')
        ?.takeIf { it.matches(IMDB_ID_REGEX) }
    if (scrobbleImdb != null) return scrobbleImdb

    val rawId = contentId ?: return null
    val type = contentType ?: "movie"
    if (ServerItemRef.isServerId(rawId)) {
        serverImdbId(rawId)
            ?.trim()
            ?.substringBefore(':')
            ?.takeIf { it.matches(IMDB_ID_REGEX) }
            ?.let { return it }
    }

    val metaImdb = metaRepository.getCachedMeta(type, rawId)?.imdbId
        ?: metaRepository.getCachedMeta(type, rawId.substringBefore(':'))?.imdbId
    return metaImdb
        ?.trim()
        ?.substringBefore(':')
        ?.takeIf { it.matches(IMDB_ID_REGEX) }
}

private val IMDB_ID_REGEX = Regex("""tt\d+""")
