package com.mappingsolution.data.places

import android.util.Log
import com.mappingsolution.data.model.Poi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class OsmApiService @Inject constructor(private val httpClient: OkHttpClient) {

    private val overpassEndpoints = listOf(
        "https://overpass.openstreetmap.fr/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://overpass-api.de/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )
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
            .addHeader("User-Agent", USER_AGENT)
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
                        val resolvedIconKey = PoiIconResolver.resolveForOsmTags(mapOf(cls to type), name)
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

    /** Fetches the deliberately narrow exploration categories used by the map. */
    suspend fun fetchPois(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        includeNatural: Boolean = true,
    ): List<Poi>? {
        val naturalQueries = if (includeNatural) """
              nwr[natural~"^(cave_entrance|waterfall|glacier|hot_spring|geyser)${'$'}"][name];
        """.trimIndent() else ""
        val query = """
            [out:json][timeout:10][bbox:$south,$west,$north,$east];
            (
              $naturalQueries
              nwr[amenity~"^(place_of_worship|planetarium)${'$'}"][name];
              nwr[historic~"^(archaeological_site|castle|ruins|fort|city_gate)${'$'}"][name];
              nwr[tourism~"^(museum|gallery|zoo|aquarium|viewpoint)${'$'}"][name];
              nwr[leisure=garden]["garden:type"=botanical][name];
              nwr[man_made~"^(lighthouse|observatory)${'$'}"][name];
            );
            out center;
        """.trimIndent()

        val body = "data=${java.net.URLEncoder.encode(query, "UTF-8")}".toRequestBody(formMediaType)

        Log.d("OsmApiService", "fetchPois bbox=[S=${"%.4f".format(south)} W=${"%.4f".format(west)} N=${"%.4f".format(north)} E=${"%.4f".format(east)}]")

        for (endpoint in overpassEndpoints) {
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Accept", "*/*")
                .addHeader("User-Agent", USER_AGENT)
                .post(body)
                .build()

            val reqStart = System.currentTimeMillis()
            Log.d("OsmApiService", "Trying endpoint: $endpoint")
            val result = runCatching {
                executeCancellable(request).use { response ->
                    val httpMs = System.currentTimeMillis() - reqStart
                    if (!response.isSuccessful) {
                        Log.e("OsmApiService", "HTTP ${response.code} from $endpoint after ${httpMs}ms: ${response.body?.string()}")
                        return@runCatching null
                    }
                    val bodyStr = response.body!!.string()
                    val parseStart = System.currentTimeMillis()
                    val json = JSONObject(bodyStr)
                    val elements = json.optJSONArray("elements") ?: return@runCatching emptyList()
                    Log.d("OsmApiService", "HTTP OK from $endpoint in ${httpMs}ms — ${elements.length()} elements (body ${bodyStr.length} bytes)")
                    val now = System.currentTimeMillis()
                    val pois = (0 until elements.length()).mapNotNull { i ->
                        runCatching {
                            val el = elements.getJSONObject(i)
                            val tags = el.optJSONObject("tags") ?: return@runCatching null
                            val name = tags.optString("name").takeIf { it.isNotBlank() }
                                ?: tags.optString("name:en").takeIf { it.isNotBlank() }
                                ?: return@runCatching null
                            val tagsMap = tags.keys().asSequence().associateWith { tags.getString(it) }
                            val desc = tagsMap["description"]
                                ?: tagsMap["description:en"]
                                ?: tagsMap["description:he"]
                            val resolvedIconKey = PoiIconResolver.resolveForOsmTags(tagsMap, name, desc.orEmpty())
                            // Prefer article/entity references: they provide both a reusable summary
                            // and a safely-attributed Commons image. Arbitrary image URLs are ignored.
                            val wikiRef = tagsMap["wikipedia"]?.takeIf { it.contains(":") && !it.startsWith("http") }
                                ?: tagsMap["wikidata"]?.takeIf { it.matches(Regex("Q\\d+")) }
                                ?: tagsMap["wikimedia_commons"]?.takeIf { it.startsWith("File:") }
                            val center = el.optJSONObject("center")
                            val lat = if (el.has("lat")) el.getDouble("lat")
                                else center?.optDouble("lat") ?: return@runCatching null
                            val lng = if (el.has("lon")) el.getDouble("lon")
                                else center?.optDouble("lon") ?: return@runCatching null
                            val osmType = el.optString("type", "node")
                            Poi(
                                id = "osm_${osmType.firstOrNull() ?: 'n'}${el.getLong("id")}",
                                groupId = OSM_POI_GROUP_ID,
                                name = name,
                                description = desc,
                                lat = lat,
                                lng = lng,
                                iconKey = resolvedIconKey,
                                wikiRef = wikiRef,
                                createdAt = now,
                                updatedAt = now,
                            )
                        }.getOrNull()
                    }
                    Log.d("OsmApiService", "Parsed ${pois.size}/${elements.length()} POIs in ${System.currentTimeMillis() - parseStart}ms")
                    pois
                }
            }

            val pois = result.getOrElse { e ->
                if (e is CancellationException) throw e
                Log.w("OsmApiService", "fetchPois failed for $endpoint after ${System.currentTimeMillis() - reqStart}ms: ${e.message}")
                null
            }
            if (pois != null) {
                if (endpoint != overpassEndpoints.first()) {
                    Log.i("OsmApiService", "fetchPois succeeded via fallback: $endpoint")
                }
                return pois
            }
        }
        Log.e("OsmApiService", "fetchPois: all Overpass endpoints failed")
        return null
    }

    /** Cancels the underlying OkHttp call immediately when a newer viewport cancels this coroutine. */
    private suspend fun executeCancellable(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            call.timeout().timeout(7, TimeUnit.SECONDS)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) continuation.resume(response)
                    else response.close()
                }
            })
        }

    private companion object {
        const val USER_AGENT =
            "mapping-solution/1.0 (https://github.com/lotanbar/mapping-solution)"
    }
}
