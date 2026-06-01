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

    private val overpassEndpoints = listOf(
        "https://overpass.openstreetmap.fr/api/interpreter",
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
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
            .addHeader("User-Agent", "mapping-solution/1.0")
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

    /**
     * Fetches natural/historic POI nodes within the given bounding box.
     * Returns all matching nodes (no count cap — natural features are sparse).
     */
    suspend fun fetchPois(south: Double, west: Double, north: Double, east: Double): List<Poi> {
        val query = """
            [out:json][timeout:10][bbox:$south,$west,$north,$east];
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

        Log.d("OsmApiService", "fetchPois bbox=[S=${"%.4f".format(south)} W=${"%.4f".format(west)} N=${"%.4f".format(north)} E=${"%.4f".format(east)}]")

        for (endpoint in overpassEndpoints) {
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Accept", "*/*")
                .addHeader("User-Agent", "mapping-solution/1.0")
                .post(body)
                .build()

            val reqStart = System.currentTimeMillis()
            Log.d("OsmApiService", "Trying endpoint: $endpoint")
            val result = runCatching {
                httpClient.newCall(request).execute().use { response ->
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
                            val desc = tagsMap["description"] ?: tagsMap["description:he"] ?: ""
                            val resolvedIconKey = PoiIconResolver.resolveForOsmTags(tagsMap, name, desc)
                            val wikiRef = tagsMap["image"]?.takeIf { it.startsWith("http") }
                                ?: tagsMap["wikimedia_commons"]?.takeIf { it.startsWith("File:") }
                                ?: tagsMap["wikipedia"]?.takeIf { it.contains(":") && !it.startsWith("http") }
                                ?: tagsMap["wikidata"]?.takeIf { it.matches(Regex("Q\\d+")) }
                            Poi(
                                id = "osm_${el.getLong("id")}",
                                groupId = OSM_POI_GROUP_ID,
                                name = name,
                                lat = el.getDouble("lat"),
                                lng = el.getDouble("lon"),
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
        return emptyList()
    }

    /**
     * Resolves a raw OSM image reference to a usable image URL.
     * - Direct URL (starts with "http"): returned as-is.
     * - Wikimedia Commons file (starts with "File:"): resolved via Commons API.
     * - Wikidata item (format "Q12345"): P18 image claim resolved via Wikidata → Commons API.
     * - Wikipedia article (format "lang:Title"): resolved via Wikipedia pageimages API.
     */
    suspend fun resolveImageUrl(wikiRef: String): String? = when {
        wikiRef.startsWith("http") -> wikiRef.replaceFirst("http://", "https://")
        wikiRef.startsWith("File:") -> resolveCommonsFile(wikiRef)
        wikiRef.matches(Regex("Q\\d+")) -> resolveWikidataItem(wikiRef)
        wikiRef.contains(":") -> resolveWikipediaArticle(wikiRef)
        else -> null
    }

    private suspend fun resolveWikidataItem(wikidataId: String): String? {
        val url = "https://www.wikidata.org/w/api.php" +
            "?action=wbgetclaims&entity=$wikidataId&property=P18&format=json"
        return runCatching {
            val request = Request.Builder().url(url)
                .addHeader("User-Agent", "mapping-solution/1.0").get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val claims = JSONObject(response.body!!.string())
                    .optJSONObject("claims") ?: return@runCatching null
                val p18 = claims.optJSONArray("P18") ?: return@runCatching null
                val filename = p18.getJSONObject(0)
                    .optJSONObject("mainsnak")
                    ?.optJSONObject("datavalue")
                    ?.optString("value")
                    ?.ifBlank { null } ?: return@runCatching null
                resolveCommonsFile("File:$filename")
            }
        }.getOrElse { e ->
            Log.w("OsmApiService", "resolveWikidataItem failed for $wikidataId", e)
            null
        }
    }

    private suspend fun resolveCommonsFile(fileRef: String): String? {
        val encoded = java.net.URLEncoder.encode(fileRef, "UTF-8")
        val url = "https://commons.wikimedia.org/w/api.php" +
            "?action=query&titles=$encoded&prop=imageinfo&iiprop=url&iiurlwidth=1600&format=json"
        return runCatching {
            val request = Request.Builder().url(url)
                .addHeader("User-Agent", "mapping-solution/1.0").get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val pages = JSONObject(response.body!!.string())
                    .getJSONObject("query").getJSONObject("pages")
                val page = pages.keys().asSequence().firstOrNull()?.let { pages.getJSONObject(it) }
                    ?: return@runCatching null
                val info = page.optJSONArray("imageinfo")?.getJSONObject(0) ?: return@runCatching null
                // Prefer 1600px thumbnail (always JPEG regardless of source format);
                // fall back to original URL if thumbnail is unavailable (small images)
                info.optString("thumburl").ifBlank { null }
                    ?: info.optString("url").ifBlank { null }
            }
        }.getOrElse { e ->
            Log.w("OsmApiService", "resolveCommonsFile failed for $fileRef", e)
            null
        }
    }

    private suspend fun resolveWikipediaArticle(wikiRef: String): String? {
        val colonIdx = wikiRef.indexOf(':')
        val lang = wikiRef.substring(0, colonIdx)
        val title = wikiRef.substring(colonIdx + 1)
        val encoded = java.net.URLEncoder.encode(title, "UTF-8")
        val url = "https://$lang.wikipedia.org/w/api.php" +
            "?action=query&titles=$encoded&prop=pageimages&pithumbsize=1600&format=json"
        return runCatching {
            val request = Request.Builder().url(url)
                .addHeader("User-Agent", "mapping-solution/1.0").get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val pages = JSONObject(response.body!!.string())
                    .getJSONObject("query").getJSONObject("pages")
                val page = pages.keys().asSequence().firstOrNull()?.let { pages.getJSONObject(it) }
                    ?: return@runCatching null
                page.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null }
            }
        }.getOrElse { e ->
            Log.w("OsmApiService", "resolveWikipediaArticle failed for $wikiRef", e)
            null
        }
    }
}
