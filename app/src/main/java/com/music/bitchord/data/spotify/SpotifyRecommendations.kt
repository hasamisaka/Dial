package com.music.bitchord.data.spotify

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.canvas.SpotifyToken
import com.music.bitchord.data.canvas.canvasGetWithStatus
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.SpotifyDialSeeds
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.data.stats.ListeningStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Spotify's recommendations, played from YouTube Music.
 *
 * A song is found on Spotify, Spotify's station for it is read with the web
 * player's own token (the `sp_dc` session Canvas and the library already
 * use), and each track the station names is matched back to a YouTube Music
 * song. Spotify only chooses; nothing is streamed from it.
 *
 * Every step is best-effort: no session, no match for the seed, an empty
 * station or a failed request all answer with an empty list, and the callers
 * keep to YouTube Music's own radio when they get one.
 */
object SpotifyRecommendations {

    private const val TAG = "SpotifyRecs"

    /** Searches run at once when matching a station back to YouTube Music. */
    private const val CONCURRENT_MATCHES = 4

    /** Most station tracks matched for one request; each costs a search. */
    private const val MAX_MATCHES = 24

    /** Recently played songs the speed dial's picks are seeded from. */
    private const val DIAL_SEEDS = 5
    private const val DIAL_TRACKS_PER_SEED = 12
    private const val DIAL_TIMEOUT_MS = 25_000L

    /** Top songs and top artists read for each span, for the dial's top seeds. */
    private const val TOP_PER_RANGE = 10
    private const val TOP_POOL_TTL_MS = 6L * 60 * 60 * 1000

    /** Whether a Spotify account is signed in. */
    fun connected(): Boolean = AppSettings.spotifySpdcToken.value.isNotBlank()

    /** Whether radio and AutoPlay should ask Spotify first. */
    fun active(): Boolean = AppSettings.spotifyAlgorithm.value && connected()

    /** YouTube Music songs for what Spotify would play after [title] by [artist]; empty when it can't say. */
    suspend fun songsFor(title: String, artist: String, limit: Int = MAX_MATCHES): List<Song> = try {
        match(picksFor(title, artist, limit))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.w(TAG, "Spotify recommendations unavailable: ${error.message}")
        emptyList()
    }

    /**
     * The speed dial's Spotify tiles: what Spotify recommends after a few
     * seeds, a few from each so no one seed fills the dial. With [seeds] at
     * [SpotifyDialSeeds.PLAYS] the seeds are the songs played here lately;
     * at [SpotifyDialSeeds.TOP] they are the account's top songs and artists
     * on Spotify, moved on by one deal each [round] (see [SpotifyTopSeeds]).
     * When the top lists can't be read, the songs played here are used.
     * Empty when there is nothing to seed from or Spotify can't be reached.
     */
    suspend fun dialPicks(seeds: SpotifyDialSeeds = SpotifyDialSeeds.PLAYS, round: Int = 0): List<ShelfItem> = try {
        withTimeoutOrNull(DIAL_TIMEOUT_MS) {
            val top = if (seeds == SpotifyDialSeeds.TOP) {
                SpotifyTopSeeds.deal(topSeedPool(), DIAL_SEEDS, round)
            } else {
                emptyList()
            }
            val (stations, exclude) = if (top.isNotEmpty()) {
                val stations = coroutineScope {
                    top.map { seed -> async { picksForSeed(seed, DIAL_TRACKS_PER_SEED) } }.awaitAll()
                }
                stations to top.filter { it.isTrack }.mapTo(HashSet()) { SpotifyRadio.songKey(it.title, it.artist) }
            } else {
                val played = ListeningStats.recentTracks(DIAL_SEEDS * 2)
                    .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
                    .take(DIAL_SEEDS)
                val stations = coroutineScope {
                    played.map { seed -> async { picksFor(seed.title, seed.artist, DIAL_TRACKS_PER_SEED) } }.awaitAll()
                }
                stations to played.mapTo(HashSet()) { SpotifyRadio.songKey(it.title, it.artist) }
            }
            val picks = SpotifyRadio.interleave(stations, limit = MAX_MATCHES, exclude = exclude)
            match(picks).map { song ->
                ShelfItem(
                    title = song.title,
                    subtitle = song.artist,
                    thumbnailUrl = song.thumbnailUrl,
                    videoId = song.videoId,
                    browseId = null,
                )
            }
        }.orEmpty()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.w(TAG, "Spotify dial picks unavailable: ${error.message}")
        emptyList()
    }

    @Volatile private var topPool: List<SpotifyTopSeeds.Seed> = emptyList()
    @Volatile private var topPoolAt = 0L
    @Volatile private var topPoolAccount = ""

