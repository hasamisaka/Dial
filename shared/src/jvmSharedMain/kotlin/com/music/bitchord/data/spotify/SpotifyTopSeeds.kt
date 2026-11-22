package com.music.bitchord.data.spotify

/**
 * The seeds of the Spotify speed dial when it starts from the account's top
 * songs and artists on Spotify, not from the songs played in Dial.
 *
 * The idea of reading the profile page's top lists through the web player
 * route comes from SpotAPI (github.com/Aran404/SpotAPI, GPL-3.0). Pure, so
 * the order can be tested without a network.
 */
object SpotifyTopSeeds {

    /** One station to ask Spotify for: a top song, or a top artist. */
    data class Seed(
        /** `spotify:track:…` or `spotify:artist:…`. */
        val uri: String,
        val title: String,
        val artist: String,
    ) {
        val isTrack: Boolean get() = uri.startsWith(TRACK)

        /** The id after the last colon of [uri]. */
        val id: String get() = uri.substringAfterLast(':')
    }

    /**
     * Every seed of [lists] (shortest span first) in turn: the first song of
     * each span and the first artist of each span, then the second of each,
     * and so on. So the newest taste comes first, but the dial also hears
     * the older one. A song or artist on more than one list is kept once,
     * at its first place.
     */
    fun pool(lists: List<SpotifyTopContent>): List<Seed> {
        val rows: List<List<Seed>> = lists.flatMap { content ->
            listOf(
                content.tracks.map { Seed(TRACK + it.id, it.title, it.artist) },
                content.artists.map { Seed(ARTIST + it.id, "", it.name) },
            )
        }
        val out = ArrayList<Seed>()
        val seen = HashSet<String>()
        val longest = rows.maxOfOrNull { it.size } ?: 0
        for (index in 0 until longest) {
            for (row in rows) {
                val seed = row.getOrNull(index) ?: continue
                if (seed.id.isNotBlank() && seen.add(seed.uri)) out += seed
            }
        }
        return out
    }

    /**
     * [count] seeds out of [pool] for one round of the dial. Round 0 (the app
     * start) takes the first ones; each refresh after that moves on by
     * [count] and comes back to the start at the end, so a pull to refresh
     * shows other tiles while the top lists stay the same.
     */
    fun deal(pool: List<Seed>, count: Int, round: Int): List<Seed> {
        if (pool.isEmpty() || count <= 0) return emptyList()
        if (pool.size <= count) return pool
        val start = Math.floorMod(round.toLong() * count, pool.size.toLong()).toInt()
        return List(count) { pool[(start + it) % pool.size] }
    }

    private const val TRACK = "spotify:track:"
    private const val ARTIST = "spotify:artist:"
}
