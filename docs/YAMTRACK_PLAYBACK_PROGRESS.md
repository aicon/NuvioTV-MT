# Yamtrack playback progress gap

Copied for the product fork. Source of truth also lives in
`Nuvio-Yamtrack-Scrobbler/docs/YAMTRACK_PLAYBACK_PROGRESS.md`.

**Do not claim live percent sync as complete** until Yamtrack displays
`playback_position_ms` / duration (or equivalent) in its UI.

This fork emits `progress` ticks to player-resource addons; the scrobbler
logs them. Completion still uses qualifying `stop` → watched write only.
