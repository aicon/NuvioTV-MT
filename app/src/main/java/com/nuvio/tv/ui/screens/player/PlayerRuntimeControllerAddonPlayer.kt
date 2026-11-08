package com.nuvio.tv.ui.screens.player

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal fun PlayerRuntimeController.emitAddonPlayerEvent(action: String) {
    val videoId = resolveAddonPlayerVideoId() ?: run {
        Log.d(PlayerRuntimeController.TAG, "addon player event skipped action=$action reason=no_imdb_id")
        return
    }
    val positionMs = currentPlaybackPositionMs()?.coerceAtLeast(0L) ?: _uiState.value.currentPosition
    val durationMs = currentPlaybackDurationMs().coerceAtLeast(0L).takeIf { it > 0L }
        ?: _uiState.value.duration.takeIf { it > 0L }
    addonPlayerEventDispatcher.emit(
        action = action,
        mediaType = contentType ?: "movie",
        videoId = videoId,
        currentTimeMs = positionMs,
        durationMs = durationMs,
        enabled = addonPlayerEventsEnabled
    )
}

internal fun PlayerRuntimeController.startAddonPlayerProgressTicker() {
    addonPlayerProgressJob?.cancel()
    if (!addonPlayerEventsEnabled) return
    val intervalMs = addonPlayerProgressIntervalSeconds.coerceIn(5, 300) * 1_000L
    addonPlayerProgressJob = scope.launch {
        while (isActive) {
            delay(intervalMs)
            if (!addonPlayerEventsEnabled) continue
            if (_uiState.value.isPaused || _uiState.value.isLoading) continue
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
        .firstOrNull { it.startsWith("tt") }
        ?.substringBefore(':')
        ?.takeIf { it.matches(Regex("""tt\d+""")) }
    if (direct != null) return direct

    val type = contentType ?: "movie"
    val id = contentId ?: return null
    val metaImdb = metaRepository.getCachedMeta(type, id)?.imdbId
        ?: metaRepository.getCachedMeta(type, id.substringBefore(':'))?.imdbId
    return metaImdb
        ?.trim()
        ?.substringBefore(':')
        ?.takeIf { it.startsWith("tt") && it.matches(Regex("""tt\d+""")) }
}
