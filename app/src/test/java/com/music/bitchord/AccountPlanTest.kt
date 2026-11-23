package com.music.bitchord

import com.music.bitchord.data.AccountPlan
import com.music.bitchord.data.AccountPlans
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountPlanTest {

    private fun player(status: String, vararg formats: Pair<Int, String>): JsonObject {
        val list = formats.joinToString(",") { (itag, mime) -> """{"itag":$itag,"mimeType":"$mime"}""" }
        return Json.parseToJsonElement(
            """{"playabilityStatus":{"status":"$status"},"streamingData":{"adaptiveFormats":[$list]}}""",
        ).jsonObject
    }

    private val video = 137 to "video/mp4; codecs=\"avc1.640028\""
    private val aac128 = 140 to "audio/mp4; codecs=\"mp4a.40.2\""
    private val aac256 = 141 to "audio/mp4; codecs=\"mp4a.40.2\""
    private val opus160 = 251 to "audio/webm; codecs=\"opus\""
    private val opus256 = 774 to "audio/webm; codecs=\"opus\""

    @Test
    fun `the 256 kbps formats mean Premium`() {
        // The audio list a Premium account was given on 2026-11-23.
        assertEquals(AccountPlan.PREMIUM, AccountPlans.youtubeFromPlayer(player("OK", video, aac128, aac256, opus160, opus256)))
        assertEquals(AccountPlan.PREMIUM, AccountPlans.youtubeFromPlayer(player("OK", aac128, opus256)))
    }

    @Test
    fun `the standard formats only mean free`() {
        assertEquals(AccountPlan.FREE, AccountPlans.youtubeFromPlayer(player("OK", video, aac128, opus160)))
    }

    @Test
    fun `an answer with no audio says nothing`() {
        assertEquals(AccountPlan.UNKNOWN, AccountPlans.youtubeFromPlayer(player("UNPLAYABLE")))
        assertEquals(AccountPlan.UNKNOWN, AccountPlans.youtubeFromPlayer(player("UNPLAYABLE", aac256)))
        assertEquals(AccountPlan.UNKNOWN, AccountPlans.youtubeFromPlayer(player("OK", video)))
        assertEquals(AccountPlan.UNKNOWN, AccountPlans.youtubeFromPlayer(player("OK")))
        assertEquals(AccountPlan.UNKNOWN, AccountPlans.youtubeFromPlayer(JsonObject(emptyMap())))
    }

    @Test
    fun `a 256 kbps video itag number is not read as audio`() {
        assertEquals(AccountPlan.FREE, AccountPlans.youtubeFromPlayer(player("OK", 141 to "video/mp4", aac128)))
    }

    @Test
    fun `reads the Spotify web player's account attributes`() {
        fun body(product: String) = """{"data":{"me":{"account":{"product":"$product","country":"GB","attributes":{"catalogue":"$product"}}}}}"""
        assertEquals(AccountPlan.PREMIUM, AccountPlans.spotifyFromAccountAttributes(body("PREMIUM")))
        assertEquals(AccountPlan.PREMIUM, AccountPlans.spotifyFromAccountAttributes(body("premium")))
        assertEquals(AccountPlan.FREE, AccountPlans.spotifyFromAccountAttributes(body("FREE")))
        assertEquals(AccountPlan.UNKNOWN, AccountPlans.spotifyFromAccountAttributes(body("SOMETHING_NEW")))
        assertEquals(AccountPlan.UNKNOWN, AccountPlans.spotifyFromAccountAttributes("""{"data":{"me":null}}"""))
        assertEquals(AccountPlan.UNKNOWN, AccountPlans.spotifyFromAccountAttributes("not json"))
    }

    @Test
    fun `sees a stale Spotify query hash`() {
        assertTrue(AccountPlans.spotifyStaleHash("""{"errors":[{"message":"PersistedQueryNotFound"}]}"""))
        assertFalse(AccountPlans.spotifyStaleHash("""{"errors":[{"message":"Unauthorized"}]}"""))
        assertFalse(AccountPlans.spotifyStaleHash("""{"data":{}}"""))
    }

    @Test
    fun `reads the Web API product`() {
        assertEquals(AccountPlan.PREMIUM, AccountPlans.spotifyFromWebApi("""{"id":"x","product":"premium"}"""))
        assertEquals(AccountPlan.FREE, AccountPlans.spotifyFromWebApi("""{"id":"x","product":"free"}"""))
        assertEquals(AccountPlan.FREE, AccountPlans.spotifyFromWebApi("""{"id":"x","product":"open"}"""))
        assertEquals(AccountPlan.UNKNOWN, AccountPlans.spotifyFromWebApi("""{"id":"x"}"""))
    }
}