    /**
     * The account's top songs and artists over the three spans, dealt into
     * one list. Read again after [TOP_POOL_TTL_MS] or a change of account;
     * these lists move slowly, so each refresh of the dial doesn't ask.
     * Empty when none of the three can be read.
     */
    private suspend fun topSeedPool(): List<SpotifyTopSeeds.Seed> {
        val account = AppSettings.spotifySpdcToken.value
        val now = System.currentTimeMillis()
        if (topPool.isNotEmpty() && account == topPoolAccount && now - topPoolAt < TOP_POOL_TTL_MS) return topPool
        val lists = coroutineScope {
            SpotifyTopRange.entries.map { range ->
                async {
                    runCatching { SpotifyLibrary.topContent(range, TOP_PER_RANGE) }
                        .onFailure { error ->
                            if (error is CancellationException) throw error
                            Log.w(TAG, "Spotify top ${range.name.lowercase()} unavailable: ${error.message}")
                        }
                        .getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
        val pool = SpotifyTopSeeds.pool(lists)
        Log.d(TAG, "Spotify top seeds: ${pool.size} from ${lists.size} spans")
        if (pool.isNotEmpty()) {
            topPool = pool
            topPoolAt = now
            topPoolAccount = account
        }
        return pool
    }

    /** The tracks of Spotify's station for a top song or artist, named. */
    private suspend fun picksForSeed(seed: SpotifyTopSeeds.Seed, limit: Int): List<SpotifyRadio.Pick> {
        val token = SpotifyToken.accessToken() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val headers = stationHeaders(token)
            var picks = fetch(SpotifyRadio.seedStationUrl(seed.uri, limit), headers, "station")
            if (picks.isEmpty() && seed.isTrack) {
                picks = fetch(SpotifyRadio.recommendationsUrl(seed.id, limit), headers, "recommendations")
            }
            named(picks.filterNot { it.id == seed.id }, headers)
                .also { Log.d(TAG, "${it.size} Spotify picks after top ${if (seed.isTrack) "song" else "artist"} '${seed.title.ifBlank { seed.artist }}'") }
        }
    }

    private fun stationHeaders(token: String): Map<String, String> = SpotifyLikeSync.headers(token) + mapOf(
        "App-platform" to "WebPlayer",
        "Origin" to "https://open.spotify.com",
        "Referer" to "https://open.spotify.com/",
    )

    /** The tracks of Spotify's station for one song, named. */
    private suspend fun picksFor(title: String, artist: String, limit: Int): List<SpotifyRadio.Pick> {
        if (title.isBlank() || artist.isBlank()) return emptyList()
        val token = SpotifyToken.accessToken() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val seed = SpotifyLikeSync.findTrack(title, artist, token)?.let(SpotifyLikeMirror::trackId)
            if (seed == null) {
                Log.d(TAG, "no Spotify match for '$title' by '$artist'")
                return@withContext emptyList()
            }
            val headers = stationHeaders(token)
            val picks = fetch(SpotifyRadio.stationUrl(seed, limit), headers, "station")
                .ifEmpty { fetch(SpotifyRadio.recommendationsUrl(seed, limit), headers, "recommendations") }
                .filterNot { it.id == seed }
            named(picks, headers).also { Log.d(TAG, "${it.size} Spotify picks after '$title'") }
        }
    }

    private fun fetch(url: String, headers: Map<String, String>, what: String): List<SpotifyRadio.Pick> {
        val (code, body) = canvasGetWithStatus(url, headers)
        if (body == null) {
            Log.w(TAG, "$what request failed, http $code")
            return emptyList()
        }
        return SpotifyRadio.parse(body)
    }

    /** A station may name its tracks by id alone; the names are asked for in one go. */
    private fun named(picks: List<SpotifyRadio.Pick>, headers: Map<String, String>): List<SpotifyRadio.Pick> {
        val nameless = picks.filterNot { it.named }.map { it.id }
        if (nameless.isEmpty()) return picks
        val names = nameless.chunked(SpotifyRadio.TRACKS_PER_REQUEST)
            .flatMap { fetch(SpotifyRadio.tracksUrl(it), headers, "track names") }
        return SpotifyRadio.withNames(picks, names)
    }

    /** Each pick's song on YouTube Music, in Spotify's order; what has no confident match is dropped. */
    private suspend fun match(picks: List<SpotifyRadio.Pick>): List<Song> = coroutineScope {
        val gate = Semaphore(CONCURRENT_MATCHES)
        picks.take(MAX_MATCHES).map { pick ->
            async {
                gate.withPermit {
                    val candidates = YtMusicRepository.search("${pick.title} ${pick.artist}", SearchFilter.SONGS)
                        .getOrNull()
                        ?.filterIsInstance<SearchResult.Track>()
                        ?.map { it.song }
                        .orEmpty()
                    TrackMatcher.best(candidates, TrackMatcher.Target(title = pick.title, artist = pick.artist))
                }
            }
        }.awaitAll().filterNotNull().distinctBy { it.videoId }
    }
}
