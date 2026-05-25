package com.mappingsolution.data.fs

import com.mappingsolution.data.model.Group
import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.util.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BulkPoiRepository @Inject constructor(private val storageManager: StorageManager) {

    private val _poisInViewport = MutableStateFlow<List<Poi>>(emptyList())
    val poisInViewport: StateFlow<List<Poi>> = _poisInViewport.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Full in-memory store: groupId → all POIs in that group's JSONL file.
     * Loaded once on first access; subsequent viewport queries filter this list in-memory
     * (microseconds) instead of re-scanning the file on every camera change.
     * Invalidated only when the group is deleted or its file is explicitly rewritten.
     */
    private val allPoisByGroupId = mutableMapOf<String, List<Poi>>()

    /** Last-emitted viewport cache — skips even the in-memory filter when bounds are identical. */
    @Volatile private var cachedNorth: Double = Double.NaN
    @Volatile private var cachedSouth: Double = Double.NaN
    @Volatile private var cachedEast:  Double = Double.NaN
    @Volatile private var cachedWest:  Double = Double.NaN
    @Volatile private var cachedGroupIds: Set<String> = emptySet()
    @Volatile private var cachedResult: List<Poi> = emptyList()

    private val BOUNDS_EPSILON = 0.001 // ~111 m — covers MapLibre resume jitter

    fun clear() {
        _poisInViewport.value = emptyList()
        cachedNorth = Double.NaN
    }

    fun getById(id: String): Poi? = _poisInViewport.value.find { it.id == id }

    /**
     * Returns POIs in the given viewport.
     *
     * - Same viewport as last call → instant (cached list, no work).
     * - Different viewport, group already in memory → instant (in-memory filter only).
     * - First call for a group → reads JSONL file once, caches all POIs in memory, then filters.
     */
    suspend fun refreshForViewport(
        bulkGroups: List<Group>,
        north: Double,
        south: Double,
        east: Double,
        west: Double,
    ) = withContext(Dispatchers.IO) {
        val visibleGroups = bulkGroups.filter { it.isVisible }
        val visibleGroupIds = visibleGroups.map { it.id }.toSet()

        // Fast path: same viewport and same groups → replay cached result immediately.
        if (!cachedNorth.isNaN() &&
            visibleGroupIds == cachedGroupIds &&
            kotlin.math.abs(north - cachedNorth) < BOUNDS_EPSILON &&
            kotlin.math.abs(south - cachedSouth) < BOUNDS_EPSILON &&
            kotlin.math.abs(east  - cachedEast)  < BOUNDS_EPSILON &&
            kotlin.math.abs(west  - cachedWest)  < BOUNDS_EPSILON
        ) {
            android.util.Log.d("BulkPoiRepo", "viewport cache HIT — ${cachedResult.size} POIs")
            _poisInViewport.value = cachedResult
            return@withContext
        }

        // Determine if any group still needs to be loaded from disk.
        val needsIO = visibleGroups.any { it.id !in allPoisByGroupId }
        if (needsIO) _isLoading.value = true

        try {
            val result = mutableListOf<Poi>()
            for (group in visibleGroups) {
                val allPois = synchronized(allPoisByGroupId) {
                    allPoisByGroupId.getOrPut(group.id) { loadGroupFromDisk(group) }
                }
                allPois.filterTo(result) { it.lat in south..north && it.lng in west..east }
            }

            android.util.Log.d("BulkPoiRepo", "in-memory filter → ${result.size} POIs (needsIO=$needsIO)")
            cachedNorth    = north
            cachedSouth    = south
            cachedEast     = east
            cachedWest     = west
            cachedGroupIds = visibleGroupIds
            cachedResult   = result.toList()
            _poisInViewport.value = cachedResult
        } finally {
            if (needsIO) _isLoading.value = false
        }
    }

    /** Reads and parses the entire JSONL file for a group into memory. Called once per group. */
    private fun loadGroupFromDisk(group: Group): List<Poi> {
        val jsonlFile = storageManager.getBulkPoisFile(group.name, group.id)
        android.util.Log.d("BulkPoiRepo", "loading '${group.name}' from disk: ${jsonlFile.length()} bytes")
        if (!jsonlFile.exists()) return emptyList()
        val pois = mutableListOf<Poi>()
        jsonlFile.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                runCatching { pois.add(parseLine(line)) }
                    .onFailure { android.util.Log.w("BulkPoiRepo", "parse error: $line", it) }
            }
        }
        android.util.Log.d("BulkPoiRepo", "loaded ${pois.size} POIs for '${group.name}'")
        return pois
    }

    /**
     * Searches all POIs across all given [groups] by name (case-insensitive, substring).
     * Groups already in memory are searched instantly; unloaded groups are read from disk first.
     */
    suspend fun searchByName(query: String, groups: List<Group>): List<Poi> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Poi>()
        for (group in groups) {
            val pois = synchronized(allPoisByGroupId) {
                allPoisByGroupId.getOrPut(group.id) { loadGroupFromDisk(group) }
            }
            pois.filterTo(results) { it.name.contains(query, ignoreCase = true) }
        }
        results
    }

    /** Deletes the bulk group folder (jsonl + images) from disk. */
    fun deleteGroup(name: String, id: String) {
        storageManager.deletePoiFolder(name, id)
        synchronized(allPoisByGroupId) { allPoisByGroupId.remove(id) }
        _poisInViewport.value = _poisInViewport.value.filter { it.groupId != id }
        cachedNorth = Double.NaN
    }

    private fun parseLine(line: String): Poi = deserializePoi(line)

    companion object {
        /** Deserializes a Poi from a single-line JSON string read from bulk_pois.jsonl. */
        fun deserializePoi(line: String): Poi {
            val json = JSONObject(line)
            val mediaArr = json.optJSONArray("mediaPaths")
            val mediaPaths = if (mediaArr != null) List(mediaArr.length()) { mediaArr.getString(it) } else emptyList()
            return Poi(
                id = json.getString("id"),
                groupId = json.optString("groupId").takeIf { it.isNotEmpty() },
                name = json.getString("name"),
                description = json.optString("description").takeIf { it.isNotEmpty() },
                lat = json.getDouble("lat"),
                lng = json.getDouble("lng"),
                elevation = if (json.has("elevation")) json.getDouble("elevation") else null,
                mediaPaths = mediaPaths,
                isVisible = json.optBoolean("isVisible", true),
                createdAt = json.getLong("createdAt"),
                updatedAt = json.getLong("updatedAt"),
                iconKey = json.optString("iconKey").takeIf { it.isNotEmpty() },
            )
        }

        /** Serializes a Poi to a single-line JSON string for storage in bulk_pois.jsonl. */
        fun serializePoi(poi: Poi): String = JSONObject().apply {
            put("id", poi.id)
            poi.groupId?.let { put("groupId", it) }
            put("name", poi.name)
            poi.description?.let { put("description", it) }
            put("lat", poi.lat)
            put("lng", poi.lng)
            poi.elevation?.let { put("elevation", it) }
            put("mediaPaths", JSONArray(poi.mediaPaths))
            put("isVisible", poi.isVisible)
            put("createdAt", poi.createdAt)
            put("updatedAt", poi.updatedAt)
            poi.iconKey?.let { put("iconKey", it) }
        }.toString()
    }
}
