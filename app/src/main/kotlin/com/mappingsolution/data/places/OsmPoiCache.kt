package com.mappingsolution.data.places

import android.content.Context
import android.util.Log
import com.mappingsolution.data.model.Poi
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Cache entry returned by [OsmPoiCache.load]. */
data class OsmCachedEntry(
    val pois: List<Poi>,
    val fetchedSouth: Double,
    val fetchedWest: Double,
    val fetchedNorth: Double,
    val fetchedEast: Double,
) {
    /** True when this entry was fetched for an area that fully contains the given viewport. */
    fun covers(south: Double, west: Double, north: Double, east: Double): Boolean =
        fetchedSouth <= south && fetchedNorth >= north &&
        fetchedWest <= west && fetchedEast >= east
}

@Singleton
class OsmPoiCache @Inject constructor(@ApplicationContext context: Context) {

    private val cacheDir = File(context.cacheDir, "osm_poi_cache").also { it.mkdirs() }

    private fun cacheFile(key: String) = File(cacheDir, "osm_$key.json")

    /**
     * Loads cached POIs for [key] if they exist and are younger than [OSM_CACHE_TTL_MS].
     * Returns null on cache miss, expiry, or read error.
     * Old cache files that pre-date bounds storage use sentinel values that always
     * fail the [OsmCachedEntry.covers] check, forcing a fresh network fetch.
     */
    fun load(key: String): OsmCachedEntry? {
        val file = cacheFile(key)
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            if (json.optInt("version", 1) < 6) return null  // v6 uses strict exploration categories
            val fetchedAt = json.getLong("fetchedAt")
            if (System.currentTimeMillis() - fetchedAt > OSM_CACHE_TTL_MS) return null
            val south = json.optDouble("south", Double.MAX_VALUE)
            val west  = json.optDouble("west",  Double.MAX_VALUE)
            val north = json.optDouble("north", -Double.MAX_VALUE)
            val east  = json.optDouble("east",  -Double.MAX_VALUE)
            val arr = json.getJSONArray("pois")
            val pois = (0 until arr.length()).mapNotNull { i ->
                runCatching { poisFromJson(arr.getJSONObject(i), fetchedAt) }.getOrNull()
            }
            OsmCachedEntry(pois, south, west, north, east)
        }.getOrElse {
            Log.w("OsmPoiCache", "Failed to read cache for $key", it)
            null
        }
    }

    /** Persists [pois] together with the viewport bounds they were fetched for. */
    fun store(key: String, pois: List<Poi>, south: Double, west: Double, north: Double, east: Double) {
        runCatching {
            val arr = JSONArray()
            pois.forEach { arr.put(poiToJson(it)) }
            val json = JSONObject().apply {
                put("version", 6)
                put("fetchedAt", System.currentTimeMillis())
                put("south", south)
                put("west", west)
                put("north", north)
                put("east", east)
                put("pois", arr)
            }
            cacheFile(key).writeText(json.toString())
        }.onFailure { Log.w("OsmPoiCache", "Failed to write cache for $key", it) }
    }

    /** Deletes all cache files older than [OSM_CACHE_TTL_MS] or in an old format. */
    fun evictStale() {
        val now = System.currentTimeMillis()
        cacheDir.listFiles { f -> f.name.startsWith("osm_") && f.extension == "json" }
            ?.forEach { file ->
                runCatching {
                    val json = JSONObject(file.readText())
                    val fetchedAt = json.getLong("fetchedAt")
                    val version = json.optInt("version", 1)
                    if (now - fetchedAt > OSM_CACHE_TTL_MS || version < 6) file.delete()
                }
            }
    }

    private fun poiToJson(poi: Poi) = JSONObject().apply {
        put("id", poi.id)
        put("name", poi.name)
        put("lat", poi.lat)
        put("lng", poi.lng)
        poi.description?.let { put("description", it) }
        poi.iconKey?.let { put("iconKey", it) }
        poi.wikiRef?.let { put("wikiRef", it) }
    }

    private fun poisFromJson(obj: JSONObject, fetchedAt: Long) = Poi(
        id = obj.getString("id"),
        groupId = OSM_POI_GROUP_ID,
        name = obj.getString("name"),
        description = obj.optString("description").ifBlank { null },
        lat = obj.getDouble("lat"),
        lng = obj.getDouble("lng"),
        iconKey = obj.optString("iconKey").ifBlank { null },
        wikiRef = obj.optString("wikiRef").ifBlank { null },
        createdAt = fetchedAt,
        updatedAt = fetchedAt,
    )
}
