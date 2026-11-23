package com.music.bitchord.data.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The shared finder of the Spotify web player's persisted-query hashes. */
class SpotifyQueryHashesTest {

    private val hashA = "a".repeat(64)
    private val hashB = "b".repeat(64)
    private val hashC = "c".repeat(64)
    private val base = "https://open.spotifycdn.com/cdn/build/web-player/"

    /** The shape of open.spotify.com: entry scripts and the version in `appServerConfig`. */
    private fun page(version: String, entry: String = "web-player.1a2b3c4d.js"): String {
        val config = java.util.Base64.getEncoder()
            .encodeToString("""{"clientVersion":"$version","other":1}""".toByteArray())
        return """
            <script id="appServerConfig" type="text/plain">$config</script>
            <script src="$base$entry"></script>
            <script src="${base}vendor~web-player.9f8e7d6c.js"></script>
            <script src="https://www.googletagmanager.com/gtm.js"></script>
        """.trimIndent()
    }

    /** The chunk file map as the web player declared it in November 2026, cut to four chunks. */
    private val chunkMap =
        """u.u=e=>""+(({1328:"xpui-pip-mini-player",4406:"xpui-routes-search",1872:"xpui-routes-playlist"})[e]||e)""" +
            """+"."+({1328:"47be1338",4406:"8a9cfb7d",1872:"27f1da16",254:"8e4bc421"})[e]+".js",""" +
            """u.miniCssF=e=>""+(({1328:"xpui-pip-mini-player"})[e]||e)+"."+({1328:"11111111"})[e]+".css""""

    private fun declare(name: String, hash: String, kind: String = "query") = """new i.l("$name","$kind","$hash",null);"""

    @Test
    fun `every declared operation is read, mutations too, the first declaration wins`() {
        val script = declare("canvas", hashA) + declare("addToLibrary", hashB, "mutation") + declare("canvas", hashC)
        assertEquals(mapOf("canvas" to hashA, "addToLibrary" to hashB), SpotifyQueryHashes.operations(script))
        // A longer name that only starts with "canvas" is a different operation.
        assertNull(SpotifyQueryHashes.operations(declare("canvasMeta", hashA))["canvas"])
        assertTrue(SpotifyQueryHashes.operations("no declarations here").isEmpty())
    }

    @Test
    fun `the page gives the entry scripts and the version`() {
        val html = page("1.3.12.512.g47edb99631a6")
        assertEquals(
            listOf("${base}web-player.1a2b3c4d.js", "${base}vendor~web-player.9f8e7d6c.js"),
            SpotifyQueryHashes.entryScripts(html),
        )
        assertEquals("1.3.12.512.g47edb99631a6", SpotifyQueryHashes.version(html))
        // Without the config, the script names stand for the version.
        val bare = """<script src="${base}web-player.1a2b3c4d.js"></script>"""
        assertEquals("${base}web-player.1a2b3c4d.js", SpotifyQueryHashes.version(bare))
    }

    @Test
    fun `lazy chunks are read off the chunk map, likely ones first, no css`() {
        val chunks = SpotifyQueryHashes.chunkScripts(chunkMap, "${base}web-player.1a2b3c4d.js", listOf("search", "playlist"))
        assertEquals(
            listOf(
                "${base}xpui-routes-search.8a9cfb7d.js",
                "${base}xpui-routes-playlist.27f1da16.js",
                "${base}xpui-pip-mini-player.47be1338.js",
                "${base}254.8e4bc421.js",
            ),
            chunks,
        )
    }

    @Test
    fun `one scan finds operations in entry and lazy scripts, and stops when all are found`() {
        val fetched = mutableListOf<String>()
        val finder = SpotifyQueryHashes.Finder(
            fetch = { url ->
                fetched += url
                when (url) {
                    SpotifyQueryHashes.PAGE_URL -> page("v1")
                    "${base}web-player.1a2b3c4d.js" -> chunkMap + declare("canvas", hashA)
                    "${base}xpui-routes-search.8a9cfb7d.js" -> declare("searchDesktop", hashB)
                    else -> ""
                }
            },
            wanted = setOf("canvas", "searchDesktop"),
            firstChunks = listOf("search"),
        )
        assertEquals(hashA, finder.find("canvas"))
        assertEquals(hashB, finder.find("searchDesktop"))
        assertEquals(
            listOf(SpotifyQueryHashes.PAGE_URL, "${base}web-player.1a2b3c4d.js", "${base}vendor~web-player.9f8e7d6c.js", "${base}xpui-routes-search.8a9cfb7d.js"),
            fetched,
            "the other chunks are not read, and the second call is served from memory",
        )
    }

