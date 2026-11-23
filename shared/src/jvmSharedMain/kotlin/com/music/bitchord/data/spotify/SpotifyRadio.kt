package com.music.bitchord.data.spotify

import com.music.bitchord.data.canvas.splitArtists
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Spotify's own "what plays next": the station it builds from one track, which is what its apps
 * queue when an album or playlist runs out.
 *
 * This is the request/response half only, free of any HTTP client so it can be tested without a
 * network. The station endpoint is the one the web player's autoplay reads. When the station comes
 * back empty, the "inspired by" mix is the fallback: Spotify names a radio playlist for the song,
 * and its songs are read. When they can't be read, the playlist extender is asked what it would add
 * to that playlist. (The public `/v1/recommendations` endpoint was the fallback before; Spotify has
 * closed it.) All are asked with the web player's own token, and what they answer is only a list of
 * songs: each one is matched back onto YouTube Music before it is played.
 *
 * The mix and extender routes are the ones SpotAPI (github.com/Aran404/SpotAPI, GPL-3.0) uses.
 */
object SpotifyRadio {

    private const val SPCLIENT = "https://spclient.wg.spotify.com"
    private const val STATIONS = "$SPCLIENT/radio-apollo/v3/stations"
    private const val MIXES = "$SPCLIENT/inspiredby-mix/v2/seed_to_playlist"
    const val EXTENDER = "$SPCLIENT/playlistextender/extendp/"
    private const val TRACKS = "https://api.spotify.com/v1/tracks"

    /** The most ids `/v1/tracks` takes in one request. */
    const val TRACKS_PER_REQUEST = 50

    private const val PLAYLIST_PREFIX = "spotify:playlist:"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** A track a station named. [title] and [artist] are blank when the answer carried only its id. */
    data class Pick(val id: String, val title: String, val artist: String) {
        val named: Boolean get() = title.isNotBlank() && artist.isNotBlank()
    }

    fun stationUrl(trackId: String, count: Int): String =
        "$STATIONS/spotify:track:$trackId?count=${count.coerceIn(1, 100)}&autoplay=true"

    /** Asks which radio playlist Spotify makes from the song (its "inspired by" mix). */
    fun mixUrl(trackId: String): String = "$MIXES/spotify:track:$trackId?response-format=json"

    /**
     * The id of the radio playlist in an answer from [mixUrl], or null. The answer lists
     * `mediaItems`; the first one with a playlist uri is the mix.
     */
    fun mixPlaylistId(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        return (root["mediaItems"] as? JsonArray).orEmpty()
            .firstNotNullOfOrNull { item ->
                (item as? JsonObject)?.text("uri")
                    ?.takeIf { it.startsWith(PLAYLIST_PREFIX) }
                    ?.removePrefix(PLAYLIST_PREFIX)
                    ?.takeIf { it.isNotBlank() }
            }
    }

    /**
     * The body of a POST to [EXTENDER]: the songs Spotify would add to [playlistId], at most
     * [count], with the [skip] track ids left out.
     */
    fun extenderBody(playlistId: String, skip: List<String>, count: Int): String = buildJsonObject {
        put("playlistURI", PLAYLIST_PREFIX + playlistId)
        putJsonArray("trackSkipIDs") { skip.distinct().forEach { add(it) } }
        put("numResults", count.coerceIn(1, 100))
    }.toString()

    /** Asks for the names of [ids], which a station may leave out. */
    fun tracksUrl(ids: List<String>): String = "$TRACKS?ids=${ids.take(TRACKS_PER_REQUEST).joinToString(",")}"

    /**
     * The tracks in an answer from the station, the playlist extender or `/v1/tracks`, in its order
     * and without repeats.
     *
     * The station answers `tracks` and may say `items` instead; the extender answers
     * `recommendedTracks` (full tracks: `id`, `name`, `artists`). A station's entry can be a full track (`name`, `artists`), a flat one
     * (`name`, `artistName`), or only a `uri` with the names under `metadata`, or with none.
     */
    fun parse(body: String): List<Pick> {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyList()
        val entries = listOf("tracks", "items", "recommendedTracks")
            .firstNotNullOfOrNull { key -> (root[key] as? JsonArray)?.takeIf { it.isNotEmpty() } }
            ?: return emptyList()
        return entries.mapNotNull { (it as? JsonObject)?.let(::pick) }.distinctBy { it.id }
    }

    private fun pick(track: JsonObject): Pick? {
        val id = track.text("uri")?.let(SpotifyLikeMirror::trackId)
            ?: track.text("id")?.takeIf { it.isNotBlank() }
            ?: return null
        val metadata = track["metadata"] as? JsonObject
        val title = track.text("name") ?: metadata?.text("title")
        val artists = (track["artists"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.text("name") }
            ?.joinToString(", ")
            ?.takeIf { it.isNotBlank() }
            ?: track.text("artistName")
            ?: metadata?.text("artist_name")
        return Pick(id, title.orEmpty(), artists.orEmpty())
    }

    /** [picks] with the names [named] has for them filled in; what still has none is dropped. */
    fun withNames(picks: List<Pick>, named: List<Pick>): List<Pick> {
        val byId = named.filter { it.named }.associateBy { it.id }
        return picks.mapNotNull { pick -> if (pick.named) pick else byId[pick.id] }
    }

    /**
     * One list out of several stations' answers: the first of each, then the second of each, so
     * no one seed fills the result. Repeats and [exclude]d songs (the seeds themselves, as
     * [songKey]s) are left out.
     */
    fun interleave(stations: List<List<Pick>>, limit: Int, exclude: Set<String> = emptySet()): List<Pick> {
        val out = ArrayList<Pick>()
        val ids = HashSet<String>()
        val songs = HashSet(exclude)
        val longest = stations.maxOfOrNull { it.size } ?: 0
        for (row in 0 until longest) {
            for (station in stations) {
                val pick = station.getOrNull(row) ?: continue
                if (out.size >= limit) return out
                if (ids.add(pick.id) && songs.add(songKey(pick.title, pick.artist))) out += pick
            }
        }
        return out
    }

    /** What makes two entries the same song: its title and lead artist, folded. */
    fun songKey(title: String, artist: String): String =
        SpotifyLikeMirror.coreTitle(title) + "|" + splitArtists(artist).firstOrNull().orEmpty()

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }
}
