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

    private val overpassClient = httpClient.newBuilder()
        .readTimeout(OVERPASS_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(OVERPASS_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val overpassEndpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )
    private val endpointHealthLock = Any()
    private val endpointCooldownUntil = mutableMapOf<String, Long>()
    private var lastHealthyEndpoint: String? = null
    private var lastHealthyAtMs: Long = 0L
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
            [out:json][timeout:$OVERPASS_QUERY_TIMEOUT_SECONDS][bbox:$south,$west,$north,$east];
            (
              $naturalQueries
              nwr[amenity=place_of_worship][name](if: t["religion"] != "jewish" && t["religion"] != "muslim");
              nwr[amenity=planetarium][name];
              nwr[historic~"^(archaeological_site|castle|ruins|fort|city_gate)${'$'}"][name];
              nwr[tourism~"^(museum|gallery|zoo|aquarium|viewpoint)${'$'}"][name];
              nwr[leisure=garden]["garden:type"=botanical][name];
              nwr[man_made~"^(lighthouse|observatory)${'$'}"][name];
            );
            out tags center qt;
        """.trimIndent()

        val body = "data=${java.net.URLEncoder.encode(query, "UTF-8")}".toRequestBody(formMediaType)

        Log.d("OsmApiService", "fetchPois bbox=[S=${"%.4f".format(south)} W=${"%.4f".format(west)} N=${"%.4f".format(north)} E=${"%.4f".format(east)}]")

        val endpoints = endpointsForAttempt()
        if (endpoints.isEmpty()) {
            Log.w("OsmApiService", "fetchPois: all Overpass endpoints are cooling down")
            return null
        }

        for (endpoint in endpoints) {
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
                        val responseBody = response.body?.string()
                        Log.e("OsmApiService", "HTTP ${response.code} from $endpoint after ${httpMs}ms: $responseBody")
                        if (response.code == 429 || response.code in 500..599) {
                            val retryAfterMs = response.header("Retry-After")
                                ?.toLongOrNull()
                                ?.times(1_000L)
                            coolDownEndpoint(endpoint, "HTTP ${response.code}", retryAfterMs)
                        }
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
                            val imageSearchNames = (
                                listOf(name) + tagsMap.filterKeys { key ->
                                    key == "short_name" || key == "loc_name" || key == "old_name" ||
                                        key == "official_name" || key == "alt_name" ||
                                        key.startsWith("name:") || key.startsWith("short_name:") ||
                                        key.startsWith("loc_name:") || key.startsWith("old_name:") ||
                                        key.startsWith("official_name:") || key.startsWith("alt_name:")
                                }.values
                            ).flatMap { it.split(';') }
                                .map(String::trim).filter(String::isNotBlank).distinct()
                            val imageRefs = listOfNotNull(
                                tagsMap["panoramax"]?.let { "panoramax:$it" },
                                tagsMap["kartaview"]?.let { "kartaview:$it" },
                                tagsMap["mapillary"]?.let { "mapillary:$it" },
                                tagsMap["flickr"]?.let { "flickr:$it" },
                                tagsMap["image"],
                                tagsMap["photo"],
                                tagsMap["url:photo"],
                            ).flatMap { it.split(';') }
                                .map(String::trim).filter(String::isNotBlank).distinct()
                            val desc = tagsMap["description"]
                                ?: tagsMap["description:en"]
                                ?: tagsMap["description:he"]
                            if (isExcludedReligiousPoi(tagsMap)) return@runCatching null
                            val resolvedIconKey = PoiIconResolver.resolveForOsmTags(tagsMap, name, desc.orEmpty())
                            // Prefer article/entity references: they provide both a reusable summary
                            // and a safely-attributed Commons image. Arbitrary image URLs are ignored.
                            val wikiRef = tagsMap["wikipedia"]?.takeIf { it.contains(":") && !it.startsWith("http") }
                                ?: tagsMap["wikidata"]?.takeIf { it.matches(Regex("Q\\d+")) }
                                ?: tagsMap["wikimedia_commons"]?.takeIf {
                                    it.startsWith("File:") || it.startsWith("Category:")
                                }
                                ?: tagsMap["image"]?.let(::commonsFileRef)
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
                                imageSearchNames = imageSearchNames,
                                imageRefs = imageRefs,
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
                if (e is IOException) {
                    coolDownEndpoint(endpoint, e.javaClass.simpleName)
                }
                Log.w("OsmApiService", "fetchPois failed for $endpoint after ${System.currentTimeMillis() - reqStart}ms: ${e.message}")
                null
            }
            // An empty result is valid in sparse regions and should be cached normally.
            // HTTP/Overpass errors already arrive through the failure path above.
            if (pois != null) {
                markEndpointHealthy(endpoint)
                if (endpoint != endpoints.first()) {
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
            val call = overpassClient.newCall(request)
            call.timeout().timeout(OVERPASS_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
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

    /**
     * Tries the recently healthy server first while it is fresh, otherwise uses the stable base
     * order. Servers that timed out, rate-limited us, or returned 5xx are skipped temporarily.
     */
    private fun endpointsForAttempt(nowMs: Long = System.currentTimeMillis()): List<String> =
        synchronized(endpointHealthLock) {
            endpointCooldownUntil.entries.removeAll { it.value <= nowMs }
            val available = overpassEndpoints.filter { (endpointCooldownUntil[it] ?: 0L) <= nowMs }
            val preferred = lastHealthyEndpoint?.takeIf {
                it in available && nowMs - lastHealthyAtMs <= LAST_HEALTHY_MAX_AGE_MS
            }
            if (preferred == null) available else listOf(preferred) + available.filterNot { it == preferred }
        }

    private fun coolDownEndpoint(endpoint: String, reason: String, requestedCooldownMs: Long? = null) {
        val nowMs = System.currentTimeMillis()
        val cooldownMs = maxOf(ENDPOINT_COOLDOWN_MS, requestedCooldownMs ?: 0L)
        synchronized(endpointHealthLock) {
            endpointCooldownUntil[endpoint] = maxOf(
                endpointCooldownUntil[endpoint] ?: 0L,
                nowMs + cooldownMs,
            )
            if (lastHealthyEndpoint == endpoint) {
                lastHealthyEndpoint = null
                lastHealthyAtMs = 0L
            }
        }
        Log.w("OsmApiService", "Cooling down $endpoint for ${cooldownMs / 1_000L}s after $reason")
    }

    private fun markEndpointHealthy(endpoint: String) {
        synchronized(endpointHealthLock) {
            endpointCooldownUntil.remove(endpoint)
            lastHealthyEndpoint = endpoint
            lastHealthyAtMs = System.currentTimeMillis()
        }
    }

    private fun isExcludedReligiousPoi(tags: Map<String, String>): Boolean {
        if (tags["amenity"] == "place_of_worship" && tags["religion"] in EXCLUDED_RELIGIONS) {
            return true
        }
        if (tags["building"] in EXCLUDED_WORSHIP_BUILDINGS) return true
        val names = tags.filterKeys { it == "name" || it.startsWith("name:") }
            .values.joinToString(" ").lowercase()
        return EXCLUDED_WORSHIP_WORDS.any(names::contains)
    }

    /** Accepts only Wikimedia-hosted OSM image links so license metadata can still be resolved. */
    private fun commonsFileRef(value: String): String? {
        if (value.startsWith("File:")) return value
        if (!value.contains("wikimedia.org", ignoreCase = true)) return null
        val decoded = runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrNull()
            ?: return null
        decoded.substringAfter("/wiki/", "")
            .takeIf { it.startsWith("File:") }
            ?.let { return it }
        val segments = runCatching { java.net.URI(decoded).path }
            .getOrNull()?.split('/')?.filter(String::isNotBlank) ?: return null
        val thumbIndex = segments.indexOf("thumb")
        val filename = if (thumbIndex >= 0 && segments.size > thumbIndex + 3) {
            segments[thumbIndex + 3]
        } else {
            segments.lastOrNull()
        }
        return filename?.takeIf { it.contains('.') }?.let { "File:$it" }
    }

    private companion object {
        const val OVERPASS_QUERY_TIMEOUT_SECONDS = 12
        const val OVERPASS_CALL_TIMEOUT_SECONDS = 12L
        const val ENDPOINT_COOLDOWN_MS = 5 * 60 * 1_000L
        const val LAST_HEALTHY_MAX_AGE_MS = 10 * 60 * 1_000L
        val EXCLUDED_RELIGIONS = setOf("jewish", "muslim")
        val EXCLUDED_WORSHIP_BUILDINGS = setOf("synagogue", "mosque")
        val EXCLUDED_WORSHIP_WORDS = listOf(
            "synagogue", "synagoge", "sinagoga", "mosque", "masjid", "בית כנסת", "מסגד", "مسجد",
        )
        const val USER_AGENT =
            "mapping-solution/1.0 (https://github.com/lotanbar/mapping-solution)"
    }
}
