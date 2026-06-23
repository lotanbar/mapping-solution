package com.mappingsolution.data.recording.processing

import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

/**
 * Tile-based in-memory cache of OSM road geometry, fetched from the Overpass API.
 *
 * Tiles are 0.01° × 0.01° (~1.1 km). Each tile is fetched with a 0.005° margin
 * so the actual bbox is ~2.2 km × 2.2 km, ensuring roads near tile edges are included.
 *
 * Road snapping reads from the current tile AND its 8 neighbours, deduplicated by OSM
 * way ID, to avoid snap gaps when the user is near a tile boundary.
 *
 * [roadsFlow] emits a fresh GeoJSON FeatureCollection after each tile update so the
 * map can render OSM roads as an overlay.
 */
@Singleton
class OsmRoadCache @Inject constructor(private val httpClient: OkHttpClient) {

    companion object {
        private const val TAG = "OsmRoadCache"

        private const val TILE_DEG = 0.01        // ~1.1 km per tile
        private const val MARGIN_DEG = 0.005     // fetch bbox extends 550 m beyond tile edge

        private const val TILE_TTL_MS = 30 * 60 * 1000L   // 30 minutes

        /** Cap total cached tiles to bound memory. Oldest tile is evicted when exceeded. */
        private const val MAX_CACHED_TILES = 30

        private val OVERPASS_ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.openstreetmap.fr/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
        )

