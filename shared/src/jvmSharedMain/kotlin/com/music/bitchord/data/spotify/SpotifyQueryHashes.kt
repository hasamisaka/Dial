package com.music.bitchord.data.spotify

import com.music.bitchord.data.DebugLog
import com.music.bitchord.data.Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Request
import java.io.File
import java.util.Base64

/**
 * The persisted-query hashes of the Spotify web player, for every Pathfinder operation Dial uses.
 *
 * Each Pathfinder operation is named by a hash, and Spotify changes these hashes when it rebuilds
 * the web player. A fixed hash then stops working and that Spotify feature stops in Dial. This
 * object reads the live hashes out of the web player's own JavaScript, for all operations at once:
 *
 * 1. Get open.spotify.com and read the web player version and its entry scripts.
 * 2. Read the entry scripts. Collect every `"name","query|mutation","<hash>"` declaration.
 * 3. Read the webpack chunk map in the entry script, and get the lazily loaded chunks too
 *    (route chunks first), until every operation Dial uses has a hash.
 *
 * The result is kept in memory and in a file in [cacheDir], with the web player version. While
 * the version stays the same, no script is read again. When no live hash is found, [KNOWN] gives
 * the last hash known to work.
 *
 * The method (chunk map, all chunks, one cache by version) comes from SpotAPI by Aran404,
 * https://github.com/Aran404/SpotAPI (GPL-3.0).
 */
object SpotifyQueryHashes {

    const val CANVAS = "canvas"
    const val LIBRARY = "libraryV3"
    const val FETCH_PLAYLIST = "fetchPlaylist"
    const val LIKED_TRACKS = "fetchLibraryTracks"
    const val SEARCH_DESKTOP = "searchDesktop"
    const val SEARCH_TRACKS = "searchTracks"
    const val HOME = "home"
    const val HOME_SECTION = "homeSection"
    const val ADD_TO_LIBRARY = "addToLibrary"
    const val REMOVE_FROM_LIBRARY = "removeFromLibrary"
    const val ADD_TO_PLAYLIST = "addToPlaylist"
    const val REMOVE_FROM_PLAYLIST = "removeFromPlaylist"
    const val PROFILE_ATTRIBUTES = "profileAttributes"
    const val ACCOUNT_ATTRIBUTES = "accountAttributes"

    /**
     * The last hashes known to work, read from web player 1.3.12.512 on 2026-11-22. Dial uses
     * these only when it cannot read the live ones. Spotify puts some operations in one
     * document, so two operations can have the same hash.
     */
    val KNOWN: Map<String, String> = mapOf(
        CANVAS to "575138ab27cd5c1b3e54da54d0a7cc8d85485402de26340c2145f0f6bb5e7a9f",
        LIBRARY to "390c78e5b951029bad359785e69b07b536a509c581cbcd0aded5e5067f187455",
        FETCH_PLAYLIST to "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf",
        LIKED_TRACKS to "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240",
        SEARCH_DESKTOP to "eef7cc54888d91bdd6802623477873caa3948ae173a0c34fd86827b267e94c03",
        SEARCH_TRACKS to "b02683192a98dde7966b5e6655a79eeb62713eab703eda9902c932818dd52751",
        HOME to "5fb7da7a03ee2776c0670856609cb85a189a3cecc36cc160b91cde79063b9cf1",
        HOME_SECTION to "5fb7da7a03ee2776c0670856609cb85a189a3cecc36cc160b91cde79063b9cf1",
        ADD_TO_LIBRARY to "896ebcb47815681340860d121cb5d494e157e2a78d3950385cd54e0393c67148",
        REMOVE_FROM_LIBRARY to "896ebcb47815681340860d121cb5d494e157e2a78d3950385cd54e0393c67148",
        ADD_TO_PLAYLIST to "b907ad6b088d5bf7680fa81198996d58398c2d55dfcdf59fcc959e0be98a418a",
        REMOVE_FROM_PLAYLIST to "b907ad6b088d5bf7680fa81198996d58398c2d55dfcdf59fcc959e0be98a418a",
        PROFILE_ATTRIBUTES to "08ffb4730af3746e04a8301396f20875dbbce10c75243803091a9274eacc8ac0",
        // Read on 2026-11-23, a later web player build than the others.
        ACCOUNT_ATTRIBUTES to "be4ce359daee01463ddb8e77536220e108e3d28105e12199f244d7a5954e9d14",
    )

