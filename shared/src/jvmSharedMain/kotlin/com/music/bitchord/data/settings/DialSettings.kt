package com.music.bitchord.data.settings

import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.isPodcast
import com.music.bitchord.data.model.isPodcastShow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The dials' layout, the same on the phone and the desktop: which dials Home
 * has, what is pinned to each, what was taken off them, and how their tiles
 * look. Kept in a [DialStore] under the keys the phone has always used.
 */
object DialSettings {
    const val SPEED_DIAL_SIZE = 36
    const val DEFAULT_SPEED_DIAL_ROWS = 4

    private var store: DialStore = InMemoryDialStore()

    /**
     * Whether a card is a podcast show or episode, which the music dial leaves
     * out. The phone widens this with the episodes it has seen.
     */
    var isPodcastTile: (ShelfItem) -> Boolean = { it.isPodcast }

    /**
     * Whether Home has its dials at all. Off, Home is the plain list of
     * sections, with no dial pages to snap between and nothing to pin to.
     */
    val speedDials = MutableStateFlow(true)

    /**
     * A second dial under the speed dial, of artists: the ones pinned to it,
     * then the most played. Off, artists are pinned to the speed dial itself.
     */
    val artistDial = MutableStateFlow(false)

    /** A die on the speed dial that starts a radio off a random personal pick. */
    val feelingLucky = MutableStateFlow(true)

    /**
     * A page to the left of the speed dial's first page, of mixes dealt at
     * random from YouTube Music and, while signed in, Spotify.
     */
    val mixesDial = MutableStateFlow(true)

    /** Items pinned to the speed dial, in the order they were pinned. */
    val speedDialPins = MutableStateFlow<List<ShelfItem>>(emptyList())

    /** Artists pinned to [artistDial]'s page, in their order there. */
    val artistDialPins = MutableStateFlow<List<ShelfItem>>(emptyList())

    /** Shows pinned to the Podcasts page's dial, in the order they were pinned. */
    val podcastDialPins = MutableStateFlow<List<ShelfItem>>(emptyList())

    /** Ids taken off the dials with "Remove from dial", kept off until pinned again. */
    val hiddenFromDial = MutableStateFlow<Set<String>>(emptySet())

    /** Whether dial tiles show their titles, unless a tile says otherwise. */
    val dialLabels = MutableStateFlow(true)

    /** Dial tiles that go against [dialLabels]: hidden while it is on, shown while it is off. */
    val dialHiddenLabels = MutableStateFlow<Set<String>>(emptySet())

    /** Whether pinned tiles on the dials carry the little pin badge. */
    val dialPinIcon = MutableStateFlow(true)

    /** How the dials' tiles are edged: the plain hairline, a contoured rim, or a glass rim. */
    val dialTileStyle = MutableStateFlow(DialTileStyle.GLASS)

    /** Where the speed dial's unpinned tiles come from; see [SpeedDialSource]. */
    val speedDialSource = MutableStateFlow(SpeedDialSource.YOUTUBE)

    /** What the Spotify speed dial's picks start from; see [SpotifyDialSeeds]. */
    val spotifyDialSeeds = MutableStateFlow(SpotifyDialSeeds.PLAYS)

    /** How many rows of three tiles the speed dial shows per page: 3 or 4. */
    val speedDialRows = MutableStateFlow(DEFAULT_SPEED_DIAL_ROWS)

    /** Whether this launch has refreshed the pins' art yet: once per fresh start. */
    var pinArtRefreshed = false

    fun init(store: DialStore) {
        this.store = store
        readAll()
    }

