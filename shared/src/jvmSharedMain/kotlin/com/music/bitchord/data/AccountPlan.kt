package com.music.bitchord.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Whether a signed-in account pays for its service, as far as the service lets the app see.
 *
 * Shown in the account settings and nothing else: no feature waits for it or is turned off by
 * it. [UNKNOWN] is an honest answer, not an error: the service did not say, or said something
 * this code does not know how to read.
 */
enum class AccountPlan { PREMIUM, FREE, UNKNOWN }

/**
 * Reading [AccountPlan] out of each service's answers. The HTTP-free half, so it can be tested.
 */
object AccountPlans {

    /**
     * The audio formats YouTube offers only to a Premium session: 256 kbps AAC (141) and
     * ~256 kbps Opus (774). A free session gets 140 and 251 at the most.
     *
     * Read from the formats and not from any text, because the formats are the same in every
     * language and in every country. YouTube Music's own account menu and membership page say
     * the plan only in words.
     */
    val YOUTUBE_PREMIUM_ITAGS = setOf(141, 774)

    /**
     * The songs the check asks the signed-in player about, in turn: "Never Gonna Give You Up" as
     * the song (ATV) and as the music video (OMV). Any song would do, as long as it plays
     * everywhere; the second is there for when the first is blocked in the listener's country.
     */
    val YOUTUBE_PROBE_VIDEOS = listOf("lYBUbBu4W08", "dQw4w9WgXcQ")

    /**
     * The plan of the account that asked for [response], a signed-in `player` answer.
     *
     * [AccountPlan.UNKNOWN] unless the song played and its audio formats are listed: an
     * unplayable answer, or one that streams only through SABR and lists no formats, says
     * nothing about the plan.
     */
    fun youtubeFromPlayer(response: JsonObject): AccountPlan {
        val status = (response["playabilityStatus"] as? JsonObject)?.text("status")
        if (status != "OK") return AccountPlan.UNKNOWN
        val formats = ((response["streamingData"] as? JsonObject)?.get("adaptiveFormats") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            .orEmpty()
        val audio = formats.filter { it.text("mimeType")?.startsWith("audio/") == true }
        if (audio.isEmpty()) return AccountPlan.UNKNOWN
        val itags = audio.mapNotNull { (it["itag"] as? JsonPrimitive)?.intOrNull }
        return if (itags.any { it in YOUTUBE_PREMIUM_ITAGS }) AccountPlan.PREMIUM else AccountPlan.FREE
    }

    /** The Pathfinder query the Spotify web player itself asks for its plan. It takes no variables. */
    const val SPOTIFY_ACCOUNT_OPERATION = "accountAttributes"

    /** The last hash known for [SPOTIFY_ACCOUNT_OPERATION]; the live one is read off the web player's scripts first. */
    const val SPOTIFY_KNOWN_ACCOUNT_HASH = "be4ce359daee01463ddb8e77536220e108e3d28105e12199f244d7a5954e9d14"

    /** Where the public Web API says the plan, as `product`. */
    const val SPOTIFY_WEB_API_ME = "https://api.spotify.com/v1/me"

    /**
     * The plan out of the answer to [SPOTIFY_ACCOUNT_OPERATION]: `data.me.account.product`, which
     * the web player reads the same way (lower case, "premium" or not).
     */
    fun spotifyFromAccountAttributes(body: String): AccountPlan {
        val root = parse(body) ?: return AccountPlan.UNKNOWN
        val account = ((root["data"] as? JsonObject)?.get("me") as? JsonObject)?.get("account") as? JsonObject
        return spotifyProduct(account?.text("product"))
    }

    /** Whether the answer to [SPOTIFY_ACCOUNT_OPERATION] says its hash is no longer known. */
    fun spotifyStaleHash(body: String): Boolean {
        val errors = parse(body)?.get("errors") as? JsonArray ?: return false
        return errors.any { (it as? JsonObject)?.text("message")?.contains("PersistedQueryNotFound", ignoreCase = true) == true }
    }

    /** The plan out of the answer to [SPOTIFY_WEB_API_ME]. */
    fun spotifyFromWebApi(body: String): AccountPlan = spotifyProduct(parse(body)?.text("product"))

    /**
     * Spotify's name for a plan: "premium" for each paid plan (Individual, Duo, Family,
     * Student), "free" for the free plan and "open" for its older name. Anything else is
     * [AccountPlan.UNKNOWN], so that a plan this code has not seen is not called free.
     */
    fun spotifyProduct(product: String?): AccountPlan {
        val name = product?.trim()?.lowercase()
        return when {
            name.isNullOrEmpty() -> AccountPlan.UNKNOWN
            name == "premium" || name.startsWith("premium_") -> AccountPlan.PREMIUM
            name == "free" || name == "open" -> AccountPlan.FREE
            else -> AccountPlan.UNKNOWN
        }
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parse(body: String): JsonObject? =
        runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
}