        private val HIGHWAY_TYPES = setOf(
            "motorway", "trunk", "primary", "secondary", "tertiary",
            "unclassified", "residential", "service",
            "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link",
            "living_street", "pedestrian", "track", "path", "footway", "cycleway", "steps", "road",
        )
    }

    private data class CachedTile(val ways: List<OsmRoadWay>, val fetchedAt: Long)

    private val cache = ConcurrentHashMap<String, CachedTile>()

    /** Guards concurrent tile fetches so only one HTTP request runs at a time. */
    private val fetchMutex = Mutex()

    /** Keys currently queued or in-flight; prevents launching duplicate fetch coroutines. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _roadsFlow = MutableStateFlow(FeatureCollection.fromFeatures(emptyList<Feature>()))
    val roadsFlow: StateFlow<FeatureCollection> = _roadsFlow

    // Cached routable graph for the current neighbourhood. Returned with a STABLE identity until
    // the user crosses into a different tile or a neighbourhood tile is (re)loaded; the online
    // matcher relies on this identity to know when its trellis must be flushed and rebuilt.
    private val graphLock = Any()
    private var graphLatIdx = Int.MIN_VALUE
    private var graphLonIdx = Int.MIN_VALUE
    private var graphSignature = -1L
    @Volatile private var cachedGraph: RoadGraph? = null

    // ── Tile key helpers ─────────────────────────────────────────────────────────────────────────

    private fun tileIndices(lat: Double, lon: Double): Pair<Int, Int> =
        floor(lat / TILE_DEG).toInt() to floor(lon / TILE_DEG).toInt()

    private fun tileKey(latIdx: Int, lonIdx: Int): String = "${latIdx}_${lonIdx}"

    // ── Public API ────────────────────────────────────────────────────────────────────────────────

    /**
     * Non-blocking: checks whether the tile containing [lat]/[lon] is cached and, if not,
     * launches a background fetch. Safe to call from any thread including the main thread.
     */
    fun ensureLoaded(lat: Double, lon: Double) {
        val (latIdx, lonIdx) = tileIndices(lat, lon)
        val key = tileKey(latIdx, lonIdx)

        val cached = cache[key]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < TILE_TTL_MS) return
        if (!inFlight.add(key)) return  // already queued or in-flight

        scope.launch {
            try {
                fetchMutex.withLock {
                    // Re-check inside the lock: another coroutine may have fetched this tile already.
                    val recheck = cache[key]
                    if (recheck != null && System.currentTimeMillis() - recheck.fetchedAt < TILE_TTL_MS) return@withLock

                    val south = latIdx * TILE_DEG - MARGIN_DEG
                    val north = (latIdx + 1) * TILE_DEG + MARGIN_DEG
                    val west  = lonIdx  * TILE_DEG - MARGIN_DEG
                    val east  = (lonIdx  + 1) * TILE_DEG + MARGIN_DEG
                    fetchTile(key, south, west, north, east)
                }
            } catch (e: Exception) {
                Log.w(TAG, "ensureLoaded: unhandled error for $key", e)
            } finally {
                inFlight.remove(key)
            }
        }
    }

    /**
     * Synchronous read — returns all cached road ways within snapping distance of [lat]/[lon].
     *
     * Merges roads from the current tile and all 8 neighbours to avoid snap gaps at tile
     * boundaries. Results are deduplicated by OSM way ID. Returns an empty list if no tiles
     * have been loaded yet (first seconds of a recording).
     */
    fun getRoadsSync(lat: Double, lon: Double): List<OsmRoadWay> {
        val (latIdx, lonIdx) = tileIndices(lat, lon)
        val now = System.currentTimeMillis()
        val seen = HashSet<Long>()
        val result = ArrayList<OsmRoadWay>()
        for (dLat in -1..1) {
            for (dLon in -1..1) {
                val tile = cache[tileKey(latIdx + dLat, lonIdx + dLon)] ?: continue
                if (now - tile.fetchedAt >= TILE_TTL_MS) continue
                for (way in tile.ways) {
                    if (seen.add(way.id)) result.add(way)
                }
            }
        }
        return result
    }

    /**
     * Returns a routable [RoadGraph] for the neighbourhood around [lat]/[lon] (the current tile
     * plus its 8 neighbours, matching [getRoadsSync]).
     *
     * The SAME instance is returned on repeated calls until the user crosses into a different tile
     * or the tile cache changes. The online map-matcher uses this stable identity to detect when
     * its Viterbi trellis (whose segment IDs are graph-specific) must be flushed and rebuilt.
     * Returns an empty graph if no tiles are loaded yet.
     */
    fun graphAround(lat: Double, lon: Double): RoadGraph {
        val (latIdx, lonIdx) = tileIndices(lat, lon)
        // Signature over the 3×3 neighbourhood's fetch times: only changes when a tile that this
        // graph actually depends on is (re)loaded, so unrelated far-tile fetches don't churn the
        // graph identity (which would needlessly flush the online matcher's trellis).
        val signature = neighbourhoodSignature(latIdx, lonIdx)
        synchronized(graphLock) {
            val current = cachedGraph
            if (current != null &&
                latIdx == graphLatIdx && lonIdx == graphLonIdx &&
                signature == graphSignature
            ) {
                return current
            }
            val graph = RoadGraph.build(getRoadsSync(lat, lon))
            cachedGraph = graph
            graphLatIdx = latIdx
            graphLonIdx = lonIdx
            graphSignature = signature
            return graph
        }
    }

    private fun neighbourhoodSignature(latIdx: Int, lonIdx: Int): Long {
        var sig = 1125899906842597L
        for (dLat in -1..1) {
            for (dLon in -1..1) {
                val fetchedAt = cache[tileKey(latIdx + dLat, lonIdx + dLon)]?.fetchedAt ?: 0L
                sig = 31L * sig + fetchedAt
            }
        }
        return sig
    }

    /**
     * Suspends until every tile the [points] pass through is loaded (re-fetching tiles that were
     * evicted or expired during a long recording). Used by the Stop pass so the full re-match has
     * complete road coverage for the entire trip.
     */
    suspend fun ensureCorridorLoaded(points: List<Pair<Double, Double>>) {
        val tiles = LinkedHashSet<Pair<Int, Int>>()
        for ((lat, lon) in points) tiles.add(tileIndices(lat, lon))
        for ((latIdx, lonIdx) in tiles) {
            val key = tileKey(latIdx, lonIdx)
            val cached = cache[key]
            if (cached != null && System.currentTimeMillis() - cached.fetchedAt < TILE_TTL_MS) continue
            fetchMutex.withLock {
                val recheck = cache[key]
                if (recheck != null && System.currentTimeMillis() - recheck.fetchedAt < TILE_TTL_MS) return@withLock
                val south = latIdx * TILE_DEG - MARGIN_DEG
                val north = (latIdx + 1) * TILE_DEG + MARGIN_DEG
                val west  = lonIdx  * TILE_DEG - MARGIN_DEG
                val east  = (lonIdx  + 1) * TILE_DEG + MARGIN_DEG
                fetchTile(key, south, west, north, east)
            }
        }
    }

    /**
     * Builds a routable [RoadGraph] from every cached tile the [points] pass through, deduplicated
     * by OSM way ID. Call [ensureCorridorLoaded] first to guarantee coverage. Used by the Stop pass.
     */
    fun corridorGraph(points: List<Pair<Double, Double>>): RoadGraph {
        val tiles = LinkedHashSet<Pair<Int, Int>>()
        for ((lat, lon) in points) tiles.add(tileIndices(lat, lon))
        val now = System.currentTimeMillis()
        val seen = HashSet<Long>()
        val ways = ArrayList<OsmRoadWay>()
        for ((latIdx, lonIdx) in tiles) {
            val tile = cache[tileKey(latIdx, lonIdx)] ?: continue
            if (now - tile.fetchedAt >= TILE_TTL_MS) continue
            for (way in tile.ways) {
                if (seen.add(way.id)) ways.add(way)
            }
        }
        return RoadGraph.build(ways)
    }

    private suspend fun fetchTile(
        key: String,
        south: Double, west: Double, north: Double, east: Double,
    ) {
        val query = """
            [out:json][timeout:15][bbox:$south,$west,$north,$east];
            (way[highway~"^(${HIGHWAY_TYPES.joinToString("|")})${'$'}"];);
            out geom;
        """.trimIndent()

        val body = "data=${java.net.URLEncoder.encode(query, "UTF-8")}"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())

        Log.d(TAG, "Fetching $key bbox=[S=${"%.4f".format(south)} W=${"%.4f".format(west)} N=${"%.4f".format(north)} E=${"%.4f".format(east)}]")

        for (endpoint in OVERPASS_ENDPOINTS) {
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("User-Agent", "mapping-solution/1.0")
                .post(body)
                .build()

            val t0 = System.currentTimeMillis()
            val ways = runCatching {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "HTTP ${response.code} from $endpoint for $key")
                        return@runCatching null
                    }
                    val json = JSONObject(response.body!!.string())
                    Log.d(TAG, "HTTP OK from $endpoint in ${System.currentTimeMillis() - t0} ms")
                    parseWays(json)
                }
            }.getOrElse { e ->
                Log.w(TAG, "Fetch error from $endpoint: ${e.message}")
                null
            } ?: continue

            Log.d(TAG, "Tile $key: ${ways.size} ways")
            evictIfNeeded()
            cache[key] = CachedTile(ways, System.currentTimeMillis())
            rebuildRoadsFlow()
            return
        }

        Log.e(TAG, "All Overpass endpoints failed for tile $key")
    }

    private fun parseWays(json: JSONObject): List<OsmRoadWay> {
        val elements = json.optJSONArray("elements") ?: return emptyList()
        val ways = ArrayList<OsmRoadWay>(elements.length())
        for (i in 0 until elements.length()) {
            runCatching {
                val el = elements.getJSONObject(i)
                if (el.optString("type") != "way") return@runCatching
                val tags = el.optJSONObject("tags") ?: return@runCatching
                val highway = tags.optString("highway")
                    .takeIf { it.isNotBlank() && it in HIGHWAY_TYPES } ?: return@runCatching
                val geometry = el.optJSONArray("geometry") ?: return@runCatching
                if (geometry.length() < 2) return@runCatching
                val pts = (0 until geometry.length()).map { j ->
                    val node = geometry.getJSONObject(j)
                    node.getDouble("lat") to node.getDouble("lon")
                }
                // OSM node IDs, parallel to geometry — these reconstruct the routable topology
                // (ways meeting at a junction share a node ID). Present with Overpass `out geom`.
                val nodesArr = el.optJSONArray("nodes")
                val nodeIds = if (nodesArr != null && nodesArr.length() == pts.size) {
                    (0 until nodesArr.length()).map { j -> nodesArr.getLong(j) }
                } else emptyList()
                ways.add(
                    OsmRoadWay(
                        id = el.getLong("id"),
                        highway = highway,
                        name = tags.optString("name").ifBlank { null },
                        points = pts,
                        nodeIds = nodeIds,
                        oneway = parseOneway(tags, highway),
                    )
                )
            }
        }
        return ways
    }

    /**
     * Resolves a way's travel direction along its node order: +1 forward-only, -1 backward-only,
     * 0 bidirectional. Honours the `oneway` tag (incl. `-1`/`reverse`) and the implicit one-way
     * cases `junction=roundabout/circular` and motorway carriageways.
     */
    private fun parseOneway(tags: JSONObject, highway: String): Int {
        when (tags.optString("oneway").lowercase()) {
            "yes", "true", "1" -> return 1
            "-1", "reverse" -> return -1
            "no", "false", "0" -> return 0
        }
        val junction = tags.optString("junction").lowercase()
        if (junction == "roundabout" || junction == "circular") return 1
        if (highway == "motorway" || highway == "motorway_link") return 1
        return 0
    }

    /**
     * Rebuilds [roadsFlow] from all non-expired cached tiles, deduplicated by OSM way ID.
     * Must be called after updating the cache.
     */
    private fun rebuildRoadsFlow() {
        val now = System.currentTimeMillis()
        val seen = HashSet<Long>()
        val features = ArrayList<Feature>()
        for (tile in cache.values) {
            if (now - tile.fetchedAt >= TILE_TTL_MS) continue
            for (way in tile.ways) {
                if (!seen.add(way.id)) continue
                val coords = way.points.map { (lat, lon) -> Point.fromLngLat(lon, lat) }
                val props = JsonObject().apply {
                    addProperty("highway", way.highway)
                    way.name?.let { addProperty("name", it) }
                }
                features.add(Feature.fromGeometry(LineString.fromLngLats(coords), props))
            }
        }
        _roadsFlow.value = FeatureCollection.fromFeatures(features)
    }

    private fun evictIfNeeded() {
        if (cache.size >= MAX_CACHED_TILES) {
            cache.entries.minByOrNull { it.value.fetchedAt }?.let { cache.remove(it.key) }
        }
    }
}
