package com.nuvio.tv.core.addon

import com.nuvio.tv.domain.model.WatchedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTrailWatchedSyncTest {

    @Test
    fun parseTokenizedManifestUrl() {
        val target = parseMediaTrailWatchedEndpoint(
            "https://mt.example/nuvio/abcToken123/manifest.json",
        )
        assertEquals("https://mt.example/nuvio/watched.json", target?.watchedUrl)
        assertEquals("abcToken123", target?.bearerToken)
        assertTrue(target?.watchedUrl?.contains("abcToken123") != true)
    }

    @Test
    fun parseBaseUrlWithoutManifest() {
        val target = parseMediaTrailWatchedEndpoint(
            "https://mt.example/nuvio/tok",
        )
        assertEquals("https://mt.example/nuvio/watched.json", target?.watchedUrl)
        assertEquals("tok", target?.bearerToken)
    }

    @Test
    fun rejectUntokenedLegacyManifest() {
        assertNull(parseMediaTrailWatchedEndpoint("https://mt.example/nuvio/manifest.json"))
        assertNull(parseMediaTrailWatchedEndpoint("https://other.example/manifest.json"))
    }

    @Test
    fun moviePayload() {
        val json = watchedPayloadJson(
            WatchedItem(
                contentId = "tt0137523",
                contentType = "movie",
                title = "Fight Club",
                watchedAt = 1_700_000_000_000L,
            ),
            played = true,
        )
        assertEquals(
            """{"action":"watched","type":"movie","id":"tt0137523","watched_at":1700000000000}""",
            json,
        )
    }

    @Test
    fun episodeUnwatchedPayload() {
        val json = watchedPayloadJson(
            WatchedItem(
                contentId = "tt0903747",
                contentType = "series",
                title = "Breaking Bad",
                season = 1,
                episode = 2,
                watchedAt = 0L,
            ),
            played = false,
        )
        assertEquals(
            """{"action":"unwatched","type":"series","id":"tt0903747","season":1,"episode":2}""",
            json,
        )
    }

    @Test
    fun seriesWithoutSeasonIsSkipped() {
        assertNull(
            watchedPayloadJson(
                WatchedItem(
                    contentId = "tt0903747",
                    contentType = "series",
                    title = "Breaking Bad",
                    watchedAt = 1L,
                ),
                played = true,
            ),
        )
    }
}