    fun readAll() {
        val p = store
        speedDials.value = p.getBoolean(KEY_SPEED_DIALS, true)
        artistDial.value = p.getBoolean(KEY_ARTIST_DIAL, false)
        feelingLucky.value = p.getBoolean(KEY_FEELING_LUCKY, true)
        mixesDial.value = p.getBoolean(KEY_MIXES_DIAL, true)
        speedDialSource.value = SpeedDialSource.entries
            .firstOrNull { it.name == p.getString(KEY_SPEED_DIAL_SOURCE) } ?: SpeedDialSource.YOUTUBE
        spotifyDialSeeds.value = SpotifyDialSeeds.entries
            .firstOrNull { it.name == p.getString(KEY_SPOTIFY_DIAL_SEEDS) } ?: SpotifyDialSeeds.PLAYS
        speedDialRows.value = p.getInt(KEY_SPEED_DIAL_ROWS, DEFAULT_SPEED_DIAL_ROWS).takeIf { it in 3..4 }
            ?: DEFAULT_SPEED_DIAL_ROWS
        speedDialPins.value = DialPins.decode(p.getString(KEY_SPEED_DIAL_PINS))
        artistDialPins.value = DialPins.decode(p.getString(KEY_ARTIST_DIAL_PINS))
        podcastDialPins.value = DialPins.decode(p.getString(KEY_PODCAST_DIAL_PINS))
        // The artist dial used to be on unless switched off. Someone who never
        // touched the switch but pinned artists to that dial keeps it, rather
        // than having those pins left on a page that is no longer shown.
        if (!p.contains(KEY_ARTIST_DIAL) && artistDialPins.value.isNotEmpty()) artistDial.value = true
        hiddenFromDial.value = p.getStringSet(KEY_HIDDEN_FROM_DIAL)
        dialHiddenLabels.value = p.getStringSet(KEY_DIAL_HIDDEN_LABELS)
        dialPinIcon.value = p.getBoolean(KEY_DIAL_PIN_ICON, true)
        dialLabels.value = p.getBoolean(KEY_DIAL_LABELS, true)
        dialTileStyle.value = p.getString(KEY_DIAL_TILE_STYLE)
            ?.let { name -> DialTileStyle.entries.firstOrNull { it.name == name } }
            // The style's first form was one switch, for the contoured rim.
            // Glass is the default when nothing was chosen.
            ?: if (p.getBoolean(KEY_DIAL_CONTOURED_EDGE, false)) DialTileStyle.EDGE else DialTileStyle.GLASS
    }

    /** The layout as it stands. */
    fun snapshot(): DialLayout = DialLayout(
        speedDials = speedDials.value,
        mixesDial = mixesDial.value,
        artistDial = artistDial.value,
        feelingLucky = feelingLucky.value,
        speedDialSource = speedDialSource.value,
        spotifyDialSeeds = spotifyDialSeeds.value,
        speedDialRows = speedDialRows.value,
        dialTileStyle = dialTileStyle.value,
        dialLabels = dialLabels.value,
        dialHiddenLabels = dialHiddenLabels.value,
        dialPinIcon = dialPinIcon.value,
        speedDialPins = speedDialPins.value,
        artistDialPins = artistDialPins.value,
        podcastDialPins = podcastDialPins.value,
        hiddenFromDial = hiddenFromDial.value,
    )

    // ── Pins ────────────────────────────────────────────────────────────

    fun isPinnedToSpeedDial(id: String?): Boolean =
        id != null && (
            speedDialPins.value.any { it.speedDialId == id } ||
                artistDialPins.value.any { it.speedDialId == id } ||
                podcastDialPins.value.any { it.speedDialId == id }
            )

    /** Whether [item] is an artist (a YouTube channel), which may have a dial of its own. */
    fun isArtist(item: ShelfItem): Boolean = item.videoId == null && item.browseId?.startsWith("UC") == true

    /**
     * Pins [item], or unpins it if it is already pinned. Returns whether it is
     * pinned now. An artist goes to the artists' own dial while that is on.
     */
    fun toggleSpeedDial(item: ShelfItem): Boolean {
        val id = item.speedDialId ?: return false
        // Pinning something taken off the dial brings it back.
        if (id in hiddenFromDial.value) setHiddenFromDial(hiddenFromDial.value - id)
        // A podcast show goes to the Podcasts page's dial. One pinned to the
        // speed dial before that page was there comes off the speed dial as before.
        if (item.isPodcastShow && speedDialPins.value.none { it.speedDialId == id }) {
            val current = podcastDialPins.value
            val pinned = current.none { it.speedDialId == id }
            setPodcastDialPins(if (pinned) (current + item).takeLast(SPEED_DIAL_SIZE) else current.filterNot { it.speedDialId == id })
            return pinned
        }
        if (isArtist(item) && (artistDial.value || artistDialPins.value.any { it.speedDialId == id })) {
            val current = artistDialPins.value
            val pinned = current.none { it.speedDialId == id }
            setArtistDialPins(if (pinned) (current + item).takeLast(SPEED_DIAL_SIZE) else current.filterNot { it.speedDialId == id })
            // Not on both dials at once.
            if (pinned) setSpeedDialPins(speedDialPins.value.filterNot { it.speedDialId == id })
            return pinned
        }
        val current = speedDialPins.value
        val pinned = current.none { it.speedDialId == id }
        val updated = if (pinned) {
            (current + item).takeLast(SPEED_DIAL_SIZE)
        } else {
            current.filterNot { it.speedDialId == id }
        }
        setSpeedDialPins(updated)
        return pinned
    }

