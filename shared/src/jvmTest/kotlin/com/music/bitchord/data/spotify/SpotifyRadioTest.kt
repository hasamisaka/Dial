package com.music.bitchord.data.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpotifyRadioTest {

    private fun pick(id: String, title: String = id, artist: String = "artist $id") = SpotifyRadio.Pick(id, title, artist)

    @Test
    fun readsFullFlatAndMetadataEntries() {
        val body = """
            {"tracks":[
              {"uri":"spotify:track:a","name":"Yellow","artists":[{"name":"Coldplay"},{"name":"Someone"}]},
              {"id":"b","name":"Clocks","artistName":"Coldplay"},
              {"uri":"spotify:track:c","metadata":{"title":"Fix You","artist_name":"Coldplay"}},
              {"uri":"spotify:track:d","uid":"x"},
              {"uri":"spotify:track:a","name":"Yellow","artists":[{"name":"Coldplay"}]},
              {"uri":"spotify:episode:e","name":"A podcast"}
            ]}
        """.trimIndent()
        assertEquals(
            listOf(
                SpotifyRadio.Pick("a", "Yellow", "Coldplay, Someone"),
                SpotifyRadio.Pick("b", "Clocks", "Coldplay"),
                SpotifyRadio.Pick("c", "Fix You", "Coldplay"),
                SpotifyRadio.Pick("d", "", ""),
            ),
            SpotifyRadio.parse(body),
        )
    }

    @Test
    fun readsItemsWhenThereAreNoTracks() {
        assertEquals(listOf("a"), SpotifyRadio.parse("""{"tracks":[],"items":[{"id":"a","name":"x"}]}""").map { it.id })
    }

    @Test
    fun readsThePlaylistExtenderAnswer() {
        val body = """
            {"recommendedTracks":[
              {"id":"a","name":"Yellow","artists":[{"id":"x","name":"Coldplay"}],"album":{"id":"y","name":"Parachutes"},"score":0.9},
              {"id":"b","name":"Clocks","artists":[{"id":"x","name":"Coldplay"}]}
            ]}
        """.trimIndent()
        assertEquals(
            listOf(SpotifyRadio.Pick("a", "Yellow", "Coldplay"), SpotifyRadio.Pick("b", "Clocks", "Coldplay")),
            SpotifyRadio.parse(body),
        )
    }

    @Test
    fun theMixIsTheFirstPlaylistInTheAnswer() {
        val body = """{"mediaItems":[{"uri":"spotify:track:a"},{"uri":"spotify:playlist:37i9dQZF1E8abc"},{"uri":"spotify:playlist:other"}]}"""
        assertEquals("37i9dQZF1E8abc", SpotifyRadio.mixPlaylistId(body))
        assertNull(SpotifyRadio.mixPlaylistId("""{"mediaItems":[{"uri":"spotify:track:a"}]}"""))
        assertNull(SpotifyRadio.mixPlaylistId("""{"error":{"status":404}}"""))
        assertNull(SpotifyRadio.mixPlaylistId("<html>"))
    }

    @Test
    fun theExtenderBodyNamesThePlaylistAndTheSkips() {
        assertEquals(
            """{"playlistURI":"spotify:playlist:p","trackSkipIDs":["a","b"],"numResults":100}""",
            SpotifyRadio.extenderBody("p", skip = listOf("a", "b", "a"), count = 500),
        )
    }

    @Test
    fun anAnswerThatIsNotAStationIsEmpty() {
        assertTrue(SpotifyRadio.parse("<html>").isEmpty())
        assertTrue(SpotifyRadio.parse("""{"error":{"status":429}}""").isEmpty())
    }

    @Test
    fun namesAreFilledInAndTheNamelessDropped() {
        val picks = listOf(pick("a"), SpotifyRadio.Pick("b", "", ""), SpotifyRadio.Pick("c", "", ""))
        val named = listOf(pick("b", "Clocks", "Coldplay"))
        assertEquals(listOf(pick("a"), pick("b", "Clocks", "Coldplay")), SpotifyRadio.withNames(picks, named))
    }

    @Test
    fun interleaveTakesFromEachStationInTurn() {
        val out = SpotifyRadio.interleave(
            listOf(listOf(pick("a1"), pick("a2"), pick("a3")), listOf(pick("b1")), listOf(pick("c1"), pick("c2"))),
            limit = 5,
        )
        assertEquals(listOf("a1", "b1", "c1", "a2", "c2"), out.map { it.id })
    }

    @Test
    fun interleaveLeavesOutRepeatsAndTheSeeds() {
        val out = SpotifyRadio.interleave(
            listOf(
                listOf(pick("a", "Yellow", "Coldplay"), pick("b", "Clocks", "Coldplay")),
                listOf(pick("c", "Yellow - Remastered 2011", "Coldplay, Someone"), pick("d", "Fix You (Live)", "Coldplay")),
            ),
            limit = 10,
            exclude = setOf(SpotifyRadio.songKey("Fix You", "Coldplay")),
        )
        assertEquals(listOf("a", "b"), out.map { it.id })
    }

    @Test
    fun urlsNameTheSeed() {
        assertEquals(
            "https://spclient.wg.spotify.com/radio-apollo/v3/stations/spotify:track:abc?count=20&autoplay=true",
            SpotifyRadio.stationUrl("abc", 20),
        )
        assertEquals(
            "https://spclient.wg.spotify.com/radio-apollo/v3/stations/spotify:artist:xyz?count=12&autoplay=true",
            SpotifyRadio.seedStationUrl("spotify:artist:xyz", 12),
        )
        assertEquals(
            "https://spclient.wg.spotify.com/inspiredby-mix/v2/seed_to_playlist/spotify:track:abc?response-format=json",
            SpotifyRadio.mixUrl("abc"),
        )
        assertEquals("https://spclient.wg.spotify.com/playlistextender/extendp/", SpotifyRadio.EXTENDER)
        assertEquals("https://api.spotify.com/v1/tracks?ids=a,b", SpotifyRadio.tracksUrl(listOf("a", "b")))
    }
}
