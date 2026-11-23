package com.music.bitchord.data

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.canvas.CANVAS_UA
import com.music.bitchord.data.canvas.SpotifyCanvasQuery
import com.music.bitchord.data.canvas.SpotifyToken
import com.music.bitchord.data.canvas.canvasGet
import com.music.bitchord.data.canvas.canvasGetWithStatus
import com.music.bitchord.data.spotify.SpotifyLikeSync
import com.music.bitchord.data.spotify.SpotifyPlayedList
import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The plan (Premium or free) of the signed-in YouTube and Spotify accounts.
 *
 * The settings show it as a badge next to the account name, and the profile picker next to
 * each profile. It also decides whether the "High quality audio" switch can be used (see
 * [youtubePremiumAudio]); nothing else reads it.
 *
 * Checked only when the app starts and when an account signs in. A YouTube profile that was
 * never checked is also checked when it becomes active. Each answer is kept on the
 * device, so the badge shows at once on the next start, before the new check is done. An
 * [AccountPlan.UNKNOWN] answer does not replace a known one: a failed request is not a change
 * of plan.
 */
object AccountPlanState {

    private const val TAG = "AccountPlan"
    private const val PREFS = "account_plans"
    private const val KEY_YOUTUBE = "youtube:"
    private const val KEY_SPOTIFY = "spotify"

    /** The start check waits this long, so it does not compete with the first screen's requests. */
    private const val START_DELAY_MS = 6_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var prefs: SharedPreferences? = null

    private val _youtubePlans = MutableStateFlow<Map<String, AccountPlan>>(emptyMap())

    /** The last known plan of each YouTube profile, by "accountId:profileId". */
    val youtubePlans: StateFlow<Map<String, AccountPlan>> = _youtubePlans

    private val _youtube = MutableStateFlow<AccountPlan?>(null)

    /** The plan of the active YouTube profile; null when not known (yet) or signed out. */
    val youtube: StateFlow<AccountPlan?> = _youtube

    private val _spotify = MutableStateFlow<AccountPlan?>(null)

    /** The plan of the connected Spotify account; null when not known (yet) or not connected. */
    val spotify: StateFlow<AccountPlan?> = _spotify

    @Volatile private var activeYoutube: String? = null
    @Volatile private var youtubeJob: Job? = null
    @Volatile private var youtubeStartChecked = false
    @Volatile private var spotifyJob: Job? = null

    /**
     * Whether YouTube playback takes the route to Premium's ~256 kbps Opus: the listener's
     * "High quality audio" switch, and only while the active profile is known to have Premium.
     */
    fun youtubePremiumAudio(): Boolean =
        AppSettings.youtubePremiumAudio.value && _youtube.value == AccountPlan.PREMIUM

    /** Reads the kept plans and starts to follow the Spotify sign-in. Call once, after [AppSettings]. */
    fun init(context: Context) {
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _youtubePlans.value = store.all.mapNotNull { (key, value) ->
            if (!key.startsWith(KEY_YOUTUBE)) return@mapNotNull null
            val plan = (value as? String)?.let { name -> AccountPlan.entries.firstOrNull { it.name == name } }
            plan?.let { key.removePrefix(KEY_YOUTUBE) to it }
        }.toMap()
        _spotify.value = store.getString(KEY_SPOTIFY, null)
            ?.let { name -> AccountPlan.entries.firstOrNull { it.name == name } }
        scope.launch {
            var first = true
            AppSettings.spotifySpdcToken.collect { cookie ->
                val atStart = first
                first = false
                if (cookie.isBlank()) {
                    spotifyJob?.cancel()
                    setSpotify(null)
                } else {
                    // The first value is the app start; any later one is a new sign-in.
                    checkSpotify(delayMs = if (atStart) START_DELAY_MS else 0L)
                }
            }
        }
    }

    /**
     * The YouTube profile now in use, as "accountId:profileId", or null when signed out.
     * Shows its kept plan at once. Checks it when this is the first profile since the app
     * started, or when it was never checked.
     */
    @Synchronized
    fun setActiveYoutube(key: String?) {
        if (key == activeYoutube) return
        activeYoutube = key
        youtubeJob?.cancel()
        _youtube.value = key?.let { _youtubePlans.value[it] }
        if (key == null) return
        val atStart = !youtubeStartChecked
        if (atStart || _youtubePlans.value[key] == null) {
            youtubeStartChecked = true
            checkYoutube(key, delayMs = if (atStart) START_DELAY_MS else 0L)
        }
    }

    /** Checks the plan of the YouTube profile [key] now: after a sign-in. */
    @Synchronized
    fun checkYoutube(key: String, delayMs: Long = 0L) {
        youtubeJob?.cancel()
        youtubeJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            val plan = YtMusicRepository.youtubePlan()
            Log.d(TAG, "YouTube plan: $plan")
            // A switch while the check ran: the answer was for another profile.
            if (activeYoutube != key || plan == AccountPlan.UNKNOWN) return@launch
            _youtubePlans.value = _youtubePlans.value + (key to plan)
            val changed = _youtube.value != plan
            _youtube.value = plan
            // Stream URLs picked under the old answer took the other route; the next play asks again.
            if (changed) StreamResolver.forgetResolved()
            prefs?.edit()?.putString(KEY_YOUTUBE + key, plan.name)?.apply()
        }
    }

    private fun checkSpotify(delayMs: Long) {
        spotifyJob?.cancel()
        spotifyJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            val plan = runCatching { spotifyPlan() }
                .onFailure {
                    if (it is CancellationException) throw it
                    Log.w(TAG, "Spotify plan check failed: ${it.message}")
                }
                .getOrDefault(AccountPlan.UNKNOWN)
            Log.d(TAG, "Spotify plan: $plan")
            if (plan != AccountPlan.UNKNOWN) setSpotify(plan)
        }
    }

    private fun setSpotify(plan: AccountPlan?) {
        _spotify.value = plan
        val editor = prefs?.edit() ?: return
        if (plan == null) editor.remove(KEY_SPOTIFY) else editor.putString(KEY_SPOTIFY, plan.name)
        editor.apply()
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
