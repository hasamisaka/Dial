package com.music.bitchord.data.settings

import android.content.SharedPreferences
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.podcasts.isPodcastTile
import com.music.bitchord.data.podcasts.PodcastSource
import com.music.bitchord.ui.components.DialTileStyle
import com.music.bitchord.ui.components.GlassTint
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/** A page the bottom bar can lead to. [id] is the tab index MainActivity keys its pages by. */
enum class NavPage(val id: Int) {
    HOME(0),
    EXPLORE(1),
    LIBRARY(2),
    SEARCH(3),
    PLAYLISTS(4),
    PODCASTS(5),
    ;

    companion object {
        fun fromId(id: Int): NavPage? = entries.firstOrNull { it.id == id }

        /** Pages that can sit in the bar's main pill. Search always has its own button at the end. */
        val pillPages = listOf(HOME, EXPLORE, PODCASTS, LIBRARY, PLAYLISTS)
        val defaultPill = listOf(HOME, PLAYLISTS, PODCASTS, LIBRARY)

        /**
         * The bar's pages for a listener who has not set them: [defaultPill]
         * in its order, then Explore, off. Home's category chips do what
         * Explore's moods did. A saved choice keeps its own order and
         * switches; [pillPages] only places a page it does not have yet.
         */
        val defaultOrder = defaultPill + EXPLORE
    }
}

enum class MiniPlayerShape { CIRCULAR, SQUARED }

/**
 * Which library the Library tab shows: the YouTube Music account's, the
 * device's (local files and downloads together), or both at once.
 */
enum class LibrarySource { YOUTUBE_MUSIC, LOCAL, ALL }

/** One entry in an ordered list where each entry can also be switched off. */
data class OrderedToggle<T>(val item: T, val enabled: Boolean)

/**
 * Layout and navigation preferences: the bottom bar, the start page, the mini
 * player's shape, the Home page's sections and the speed dial pins.
 *
 * Stored in the same preference file as [AppSettings], so backups and restores
 * carry them along with everything else.
 */
object CustomizationSettings {
    private var prefs: SharedPreferences? = null

    /** The pages in the bar's main pill, in order, each either shown or hidden. */
    val navTabs = MutableStateFlow(NavPage.defaultOrder.map { OrderedToggle(it, it in NavPage.defaultPill) })
    val startPage = MutableStateFlow(NavPage.HOME)
    val keepNavBarExpanded = MutableStateFlow(false)

    /** The skip button on the miniplayer. Off: swiping it sideways still skips either way. */
    val miniPlayerNextButton = MutableStateFlow(false)

    /** Fill thumbnails that aren't square, trimming any bars baked into them, instead of fitting them. */
    val cropThumbnails = MutableStateFlow(true)

    /**
     * A playlist still wearing YouTube Music's default 2x2 grid gets a moving
     * collage of its most recently added covers on its page instead.
     */
    val generatePlaylistImages = MutableStateFlow(true)

    /** Playlists (keyed without their `VL`) whose page keeps its own artwork. */
    val playlistBackdropsOff = MutableStateFlow<Set<String>>(emptySet())

    // The dial layout lives in the shared module's DialSettings, kept in these
    // same preferences under the same keys; these names stay for the app's callers.
    val speedDials get() = DialSettings.speedDials
    val artistDial get() = DialSettings.artistDial
    val feelingLucky get() = DialSettings.feelingLucky
    val mixesDial get() = DialSettings.mixesDial
    val artistDialPins get() = DialSettings.artistDialPins
    val hiddenFromDial get() = DialSettings.hiddenFromDial
    val dialLabels get() = DialSettings.dialLabels
    val dialHiddenLabels get() = DialSettings.dialHiddenLabels
    val dialPinIcon get() = DialSettings.dialPinIcon
    val dialTileStyle get() = DialSettings.dialTileStyle
    val speedDialSource get() = DialSettings.speedDialSource
    val spotifyDialSeeds get() = DialSettings.spotifyDialSeeds
    val speedDialRows get() = DialSettings.speedDialRows

    val miniPlayerShape = MutableStateFlow(MiniPlayerShape.SQUARED)

    /**
     * Over a Spotify Canvas, shrink the controls to the foot of the screen
     * instead of hiding them: title, live lyrics and transport stay.
     */
    val compactCanvasControls = MutableStateFlow(true)

    /** How the player's seek bar is drawn: Glow (the default), Wave or Plain. */
    val progressBarStyle = MutableStateFlow(ProgressBarStyle.GLOW)

    /** One UI 9's wave progress on the player's seek bar; follows [progressBarStyle]. */
    val wavyProgressBar = MutableStateFlow(false)

    /** The OnePlus-style glow bar on the player's seek bar; follows [progressBarStyle]. */
    val glowProgressBar = MutableStateFlow(true)

    /**
     * Icons the listener gave audio outputs from the player's output button, by the
     * player's output key ("BLUETOOTH:Pixel Buds Pro"), each an AudioOutputIcon name.
     */
    val outputIcons = MutableStateFlow<Map<String, String>>(emptyMap())

    private val DEFAULT_TAB_VIEWS = listOf(LibraryViewType.LIST, LibraryViewType.LIST, LibraryViewType.GRID)

    /** List or grid for each folder tab — songs, artists, albums — on its own. */
    val libraryTabViews = MutableStateFlow(DEFAULT_TAB_VIEWS)

    /** Slow colour blobs behind the app's pages. */
    val ambientBackground = MutableStateFlow(true)

    /**
     * Those blobs hold still: the colours stay, the drift stops. Each step of
     * the drift redraws the page and every blurred bar over it, a few times a
     * second for as long as the app is in front.
     */
    val ambientStatic = MutableStateFlow(false)

    /** Whether those blobs take the playing song's artwork colour. */
    val ambientFollowsArtwork = MutableStateFlow(true)

    /** How see-through Liquid Glass surfaces are, 0 (opaque fill) to 1 (no fill at all). */
    val glassTransparency = MutableStateFlow(DEFAULT_GLASS_TRANSPARENCY)

    /** The colour washed into Liquid Glass surfaces; it ships as the theme's own black or white. */
    val glassTint = MutableStateFlow(GlassTint.themeNeutral(dark = true))

    /** How far the glass's fill is darkened towards black, 0 (not at all) to 1. */
    val glassTintDarkness = MutableStateFlow(DEFAULT_GLASS_TINT_DARKNESS)

    /** How much colour Liquid Glass gives what is behind it: 0 grey, 1 as it is, 2 doubled. */
    val glassSaturation = MutableStateFlow(DEFAULT_GLASS_SATURATION)

