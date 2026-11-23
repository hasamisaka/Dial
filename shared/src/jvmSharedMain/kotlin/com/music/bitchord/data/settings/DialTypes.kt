package com.music.bitchord.data.settings

import com.music.bitchord.data.model.ShelfItem

/**
 * Where the speed dial's tiles after the pins come from: what was played
 * lately, YouTube Music's own speed dial (the default), or what Spotify
 * recommends after the songs played lately. The pickers show them in this
 * order; Spotify is only offered while a Spotify account is signed in.
 */
enum class SpeedDialSource {
    HISTORY, YOUTUBE, SPOTIFY;

    /** This choice as it applies now: Spotify without a Spotify account is YouTube Music. */
    fun effective(spotifyConnected: Boolean): SpeedDialSource =
        if (this == SPOTIFY && !spotifyConnected) YOUTUBE else this

    companion object {
        /** The sources a picker offers. */
        fun choices(spotifyConnected: Boolean): List<SpeedDialSource> =
            if (spotifyConnected) entries else entries - SPOTIFY
    }
}

/**
 * What the Spotify speed dial's picks start from: the songs played in Dial
 * lately (the default), or the account's own top songs and artists on
 * Spotify over the last four weeks, six months and year.
 */
enum class SpotifyDialSeeds {
    PLAYS, TOP,
}

/** The dial's tiles past the pins, and which of them are new releases (for their tag). */
data class DialPicks(
    val items: List<ShelfItem>,
    val newReleaseIds: Set<String>,
)

/** How the dials' tiles are edged. */
enum class DialTileStyle {
    /** The plain hairline every thumbnail has. */
    NONE,

    /** A bevelled rim that catches the light along the top. */
    EDGE,

    /** Clear glass over the art, which fills the tile: frosted only along the edge. */
    GLASS,
}

/**
 * The whole dial layout at one moment: what the dials show and how. This is
 * what a paired device will be sent to keep its dials the same.
 */
data class DialLayout(
    val speedDials: Boolean,
    val mixesDial: Boolean,
    val artistDial: Boolean,
    val feelingLucky: Boolean,
    val speedDialSource: SpeedDialSource,
    val spotifyDialSeeds: SpotifyDialSeeds,
    val speedDialRows: Int,
    val dialTileStyle: DialTileStyle,
    val dialLabels: Boolean,
    val dialHiddenLabels: Set<String>,
    val dialPinIcon: Boolean,
    val speedDialPins: List<ShelfItem>,
    val artistDialPins: List<ShelfItem>,
    val podcastDialPins: List<ShelfItem>,
    val hiddenFromDial: Set<String>,
)
