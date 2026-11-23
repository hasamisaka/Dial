package com.music.bitchord.data.spotify

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.canvas.SpotifyToken
import com.music.bitchord.data.lyrics.SpotifyLyrics
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Android side of [SpotifyLyrics]: the listener's own Spotify sign-in
 * (the `sp_dc` web player session of [SpotifyToken]) and the web player's own
 * search ([SpotifyLikeSync.findTrack]) to find the track.
 *
 * Set as [SpotifyLyrics.account] at start. With no sign-in it answers null
 * before any request, so the lyrics source is free until someone signs in.
 */
object SpotifyAccountLyrics : SpotifyLyrics.Account {

    private const val TAG = "SpotifyLyrics"

    /**
     * Longest a lyrics lookup waits for a token. A first token is minted in an
     * offscreen WebView, which can take up to 20 s; the lyrics race awaits its
     * sources in order, so a source ahead of the others must not hold them that
     * long. The mint itself runs on in [scope] and is ready for the next song.
     */
    private const val TOKEN_WAIT_MS = 4_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun install() {
        SpotifyLyrics.account = this
    }

    private suspend fun token(): String? {
        if (AppSettings.spotifySpdcToken.value.isBlank()) return null
        val mint = scope.async { SpotifyToken.accessToken() }
        return withTimeoutOrNull(TOKEN_WAIT_MS) { mint.await() }
            .also { if (it == null) Log.d(TAG, "no Spotify token in time; skipped") }
    }

    override suspend fun headers(): Map<String, String>? {
        val token = token() ?: return null
        return withContext(Dispatchers.IO) {
            SpotifyLikeSync.headers(token) + mapOf(
                "App-platform" to "WebPlayer",
                "Origin" to "https://open.spotify.com",
                "Referer" to "https://open.spotify.com/",
            )
        }
    }

    override suspend fun trackId(title: String, artist: String, durationMs: Long): String? {
        if (title.isBlank() || artist.isBlank()) return null
        val token = token() ?: return null
        return withContext(Dispatchers.IO) {
            SpotifyLikeSync.findTrack(title, artist, token)?.let(SpotifyLikeMirror::trackId)
        }.also { if (it == null) Log.d(TAG, "no Spotify match for '$title' by '$artist'") }
    }
}
