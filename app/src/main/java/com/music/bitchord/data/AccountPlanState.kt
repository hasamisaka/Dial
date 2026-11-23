package com.music.bitchord.data

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.canvas.CANVAS_UA
import com.music.bitchord.data.canvas.SpotifyCanvasQuery
import com.music.bitchord.data.canvas.SpotifyToken
import com.music.bitchord.data.canvas.canvasGet
import com.music.bitchord.data.canvas.canvasGetWithStatus
import com.music.bitchord.data.spotify.SpotifyLikeSync
import com.music.bitchord.data.spotify.SpotifyPlayedList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The plan (Premium or free) of the signed-in YouTube and Spotify accounts, for the account
 * settings to show.
 *
 * Each value is null while a check runs, then an [AccountPlan]. Checked when the settings ask,
 * once for each account in a process; a tap on the row checks again. Display only: no feature
 * reads these values, so a wrong or [AccountPlan.UNKNOWN] answer costs nothing but the line.
 */
object AccountPlanState {

    private const val TAG = "AccountPlan"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _youtube = MutableStateFlow<AccountPlan?>(null)
    val youtube: StateFlow<AccountPlan?> = _youtube

    private val _spotify = MutableStateFlow<AccountPlan?>(null)
    val spotify: StateFlow<AccountPlan?> = _spotify

    @Volatile private var youtubeKey: String? = null
    @Volatile private var youtubeJob: Job? = null
    @Volatile private var spotifyKey: Int? = null
    @Volatile private var spotifyJob: Job? = null

    /**
     * Checks the YouTube plan of the account [key] names (any text that changes with the
     * account or channel), unless it was already checked. [force] checks again.
     */
    @Synchronized
    fun checkYoutube(key: String, force: Boolean = false) {
        if (!force && key == youtubeKey && (_youtube.value != null || youtubeJob?.isActive == true)) return
        youtubeKey = key
        youtubeJob?.cancel()
        _youtube.value = null
        youtubeJob = scope.launch {
            val plan = YtMusicRepository.youtubePlan()
            Log.d(TAG, "YouTube plan: $plan")
            if (youtubeKey == key) _youtube.value = plan
        }
    }

    /** The same as [checkYoutube], for the Spotify sign-in [cookie]. */
    @Synchronized
    fun checkSpotify(cookie: String, force: Boolean = false) {
        if (cookie.isBlank()) return
        val key = cookie.hashCode()
        if (!force && key == spotifyKey && (_spotify.value != null || spotifyJob?.isActive == true)) return
        spotifyKey = key
        spotifyJob?.cancel()
        _spotify.value = null
        spotifyJob = scope.launch {
            val plan = runCatching { spotifyPlan() }
                .onFailure {
                    if (it is CancellationException) throw it
                    Log.w(TAG, "Spotify plan check failed: ${it.message}")
                }
                .getOrDefault(AccountPlan.UNKNOWN)
            Log.d(TAG, "Spotify plan: $plan")
            if (spotifyKey == key) _spotify.value = plan
        }
    }

    // ---- Spotify ----------------------------------------------------------

    private val jsonType = "application/json".toMediaType()

    private val accountHash = SpotifyCanvasQuery.QueryHashes(
        fetch = { url -> canvasGet(url, mapOf("User-Agent" to CANVAS_UA)) },
        operation = AccountPlans.SPOTIFY_ACCOUNT_OPERATION,
        fallback = AccountPlans.SPOTIFY_KNOWN_ACCOUNT_HASH,
    )

    /**
     * The web player's own way first: the `accountAttributes` query it asks for its plan. The
     * public Web API second, since it often turns the web player's sign-in away.
     */
    private suspend fun spotifyPlan(): AccountPlan {
        val token = SpotifyToken.accessToken() ?: return AccountPlan.UNKNOWN
        val headers = SpotifyLikeSync.headers(token)
        val viaWeb = spotifyViaWeb(headers)
        if (viaWeb != AccountPlan.UNKNOWN) return viaWeb
        val (code, body) = canvasGetWithStatus(AccountPlans.SPOTIFY_WEB_API_ME, headers)
        if (body == null) Log.w(TAG, "Web API /v1/me: http $code")
        return body?.let(AccountPlans::spotifyFromWebApi) ?: AccountPlan.UNKNOWN
    }

    private fun spotifyViaWeb(headers: Map<String, String>, isRetry: Boolean = false): AccountPlan {
        if ("Client-Token" !in headers) return AccountPlan.UNKNOWN
        val hash = accountHash.hash(forceRefresh = isRetry)
        val query = SpotifyPlayedList.queryBody(AccountPlans.SPOTIFY_ACCOUNT_OPERATION, hash)
        val request = Request.Builder()
            .url(SpotifyCanvasQuery.ENDPOINT)
            .post(query.toByteArray(Charsets.UTF_8).toRequestBody(jsonType))
            .apply { (headers + ("App-platform" to "WebPlayer")).forEach { (name, value) -> header(name, value) } }
            .build()
        val (code, body) = runCatching {
            Http.client.newCall(request).execute().use { response ->
                response.code to if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrDefault(-1 to null)
        if (body == null) {
            Log.w(TAG, "accountAttributes: http $code")
            return AccountPlan.UNKNOWN
        }
        val plan = AccountPlans.spotifyFromAccountAttributes(body)
        if (plan == AccountPlan.UNKNOWN && !isRetry && AccountPlans.spotifyStaleHash(body)) {
            return spotifyViaWeb(headers, isRetry = true)
        }
        if (plan == AccountPlan.UNKNOWN) Log.w(TAG, "accountAttributes answered with no plan")
        return plan
    }
}
