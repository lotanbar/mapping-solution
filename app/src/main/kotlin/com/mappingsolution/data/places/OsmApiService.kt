package com.mappingsolution.data.places

import android.util.Log
import com.mappingsolution.data.model.Poi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OsmApiService @Inject constructor(private val httpClient: OkHttpClient) {

    private val overpassUrl = "https://overpass-api.de/api/interpreter"
    private val formMediaType = "application/x-www-form-urlencoded".toMediaType()

    /**
     * Searches for places matching [query] using Nominatim, constrained to the given bounding box.
     * viewbox format: west, north, east, south (left, top, right, bottom).
     * Returns at most 5 results.
     */
    suspend fun searchText(
        query: String,
        south: Double,
        west: Double,
        north: Double,
        east: Double,
    ): List<Poi> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        // Nominatim viewbox: left,top,right,bottom = west,north,east,south
        val viewbox = "$west,$north,$east,$south"
        val url = "https://nominatim.openstreetmap.org/search" +
            "?q=$encodedQuery&format=json&viewbox=$viewbox&bounded=1&limit=5"

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "MappingSolution/1.0")
            .get()
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("OsmApiService", "searchText HTTP ${response.code}")
                    return@runCatching emptyList()
                }
                val jsonArray = org.json.JSONArray(response.body!!.string())
                val now = System.currentTimeMillis()
                (0 until jsonArray.length()).mapNotNull { i ->
                    runCatching {
                        val el = jsonArray.getJSONObject(i)
                        val osmType = el.optString("osm_type", "")
                        val osmId = el.optLong("osm_id", 0L)
                        val displayName = el.optString("display_name", "")
                        val name = displayName.substringBefore(",").trim()
                            .takeIf { it.isNotBlank() } ?: return@runCatching null
                        val cls = el.optString("class", "")
                        val type = el.optString("type", "")
                        val resolvedIconKey = PoiIconResolver.resolveForOsmTags(mapOf(cls to type))
                        val prefix = when (osmType) {
                            "node" -> "n"
                            "way" -> "w"
                            "relation" -> "r"
                            else -> "x"
                        }
                        Poi(
                            id = "osm_${prefix}${osmId}",
                            groupId = OSM_POI_GROUP_ID,
                            name = name,
                            lat = el.getString("lat").toDouble(),
                            lng = el.getString("lon").toDouble(),
                            iconKey = resolvedIconKey,
                            createdAt = now,
                            updatedAt = now,
                        )
                    }.getOrNull()
                }
            }
        }.getOrElse { e ->
            Log.e("OsmApiService", "searchText failed", e)
            emptyList()
        }
    }

    /**
     * Fetches natural/historic POI nodes within the given bounding box.
     * Returns all matching nodes (no count cap — natural features are sparse).
     */
    suspend fun fetchPois(south: Double, west: Double, north: Double, east: Double): List<Poi> {
        val query = """
            [out:json][timeout:25][bbox:$south,$west,$north,$east];
            (
              node[natural~"^(peak|volcano|cave_entrance|waterfall|glacier|hot_spring|geyser)${'$'}"][name];
              node[leisure=nature_reserve][name];
              node[amenity=observatory][name];
              node[historic~"^(monument|castle|archaeological_site|ruins|fort|memorial)${'$'}"][name];
              node[tourism=viewpoint][name];
              node[man_made=lighthouse][name];
            );
            out body;
        """.trimIndent()

        val body = "data=${java.net.URLEncoder.encode(query, "UTF-8")}".toRequestBody(formMediaType)
        val request = Request.Builder()
            .url(overpassUrl)
            .addHeader("Accept", "*/*")
            .addHeader("User-Agent", "MappingSolution/1.0")
            .post(body)
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("OsmApiService", "HTTP ${response.code}: ${response.body?.string()}")
                    return@runCatching emptyList()
                }
                val json = JSONObject(response.body!!.string())
                val elements = json.optJSONArray("elements") ?: return@runCatching emptyList()
                val now = System.currentTimeMillis()
                (0 until elements.length()).mapNotNull { i ->
                    runCatching {
                        val el = elements.getJSONObject(i)
                        val tags = el.optJSONObject("tags") ?: return@runCatching null
                        val name = tags.optString("name").takeIf { it.isNotBlank() }
                            ?: tags.optString("name:en").takeIf { it.isNotBlank() }
                            ?: return@runCatching null
                        val tagsMap = tags.keys().asSequence().associateWith { tags.getString(it) }
                        val resolvedIconKey = PoiIconResolver.resolveForOsmTags(tagsMap)
                        Poi(
                            id = "osm_${el.getLong("id")}",
                            groupId = OSM_POI_GROUP_ID,
                            name = name,
                            lat = el.getDouble("lat"),
                            lng = el.getDouble("lon"),
                            iconKey = resolvedIconKey,
                            createdAt = now,
                            updatedAt = now,
                        )
                    }.getOrNull()
                }
            }
        }.getOrElse { e ->
            Log.e("OsmApiService", "fetchPois failed", e)
            emptyList()
        }
    }
}
