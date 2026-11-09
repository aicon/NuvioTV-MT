package com.nuvio.tv.core.addon

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One-shot pause/resume commands forwarded from Yamtrack via the scrobbler
 * `/playback-control.json` poll. Kept as a process-local bus so the dispatcher
 * does not need a PlayerRuntimeController reference.
 */
object AddonPlayerRemoteCommands {
    private val _commands = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val commands: SharedFlow<String> = _commands.asSharedFlow()

    fun offer(command: String) {
        val normalized = command.trim().lowercase()
        if (normalized !in setOf("pause", "resume", "play", "start")) return
        _commands.tryEmit(normalized)
    }
}