    /** How far Liquid Glass blurs what is behind it, in dp. */
    val glassBlur = MutableStateFlow(DEFAULT_GLASS_BLUR)

    /**
     * Draws Liquid Glass in the New (clear) style, ClearLiquidGlass.kt,
     * instead of the Classic frosted one.
     */
    val clearGlassStyle = MutableStateFlow(true)

    /**
     * The New style's own options, kept apart from Classic's because the two
     * ship at very different settings (a trace of frost against a full blur).
     * Transparency here is how little of the theme's surface is laid in under
     * the clear veil: 1 is none, as the style ships, 0 is opaque.
     */
    val clearGlassTransparency = MutableStateFlow(DEFAULT_CLEAR_GLASS_TRANSPARENCY)
    val clearGlassBlur = MutableStateFlow(DEFAULT_CLEAR_GLASS_BLUR)
    val clearGlassSaturation = MutableStateFlow(DEFAULT_CLEAR_GLASS_SATURATION)

    /** The New style's own tint; it ships black on the dark theme and white on the light one. */
    val clearGlassTint = MutableStateFlow(DEFAULT_CLEAR_GLASS_TINT)

    /**
     * The New style's tint darkness, one for each theme: a dark page takes
     * more shade than a light one before the glass reads as darkened.
     */
    val clearGlassTintDarkness = MutableStateFlow(DEFAULT_CLEAR_GLASS_TINT_DARKNESS)
    val clearGlassTintDarknessLight = MutableStateFlow(DEFAULT_CLEAR_GLASS_TINT_DARKNESS_LIGHT)

    /**
     * The light theme's own copy of every glass option above (the ones above
     * are the dark theme's), so the glass can be set up for each theme apart.
     * Until one is changed it holds what the dark theme's was, but for the
     * tint and its darkness, which ship white and unshaded on the light theme.
     */
    val glassTransparencyLight = MutableStateFlow(DEFAULT_GLASS_TRANSPARENCY)
    val glassTintLight = MutableStateFlow(GlassTint.themeNeutral(dark = false))
    val glassTintDarknessLight = MutableStateFlow(DEFAULT_GLASS_TINT_DARKNESS_LIGHT)
    val glassSaturationLight = MutableStateFlow(DEFAULT_GLASS_SATURATION)
    val glassBlurLight = MutableStateFlow(DEFAULT_GLASS_BLUR)
    val clearGlassTransparencyLight = MutableStateFlow(DEFAULT_CLEAR_GLASS_TRANSPARENCY)
    val clearGlassBlurLight = MutableStateFlow(DEFAULT_CLEAR_GLASS_BLUR)
    val clearGlassSaturationLight = MutableStateFlow(DEFAULT_CLEAR_GLASS_SATURATION)
    val clearGlassTintLight = MutableStateFlow(DEFAULT_CLEAR_GLASS_TINT_LIGHT)

    /** One theme's glass options for one style, Classic or New. */
    class GlassOptionSet(
        val transparency: MutableStateFlow<Float>,
        val blur: MutableStateFlow<Float>,
        val saturation: MutableStateFlow<Float>,
        val tint: MutableStateFlow<GlassTint>,
        val darkness: MutableStateFlow<Float>,
    )

    private val classicDarkGlass by lazy {
        GlassOptionSet(glassTransparency, glassBlur, glassSaturation, glassTint, glassTintDarkness)
    }
    private val classicLightGlass by lazy {
        GlassOptionSet(glassTransparencyLight, glassBlurLight, glassSaturationLight, glassTintLight, glassTintDarknessLight)
    }
    private val clearDarkGlass by lazy {
        GlassOptionSet(clearGlassTransparency, clearGlassBlur, clearGlassSaturation, clearGlassTint, clearGlassTintDarkness)
    }
    private val clearLightGlass by lazy {
        GlassOptionSet(
            clearGlassTransparencyLight,
            clearGlassBlurLight,
            clearGlassSaturationLight,
            clearGlassTintLight,
            clearGlassTintDarknessLight,
        )
    }

    /** The glass options for the New ([clear]) or Classic style, in the [dark] or the light theme. */
    fun glassOptions(clear: Boolean, dark: Boolean): GlassOptionSet = when {
        clear && dark -> clearDarkGlass
        clear -> clearLightGlass
        dark -> classicDarkGlass
        else -> classicLightGlass
    }

    /** The library last picked from the Library page's logo; remembered across launches. */
    val librarySource = MutableStateFlow(LibrarySource.ALL)

    /** Items pinned to the speed dial, in the order they were pinned. */
    val speedDialPins get() = DialSettings.speedDialPins

    /**
     * The speed dial as Home last showed it, saved so the next launch opens on
     * the same tiles straight away instead of building them up as the feed,
     * Recently played and the rest come in.
     */
    val lastSpeedDial = MutableStateFlow<List<ShelfItem>>(emptyList())

    /** Which of [lastSpeedDial]'s tiles were tagged as new releases. */
    val lastDialNewReleases = MutableStateFlow<Set<String>>(emptySet())

    /** Shows pinned to the Podcasts page's dial, in the order they were pinned. */
    val podcastDialPins get() = DialSettings.podcastDialPins

    /** The Podcasts page's dial as it last showed, for the page to open on at once; see [lastSpeedDial]. */
    val lastPodcastDial = MutableStateFlow<List<ShelfItem>>(emptyList())

    /**
     * "Shows for you" as YouTube Music last gave it, under the title it had.
     * YouTube leaves that shelf out of some answers; this stands in then, and
     * is the Podcasts page's row of large cards until its feed is in.
     */
    val podcastShowsForYou = MutableStateFlow<HomeShelf?>(null)

    /** Whose [podcastShowsForYou] is: another listener's shows are not this one's. */
    private var podcastShowsListener = ""

    /**
     * Whether the Podcasts page's dial has the "Episodes for later" tile, where
     * the speed dial has its die. Off until it is asked for.
     */
    val podcastEpisodesForLater = MutableStateFlow(false)

