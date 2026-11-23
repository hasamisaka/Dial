package com.music.bitchord.desktop

import com.music.bitchord.data.canvas.SpotifyCanvasQuery
import com.music.bitchord.data.canvas.SpotifyCanvasQuery.Answer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Pathfinder `canvas` query the Spotify source now asks first (shared by the phone and the
 * desktop): the request it sends, and every shape of answer it has to read. The hash itself comes
 * from `SpotifyQueryHashes`, which has its own test.
 */
class SpotifyCanvasQueryTest {

    private val uri = "spotify:track:4cOdK2wGLETKBW3PvgPWqT"
    private val hashA = "a".repeat(64)

    @Test
    fun `the request names the canvas operation, the track and the hash`() {
        val body = Json.parseToJsonElement(SpotifyCanvasQuery.requestBody(uri, hashA)).jsonObject
        assertEquals("canvas", body["operationName"]!!.jsonPrimitive.content)
        // `trackUri`, not the older `uri` variable: the query ignores the wrong name and answers
        // with no canvas, which looks exactly like a track that has none.
        assertEquals(uri, body["variables"]!!.jsonObject["trackUri"]!!.jsonPrimitive.content)
        val persisted = body["extensions"]!!.jsonObject["persistedQuery"]!!.jsonObject
        assertEquals(1, persisted["version"]!!.jsonPrimitive.int)
        assertEquals(hashA, persisted["sha256Hash"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a canvas is read from data trackUnion canvas url`() {
        val answer = SpotifyCanvasQuery.parse(
            """{"data":{"trackUnion":{"__typename":"Track","canvas":{"type":"VIDEO_LOOPING","uri":"spotify:canvas:x","url":"https://canvaz.scdn.co/upload/artist/x/video/y.cnvs.mp4"}}}}""",
        )
        assertEquals(Answer.Found("https://canvaz.scdn.co/upload/artist/x/video/y.cnvs.mp4", "VIDEO_LOOPING"), answer)
    }

    @Test
    fun `a track without a canvas is a final answer, not a failure`() {
        val nullCanvas = assertIs<Answer.NoCanvas>(
            SpotifyCanvasQuery.parse("""{"data":{"trackUnion":{"__typename":"Track","canvas":null}}}"""),
        )
        assertEquals("canvas null", nullCanvas.detail)

        val missingCanvas = assertIs<Answer.NoCanvas>(
            SpotifyCanvasQuery.parse("""{"data":{"trackUnion":{"__typename":"Track"}}}"""),
        )
        assertEquals("canvas null", missingCanvas.detail)

        val missingTrack = assertIs<Answer.NoCanvas>(
            SpotifyCanvasQuery.parse("""{"data":{"trackUnion":null}}"""),
        )
        assertEquals("canvas null", missingTrack.detail)
    }

    @Test
    fun `an image canvas or a link that is not https is not played`() {
        val image = assertIs<Answer.NoCanvas>(
            SpotifyCanvasQuery.parse("""{"data":{"trackUnion":{"canvas":{"type":"IMAGE","url":"https://canvaz.scdn.co/upload/x.jpg"}}}}"""),
        )
        assertTrue(image.detail.contains("type=IMAGE"))

        val insecure = assertIs<Answer.NoCanvas>(
            SpotifyCanvasQuery.parse("""{"data":{"trackUnion":{"canvas":{"url":"http://canvaz.scdn.co/x.cnvs.mp4"}}}}"""),
        )
        assertTrue(insecure.detail.contains("url=http://"))

        val fileIdVideo = assertIs<Answer.NoCanvas>(
            SpotifyCanvasQuery.parse("""{"data":{"trackUnion":{"canvas":{"type":"VIDEO_LOOPING","fileId":"abc123"}}}}"""),
        )
        assertTrue(fileIdVideo.detail.contains("fileId=abc123"))
    }

    @Test
    fun `a stale hash is a failure that says the hash should be looked up again`() {
        val answer = SpotifyCanvasQuery.parse("""{"errors":[{"message":"PersistedQueryNotFound"}]}""")
        assertIs<Answer.Failed>(answer)
        assertTrue(answer.staleHash)
    }

    @Test
    fun `other failures fall back without blaming the hash`() {
        val errored = SpotifyCanvasQuery.parse("""{"errors":[{"message":"Something else"}],"data":null}""")
        assertIs<Answer.Failed>(errored)
        assertEquals(false, errored.staleHash)
        assertIs<Answer.Failed>(SpotifyCanvasQuery.parse("<html>rate limited</html>"))
        assertIs<Answer.Failed>(SpotifyCanvasQuery.parse(""))
    }
}
