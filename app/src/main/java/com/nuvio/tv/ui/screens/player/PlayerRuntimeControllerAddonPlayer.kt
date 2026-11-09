package com.nuvio.tv.ui.screens.player

import android.util.Log
import com.nuvio.tv.core.addon.AddonPlayerRemoteCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    // Control poll must stay alive while paused so Yamtrack resume still arrives.
    ensureAddonPlayerControlSession()
}

internal fun PlayerRuntimeController.stopAddonPlayerProgressTicker() {
    addonPlayerProgressJob?.cancel()
    addonPlayerProgressJob = null
}

internal fun PlayerRuntimeController.ensureAddonPlayerControlSession() {
    if (!addonPlayerEventsEnabled) return
    if (addonPlayerControlJob?.isActive != true) {
        startAddonPlayerControlPoller()
    }
    if (addonPlayerCommandJob?.isActive != true) {
        startAddonPlayerCommandCollector()
    }
}

internal fun PlayerRuntimeController.stopAddonPlayerControlSession() {
    stopAddonPlayerProgressTicker()
    stopAddonPlayerControlPoller()
    stopAddonPlayerCommandCollector()
}

internal fun PlayerRuntimeController.startAddonPlayerControlPoller() {
    addonPlayerControlJob?.cancel()
    if (!addonPlayerEventsEnabled) return
    addonPlayerControlJob = scope.launch {
        while (isActive) {
            if (addonPlayerEventsEnabled) {
                addonPlayerEventDispatcher.pollRemoteCommands(enabled = true)
            }
            delay(2_000L)
        }
    }
}

internal fun PlayerRuntimeController.stopAddonPlayerControlPoller() {
    addonPlayerControlJob?.cancel()
    addonPlayerControlJob = null
}

internal fun PlayerRuntimeController.startAddonPlayerCommandCollector() {
    addonPlayerCommandJob?.cancel()
    addonPlayerCommandJob = scope.launch {
        AddonPlayerRemoteCommands.commands.collect { command ->
            applyAddonPlayerRemoteCommand(command)
        }
    }
}

internal fun PlayerRuntimeController.stopAddonPlayerCommandCollector() {
    addonPlayerCommandJob?.cancel()
    addonPlayerCommandJob = null
}

internal suspend fun PlayerRuntimeController.applyAddonPlayerRemoteCommand(command: String) {
    Log.i(PlayerRuntimeController.TAG, "addon remote command=$command")
    withContext(Dispatchers.Main) {
        when (command) {
            "pause" -> {
                runCatching {
                    _exoPlayer?.playWhenReady = false
                    setPlaybackPaused(true)
                    userPausedManually = true
                }
                emitAddonPlayerEvent("pause")
            }
            "resume", "play", "start" -> {
                runCatching {
                    userPausedManually = false
                    _exoPlayer?.playWhenReady = true
                    setPlaybackPaused(false)
                    startAddonPlayerProgressTicker()
                }
                emitAddonPlayerEvent("start")
            }
        }
    }
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