    /** Whose recommended shows fill the Podcasts page's dial after the pins. */
    val podcastSource = MutableStateFlow(PodcastSource.YOUTUBE)

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        DialSettings.isPodcastTile = { it.isPodcastTile }
        DialSettings.init(SharedPreferencesDialStore(prefs))
        readAll()
    }

    fun readAll() {
        val p = prefs ?: return
        navTabs.value = readOrdered(
            p.getString(KEY_NAV_TABS, null),
            NavPage.pillPages,
            NavPage.defaultPill.toSet(),
            defaultOrder = NavPage.defaultOrder,
        ) {
            NavPage.entries.firstOrNull { page -> page.name == it }
        }.let(::ensureOneTab)
        startPage.value = NavPage.entries.firstOrNull { it.name == p.getString(KEY_START_PAGE, null) } ?: NavPage.HOME
        keepNavBarExpanded.value = p.getBoolean(KEY_KEEP_NAV_EXPANDED, false)
        miniPlayerNextButton.value = p.getBoolean(KEY_MINI_PLAYER_NEXT, false)
        cropThumbnails.value = p.getBoolean(KEY_CROP_THUMBNAILS, true)
        generatePlaylistImages.value = p.getBoolean(KEY_GENERATE_PLAYLIST_IMAGES, true)
        playlistBackdropsOff.value = p.getStringSet(KEY_PLAYLIST_BACKDROPS_OFF, null)?.toSet().orEmpty()
        miniPlayerShape.value = MiniPlayerShape.entries
            .firstOrNull { it.name == p.getString(KEY_MINI_PLAYER_SHAPE, null) } ?: MiniPlayerShape.SQUARED
        compactCanvasControls.value = p.getBoolean(KEY_COMPACT_CANVAS_CONTROLS, true)
        // Before the picker there were two switches. Only a deliberate "both off" carries over
        // (as Plain); everyone else moves to the new default, Glow.
        val style = ProgressBarStyle.entries.firstOrNull { it.name == p.getString(KEY_PROGRESS_BAR_STYLE, null) }
            ?: if (p.contains(KEY_WAVY_PROGRESS_BAR) && !p.getBoolean(KEY_WAVY_PROGRESS_BAR, true) &&
                !p.getBoolean(KEY_GLOW_PROGRESS_BAR, false)
            ) ProgressBarStyle.PLAIN else ProgressBarStyle.GLOW
        applyProgressBarStyle(style)
        outputIcons.value = readOutputIcons(p.getString(KEY_OUTPUT_ICONS, null))
        libraryTabViews.value = p.getString(KEY_LIBRARY_TAB_VIEWS, null)
            ?.split(",")
            ?.mapNotNull { name -> LibraryViewType.entries.firstOrNull { it.name == name } }
            ?.takeIf { it.size == DEFAULT_TAB_VIEWS.size }
            ?: DEFAULT_TAB_VIEWS
        ambientBackground.value = p.getBoolean(KEY_AMBIENT_BACKGROUND, true)
        ambientStatic.value = p.getBoolean(KEY_AMBIENT_STATIC, false)
        ambientFollowsArtwork.value = p.getBoolean(KEY_AMBIENT_FOLLOWS_ARTWORK, true)
        // The glass's defaults were redrawn (90% see-through, 4dp, 90%
        // saturation, black shaded 30% on dark, white unshaded on light). The
        // light theme's set was written out on first launch whether or not it
        // was ever touched, so what is saved cannot be told from a choice:
        // every install starts once from the new defaults.
        if (p.getInt(KEY_GLASS_DEFAULTS, 0) < GLASS_DEFAULTS_VERSION) {
            val edit = p.edit()
            for (key in GLASS_OPTION_KEYS) edit.remove(key).remove(key + LIGHT)
            edit.remove(KEY_CLEAR_GLASS_TINT_DARKNESS_LIGHT)
                .putInt(KEY_GLASS_DEFAULTS, GLASS_DEFAULTS_VERSION)
                .apply()
            // The removals are read back below: apply() updates the in-memory
            // map at once, the disk write follows.
        }
        glassTransparency.value = p.getFloat(KEY_GLASS_TRANSPARENCY, DEFAULT_GLASS_TRANSPARENCY).coerceIn(0f, 1f)
        glassTint.value = GlassTint.fromSaved(p.getString(KEY_GLASS_TINT, null), dark = true)
            ?: GlassTint.themeNeutral(dark = true)
        glassTintDarkness.value = p.getFloat(KEY_GLASS_TINT_DARKNESS, DEFAULT_GLASS_TINT_DARKNESS).coerceIn(0f, 1f)
        glassBlur.value = p.getFloat(KEY_GLASS_BLUR, DEFAULT_GLASS_BLUR).coerceIn(0f, MAX_GLASS_BLUR)
        glassSaturation.value = p.getFloat(KEY_GLASS_SATURATION, DEFAULT_GLASS_SATURATION).coerceIn(0f, MAX_GLASS_SATURATION)
        clearGlassStyle.value = p.getBoolean(KEY_CLEAR_GLASS_STYLE, true)
        clearGlassTransparency.value =
            p.getFloat(KEY_CLEAR_GLASS_TRANSPARENCY, DEFAULT_CLEAR_GLASS_TRANSPARENCY).coerceIn(0f, 1f)
        clearGlassBlur.value = p.getFloat(KEY_CLEAR_GLASS_BLUR, DEFAULT_CLEAR_GLASS_BLUR).coerceIn(0f, MAX_GLASS_BLUR)
        clearGlassSaturation.value =
            p.getFloat(KEY_CLEAR_GLASS_SATURATION, DEFAULT_CLEAR_GLASS_SATURATION).coerceIn(0f, MAX_GLASS_SATURATION)
        clearGlassTint.value = GlassTint.fromSaved(p.getString(KEY_CLEAR_GLASS_TINT, null), dark = true)
            ?: DEFAULT_CLEAR_GLASS_TINT
        clearGlassTintDarkness.value =
            p.getFloat(KEY_CLEAR_GLASS_TINT_DARKNESS, DEFAULT_CLEAR_GLASS_TINT_DARKNESS).coerceIn(0f, 1f)
        clearGlassTintDarknessLight.value =
            p.getFloat(KEY_CLEAR_GLASS_TINT_DARKNESS_LIGHT, DEFAULT_CLEAR_GLASS_TINT_DARKNESS_LIGHT).coerceIn(0f, 1f)
        // The light theme's set: what the dark theme has, until it is given its own.
        glassTransparencyLight.value =
            p.getFloat(KEY_GLASS_TRANSPARENCY + LIGHT, glassTransparency.value).coerceIn(0f, 1f)
        // The tint and its darkness are the light theme's own from the start.
        glassTintLight.value = GlassTint.fromSaved(p.getString(KEY_GLASS_TINT + LIGHT, null), dark = false)
            ?: GlassTint.themeNeutral(dark = false)
        glassTintDarknessLight.value =
            p.getFloat(KEY_GLASS_TINT_DARKNESS + LIGHT, DEFAULT_GLASS_TINT_DARKNESS_LIGHT).coerceIn(0f, 1f)
        glassBlurLight.value = p.getFloat(KEY_GLASS_BLUR + LIGHT, glassBlur.value).coerceIn(0f, MAX_GLASS_BLUR)
        glassSaturationLight.value =
            p.getFloat(KEY_GLASS_SATURATION + LIGHT, glassSaturation.value).coerceIn(0f, MAX_GLASS_SATURATION)
        clearGlassTransparencyLight.value =
            p.getFloat(KEY_CLEAR_GLASS_TRANSPARENCY + LIGHT, clearGlassTransparency.value).coerceIn(0f, 1f)
        clearGlassBlurLight.value =
            p.getFloat(KEY_CLEAR_GLASS_BLUR + LIGHT, clearGlassBlur.value).coerceIn(0f, MAX_GLASS_BLUR)
        clearGlassSaturationLight.value =
            p.getFloat(KEY_CLEAR_GLASS_SATURATION + LIGHT, clearGlassSaturation.value).coerceIn(0f, MAX_GLASS_SATURATION)
        clearGlassTintLight.value = GlassTint.fromSaved(p.getString(KEY_CLEAR_GLASS_TINT + LIGHT, null), dark = false)
            ?: DEFAULT_CLEAR_GLASS_TINT_LIGHT
        // Written out the first time, so a later change to the dark theme's
        // options does not move the light theme's on the next launch.
        if (!p.contains(KEY_GLASS_TRANSPARENCY + LIGHT)) {
            p.edit()
                .putFloat(KEY_GLASS_TRANSPARENCY + LIGHT, glassTransparencyLight.value)
                .putString(KEY_GLASS_TINT + LIGHT, glassTintLight.value.name)
                .putFloat(KEY_GLASS_TINT_DARKNESS + LIGHT, glassTintDarknessLight.value)
                .putFloat(KEY_GLASS_BLUR + LIGHT, glassBlurLight.value)
                .putFloat(KEY_GLASS_SATURATION + LIGHT, glassSaturationLight.value)
                .putFloat(KEY_CLEAR_GLASS_TRANSPARENCY + LIGHT, clearGlassTransparencyLight.value)
                .putFloat(KEY_CLEAR_GLASS_BLUR + LIGHT, clearGlassBlurLight.value)
                .putFloat(KEY_CLEAR_GLASS_SATURATION + LIGHT, clearGlassSaturationLight.value)
                .putString(KEY_CLEAR_GLASS_TINT + LIGHT, clearGlassTintLight.value.name)
                .apply()
        }
        librarySource.value = LibrarySource.entries
            .firstOrNull { it.name == p.getString(KEY_LIBRARY_SOURCE, null) } ?: LibrarySource.ALL
        lastSpeedDial.value = readPins(p.getString(KEY_LAST_SPEED_DIAL, null))
        lastDialNewReleases.value = p.getStringSet(KEY_LAST_DIAL_NEW_RELEASES, emptySet())?.toSet().orEmpty()
        lastPodcastDial.value = readPins(p.getString(KEY_LAST_PODCAST_DIAL, null))
        podcastEpisodesForLater.value = p.getBoolean(KEY_PODCAST_EPISODES_FOR_LATER, false)
        podcastSource.value = PodcastSource.entries
            .firstOrNull { it.name == p.getString(KEY_PODCAST_SOURCE, null) } ?: PodcastSource.YOUTUBE
        podcastShowsListener = p.getString(KEY_PODCAST_SHOWS_LISTENER, null).orEmpty()
        podcastShowsForYou.value = readPins(p.getString(KEY_PODCAST_SHOWS_FOR_YOU, null))
            .takeIf { it.isNotEmpty() }
            ?.let { HomeShelf(p.getString(KEY_PODCAST_SHOWS_TITLE, null).orEmpty(), it) }
        // Retired settings: tilt parallax and the ambient style picker are gone.
        if (p.contains(KEY_RETIRED_DIAL_PARALLAX) || p.contains(KEY_RETIRED_AMBIENT_STYLE)) {
            p.edit().remove(KEY_RETIRED_DIAL_PARALLAX).remove(KEY_RETIRED_AMBIENT_STYLE).apply()
        }
    }

    // ── Speed dial (shared DialSettings) ────────────────────────────────

    fun isPinnedToSpeedDial(id: String?): Boolean = DialSettings.isPinnedToSpeedDial(id)

    /** Whether [item] is an artist (a YouTube channel), which may have a dial of its own. */
    fun isArtist(item: ShelfItem): Boolean = DialSettings.isArtist(item)

    /** Pins [item], or unpins it; see [DialSettings.toggleSpeedDial]. */
    fun toggleSpeedDial(item: ShelfItem): Boolean = DialSettings.toggleSpeedDial(item)

    /** Whether this launch has refreshed the pins' art yet: once per fresh start. */
    var pinArtRefreshed: Boolean
        get() = DialSettings.pinArtRefreshed
        set(value) { DialSettings.pinArtRefreshed = value }

    fun refreshPinArt(items: List<ShelfItem>) = DialSettings.refreshPinArt(items)

    fun refreshPinArt(art: Map<String, String>) = DialSettings.refreshPinArt(art)

    fun pinnedDynamicMixes(): List<String> = DialSettings.pinnedDynamicMixes()

    fun isDynamicMix(browseId: String): Boolean = DialSettings.isDynamicMix(browseId)

    fun setSpeedDialPins(value: List<ShelfItem>) = DialSettings.setSpeedDialPins(value)

    fun removeFromDial(item: ShelfItem) = DialSettings.removeFromDial(item)

    fun dialLabelShown(id: String?): Boolean = DialSettings.dialLabelShown(id)

    fun toggleDialLabel(item: ShelfItem) = DialSettings.toggleDialLabel(item)

    fun setDialLabels(value: Boolean) = DialSettings.setDialLabels(value)

    fun setDialPinIcon(value: Boolean) = DialSettings.setDialPinIcon(value)

    fun setDialTileStyle(value: DialTileStyle) = DialSettings.setDialTileStyle(value)

    fun setArtistDialPins(value: List<ShelfItem>) = DialSettings.setArtistDialPins(value)

    fun setPodcastDialPins(value: List<ShelfItem>) = DialSettings.setPodcastDialPins(value)

    /** Saves the dial the Podcasts page is showing, for the page to open on next time. */
    fun setLastPodcastDial(value: List<ShelfItem>) {
        if (value == lastPodcastDial.value) return
        lastPodcastDial.value = value
        prefs?.edit()?.putString(KEY_LAST_PODCAST_DIAL, writeItems(value))?.apply()
    }

    /** [podcastShowsForYou], when it is [listener]'s (null is a guest); null when it is someone else's. */
    fun podcastShowsFor(listener: String?): HomeShelf? =
        podcastShowsForYou.value.takeIf { podcastShowsListener == listener.orEmpty() }

    /** Keeps [listener]'s "Shows for you" shelf for the answers that leave it out. */
    fun setPodcastShowsForYou(shelf: HomeShelf, listener: String?) {
        if (shelf == podcastShowsForYou.value && podcastShowsListener == listener.orEmpty()) return
        podcastShowsForYou.value = shelf
        podcastShowsListener = listener.orEmpty()
        prefs?.edit()
            ?.putString(KEY_PODCAST_SHOWS_FOR_YOU, writeItems(shelf.items))
            ?.putString(KEY_PODCAST_SHOWS_TITLE, shelf.title)
            ?.putString(KEY_PODCAST_SHOWS_LISTENER, podcastShowsListener)
            ?.apply()
    }

    fun setPodcastSource(value: PodcastSource) {
        podcastSource.value = value
        prefs?.edit()?.putString(KEY_PODCAST_SOURCE, value.name)?.apply()
    }

    fun setPodcastEpisodesForLater(value: Boolean) {
        podcastEpisodesForLater.value = value
        prefs?.edit()?.putBoolean(KEY_PODCAST_EPISODES_FOR_LATER, value)?.apply()
    }

    fun setFeelingLucky(value: Boolean) = DialSettings.setFeelingLucky(value)

    fun setSpeedDials(value: Boolean) = DialSettings.setSpeedDials(value)

    fun setMixesDial(value: Boolean) = DialSettings.setMixesDial(value)

    fun setArtistDial(value: Boolean) = DialSettings.setArtistDial(value)

    /** Saves the dial Home is showing, for the next launch to open on. */
    fun setLastSpeedDial(value: List<ShelfItem>, newReleases: Set<String> = emptySet()) {
        if (value == lastSpeedDial.value && newReleases == lastDialNewReleases.value) return
        lastSpeedDial.value = value
        lastDialNewReleases.value = newReleases
        prefs?.edit()
            ?.putString(KEY_LAST_SPEED_DIAL, writeItems(value))
            ?.putStringSet(KEY_LAST_DIAL_NEW_RELEASES, newReleases)
            ?.apply()
    }

    private fun writeItems(items: List<ShelfItem>): String = DialPins.encode(items)

    /**
     * Up to [SPEED_DIAL_SIZE] items: pins first, then recently played, then
     * YouTube's own speed dial when the feed has one, then the rest of the feed.
     * A song played from an album or playlist stands for that album or
     * playlist, once, where its latest play would be (see [PlayedCollections]).
     *
     * Recently played goes ahead of YouTube's speed dial: that shelf ranks by
     * how often something has been played, not how lately, so it barely moves
     * from week to week and, coming first, filled the tiles after the pins
     * with the same things however the listening changed.
     */
    fun buildSpeedDial(pins: List<ShelfItem>, shelves: List<HomeShelf>): List<ShelfItem> =
        assembleDial(pins, historyPicks(shelves))

    /** [buildSpeedDial] without the pins: recently played, YouTube's speed dial, then the rest of the feed. */
    fun historyPicks(shelves: List<HomeShelf>): List<ShelfItem> {
        val official = asPlayed(shelfItems(shelves, SPEED_DIAL_TITLE))
        // Tracks from the library and other files never reach YouTube's history.
        val recents = asPlayed(DevicePlays.withDevicePlays(shelfItems(shelves, RECENTS_TITLE)))
        val rest = shelves
            .filterNot { it.title.equals(SPEED_DIAL_TITLE, true) || it.title.equals(RECENTS_TITLE, true) }
            .flatMap { it.items }
        return recents + official + rest
    }

    /**
     * The tiles past the pins for [source], drawn once per round (app load or
     * pull to refresh) by the caller, which holds on to them until the next.
     */
    fun pickSpeedDial(source: SpeedDialSource, shelves: List<HomeShelf>): DialPicks {
        fun items(match: (String) -> Boolean) = shelves.filter { match(it.title) }.flatMap { it.items }
        return SpeedDialPicker.pick(
            source = source,
            history = historyPicks(shelves),
            official = shelfItems(shelves, SPEED_DIAL_TITLE),
            listenAgain = items(SpeedDialPicker::isListenAgainShelf),
            recents = asPlayed(DevicePlays.withDevicePlays(shelfItems(shelves, RECENTS_TITLE))),
            forgotten = items(SpeedDialPicker::isForgottenShelf),
            quickPicks = items(SpeedDialPicker::isQuickPicksShelf),
            size = SPEED_DIAL_SIZE,
        )
    }

    /**
     * Pins first, then [picks], minus what was taken off the dial, up to
     * [SPEED_DIAL_SIZE]. The dial is music: podcast shows and episodes in
     * [picks] are left to the Podcasts page's dial. A pin stays, as the
     * listener put it there.
     */
    fun assembleDial(pins: List<ShelfItem>, picks: List<ShelfItem>): List<ShelfItem> =
        DialSettings.assembleDial(pins, picks)

    private fun shelfItems(shelves: List<HomeShelf>, title: String) =
        shelves.firstOrNull { it.title.equals(title, ignoreCase = true) }?.items.orEmpty()

    private fun asPlayed(items: List<ShelfItem>) = items.map { PlayedCollections.collectionFor(it.videoId) ?: it }

    private fun readPins(stored: String?): List<ShelfItem> = DialPins.decode(stored)

    /** The pages the bar shows, Search last. */
    fun visibleNavPages(tabs: List<OrderedToggle<NavPage>> = navTabs.value): List<NavPage> =
        tabs.filter { it.enabled }.map { it.item } + NavPage.SEARCH

    fun setNavTabs(value: List<OrderedToggle<NavPage>>) {
        val fixed = ensureOneTab(value)
        navTabs.value = fixed
        prefs?.edit()?.putString(KEY_NAV_TABS, writeOrdered(fixed) { it.name })?.apply()
    }

    fun setStartPage(value: NavPage) {
        startPage.value = value
        prefs?.edit()?.putString(KEY_START_PAGE, value.name)?.apply()
    }

    fun setMiniPlayerNextButton(value: Boolean) {
        miniPlayerNextButton.value = value
        prefs?.edit()?.putBoolean(KEY_MINI_PLAYER_NEXT, value)?.apply()
    }

    fun playlistBackdropKey(browseId: String): String = browseId.removePrefix("VL")

    /** Turns the generated backdrop on or off for one playlist's page. */
    fun setPlaylistBackdrop(browseId: String, on: Boolean) {
        val key = playlistBackdropKey(browseId)
        val next = if (on) playlistBackdropsOff.value - key else playlistBackdropsOff.value + key
        playlistBackdropsOff.value = next
        prefs?.edit()?.putStringSet(KEY_PLAYLIST_BACKDROPS_OFF, next)?.apply()
    }

    fun setGeneratePlaylistImages(value: Boolean) {
        generatePlaylistImages.value = value
        prefs?.edit()?.putBoolean(KEY_GENERATE_PLAYLIST_IMAGES, value)?.apply()
    }

    fun setCropThumbnails(value: Boolean) {
        cropThumbnails.value = value
        prefs?.edit()?.putBoolean(KEY_CROP_THUMBNAILS, value)?.apply()
    }

    fun setKeepNavBarExpanded(value: Boolean) {
        keepNavBarExpanded.value = value
        prefs?.edit()?.putBoolean(KEY_KEEP_NAV_EXPANDED, value)?.apply()
    }

    fun setLibrarySource(value: LibrarySource) {
        librarySource.value = value
        prefs?.edit()?.putString(KEY_LIBRARY_SOURCE, value.name)?.apply()
    }

    fun setLibraryTabView(tab: Int, value: LibraryViewType) {
        val updated = libraryTabViews.value.toMutableList()
        if (tab !in updated.indices) return
        updated[tab] = value
        libraryTabViews.value = updated
        prefs?.edit()?.putString(KEY_LIBRARY_TAB_VIEWS, updated.joinToString(",") { it.name })?.apply()
    }

    fun setAmbientBackground(value: Boolean) {
        ambientBackground.value = value
        prefs?.edit()?.putBoolean(KEY_AMBIENT_BACKGROUND, value)?.apply()
    }

    fun setAmbientStatic(value: Boolean) {
        ambientStatic.value = value
        prefs?.edit()?.putBoolean(KEY_AMBIENT_STATIC, value)?.apply()
    }

    fun setAmbientFollowsArtwork(value: Boolean) {
        ambientFollowsArtwork.value = value
        prefs?.edit()?.putBoolean(KEY_AMBIENT_FOLLOWS_ARTWORK, value)?.apply()
    }

    /*
     * Each glass setter writes the dark theme's option, or the light theme's
     * when [dark] is false. The light theme's keys are the dark one's plus
     * [LIGHT], and are always written out, a reset included: left out, a
     * light option reads back as whatever the dark theme's is.
     */

    fun setGlassTransparency(value: Float, dark: Boolean = true) =
        putGlass(glassOptions(clear = false, dark).transparency, KEY_GLASS_TRANSPARENCY, dark, value.coerceIn(0f, 1f))

    fun setGlassTint(value: GlassTint, dark: Boolean = true) =
        putGlassTint(glassOptions(clear = false, dark).tint, KEY_GLASS_TINT, dark, value)

    fun setGlassTintDarkness(value: Float, dark: Boolean = true) =
        putGlass(glassOptions(clear = false, dark).darkness, KEY_GLASS_TINT_DARKNESS, dark, value.coerceIn(0f, 1f))

    fun setGlassBlur(value: Float, dark: Boolean = true) =
        putGlass(glassOptions(clear = false, dark).blur, KEY_GLASS_BLUR, dark, value.coerceIn(0f, MAX_GLASS_BLUR))

    fun setGlassSaturation(value: Float, dark: Boolean = true) = putGlass(
        glassOptions(clear = false, dark).saturation,
        KEY_GLASS_SATURATION,
        dark,
        value.coerceIn(0f, MAX_GLASS_SATURATION),
    )

    fun setClearGlassStyle(value: Boolean) {
        clearGlassStyle.value = value
        prefs?.edit()?.putBoolean(KEY_CLEAR_GLASS_STYLE, value)?.apply()
    }

    fun setClearGlassTransparency(value: Float, dark: Boolean = true) = putGlass(
        glassOptions(clear = true, dark).transparency,
        KEY_CLEAR_GLASS_TRANSPARENCY,
        dark,
        value.coerceIn(0f, 1f),
    )

    fun setClearGlassBlur(value: Float, dark: Boolean = true) =
        putGlass(glassOptions(clear = true, dark).blur, KEY_CLEAR_GLASS_BLUR, dark, value.coerceIn(0f, MAX_GLASS_BLUR))

    fun setClearGlassSaturation(value: Float, dark: Boolean = true) = putGlass(
        glassOptions(clear = true, dark).saturation,
        KEY_CLEAR_GLASS_SATURATION,
        dark,
        value.coerceIn(0f, MAX_GLASS_SATURATION),
    )

    fun setClearGlassTint(value: GlassTint, dark: Boolean = true) =
        putGlassTint(glassOptions(clear = true, dark).tint, KEY_CLEAR_GLASS_TINT, dark, value)

    /** Sets the New style's tint darkness for the dark theme, or the light one's when [dark] is false. */
    fun setClearGlassTintDarkness(value: Float, dark: Boolean = true) {
        val clamped = value.coerceIn(0f, 1f)
        if (dark) {
            clearGlassTintDarkness.value = clamped
            prefs?.edit()?.putFloat(KEY_CLEAR_GLASS_TINT_DARKNESS, clamped)?.apply()
        } else {
            clearGlassTintDarknessLight.value = clamped
            prefs?.edit()?.putFloat(KEY_CLEAR_GLASS_TINT_DARKNESS_LIGHT, clamped)?.apply()
        }
    }

    private fun putGlass(flow: MutableStateFlow<Float>, key: String, dark: Boolean, value: Float) {
        flow.value = value
        prefs?.edit()?.putFloat(if (dark) key else key + LIGHT, value)?.apply()
    }

    private fun putGlassTint(flow: MutableStateFlow<GlassTint>, key: String, dark: Boolean, value: GlassTint) {
        flow.value = value
        prefs?.edit()?.putString(if (dark) key else key + LIGHT, value.name)?.apply()
    }

    /**
     * Puts the New style's transparency, tint, darkness, saturation and blur
     * back to how it ships, for the [dark] theme or the light one.
     */
    fun resetClearGlassOptions(dark: Boolean = true) {
        setClearGlassTransparency(DEFAULT_CLEAR_GLASS_TRANSPARENCY, dark)
        setClearGlassBlur(DEFAULT_CLEAR_GLASS_BLUR, dark)
        setClearGlassSaturation(DEFAULT_CLEAR_GLASS_SATURATION, dark)
        setClearGlassTint(if (dark) DEFAULT_CLEAR_GLASS_TINT else DEFAULT_CLEAR_GLASS_TINT_LIGHT, dark)
        setClearGlassTintDarkness(
            if (dark) DEFAULT_CLEAR_GLASS_TINT_DARKNESS else DEFAULT_CLEAR_GLASS_TINT_DARKNESS_LIGHT,
            dark,
        )
    }

    /**
     * Puts Classic's transparency, tint, its darkness, saturation and blur
     * back to how the glass ships, for the [dark] theme or the light one.
     */
    fun resetGlassOptions(dark: Boolean = true) {
        setGlassTransparency(DEFAULT_GLASS_TRANSPARENCY, dark)
        setGlassTint(GlassTint.themeNeutral(dark), dark)
        setGlassTintDarkness(if (dark) DEFAULT_GLASS_TINT_DARKNESS else DEFAULT_GLASS_TINT_DARKNESS_LIGHT, dark)
        setGlassSaturation(DEFAULT_GLASS_SATURATION, dark)
        setGlassBlur(DEFAULT_GLASS_BLUR, dark)
    }

    fun setProgressBarStyle(style: ProgressBarStyle) {
        applyProgressBarStyle(style)
        prefs?.edit()?.putString(KEY_PROGRESS_BAR_STYLE, style.name)?.apply()
    }

    private fun applyProgressBarStyle(style: ProgressBarStyle) {
        progressBarStyle.value = style
        wavyProgressBar.value = style == ProgressBarStyle.WAVE
        glowProgressBar.value = style == ProgressBarStyle.GLOW
    }

    fun setOutputIcon(key: String, icon: String?) {
        val updated = if (icon == null) outputIcons.value - key else outputIcons.value + (key to icon)
        outputIcons.value = updated
        val json = JSONObject().apply { updated.forEach { (k, v) -> put(k, v) } }
        prefs?.edit()?.putString(KEY_OUTPUT_ICONS, json.toString())?.apply()
    }

    private fun readOutputIcons(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
    }

    fun setCompactCanvasControls(value: Boolean) {
        compactCanvasControls.value = value
        prefs?.edit()?.putBoolean(KEY_COMPACT_CANVAS_CONTROLS, value)?.apply()
    }

    fun setMiniPlayerShape(value: MiniPlayerShape) {
        miniPlayerShape.value = value
        prefs?.edit()?.putString(KEY_MINI_PLAYER_SHAPE, value.name)?.apply()
    }

    fun setSpeedDialSource(value: SpeedDialSource) = DialSettings.setSpeedDialSource(value)

    fun setSpotifyDialSeeds(value: SpotifyDialSeeds) = DialSettings.setSpotifyDialSeeds(value)

    fun setSpeedDialRows(value: Int) = DialSettings.setSpeedDialRows(value)

    private fun ensureOneTab(tabs: List<OrderedToggle<NavPage>>): List<OrderedToggle<NavPage>> =
        if (tabs.any { it.enabled }) {
            tabs
        } else {
            tabs.map { if (it.item == NavPage.HOME) it.copy(enabled = true) else it }
        }

    /**
     * Reads "NAME:1,NAME:0,…". Unknown names are dropped, and any [all] entry the
     * stored list doesn't mention is put in, enabled when it is in [defaultOn],
     * so entries added in later builds still show up. It goes in ahead of the
     * first stored entry that follows it in [all], and at the end when none
     * does: Podcasts, added between Explore and Library, lands before Library
     * in an order saved before there was a Podcasts page.
     */
    internal fun <T> readOrdered(
        stored: String?,
        all: List<T>,
        defaultOn: Set<T>,
        /** The order with nothing saved; [all] unless told otherwise. */
        defaultOrder: List<T> = all,
        parse: (String) -> T?,
    ): List<OrderedToggle<T>> {
        if (stored.isNullOrBlank()) return defaultOrder.map { OrderedToggle(it, it in defaultOn) }
        val saved: List<OrderedToggle<T>> = stored.split(",").mapNotNull { entry ->
            val item: T = parse(entry.substringBefore(':')) ?: return@mapNotNull null
            OrderedToggle<T>(item, entry.substringAfter(':', "1") != "0")
        }.distinctBy { it.item }
        val ordered = saved.filter { it.item in all }.toMutableList()
        all.forEachIndexed { index, item ->
            if (ordered.any { it.item == item }) return@forEachIndexed
            val followers = all.subList(index + 1, all.size)
            val at = ordered.indexOfFirst { it.item in followers }
            ordered.add(if (at >= 0) at else ordered.size, OrderedToggle<T>(item, item in defaultOn))
        }
        return ordered
    }

    internal fun <T> writeOrdered(value: List<OrderedToggle<T>>, name: (T) -> String): String =
        value.joinToString(",") { "${name(it.item)}:${if (it.enabled) 1 else 0}" }

    /** The title the speed dial goes by, both YouTube's and the one built here. */
    const val SPEED_DIAL_TITLE = "Speed dial"
    private const val RECENTS_TITLE = "Recents"
    const val SPEED_DIAL_SIZE = DialSettings.SPEED_DIAL_SIZE
    private const val SPEED_DIAL_COLUMNS = 3
    const val DEFAULT_SPEED_DIAL_ROWS = DialSettings.DEFAULT_SPEED_DIAL_ROWS

    /**
     * The glass as it ships, in both styles: 90% see-through, a 10dp frost,
     * the colour behind it taken down a touch (90%), and the theme's own
     * tint, black shaded 30% on the dark theme and white, unshaded, on the
     * light one.
     */
    const val DEFAULT_GLASS_TRANSPARENCY = 0.9f
    const val DEFAULT_GLASS_BLUR = 10f
    const val MAX_GLASS_BLUR = 32f
    const val DEFAULT_GLASS_SATURATION = 0.9f
    const val MAX_GLASS_SATURATION = 2f
    const val DEFAULT_GLASS_TINT_DARKNESS = 0.3f
    const val DEFAULT_GLASS_TINT_DARKNESS_LIGHT = 0f

    const val DEFAULT_CLEAR_GLASS_TRANSPARENCY = DEFAULT_GLASS_TRANSPARENCY
    const val DEFAULT_CLEAR_GLASS_BLUR = DEFAULT_GLASS_BLUR
    const val DEFAULT_CLEAR_GLASS_SATURATION = DEFAULT_GLASS_SATURATION
    val DEFAULT_CLEAR_GLASS_TINT: GlassTint get() = GlassTint.BLACK
    val DEFAULT_CLEAR_GLASS_TINT_LIGHT: GlassTint get() = GlassTint.WHITE
    const val DEFAULT_CLEAR_GLASS_TINT_DARKNESS = DEFAULT_GLASS_TINT_DARKNESS
    const val DEFAULT_CLEAR_GLASS_TINT_DARKNESS_LIGHT = DEFAULT_GLASS_TINT_DARKNESS_LIGHT

    /**
     * What the fills are measured against, which is where each style used to
     * ship: Classic's fill, tint and shade are at full strength at 60%
     * transparency and its tint as picked at 150% saturation; the New
     * style's at 80% and 110%. Kept apart from the defaults so that moving a
     * default does not change what a given slider position looks like.
     */
    const val GLASS_REFERENCE_TRANSPARENCY = 0.6f
    const val GLASS_REFERENCE_SATURATION = 1.5f
    const val CLEAR_GLASS_REFERENCE_TRANSPARENCY = 0.8f
    const val CLEAR_GLASS_REFERENCE_SATURATION = 1.1f

    private const val KEY_NAV_TABS = "custom_nav_tabs"
    private const val KEY_OUTPUT_ICONS = "output_icons"
    private const val KEY_COMPACT_CANVAS_CONTROLS = "custom_compact_canvas_controls"
    private const val KEY_WAVY_PROGRESS_BAR = "custom_wavy_progress_bar"
    private const val KEY_GLOW_PROGRESS_BAR = "custom_glow_progress_bar"
    private const val KEY_PROGRESS_BAR_STYLE = "custom_progress_bar_style"
    private const val KEY_AMBIENT_BACKGROUND = "custom_ambient_background"
    private const val KEY_AMBIENT_STATIC = "custom_ambient_static"
    private const val KEY_LIBRARY_TAB_VIEWS = "custom_library_tab_views"
    private const val KEY_AMBIENT_FOLLOWS_ARTWORK = "custom_ambient_follows_artwork"
    /** What a glass option's key ends in for the light theme's copy of it. */
    private const val LIGHT = "_light"
    private const val KEY_GLASS_TRANSPARENCY = "custom_glass_transparency"
    private const val KEY_GLASS_TINT = "custom_glass_tint"
    private const val KEY_GLASS_BLUR = "custom_glass_blur"
    private const val KEY_GLASS_TINT_DARKNESS = "custom_glass_tint_darkness"
    private const val KEY_GLASS_SATURATION = "custom_glass_saturation"
    private const val KEY_CLEAR_GLASS_STYLE = "custom_clear_glass_style"
    private const val KEY_CLEAR_GLASS_TRANSPARENCY = "custom_clear_glass_transparency"
    private const val KEY_CLEAR_GLASS_BLUR = "custom_clear_glass_blur"
    private const val KEY_CLEAR_GLASS_SATURATION = "custom_clear_glass_saturation"
    private const val KEY_CLEAR_GLASS_TINT_DARKNESS = "custom_clear_glass_tint_darkness"
    private const val KEY_CLEAR_GLASS_TINT_DARKNESS_LIGHT = "custom_clear_glass_tint_darkness_light"
    private const val KEY_CLEAR_GLASS_TINT = "custom_clear_glass_tint"
    private const val KEY_GLASS_DEFAULTS = "custom_glass_defaults_version"
    private const val GLASS_DEFAULTS_VERSION = 2

    /** Every glass option's key, for the dark theme; the light theme's are these plus [LIGHT]. */
    private val GLASS_OPTION_KEYS = listOf(
        KEY_GLASS_TRANSPARENCY,
        KEY_GLASS_TINT,
        KEY_GLASS_BLUR,
        KEY_GLASS_TINT_DARKNESS,
        KEY_GLASS_SATURATION,
        KEY_CLEAR_GLASS_TRANSPARENCY,
        KEY_CLEAR_GLASS_BLUR,
        KEY_CLEAR_GLASS_SATURATION,
        KEY_CLEAR_GLASS_TINT_DARKNESS,
        KEY_CLEAR_GLASS_TINT,
    )
    private const val KEY_LIBRARY_SOURCE = "custom_library_source"
    private const val KEY_LAST_SPEED_DIAL = "custom_last_speed_dial"
    private const val KEY_MINI_PLAYER_NEXT = "custom_mini_player_next"
    private const val KEY_CROP_THUMBNAILS = "custom_crop_thumbnails"
    private const val KEY_GENERATE_PLAYLIST_IMAGES = "custom_generate_playlist_images"
    private const val KEY_PLAYLIST_BACKDROPS_OFF = "custom_playlist_backdrops_off"
    private const val KEY_LAST_PODCAST_DIAL = "custom_last_podcast_dial"
    private const val KEY_PODCAST_SHOWS_FOR_YOU = "custom_podcast_shows_for_you"
    private const val KEY_PODCAST_SHOWS_TITLE = "custom_podcast_shows_title"
    private const val KEY_PODCAST_SHOWS_LISTENER = "custom_podcast_shows_listener"
    private const val KEY_PODCAST_EPISODES_FOR_LATER = "custom_podcast_episodes_for_later"
    private const val KEY_PODCAST_SOURCE = "custom_podcast_source"
    private const val KEY_RETIRED_DIAL_PARALLAX = "custom_dial_parallax"
    private const val KEY_RETIRED_AMBIENT_STYLE = "custom_ambient_style"
    private const val KEY_START_PAGE = "custom_start_page"
    private const val KEY_KEEP_NAV_EXPANDED = "custom_keep_nav_expanded"
    private const val KEY_MINI_PLAYER_SHAPE = "custom_mini_player_shape"
    private const val KEY_LAST_DIAL_NEW_RELEASES = "custom_last_dial_new_releases"
}

/**
 * When each track was last started, by video id, for the folders' "Last played"
 * sort. Kept on this device only; YouTube's own history is per account and
 * not something a folder of local files could be matched against.
 */
object LastPlayed {
    private var prefs: SharedPreferences? = null
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun init(context: android.content.Context) {
        val p = context.getSharedPreferences("bitchord_last_played", android.content.Context.MODE_PRIVATE)
        prefs = p
        cache.clear()
        p.all.forEach { (key, value) -> (value as? Long)?.let { cache[key] = it } }
    }

    fun record(videoId: String) {
        if (videoId.isBlank()) return
        val now = System.currentTimeMillis()
        cache[videoId] = now
        prefs?.edit()?.putLong(videoId, now)?.apply()
    }

    /** Milliseconds since the epoch, or 0 for never. */
    fun at(videoId: String): Long = cache[videoId] ?: 0L
}

/** The player's seek bar styles, offered in Settings with a preview of each. */
enum class ProgressBarStyle { GLOW, WAVE, PLAIN }