    /**
     * Gives pinned tiles the art [items] carry for them now. A pin is saved
     * with the art it had when it was pinned, but YouTube redraws the cover of
     * a mix it rebuilds (Discover Mix, Supermix, New Release Mix) with each
     * rebuild, so the pinned tile would otherwise keep last week's.
     */
    fun refreshPinArt(items: List<ShelfItem>) {
        val art = HashMap<String, String>()
        items.forEach { item ->
            val id = item.speedDialId ?: return@forEach
            item.thumbnailUrl?.let { art.putIfAbsent(id, it) }
        }
        refreshPinArt(art)
    }

    /** [refreshPinArt] from art already keyed by the pin's id. */
    fun refreshPinArt(art: Map<String, String>) {
        if (art.isEmpty()) return
        fun fresh(pins: List<ShelfItem>) = pins.map { pin ->
            val url = art[pin.speedDialId]
            if (url == null || url == pin.thumbnailUrl) pin else pin.copy(thumbnailUrl = url)
        }
        val speed = fresh(speedDialPins.value)
        if (speed != speedDialPins.value) setSpeedDialPins(speed)
        val artists = fresh(artistDialPins.value)
        if (artists != artistDialPins.value) setArtistDialPins(artists)
    }

    /**
     * The pinned mixes YouTube builds for the listener and rebuilds over time,
     * whose `RDTMAK` ids are the same from one rebuild to the next.
     */
    fun pinnedDynamicMixes(): List<String> =
        speedDialPins.value.mapNotNull { pin -> pin.browseId?.takeIf { pin.videoId == null && isDynamicMix(it) } }

    fun isDynamicMix(browseId: String): Boolean = browseId.removePrefix("VL").startsWith("RDTMAK")

    /** Replaces the pins, in the order given. */
    fun setSpeedDialPins(value: List<ShelfItem>) {
        val updated = value.distinctBy { it.speedDialId }.take(SPEED_DIAL_SIZE)
        speedDialPins.value = updated
        store.edit { putString(KEY_SPEED_DIAL_PINS, DialPins.encode(updated)) }
    }

    /** Replaces the artists' dial's pins, in the order given. */
    fun setArtistDialPins(value: List<ShelfItem>) {
        val updated = value.distinctBy { it.speedDialId }.take(SPEED_DIAL_SIZE)
        artistDialPins.value = updated
        store.edit { putString(KEY_ARTIST_DIAL_PINS, DialPins.encode(updated)) }
    }

    /** Replaces the Podcasts dial's pins, in the order given. */
    fun setPodcastDialPins(value: List<ShelfItem>) {
        val updated = value.distinctBy { it.speedDialId }.take(SPEED_DIAL_SIZE)
        if (updated == podcastDialPins.value) return
        podcastDialPins.value = updated
        store.edit { putString(KEY_PODCAST_DIAL_PINS, DialPins.encode(updated)) }
    }

    /**
     * Takes [item] off whichever dial it is on: unpinned, and kept off even
     * where YouTube's own speed dial, Recently played or the play counts would
     * put it back — until it is pinned again.
     */
    fun removeFromDial(item: ShelfItem) {
        val id = item.speedDialId ?: return
        setSpeedDialPins(speedDialPins.value.filterNot { it.speedDialId == id })
        setArtistDialPins(artistDialPins.value.filterNot { it.speedDialId == id })
        setPodcastDialPins(podcastDialPins.value.filterNot { it.speedDialId == id })
        setHiddenFromDial(hiddenFromDial.value + id)
    }

    private fun setHiddenFromDial(value: Set<String>) {
        hiddenFromDial.value = value
        store.edit { putStringSet(KEY_HIDDEN_FROM_DIAL, value) }
    }

    /**
     * Pins first, then [picks], minus what was taken off the dial, up to
     * [SPEED_DIAL_SIZE]. The dial is music: podcast shows and episodes in
     * [picks] are left to the Podcasts page's dial. A pin stays, as the
     * listener put it there.
     */
    fun assembleDial(pins: List<ShelfItem>, picks: List<ShelfItem>): List<ShelfItem> {
        val hidden = hiddenFromDial.value
        return (pins + picks.filterNot(isPodcastTile))
            .filter { it.speedDialId != null && it.speedDialId !in hidden }
            .distinctBy { it.speedDialId }
            .take(SPEED_DIAL_SIZE)
    }

    // ── Labels and look ─────────────────────────────────────────────────

    /** Whether a dial tile's title shows: the default, unless the tile overrides it. */
    fun dialLabelShown(id: String?): Boolean = dialLabels.value != (id != null && id in dialHiddenLabels.value)