    /** Where the found hashes are kept between launches. Set by each app at start-up; null keeps them in memory only. */
    @Volatile
    var cacheDir: File? = null

    const val PAGE_URL = "https://open.spotify.com/"
    private const val TAG = "SpotifyQueryHashes"
    private const val FILE_NAME = "spotify_query_hashes.json"

    /** The browser user agent; open.spotify.com gives an unknown one a different page. */
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/122.0.0.0 Safari/537.36"

    /** Lazy: the values it reads are set further down in this object. */
    private val finder by lazy {
        Finder(
            fetch = ::get,
            wanted = KNOWN.keys,
            firstChunks = LIKELY_CHUNKS,
            store = object : Finder.Store {
                override fun read(): String? = runCatching { file()?.takeIf { it.isFile }?.readText() }.getOrNull()
                override fun write(text: String) {
                    runCatching { file()?.let { it.parentFile?.mkdirs(); it.writeText(text) } }
                }
                private fun file(): File? = cacheDir?.let { File(it, FILE_NAME) }
            },
            log = { DebugLog.d(TAG, it) },
        )
    }

    /**
     * The live hash of [operation], or its [KNOWN] hash. Blocks while the web player is read,
     * so call it off the main thread. Set [forceRefresh] after Spotify answers
     * "PersistedQueryNotFound": the web player is then read again (at most once in ten minutes).
     */
    fun hash(operation: String, forceRefresh: Boolean = false): String =
        finder.find(operation, forceRefresh) ?: KNOWN.getValue(operation)

