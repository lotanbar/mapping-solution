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
 * A single OSM way (road/path) with geometry and metadata.
 * [id] is the OSM way ID, used for deduplication across overlapping tile fetches.
 */
data class OsmRoadWay(
    val id: Long,
    val highway: String,
    val name: String?,
    val points: List<Pair<Double, Double>>,
)

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

    // ── Internal fetch logic ──────────────────────────────────────────────────────────────────────

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
                ways.add(
                    OsmRoadWay(
                        id = el.getLong("id"),
                        highway = highway,
                        name = tags.optString("name").ifBlank { null },
                        points = pts,
                    )
                )
            }
        }
        return ways
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