    /** Shows or hides one dial tile's title, against the default. */
    fun toggleDialLabel(item: ShelfItem) {
        val id = item.speedDialId ?: return
        val value = if (id in dialHiddenLabels.value) dialHiddenLabels.value - id else dialHiddenLabels.value + id
        setDialLabelOverrides(value)
    }

    /** Shows or hides every tile's title; the tiles' own choices start over from it. */
    fun setDialLabels(value: Boolean) {
        dialLabels.value = value
        store.edit { putBoolean(KEY_DIAL_LABELS, value) }
        setDialLabelOverrides(emptySet())
    }

    private fun setDialLabelOverrides(value: Set<String>) {
        dialHiddenLabels.value = value
        store.edit { putStringSet(KEY_DIAL_HIDDEN_LABELS, value) }
    }

    fun setDialPinIcon(value: Boolean) {
        dialPinIcon.value = value
        store.edit { putBoolean(KEY_DIAL_PIN_ICON, value) }
    }

    fun setDialTileStyle(value: DialTileStyle) {
        dialTileStyle.value = value
        store.edit {
            putString(KEY_DIAL_TILE_STYLE, value.name)
            remove(KEY_DIAL_CONTOURED_EDGE)
        }
    }

    // ── Which dials ─────────────────────────────────────────────────────

    fun setFeelingLucky(value: Boolean) {
        feelingLucky.value = value
        store.edit { putBoolean(KEY_FEELING_LUCKY, value) }
    }

    /** Pins are kept while the dials are off, so turning them back on restores them. */
    fun setSpeedDials(value: Boolean) {
        speedDials.value = value
        store.edit { putBoolean(KEY_SPEED_DIALS, value) }
    }

    fun setMixesDial(value: Boolean) {
        mixesDial.value = value
        store.edit { putBoolean(KEY_MIXES_DIAL, value) }
    }

    /** Turning it on moves pinned artists onto their dial; off, back onto the speed dial. */
    fun setArtistDial(value: Boolean) {
        artistDial.value = value
        store.edit { putBoolean(KEY_ARTIST_DIAL, value) }
        if (value) {
            val artists = speedDialPins.value.filter { isArtist(it) }
            if (artists.isNotEmpty()) {
                setArtistDialPins(artistDialPins.value + artists)
                setSpeedDialPins(speedDialPins.value.filterNot { isArtist(it) })
            }
        } else if (artistDialPins.value.isNotEmpty()) {
            setSpeedDialPins(speedDialPins.value + artistDialPins.value)
            setArtistDialPins(emptyList())
        }
    }

    fun setSpeedDialSource(value: SpeedDialSource) {
        speedDialSource.value = value
        store.edit { putString(KEY_SPEED_DIAL_SOURCE, value.name) }
    }

    fun setSpotifyDialSeeds(value: SpotifyDialSeeds) {
        spotifyDialSeeds.value = value
        store.edit { putString(KEY_SPOTIFY_DIAL_SEEDS, value.name) }
    }

    fun setSpeedDialRows(value: Int) {
        val rows = value.coerceIn(3, 4)
        speedDialRows.value = rows
        store.edit { putInt(KEY_SPEED_DIAL_ROWS, rows) }
    }

    // The phone's preference keys, unchanged since before the dials were shared.
    private const val KEY_SPEED_DIAL_PINS = "custom_speed_dial_pins"
    private const val KEY_SPEED_DIALS = "custom_speed_dials"
    private const val KEY_ARTIST_DIAL = "custom_artist_dial"
    private const val KEY_FEELING_LUCKY = "custom_feeling_lucky"
    private const val KEY_MIXES_DIAL = "custom_mixes_dial"
    private const val KEY_ARTIST_DIAL_PINS = "custom_artist_dial_pins"
    private const val KEY_PODCAST_DIAL_PINS = "custom_podcast_dial_pins"
    private const val KEY_HIDDEN_FROM_DIAL = "custom_hidden_from_dial"
    private const val KEY_DIAL_HIDDEN_LABELS = "custom_dial_hidden_labels"
    private const val KEY_DIAL_PIN_ICON = "custom_dial_pin_icon"
    private const val KEY_DIAL_LABELS = "custom_dial_labels"
    private const val KEY_DIAL_CONTOURED_EDGE = "custom_dial_contoured_edge"
    private const val KEY_DIAL_TILE_STYLE = "custom_dial_tile_style"
    private const val KEY_SPEED_DIAL_ROWS = "custom_speed_dial_rows"
    private const val KEY_SPEED_DIAL_SOURCE = "custom_speed_dial_source"
    private const val KEY_SPOTIFY_DIAL_SEEDS = "custom_spotify_dial_seeds"
}