    /** True when a Pathfinder answer says that the hash is not known (an old hash). */
    fun isStale(body: String?): Boolean = body?.contains("PersistedQueryNotFound", ignoreCase = true) == true

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return runCatching {
            Http.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    // ---- Reading the web player ------------------------------------------

    /** `"canvas","query","<64 hex>"`; mutations are declared the same way. */
    private val OPERATION_DECLARATION =
        Regex("""["'](\w+)["']\s*,\s*["'](?:query|mutation)["']\s*,\s*["']([0-9a-f]{64})["']""")

    private val ENTRY_SCRIPT =
        Regex("""https://open\.spotifycdn\.com/cdn/build/web-player/[A-Za-z0-9._~-]+\.js""")

    private val APP_SERVER_CONFIG =
        Regex("""<script[^>]*id=["']appServerConfig["'][^>]*>\s*([A-Za-z0-9+/=_-]+)\s*</script>""")

    private val CLIENT_VERSION = Regex(""""clientVersion"\s*:\s*"([^"]+)"""")

    /**
     * The webpack function that names a lazy chunk's file:
     * `({1328:"xpui-routes-search",…})[e]||e)+"."+({1328:"47be1338",…})[e]+".js"`.
     * The name map is not always there; a chunk without a name uses its id.
     */
    private val CHUNK_FILE_MAP = Regex(
        """(?:\(?\{([^{}]*)\}\)?\[(\w+)\]\|\|\w+\)\+["']\.["']\+)?\(?\{([^{}]*)\}\)?\[\w+\]\+["']\.js["']""",
    )

    private val MAP_ENTRY = Regex("""["']?([\w-]+)["']?\s*:\s*["']([^"']+)["']""")
    private val CHUNK_NAME = Regex("""[A-Za-z0-9._~-]+""")
    private val CHUNK_HASH = Regex("""[0-9a-f]{6,}""")

    /** Every operation declared in [script] and its hash. When one is declared twice, the first wins. */
    fun operations(script: String): Map<String, String> {
        val found = LinkedHashMap<String, String>()
        for (match in OPERATION_DECLARATION.findAll(script)) {
            found.putIfAbsent(match.groupValues[1], match.groupValues[2])
        }
        return found
    }

    /** The web player's entry scripts, as the page links them. */
    fun entryScripts(html: String): List<String> = ENTRY_SCRIPT.findAll(html).map { it.value }.distinct().toList()

    /**
     * The web player version: `clientVersion` from the page's `appServerConfig`. When that is not
     * there, the entry script names, which change with each build too.
     */
    fun version(html: String): String? {
        val config = APP_SERVER_CONFIG.find(html)?.groupValues?.get(1)
        val decoded = config?.let { text ->
            runCatching { String(Base64.getMimeDecoder().decode(text.replace('-', '+').replace('_', '/'))) }.getOrNull()
        }
        decoded?.let { CLIENT_VERSION.find(it)?.groupValues?.get(1) }?.let { return it }
        return entryScripts(html).sorted().joinToString("|").ifEmpty { null }
    }

    /**
     * Words in the names of the chunks that hold the operations Dial uses. These chunks are read
     * first, so that a scan usually stops after a few chunks.
     */
    val LIKELY_CHUNKS = listOf("search", "playlist", "collection", "library", "home")

    /**
     * The lazy chunk scripts that [entryScript] (got from [entryUrl]) can load. Chunks with a
     * word of [firstNames] in their name come first (in the order of the words), then route
     * chunks, then other named chunks, then chunks with only an id.
     */
    fun chunkScripts(entryScript: String, entryUrl: String, firstNames: List<String> = emptyList()): List<String> {
        val base = entryUrl.substringBeforeLast('/') + "/"
        val chunks = mutableListOf<Pair<Int, String>>()
        for (match in CHUNK_FILE_MAP.findAll(entryScript)) {
            val names = mapEntries(match.groupValues[1])
            val hashes = mapEntries(match.groupValues[3])
            for ((id, hash) in hashes) {
                if (!CHUNK_HASH.matches(hash)) continue
                val name = names[id] ?: id
                if (!CHUNK_NAME.matches(name)) continue
                val hint = firstNames.indexOfFirst { it in name }
                val rank = when {
                    names[id] == null -> firstNames.size + 2
                    hint >= 0 -> hint
                    "routes" in name -> firstNames.size
                    else -> firstNames.size + 1
                }
                chunks += rank to "$base$name.$hash.js"
            }
        }
        return chunks.sortedBy { it.first }.map { it.second }.distinct()
    }

    private fun mapEntries(body: String): Map<String, String> =
        if (body.isEmpty()) emptyMap()
        else MAP_ENTRY.findAll(body).associate { it.groupValues[1] to it.groupValues[2] }

    /**
     * Finds and keeps the hashes. Free of any HTTP client, so it can be tested without a network:
     * [fetch] is a GET (URL in, body out, null on failure).
     */
    class Finder(
        private val fetch: (String) -> String?,
        /** The operations to find. Reading chunks stops when all of these have a hash. */
        private val wanted: Set<String>,
        private val now: () -> Long = System::currentTimeMillis,
        private val store: Store? = null,
        private val maxChunks: Int = 400,
        /** See [chunkScripts]. */
        private val firstChunks: List<String> = emptyList(),
        private val log: (String) -> Unit = {},
    ) {
        /** A place to keep the found hashes between launches. */
        interface Store {
            fun read(): String?
            fun write(text: String)
        }

        private var loaded = false
        private var version: String? = null
        private var hashes: Map<String, String> = emptyMap()

        /** True when the last scan read every chunk, so an operation not found is not in the web player. */
        private var complete = false
        private var checkedAt = 0L
        private var lastFailed = false
        private val requested = LinkedHashSet<String>()

        /** The live hash of [operation], or null when the web player does not give one. */
        @Synchronized
        fun find(operation: String, forceRefresh: Boolean = false): String? {
            load()
            val newlyAsked = requested.add(operation) && operation !in wanted
            val age = now() - checkedAt
            val due = when {
                checkedAt == 0L -> true
                forceRefresh -> age >= FORCED_MIN_MS
                // An operation the last scan did not look for: look once more.
                newlyAsked && operation !in hashes && !complete -> age >= FORCED_MIN_MS
                lastFailed || hashes.isEmpty() -> age >= RETRY_MS
                else -> age >= CHECK_MS
            }
            if (due) refresh()
            return hashes[operation]
        }

        private fun refresh() {
            checkedAt = now()
            val html = fetch(PAGE_URL)
            val entries = html?.let(::entryScripts).orEmpty()
            if (html == null || entries.isEmpty()) {
                log("web player page not read; keeping ${hashes.size} hashes")
                lastFailed = true
                save()
                return
            }
            val pageVersion = version(html)
            val target = wanted + requested
            if (pageVersion != null && pageVersion == version && hashes.isNotEmpty() &&
                (complete || target.all { it in hashes })
            ) {
                lastFailed = false
                save()
                return
            }
            val found = LinkedHashMap<String, String>()
            val chunks = mutableListOf<String>()
            var allRead = true
            for (url in entries) {
                val body = fetch(url)
                if (body == null) {
                    allRead = false
                    continue
                }
                operations(body).forEach { (name, hash) -> found.putIfAbsent(name, hash) }
                chunks += chunkScripts(body, url, firstChunks)
            }
            var chunksRead = 0
            val queue = chunks.distinct()
            for (url in queue) {
                if (target.all { it in found }) break
                if (chunksRead >= maxChunks) break
                chunksRead++
                val body = fetch(url)
                if (body == null) {
                    allRead = false
                    continue
                }
                operations(body).forEach { (name, hash) -> found.putIfAbsent(name, hash) }
            }
            if (found.isEmpty()) {
                log("no operations in the web player scripts; keeping ${hashes.size} hashes")
                lastFailed = true
                save()
                return
            }
            val missing = target.filter { it !in found }
            log("web player $pageVersion: ${found.size} operations from ${entries.size} entry and $chunksRead chunk scripts" +
                if (missing.isEmpty()) "" else "; not found: ${missing.joinToString()}")
            version = pageVersion
            hashes = found
            complete = allRead && chunksRead == queue.size
            lastFailed = false
            save()
        }

        private fun load() {
            if (loaded) return
            loaded = true
            val text = store?.read() ?: return
            runCatching {
                val root = json.parseToJsonElement(text).jsonObject
                version = (root["version"] as? JsonPrimitive)?.contentOrNull
                checkedAt = (root["checkedAt"] as? JsonPrimitive)?.longOrNull ?: 0L
                complete = (root["complete"] as? JsonPrimitive)?.booleanOrNull ?: false
                hashes = (root["hashes"] as? JsonObject).orEmpty()
                    .mapNotNull { (name, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { name to it } }
                    .toMap()
            }
        }

        private fun save() {
            val store = store ?: return
            val text = buildJsonObject {
                put("version", version)
                put("checkedAt", checkedAt)
                put("complete", complete)
                putJsonObject("hashes") { hashes.forEach { (name, hash) -> put(name, hash) } }
            }.toString()
            store.write(text)
        }

        private companion object {
            /** How long the version found is trusted before the page is read again. */
            const val CHECK_MS = 6L * 60 * 60 * 1000

            /** How long to wait after a failed read. */
            const val RETRY_MS = 30L * 60 * 1000

            /** The shortest time between two reads that an old hash asks for. */
            const val FORCED_MIN_MS = 10L * 60 * 1000

            val json = Json { ignoreUnknownKeys = true; isLenient = true }
        }
    }
}
