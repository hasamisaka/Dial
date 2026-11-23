package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Spotify's own lyrics, read with the listener's own Spotify sign-in.
 *
 * This is the `color-lyrics` route the web player uses for its lyrics panel,
 * asked with the same web player token Canvas, like sync and the Spotify
 * algorithm already use. [LyricsSource.PAXSENIX_SPOTIFY] gets the same
 * catalogue through a third party with an API key; this goes direct, and that
 * one stays behind it in the order as the fallback.
 *
 * The route and its answer come from SpotAPI (github.com/Aran404/SpotAPI,
 * GPL-3.0, `spotapi/lyrics.py` and `spotapi/models/lyrics.py`).
 *
 * Shared code has no Spotify session of its own: the token lives with the
 * platform that signed in. The platform sets [account] at start; with none set,
 * or nobody signed in, this source answers nothing without a request.
 */
object SpotifyLyrics {

    private const val SPCLIENT = "https://spclient.wg.spotify.com"

    /** What the platform gives this source: the signed-in session, and a way to find the track. */
    interface Account {
        /**
         * The headers that carry the signed-in session (bearer token, client
         * token, web player headers), or null when no one is signed in or no
         * token can be had in time.
         */
        suspend fun headers(): Map<String, String>?

        /** Spotify's id for this song (the part after `spotify:track:`), or null. */
        suspend fun trackId(title: String, artist: String, durationMs: Long): String?
    }

    @Volatile
    var account: Account? = null

    suspend fun lyrics(title: String, artist: String, durationMs: Long): List<LyricLine>? {
        val account = account ?: return null
        val headers = account.headers() ?: return null
        val id = account.trackId(title, artist, durationMs) ?: return null
        return withContext(Dispatchers.IO) {
            lyricsGetWithHeaders(url(id), headers + ("Accept" to "application/json"))
        }?.let(::parse)
    }

    fun url(trackId: String): String =
        "$SPCLIENT/color-lyrics/v2/track/$trackId?format=json&vocalRemoval=false&market=from_token"

    /**
     * The lines of a `color-lyrics` answer.
     *
     * `syncType` is `LINE_SYNCED`, `SYLLABLE_SYNCED` or `UNSYNCED`. Every time
     * is a string of milliseconds; an unsynced answer stamps each line `0`.
     * A line of only `♪` marks an instrumental break. A syllable is a start
     * time and a count of characters, taken from the line's text in turn.
     */
    fun parse(raw: String): List<LyricLine>? {
        val root = runCatching { lyricsJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return null
        val lyrics = root["lyrics"] as? JsonObject ?: root
        val rows = (lyrics["lines"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return null
        val synced = lyrics.text("syncType")?.uppercase() != "UNSYNCED"

        val lines = rows.mapIndexedNotNull { index, row ->
            val text = row.text("words")?.trim() ?: return@mapIndexedNotNull null
            if (!synced) return@mapIndexedNotNull LyricLine(0L, text)
            val start = row.millis("startTimeMs") ?: return@mapIndexedNotNull null
            // A break: drawn as the blank line every other source uses for one.
            if (text.isEmpty() || text.all { it == '♪' || it.isWhitespace() }) {
                return@mapIndexedNotNull LyricLine(start, "")
            }
            val next = rows.getOrNull(index + 1)?.millis("startTimeMs")?.takeIf { it >= start }
            val end = row.millis("endTimeMs")?.takeIf { it > start } ?: next
            val words = syllableWords(row, start, end)
            LyricLine(
                timeMs = start,
                // Kept as written: the words are found in it in turn, and
                // joined with spaces a Japanese line would gain them.
                text = text,
                words = words,
                sungUntilMs = end,
            )
        }
        if (lines.none { it.text.isNotBlank() }) return null
        if (!synced) return lines
        val sorted = lines.sortedBy { it.timeMs }
        // Nothing stands for the intro, so give the run-up its own break.
        return if (sorted.first().timeMs >= MIN_GAP_MS && !sorted.first().isGap) {
            listOf(LyricLine(0L, "")) + sorted
        } else {
            sorted
        }
    }

    /**
     * The words of a syllable-synced line, or none when the line has no
     * syllables or their character counts do not add up to its text. Then
     * the line is still used, timed as a whole.
     */
    private fun syllableWords(row: JsonObject, lineStart: Long, lineEnd: Long?): List<LyricWord> {
        val raw = row.text("words") ?: return emptyList()
        val syllables = (row["syllables"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        if (syllables.isEmpty()) return emptyList()
        val parts = syllables.map { syllable ->
            val start = syllable.millis("startTimeMs") ?: return emptyList()
            val chars = syllable.millis("numChars")?.toInt() ?: return emptyList()
            if (chars < 0) return emptyList()
            start to chars
        }
        if (parts.sumOf { it.second } != raw.length) return emptyList()
        var offset = 0
        val runs = parts.mapIndexed { index, (start, chars) ->
            val piece = raw.substring(offset, offset + chars)
            offset += chars
            val end = parts.getOrNull(index + 1)?.first ?: lineEnd ?: (start + LAST_SYLLABLE_MS)
            TimedRun(maxOf(start, lineStart), maxOf(start, end), piece)
        }
        return wordsFromRuns(runs)
    }

    /** How long the last syllable of a line with no known end is held. */
    private const val LAST_SYLLABLE_MS = 800L

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    /** A number Spotify writes as a string ("1230") or, sometimes, as a number. */
    private fun JsonObject.millis(key: String): Long? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
}
