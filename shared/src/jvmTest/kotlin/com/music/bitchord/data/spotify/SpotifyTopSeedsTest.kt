package com.music.bitchord.data.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpotifyTopSeedsTest {
    private fun track(id: String) = SpotifyTrack(id, "Song $id", "Artist $id", null, 0, null)
    private fun artist(id: String) = SpotifyArtist(id, "Artist $id", null)

    private val short = SpotifyTopContent(listOf(track("s1"), track("s2")), listOf(artist("A1"), artist("A2")))
    private val mid = SpotifyTopContent(listOf(track("m1"), track("s1")), listOf(artist("A1"), artist("A3")))
    private val long = SpotifyTopContent(listOf(track("l1")), emptyList())

    @Test
    fun poolTakesEachSpanInTurnAndKeepsRepeatsOnce() {
        val pool = SpotifyTopSeeds.pool(listOf(short, mid, long))
        assertEquals(
            listOf(
                "spotify:track:s1", "spotify:artist:A1", "spotify:track:m1", "spotify:track:l1",
                "spotify:track:s2", "spotify:artist:A2", "spotify:artist:A3",
            ),
            pool.map { it.uri },
        )
        assertTrue(pool.first().isTrack)
        assertEquals("Song s1", pool.first().title)
        assertEquals("", pool[1].title)
        assertEquals("Artist A1", pool[1].artist)
        assertEquals("A1", pool[1].id)
    }

    @Test
    fun eachRoundDealsTheNextSeedsAndWrapsAround() {
        val pool = SpotifyTopSeeds.pool(listOf(short, mid, long))
        assertEquals(pool.take(3), SpotifyTopSeeds.deal(pool, 3, round = 0))
        assertEquals(pool.subList(3, 6), SpotifyTopSeeds.deal(pool, 3, round = 1))
        assertEquals(listOf(pool[6], pool[0], pool[1]), SpotifyTopSeeds.deal(pool, 3, round = 2))
        assertEquals(pool, SpotifyTopSeeds.deal(pool, 10, round = 4))
        assertEquals(emptyList(), SpotifyTopSeeds.deal(emptyList(), 5, round = 1))
    }
}