    @Test
    fun `the hashes are kept until the web player version changes`() {
        var now = 1_000L
        var version = "v1"
        var hash = hashA
        val fetched = mutableListOf<String>()
        var saved: String? = null
        val store = object : SpotifyQueryHashes.Finder.Store {
            override fun read() = saved
            override fun write(text: String) { saved = text }
        }
        val fetch: (String) -> String? = { url ->
            fetched += url
            if (url == SpotifyQueryHashes.PAGE_URL) page(version) else declare("canvas", hash)
        }
        val finder = SpotifyQueryHashes.Finder(fetch = fetch, wanted = setOf("canvas"), now = { now }, store = store)
        assertEquals(hashA, finder.find("canvas"))
        val firstScan = fetched.size

        // A later launch reads the file; while the version stays, only the page is read.
        val relaunched = SpotifyQueryHashes.Finder(fetch = fetch, wanted = setOf("canvas"), now = { now }, store = store)
        now += 7L * 60 * 60 * 1000
        fetched.clear()
        assertEquals(hashA, relaunched.find("canvas"))
        assertEquals(listOf(SpotifyQueryHashes.PAGE_URL), fetched)

        // A new web player: the scripts are read again.
        version = "v2"
        hash = hashB
        fetched.clear()
        assertEquals(hashA, relaunched.find("canvas"), "inside the check time nothing is read")
        assertTrue(fetched.isEmpty())
        now += 11L * 60 * 1000
        assertEquals(hashB, relaunched.find("canvas", forceRefresh = true))
        assertEquals(firstScan, fetched.size)
    }

    @Test
    fun `without a readable player nothing is found, and the page is not read on every call`() {
        var now = 1_000L
        var calls = 0
        val finder = SpotifyQueryHashes.Finder(fetch = { calls++; null }, wanted = setOf("canvas"), now = { now })
        assertNull(finder.find("canvas"))
        assertNull(finder.find("canvas"))
        assertEquals(1, calls, "a failed read waits")
        now += 31L * 60 * 1000
        finder.find("canvas")
        assertEquals(2, calls, "and is tried again after the wait")
        finder.find("canvas", forceRefresh = true)
        assertEquals(2, calls, "an old hash does not read the page again inside ten minutes")
        now += 11L * 60 * 1000
        finder.find("canvas", forceRefresh = true)
        assertEquals(3, calls, "but does after them")
    }

    @Test
    fun `a failed read keeps the hashes already found`() {
        var now = 1_000L
        var reachable = true
        val finder = SpotifyQueryHashes.Finder(
            fetch = { url ->
                when {
                    !reachable -> null
                    url == SpotifyQueryHashes.PAGE_URL -> page("v1")
                    else -> declare("canvas", hashB)
                }
            },
            wanted = setOf("canvas"),
            now = { now },
        )
        assertEquals(hashB, finder.find("canvas"))
        reachable = false
        now += 11L * 60 * 1000
        assertEquals(hashB, finder.find("canvas", forceRefresh = true))
    }

    @Test
    fun `an old hash is seen in the answer`() {
        assertTrue(SpotifyQueryHashes.isStale("""{"errors":[{"message":"PersistedQueryNotFound"}]}"""))
        assertFalse(SpotifyQueryHashes.isStale("""{"data":{}}"""))
        assertFalse(SpotifyQueryHashes.isStale(null))
    }

    @Test
    fun `every operation Dial uses has a known hash`() {
        for (operation in listOf(
            SpotifyQueryHashes.CANVAS, SpotifyQueryHashes.LIBRARY, SpotifyQueryHashes.FETCH_PLAYLIST,
            SpotifyQueryHashes.LIKED_TRACKS, SpotifyQueryHashes.SEARCH_DESKTOP, SpotifyQueryHashes.SEARCH_TRACKS,
            SpotifyQueryHashes.HOME, SpotifyQueryHashes.HOME_SECTION, SpotifyQueryHashes.ADD_TO_LIBRARY,
            SpotifyQueryHashes.REMOVE_FROM_LIBRARY, SpotifyQueryHashes.ADD_TO_PLAYLIST,
            SpotifyQueryHashes.REMOVE_FROM_PLAYLIST, SpotifyQueryHashes.PROFILE_ATTRIBUTES,
        )) {
            assertTrue(Regex("[0-9a-f]{64}").matches(SpotifyQueryHashes.KNOWN.getValue(operation)), operation)
        }
    }
}
