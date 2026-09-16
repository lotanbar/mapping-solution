package com.mappingsolution.data.places

import org.jsoup.parser.Parser
import org.jsoup.Jsoup
import com.mappingsolution.data.util.StorageManager
import com.mappingsolution.data.util.ApiKeys
import com.mappingsolution.data.util.AppLog
import com.mappingsolution.data.model.Poi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class WikimediaImage(
    val imageUrl: String,
    val imageSourceUrl: String? = null,
    val imageAuthor: String? = null,
    val imageLicense: String? = null,
    val imageLicenseUrl: String? = null,
) {
    val imageCredit: String?
        get() = listOfNotNull(
            imageAuthor?.takeIf { it.isNotBlank() },
            imageLicense?.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifBlank { null }
}

data class WikimediaContent(
    val imageUrl: String? = null,
    val description: String? = null,
    val pageUrl: String? = null,
    val imageSourceUrl: String? = null,
    val imageAuthor: String? = null,
    val imageLicense: String? = null,
    val imageLicenseUrl: String? = null,
    val additionalImages: List<WikimediaImage> = emptyList(),
) {
    val hasContent: Boolean get() = imageUrl != null || description != null

    val imageCredit: String?
        get() = listOfNotNull(
            imageAuthor?.takeIf { it.isNotBlank() },
            imageLicense?.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifBlank { null }

    val images: List<WikimediaImage>
        get() = buildList {
            imageUrl?.let {
                add(WikimediaImage(it, imageSourceUrl, imageAuthor, imageLicense, imageLicenseUrl))
            }
            addAll(additionalImages.filterNot { candidate -> candidate.imageUrl == imageUrl })
        }.distinctBy(WikimediaImage::imageUrl)
}

/**
 * Resolves OSM wikipedia/wikidata/wikimedia_commons references into reusable content.
 * Results are cached for 30 days. Network calls are serialized and limited to 2/second,
 * with an identifying User-Agent and Retry-After handling for Wikimedia's public APIs.
 */
@Singleton
class WikimediaRepository @Inject constructor(
    storageManager: StorageManager,
    private val httpClient: OkHttpClient,
    private val apiKeys: ApiKeys,
) {
    private val cacheDir = storageManager.getCacheDir("wikimedia_poi_cache")
    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()
    private val resolveMutexes = ConcurrentHashMap<String, Mutex>()
    private val hostThrottles = ConcurrentHashMap<String, HostThrottle>()

    suspend fun getContent(poi: Poi): WikimediaContent? = withContext(Dispatchers.IO) {
        val searchNames = poiSearchNames(poi)
        val cacheKey = "v$CACHE_VERSION|mapillary=${apiKeys.mapillary.isNotBlank()}|" +
            "${poi.id}|${poi.wikiRef.orEmpty()}|" +
            poi.imageRefs.joinToString(";") + "|" + searchNames.joinToString(";") +
            "|${"%.5f".format(poi.lat)},${"%.5f".format(poi.lng)}"
        loadMemoryCache(cacheKey)?.let {
            return@withContext it.content.takeIf(WikimediaContent::hasContent)
        }
        loadCache(cacheKey)?.let {
            memoryCache[cacheKey] = it
            return@withContext it.content.takeIf(WikimediaContent::hasContent)
        }

        resolveMutexes.getOrPut(cacheKey) { Mutex() }.withLock {
            loadMemoryCache(cacheKey)?.let {
                return@withLock it.content.takeIf(WikimediaContent::hasContent)
            }
            loadCache(cacheKey)?.let {
                memoryCache[cacheKey] = it
                return@withLock it.content.takeIf(WikimediaContent::hasContent)
            }

            val attempt = ResolutionAttempt()
            val resolved = try {
                val exactImages = attempt.allProviders(poi.imageRefs.map { ref ->
                    suspend { resolveExactImageRef(ref, poi.name) }
                })
                val linked = attempt.provider {
                    poi.wikiRef?.let { resolve(it, poi.name, attempt) }
                }
                val discovered = if (linked?.imageUrl == null) {
                    val wikipedia = attempt.provider {
                        resolveWikipediaByName(searchNames, poi, attempt)
                    }
                    val wikidata = if (wikipedia?.imageUrl == null) {
                        attempt.provider { resolveWikidataByName(searchNames, poi, attempt) }
                    } else null
                    wikipedia.withFallbackContent(wikidata)
                } else null
                val base = linked.withFallbackContent(discovered.withoutImage())
                val discoveredImages = attempt.allProviders(
                    listOf(
                        { resolveNearbyImage(poi, searchNames) },
                    ),
                )
                base.withImages(
                    exactImages + listOfNotNull(
                        linked?.takeIf { it.imageUrl != null },
                        discovered?.takeIf { it.imageUrl != null },
                    ) + discoveredImages,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to resolve Wikimedia content for ${poi.name}", e)
                return@withLock null
            }
            val cached = resolved ?: WikimediaContent()
            // A provider outage is not evidence that a POI has no image. Cache complete misses
            // only when every attempted provider answered successfully; positive images are safe
            // to cache even if an earlier optional source failed.
            if (cached.imageUrl != null || !attempt.hadFailure) {
                val entry = CacheEntry(cached, System.currentTimeMillis())
                memoryCache[cacheKey] = entry
                storeCache(cacheKey, entry)
            }
            cached.takeIf(WikimediaContent::hasContent)
        }
    }

    private inner class ResolutionAttempt {
        var hadFailure: Boolean = false
            private set

        suspend fun provider(block: suspend () -> WikimediaContent?): WikimediaContent? = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            hadFailure = true
            AppLog.w(TAG, "Image provider failed; continuing", e)
            null
        }

        suspend fun allProviders(
            providers: List<suspend () -> WikimediaContent?>,
        ): List<WikimediaContent> = providers.mapNotNull { provider(it) }
    }

    private suspend fun resolveExactImageRef(ref: String, poiName: String): WikimediaContent? {
        return when {
            ref.startsWith("website:") -> resolveOfficialWebsiteImage(ref.substringAfter(':'))
            ref.startsWith("panoramax:") -> resolvePanoramaxId(ref.substringAfter(':'))
            ref.startsWith("kartaview:") -> resolveKartaViewId(ref.substringAfter(':'))
            ref.startsWith("mapillary:") -> resolveMapillaryId(ref.substringAfter(':'))
            ref.startsWith("flickr:") -> resolveFlickrUrl(ref.substringAfter(':'))
            ref.startsWith("File:") -> resolveCommonsFile(ref)
            ref.startsWith("Category:") -> resolveCommonsCategory(ref, poiName)
            "panoramax.xyz" in ref -> resolvePanoramaxId(ref)
            "kartaview.org" in ref || "openstreetcam.org" in ref -> resolveKartaViewId(ref)
            "mapillary.com" in ref -> resolveMapillaryId(ref)
            "flickr.com" in ref -> resolveFlickrUrl(ref)
            "commons.wikimedia.org" in ref || "upload.wikimedia.org" in ref -> {
                val fileRef = commonsFileRef(ref)
                if (fileRef != null) resolveCommonsFile(fileRef) else null
            }
            else -> resolveDirectImageUrl(ref)
        }
    }

    /** An OSM image/photo tag points at this URL explicitly, so no proximity guess is involved. */
    private fun resolveDirectImageUrl(rawUrl: String): WikimediaContent? {
        val url = externalHttpUrl(rawUrl) ?: return null
        return WikimediaContent(imageUrl = url, imageSourceUrl = url)
    }

    /** Uses the preview image declared by an OSM-linked official website. */
    private suspend fun resolveOfficialWebsiteImage(rawUrl: String): WikimediaContent? {
        val website = externalHttpUrl(rawUrl) ?: return null
        val request = Request.Builder()
            .url(website)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .get()
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Official website HTTP ${response.code}")
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            if (contentType.isNotBlank() && "html" !in contentType) return@use null
            val source = response.body?.source() ?: return@use null
            source.request(WEBSITE_HTML_LIMIT_BYTES)
            val html = source.readUtf8(minOf(source.buffer.size, WEBSITE_HTML_LIMIT_BYTES))
            val imageRef = websitePreviewImage(html) ?: return@use null
            val imageUrl = response.request.url.resolve(Parser.unescapeEntities(imageRef, true))?.toString() ?: return@use null
            WikimediaContent(
                imageUrl = imageUrl,
                imageSourceUrl = response.request.url.toString(),
                imageAuthor = response.request.url.host,
            )
        }
    }

    private fun websitePreviewImage(html: String): String? {
        val metaTags = META_TAG.findAll(html)
        for (tagMatch in metaTags) {
            val attributes = HTML_ATTRIBUTE.findAll(tagMatch.value).associate { match ->
                match.groupValues[1].lowercase() to match.groupValues[3]
            }
            val property = (attributes["property"] ?: attributes["name"])?.lowercase()
            if (property in WEBSITE_IMAGE_PROPERTIES) {
                attributes["content"]?.takeIf(String::isNotBlank)?.let { return it }
            }
        }
        return IMAGE_SRC_LINK.find(html)?.groupValues?.getOrNull(3)?.takeIf(String::isNotBlank)
    }

    private fun externalHttpUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        val candidate = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("www.", ignoreCase = true) -> "https://$trimmed"
            else -> return null
        }
        return runCatching { candidate.toHttpUrl().toString() }.getOrNull()
    }

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
        } else segments.lastOrNull()
        return filename?.takeIf { it.contains('.') }?.let { "File:$it" }
    }

    private suspend fun resolvePanoramaxId(rawId: String): WikimediaContent? {
        val id = PANORAMAX_ID.find(rawId)?.value ?: return null
        val json = requestJson("https://api.panoramax.xyz/api/search?ids=$id&limit=1")
        return panoramaFeatureContent(json.optJSONArray("features")?.optJSONObject(0))
    }

    private suspend fun resolveKartaViewId(rawId: String): WikimediaContent? {
        val id = Regex("\\d+").find(rawId)?.value ?: return null
        val item = requestJson("https://api.openstreetcam.org/2.0/photo/?id=$id")
            .optJSONObject("result")?.optJSONArray("data")?.optJSONObject(0) ?: return null
        return WikimediaContent(
            imageUrl = item.optString("imageProcUrl").ifBlank { item.optString("fileurlProc") }
                .ifBlank { return null },
            imageSourceUrl = "https://kartaview.org/details/$id/track-info",
            imageAuthor = item.optString("username").ifBlank { "KartaView contributor" },
            imageLicense = "CC BY-SA 4.0",
            imageLicenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
        )
    }

    private suspend fun resolveMapillaryId(rawId: String): WikimediaContent? {
        val token = apiKeys.mapillary.trim()
        if (token.isBlank()) return null
        val id = mapillaryImageId(rawId) ?: return null
        val url = "https://graph.mapillary.com/$id".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "id,thumb_1024_url,creator")
            .build()
        return mapillaryImage(requestMapillaryJson(url.toString(), token))?.asContent()
    }

    private suspend fun resolveFlickrUrl(rawUrl: String): WikimediaContent? {
        val url = rawUrl.takeIf { it.startsWith("http") } ?: return null
        val endpoint = "https://www.flickr.com/services/oembed/".toHttpUrl().newBuilder()
            .addQueryParameter("format", "json")
            .addQueryParameter("url", url)
            .build()
        val json = requestJson(endpoint.toString())
        val licenseId = json.optString("license_id")
        val license = FLICKR_LICENSES[licenseId] ?: return null
        return WikimediaContent(
            imageUrl = json.optString("url").ifBlank { json.optString("thumbnail_url") }
                .ifBlank { return null },
            imageSourceUrl = url,
            imageAuthor = json.optString("author_name").ifBlank { null },
            imageLicense = license.first,
            imageLicenseUrl = license.second,
        )
    }

    private suspend fun resolve(
        ref: String,
        poiName: String,
        attempt: ResolutionAttempt,
    ): WikimediaContent? = when {
        ref.startsWith("File:") -> resolveCommonsFile(ref)
        ref.startsWith("Category:") -> resolveCommonsCategory(ref, poiName)
        ref.matches(Regex("Q\\d+")) -> resolveWikidata(ref, attempt = attempt)
        ref.contains(':') && !ref.startsWith("http") -> resolveWikipedia(ref, attempt = attempt)
        else -> null
    }

    private suspend fun resolveWikipedia(
        ref: String,
        expectedLocation: Poi? = null,
        attempt: ResolutionAttempt,
    ): WikimediaContent? {
        val separator = ref.indexOf(':')
        if (separator <= 0 || separator == ref.lastIndex) return null
        val language = ref.substring(0, separator)
        val title = ref.substring(separator + 1)
        val url = "https://$language.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("prop", "extracts|pageprops|pageimages|coordinates|info|images")
            .addQueryParameter("exintro", "1")
            .addQueryParameter("explaintext", "1")
            .addQueryParameter("redirects", "1")
            .addQueryParameter("inprop", "url")
            .addQueryParameter("piprop", "name")
            .addQueryParameter("imlimit", "20")
            .addQueryParameter("titles", title)
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .build()

        val page = requestJson(url.toString())
            .optJSONObject("query")
            ?.optJSONArray("pages")
            ?.optJSONObject(0)
            ?: return null
        val description = page.optString("extract").ifBlank { null }
        val pageUrl = page.optString("fullurl").ifBlank { null }
        val wikidataId = page.optJSONObject("pageprops")
            ?.optString("wikibase_item")
            ?.ifBlank { null }
        if (page.optJSONObject("pageprops")?.has("disambiguation") == true) return null
        if (expectedLocation != null) {
            val nameScore = nameMatchScore(title, listOf(page.optString("title")))
            if (nameScore < MIN_WIKIDATA_NAME_SCORE) return null
            val coordinates = page.optJSONArray("coordinates")?.optJSONObject(0) ?: return null
            val lat = coordinates.optDouble("lat", Double.NaN)
            val lng = coordinates.optDouble("lon", Double.NaN)
            if (!lat.isFinite() || !lng.isFinite()) return null
            val typeMatches = semanticTypeMatches(expectedLocation, description.orEmpty())
            val distance = distanceMeters(expectedLocation.lat, expectedLocation.lng, lat, lng)
            if (distance > semanticDistanceLimitMeters(expectedLocation, nameScore, typeMatches)) {
                return null
            }
        }
        val wikidata = wikidataId?.let {
            attempt.provider { resolveWikidata(it, includeDescription = false, attempt = attempt) }
        }
        val pageImage = page.optString("pageimage").ifBlank { null }
            ?.let { attempt.provider { resolveCommonsFile("File:$it") } }
        val articleFileRefs = page.optJSONArray("images")?.let { images ->
            (0 until images.length()).mapNotNull { index ->
                images.optJSONObject(index)?.optString("title")?.takeIf { it.startsWith("File:") }
            }
        }.orEmpty()
        val currentImages = listOfNotNull(wikidata, pageImage)
            .flatMap(WikimediaContent::images)
            .distinctBy(WikimediaImage::imageUrl)
        val articleImages = if (currentImages.size < MAX_IMAGES_PER_POI) {
            attempt.provider { resolveCommonsFiles(articleFileRefs, page.optString("title")) }
        } else null
        val imageCandidates = listOfNotNull(wikidata, pageImage, articleImages)
        val image = imageCandidates.firstOrNull()?.withImages(imageCandidates)
        return WikimediaContent(
            imageUrl = image?.imageUrl,
            description = description,
            pageUrl = pageUrl,
            imageSourceUrl = image?.imageSourceUrl,
            imageAuthor = image?.imageAuthor,
            imageLicense = image?.imageLicense,
            imageLicenseUrl = image?.imageLicenseUrl,
            additionalImages = image?.additionalImages.orEmpty(),
        )
    }

    /** Finds unlinked English Wikipedia articles using exact OSM names. */
    private suspend fun resolveWikipediaByName(
        names: List<String>,
        poi: Poi,
        attempt: ResolutionAttempt,
    ): WikimediaContent? {
        var firstContent: WikimediaContent? = null
        for (name in names.filter(::containsLatinLetter).take(MAX_SEARCH_NAMES)) {
            val content = resolveWikipedia("en:$name", expectedLocation = poi, attempt = attempt)
            if (content?.imageUrl != null) return content
            if (firstContent == null && content?.hasContent == true) firstContent = content
        }
        return firstContent
    }

    /** Discovers unlinked Wikidata entities, accepting them only when coordinates agree. */
    private suspend fun resolveWikidataByName(
        names: List<String>,
        poi: Poi,
        attempt: ResolutionAttempt,
    ): WikimediaContent? {
        var firstContent: WikimediaContent? = null
        val seenIds = mutableSetOf<String>()
        for (name in names.take(MAX_SEARCH_NAMES)) {
            if (normalize(name).length < 2) continue
            val language = when {
                name.any { it in '\u0600'..'\u06ff' } -> "ar"
                name.any { it in '\u0590'..'\u05ff' } -> "he"
                else -> "en"
            }
            val searchUrl = "https://www.wikidata.org/w/api.php".toHttpUrl().newBuilder()
                .addQueryParameter("action", "wbsearchentities")
                .addQueryParameter("search", name)
                .addQueryParameter("language", language)
                .addQueryParameter("uselang", "en")
                .addQueryParameter("type", "item")
                .addQueryParameter("limit", "5")
                .addQueryParameter("format", "json")
                .build()
            val search = requestJson(searchUrl.toString()).optJSONArray("search") ?: continue
            val searchHits = (0 until search.length()).mapNotNull { index ->
                search.optJSONObject(index)?.takeIf {
                    it.optString("id").matches(Regex("Q\\d+"))
                }
            }
            val ids = searchHits.map { it.optString("id") }
            if (ids.isEmpty()) continue
            val entitiesUrl = "https://www.wikidata.org/w/api.php".toHttpUrl().newBuilder()
                .addQueryParameter("action", "wbgetentities")
                .addQueryParameter("ids", ids.joinToString("|"))
                .addQueryParameter("props", "claims|labels|aliases|descriptions")
                .addQueryParameter("languages", listOf(language, "en").distinct().joinToString("|"))
                .addQueryParameter("format", "json")
                .build()
            val entities = requestJson(entitiesUrl.toString()).optJSONObject("entities") ?: continue
            val candidates = searchHits.mapNotNull { searchHit ->
                val id = searchHit.optString("id")
                val entity = entities.optJSONObject(id) ?: return@mapNotNull null
                val coordinate = wikidataCoordinate(entity) ?: return@mapNotNull null
                val distance = distanceMeters(
                    poi.lat,
                    poi.lng,
                    coordinate.first,
                    coordinate.second,
                )
                val nameScore = wikidataNameScore(name, entity, searchHit)
                if (nameScore < MIN_WIKIDATA_NAME_SCORE) return@mapNotNull null
                val typeMatches = wikidataTypeMatches(poi, entity, searchHit)
                val maxDistance = semanticDistanceLimitMeters(poi, nameScore, typeMatches)
                if (distance > maxDistance) return@mapNotNull null
                WikidataCandidate(
                    id = id,
                    entity = entity,
                    distance = distance,
                    nameScore = nameScore,
                    typeMatches = typeMatches,
                    hasDirectImage = hasDirectWikidataImage(entity),
                )
            }.sortedWith(
                compareByDescending<WikidataCandidate> { it.nameScore }
                    .thenByDescending { it.typeMatches }
                    .thenByDescending { it.hasDirectImage }
                    .thenBy { it.distance },
            )
            for (candidate in candidates) {
                if (!seenIds.add(candidate.id)) continue
                val content = attempt.provider {
                    resolveWikidata(candidate.id, attempt = attempt, entityJson = candidate.entity)
                }
                if (content?.imageUrl != null) return content
                if (firstContent == null && content?.hasContent == true) firstContent = content
            }
        }
        return firstContent
    }

    private data class WikidataCandidate(
        val id: String,
        val entity: JSONObject,
        val distance: Double,
        val nameScore: Int,
        val typeMatches: Boolean,
        val hasDirectImage: Boolean,
    )

    private fun wikidataNameScore(
        expectedName: String,
        entity: JSONObject,
        searchHit: JSONObject,
    ): Int {
        val candidateNames = buildList {
            add(searchHit.optString("label"))
            add(searchHit.optJSONObject("match")?.optString("text").orEmpty())
            entity.optJSONObject("labels")?.let { labels ->
                labels.keys().forEach { language ->
                    add(labels.optJSONObject(language)?.optString("value").orEmpty())
                }
            }
            entity.optJSONObject("aliases")?.let { aliases ->
                aliases.keys().forEach { language ->
                    val values = aliases.optJSONArray(language) ?: return@forEach
                    (0 until values.length()).forEach { index ->
                        add(values.optJSONObject(index)?.optString("value").orEmpty())
                    }
                }
            }
        }.filter(String::isNotBlank)
        return nameMatchScore(expectedName, candidateNames)
    }

    private fun nameMatchScore(expectedName: String, candidateNames: List<String>): Int {
        val expected = normalize(expectedName)
        if (expected.isBlank()) return 0
        if (candidateNames.any { normalize(it) == expected }) return WIKIDATA_EXACT_NAME_SCORE
        val expectedTokens = matchingTokens(expectedName)
        if (expectedTokens.isEmpty()) return 0
        return candidateNames.maxOfOrNull { candidate ->
            val candidateTokens = matchingTokens(candidate).toSet()
            val matched = expectedTokens.count(candidateTokens::contains)
            when {
                matched == expectedTokens.size -> 3
                expectedTokens.size >= 2 && matched * 4 >= expectedTokens.size * 3 -> 2
                else -> 0
            }
        } ?: 0
    }

    private fun wikidataTypeMatches(
        poi: Poi,
        entity: JSONObject,
        searchHit: JSONObject,
    ): Boolean {
        val descriptions = buildList {
            add(searchHit.optString("description"))
            entity.optJSONObject("descriptions")?.let { values ->
                values.keys().forEach { language ->
                    add(values.optJSONObject(language)?.optString("value").orEmpty())
                }
            }
        }.joinToString(" ").let(::normalize)
        return semanticTypeMatches(poi, descriptions)
    }

    private fun semanticTypeMatches(poi: Poi, text: String): Boolean {
        val expectedWords = WIKIDATA_TYPE_WORDS[poi.iconKey] ?: return true
        val normalizedText = normalize(text)
        return expectedWords.any { expected ->
            Regex("(^| )${Regex.escape(expected)}( |$)").containsMatchIn(normalizedText)
        }
    }

    private fun semanticDistanceLimitMeters(
        poi: Poi,
        nameScore: Int,
        typeMatches: Boolean,
    ): Double {
        if (!typeMatches) {
            return if (nameScore == WIKIDATA_EXACT_NAME_SCORE) {
                WIKIDATA_EXACT_UNTYPED_DISTANCE_METERS
            } else 0.0
        }
        val categoryLimit = when (poi.iconKey) {
            "ruins", "castle", "zoo", "garden" -> 1_500.0
            "waterfall", "natural", "cave" -> 1_000.0
            else -> 600.0
        }
        return when (nameScore) {
            WIKIDATA_EXACT_NAME_SCORE -> categoryLimit
            3 -> minOf(categoryLimit, 750.0)
            else -> minOf(categoryLimit, 300.0)
        }
    }

    private fun hasDirectWikidataImage(entity: JSONObject): Boolean {
        val claims = entity.optJSONObject("claims") ?: return false
        return claims.optJSONArray("P18")?.length()?.let { it > 0 } == true ||
            claims.optJSONArray("P373")?.length()?.let { it > 0 } == true
    }

    private suspend fun resolveWikidata(
        id: String,
        includeDescription: Boolean = true,
        attempt: ResolutionAttempt,
        entityJson: JSONObject? = null,
    ): WikimediaContent? {
        val entity = entityJson ?: requestJson("https://www.wikidata.org/wiki/Special:EntityData/$id.json")
            .optJSONObject("entities")?.optJSONObject(id) ?: return null
        val descriptions = entity.optJSONObject("descriptions")
        val description = if (includeDescription) {
            descriptions?.optJSONObject("en")?.optString("value")?.ifBlank { null }
                ?: descriptions?.keys()?.asSequence()?.firstOrNull()
                    ?.let { descriptions.optJSONObject(it)?.optString("value") }
                    ?.ifBlank { null }
        } else null
        val filename = entity.optJSONObject("claims")
            ?.optJSONArray("P18")
            ?.optJSONObject(0)
            ?.optJSONObject("mainsnak")
            ?.optJSONObject("datavalue")
            ?.optString("value")
            ?.ifBlank { null }
        val commonsCategory = entity.optJSONObject("claims")
            ?.optJSONArray("P373")
            ?.optJSONObject(0)
            ?.optJSONObject("mainsnak")
            ?.optJSONObject("datavalue")
            ?.optString("value")
            ?.ifBlank { null }
        val imageCandidates = mutableListOf<WikimediaContent>()
        filename?.let { attempt.provider { resolveCommonsFile("File:$it") } }
            ?.let(imageCandidates::add)
        commonsCategory?.let {
            attempt.provider { resolveCommonsCategory("Category:$it", "") }
        }?.let(imageCandidates::add)
        if (imageCandidates.flatMap(WikimediaContent::images).distinctBy(WikimediaImage::imageUrl).size < MAX_IMAGES_PER_POI) {
            attempt.provider { resolveCommonsDepiction(id) }?.let(imageCandidates::add)
        }
        if (imageCandidates.isEmpty()) {
            attempt.provider { resolveCloseHostImage(entity, id) }?.let(imageCandidates::add)
        }
        val image = imageCandidates.firstOrNull()?.withImages(imageCandidates)
        return WikimediaContent(
            imageUrl = image?.imageUrl,
            description = description,
            pageUrl = "https://www.wikidata.org/wiki/$id",
            imageSourceUrl = image?.imageSourceUrl,
            imageAuthor = image?.imageAuthor,
            imageLicense = image?.imageLicense,
            imageLicenseUrl = image?.imageLicenseUrl,
            additionalImages = image?.additionalImages.orEmpty(),
        )
    }

    /** Finds Commons media whose structured data explicitly says that it depicts this item. */
    private suspend fun resolveCommonsDepiction(id: String): WikimediaContent? {
        val url = commonsImageQuery().newBuilder()
            .addQueryParameter("generator", "search")
            .addQueryParameter("gsrsearch", "haswbstatement:P180=$id")
            .addQueryParameter("gsrnamespace", "6")
            .addQueryParameter("gsrlimit", "12")
            .build()
        return bestImage(requestJson(url.toString()), "")
    }

    /**
     * A POI inside a named host building can safely use the host's image when both Wikidata
     * coordinates are nearly identical. The tight limit prevents a generic campus photo from
     * being presented as a photo of a different building on the same campus.
     */
    private suspend fun resolveCloseHostImage(child: JSONObject, childId: String): WikimediaContent? {
        val childCoordinate = wikidataCoordinate(child) ?: return null
        val parentIds = child.optJSONObject("claims")
            ?.optJSONArray("P361")
            ?.let { claims ->
                (0 until claims.length()).mapNotNull { index ->
                    claims.optJSONObject(index)
                        ?.optJSONObject("mainsnak")
                        ?.optJSONObject("datavalue")
                        ?.optJSONObject("value")
                        ?.optString("id")
                        ?.takeIf { it.matches(Regex("Q\\d+")) }
                }
            }.orEmpty()
        for (parentId in parentIds.take(3)) {
            val parent = requestJson("https://www.wikidata.org/wiki/Special:EntityData/$parentId.json")
                .optJSONObject("entities")?.optJSONObject(parentId) ?: continue
            val parentCoordinate = wikidataCoordinate(parent) ?: continue
            if (distanceMeters(
                    childCoordinate.first,
                    childCoordinate.second,
                    parentCoordinate.first,
                    parentCoordinate.second,
                ) > HOST_IMAGE_MAX_DISTANCE_METERS
            ) continue
            val childLabelTokens = wikidataLabelTokens(child, childId)
            val parentLabelTokens = wikidataLabelTokens(parent, parentId)
            if (childLabelTokens.isNotEmpty() && parentLabelTokens.isNotEmpty() &&
                childLabelTokens.intersect(parentLabelTokens).isEmpty()
            ) continue
            val filename = parent.optJSONObject("claims")
                ?.optJSONArray("P18")
                ?.optJSONObject(0)
                ?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")
                ?.optString("value")
                ?.ifBlank { null }
            filename?.let { resolveCommonsFile("File:$it") }?.let { return it }
        }
        return null
    }

    private fun wikidataLabelTokens(entity: JSONObject, entityId: String): Set<String> {
        val labels = entity.optJSONObject("labels") ?: return emptySet()
        return labels.keys().asSequence().flatMap { language ->
            val label = labels.optJSONObject(language)?.optString("value").orEmpty()
            normalize(label).split(' ').asSequence()
        }.filter { token ->
            token.length >= 4 && token !in HOST_LABEL_STOPWORDS && token != entityId.lowercase()
        }.toSet()
    }

    private fun wikidataCoordinate(entity: JSONObject): Pair<Double, Double>? {
        val value = entity.optJSONObject("claims")
            ?.optJSONArray("P625")
            ?.optJSONObject(0)
            ?.optJSONObject("mainsnak")
            ?.optJSONObject("datavalue")
            ?.optJSONObject("value") ?: return null
        val lat = value.optDouble("latitude", Double.NaN)
        val lng = value.optDouble("longitude", Double.NaN)
        return if (lat.isFinite() && lng.isFinite()) lat to lng else null
    }

    private suspend fun resolveCommonsFile(fileRef: String): WikimediaContent? {
        val url = "https://commons.wikimedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url|mime|extmetadata")
            .addQueryParameter("iiextmetadatafilter", "Artist|LicenseShortName|LicenseUrl")
            .addQueryParameter("iiurlwidth", COMMONS_IMAGE_WIDTH.toString())
            .addQueryParameter("titles", fileRef)
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .build()
        val page = requestJson(url.toString())
            .optJSONObject("query")
            ?.optJSONArray("pages")
            ?.optJSONObject(0)
            ?: return null
        return imageContent(page)
    }

    /** Resolves several Commons files in one attributed request, used for article galleries. */
    private suspend fun resolveCommonsFiles(fileRefs: List<String>, poiName: String): WikimediaContent? {
        if (fileRefs.isEmpty()) return null
        val url = commonsImageQuery().newBuilder()
            .addQueryParameter("titles", fileRefs.take(20).joinToString("|"))
            .build()
        return bestImage(
            requestJson(url.toString()),
            poiName,
            requireRelevantTitle = true,
        )
    }

    private suspend fun resolveCommonsCategory(
        categoryRef: String,
        poiName: String,
    ): WikimediaContent? {
        val url = commonsImageQuery().newBuilder()
            .addQueryParameter("generator", "categorymembers")
            .addQueryParameter("gcmtitle", categoryRef)
            .addQueryParameter("gcmtype", "file")
            .addQueryParameter("gcmlimit", "12")
            .build()
        return bestImage(requestJson(url.toString()), poiName)
    }

    private suspend fun resolveNearbyImage(poi: Poi, names: List<String>): WikimediaContent? {
        val url = commonsImageQuery().newBuilder()
            .setQueryParameter("prop", "imageinfo|coordinates")
            .addQueryParameter("generator", "geosearch")
            .addQueryParameter("ggsprimary", "all")
            .addQueryParameter("ggsnamespace", "6")
            .addQueryParameter("ggsradius", "1000")
            .addQueryParameter("ggslimit", "12")
            .addQueryParameter("ggscoord", "${poi.lat}|${poi.lng}")
            .build()
        return bestNearbyImage(requestJson(url.toString()), poi, names)
    }

    private fun bestNearbyImage(
        json: JSONObject,
        poi: Poi,
        names: List<String>,
    ): WikimediaContent? {
        val pages = json.optJSONObject("query")?.optJSONArray("pages") ?: return null
        val candidates = (0 until pages.length()).mapNotNull { index ->
            val page = pages.optJSONObject(index) ?: return@mapNotNull null
            val coordinates = page.optJSONArray("coordinates")?.optJSONObject(0)
                ?: return@mapNotNull null
            val lat = coordinates.optDouble("lat", Double.NaN)
            val lng = coordinates.optDouble("lon", Double.NaN)
            if (!lat.isFinite() || !lng.isFinite()) return@mapNotNull null
            val distance = distanceMeters(poi.lat, poi.lng, lat, lng)
            if (distance > COMMONS_NEARBY_RADIUS_METERS) return@mapNotNull null
            val titleScore = names.maxOfOrNull {
                imageTitleScore(page.optString("title"), it)
            } ?: 0
            if (titleScore < STRONG_IMAGE_TITLE_SCORE) return@mapNotNull null
            val content = imageContent(page) ?: return@mapNotNull null
            NearbyImageCandidate(titleScore, distance, page.optInt("index", index), content)
        }.sortedWith(
            compareByDescending<NearbyImageCandidate> { it.titleScore }
                .thenBy { it.distance }
                .thenBy { it.index },
        ).map(NearbyImageCandidate::content)
        return candidates.firstOrNull()?.withImages(candidates)
    }

    private data class NearbyImageCandidate(
        val titleScore: Int,
        val distance: Double,
        val index: Int,
        val content: WikimediaContent,
    )

    private fun mapillaryImage(item: JSONObject): WikimediaImage? {
        val id = item.optString("id").ifBlank { return null }
        val imageUrl = item.optString("thumb_1024_url").ifBlank { return null }
        val creator = item.optJSONObject("creator")
        return WikimediaImage(
            imageUrl = imageUrl,
            imageSourceUrl = "https://www.mapillary.com/app/?pKey=$id&focus=photo",
            imageAuthor = creator?.optString("username")?.ifBlank { null }
                ?: "Mapillary contributor",
            imageLicense = "CC BY-SA 4.0",
            imageLicenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
        )
    }

    private fun WikimediaImage.asContent(
        additionalImages: List<WikimediaImage> = emptyList(),
    ): WikimediaContent = WikimediaContent(
        imageUrl = imageUrl,
        imageSourceUrl = imageSourceUrl,
        imageAuthor = imageAuthor,
        imageLicense = imageLicense,
        imageLicenseUrl = imageLicenseUrl,
        additionalImages = additionalImages,
    )

    private fun mapillaryImageId(rawValue: String): String? {
        val value = rawValue.trim()
        if (value.matches(MAPILLARY_BARE_ID)) return value
        return MAPILLARY_URL_ID.find(value)?.groupValues?.getOrNull(1)
    }

    private fun panoramaFeatureContent(feature: JSONObject?): WikimediaContent? {
        feature ?: return null
        val imageUrl = feature.optJSONObject("assets")
            ?.optJSONObject("sd")
            ?.optString("href")
            ?.ifBlank { null } ?: return null
        val properties = feature.optJSONObject("properties")
        val licenseLink = feature.optJSONArray("links")?.let { links ->
            (0 until links.length()).mapNotNull { links.optJSONObject(it) }
                .firstOrNull { it.optString("rel") == "license" }
        }
        val sourceUrl = feature.optJSONArray("links")?.let { links ->
            (0 until links.length()).mapNotNull { links.optJSONObject(it) }
                .firstOrNull { it.optString("rel") == "via" }
                ?.optString("href")
        }
        return WikimediaContent(
            imageUrl = imageUrl,
            imageSourceUrl = sourceUrl?.let { "$it/#focus=pic&pic=${feature.optString("id")}" }
                ?: feature.optJSONArray("links")?.let { links ->
                    (0 until links.length()).mapNotNull { links.optJSONObject(it) }
                        .firstOrNull { it.optString("rel") == "self" }?.optString("href")
                },
            imageAuthor = feature.optJSONArray("providers")?.optJSONObject(0)
                ?.optString("name")?.ifBlank { null }
                ?: properties?.optString("geovisio:producer")?.ifBlank { null },
            imageLicense = properties?.optString("license")?.ifBlank { null }
                ?: licenseLink?.optString("title")?.substringAfterLast('(')?.substringBefore(')'),
            imageLicenseUrl = licenseLink?.optString("href")?.ifBlank { null },
        )
    }

    private fun commonsImageQuery() =
        "https://commons.wikimedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url|mime|extmetadata")
            .addQueryParameter("iiextmetadatafilter", "Artist|LicenseShortName|LicenseUrl")
            .addQueryParameter("iiurlwidth", COMMONS_IMAGE_WIDTH.toString())
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .build()

    private fun bestImage(
        json: JSONObject,
        poiName: String,
        requireRelevantTitle: Boolean = false,
    ): WikimediaContent? = bestImage(json, listOf(poiName), requireRelevantTitle)

    private fun bestImage(
        json: JSONObject,
        poiNames: List<String>,
        requireRelevantTitle: Boolean = false,
    ): WikimediaContent? {
        val pages = json.optJSONObject("query")?.optJSONArray("pages") ?: return null
        val candidates = (0 until pages.length()).mapNotNull { index ->
            val page = pages.optJSONObject(index) ?: return@mapNotNull null
            val content = imageContent(page) ?: return@mapNotNull null
            val score = poiNames.maxOfOrNull { imageTitleScore(page.optString("title"), it) } ?: 0
            Triple(score, page.optInt("index", index), content)
        }.filter { (score, _, _) -> !requireRelevantTitle || score > 0 }
        val sorted = candidates.sortedWith(
            compareByDescending<Triple<Int, Int, WikimediaContent>> { it.first }
                .thenBy { it.second },
        ).map { it.third }
        return sorted.firstOrNull()?.withImages(sorted)
    }

    private fun imageTitleScore(title: String, poiName: String): Int {
        val normalizedTitle = normalize(title.substringAfter(':').substringBeforeLast('.'))
        if (REJECTED_IMAGE_WORDS.any { word ->
                Regex("(^| )${Regex.escape(word)}( |$)").containsMatchIn(normalizedTitle)
            }
        ) return Int.MIN_VALUE
        val tokens = significantTokens(poiName)
        if (tokens.isEmpty()) return 0
        val titleTokens = normalizedTitle.split(' ').toSet()
        val matchedTokens = tokens.count(titleTokens::contains)
        val normalizedName = normalize(poiName)
        val exactPhrase = normalizedName.isNotBlank() && Regex(
            "(^| )${Regex.escape(normalizedName)}( |$)",
        ).containsMatchIn(normalizedTitle)
        return matchedTokens + if (exactPhrase) IMAGE_EXACT_PHRASE_BONUS else 0
    }

    private fun significantTokens(value: String): List<String> =
        matchingTokens(value)

    private fun matchingTokens(value: String): List<String> = normalize(value)
        .split(' ')
        .filter { token ->
            val minimumLength = if (token.any(::isLatinLetter)) 3 else 2
            token.length >= minimumLength && token !in NAME_MATCH_STOPWORDS
        }

    private fun isLatinLetter(character: Char): Boolean =
        character in 'a'..'z' || character in 'A'..'Z'

    private fun poiSearchNames(poi: Poi): List<String> =
        (poi.imageSearchNames + poi.name)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy(::normalize)
            .sortedByDescending(::containsLatinLetter)

    private fun containsLatinLetter(value: String): Boolean =
        value.any { it in 'A'..'Z' || it in 'a'..'z' }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return earthRadius * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun imageContent(page: JSONObject): WikimediaContent? {
        val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return null
        val mime = info.optString("mime")
        if (!mime.startsWith("image/") || mime == "image/svg+xml") return null
        val metadata = info.optJSONObject("extmetadata")
        return WikimediaContent(
            imageUrl = info.optString("thumburl").ifBlank { null }
                ?: info.optString("url").ifBlank { null },
            imageSourceUrl = info.optString("descriptionurl").ifBlank { null },
            imageAuthor = metadata.metadataValue("Artist")?.toPlainText(),
            imageLicense = metadata.metadataValue("LicenseShortName")?.toPlainText(),
            imageLicenseUrl = metadata.metadataValue("LicenseUrl"),
        ).takeIf(WikimediaContent::hasContent)
    }

    private fun WikimediaContent?.withoutImage(): WikimediaContent? = this?.copy(
        imageUrl = null,
        imageSourceUrl = null,
        imageAuthor = null,
        imageLicense = null,
        imageLicenseUrl = null,
        additionalImages = emptyList(),
    )

    private fun WikimediaContent?.withFallbackContent(fallback: WikimediaContent?): WikimediaContent? {
        if (this == null) return fallback
        if (fallback == null) return this
        val usesFallbackImage = imageUrl == null
        return copy(
            imageUrl = imageUrl ?: fallback.imageUrl,
            description = description ?: fallback.description,
            pageUrl = pageUrl ?: fallback.pageUrl,
            imageSourceUrl = imageSourceUrl ?: fallback.imageSourceUrl,
            imageAuthor = imageAuthor ?: fallback.imageAuthor,
            imageLicense = imageLicense ?: fallback.imageLicense,
            imageLicenseUrl = imageLicenseUrl ?: fallback.imageLicenseUrl,
            additionalImages = if (usesFallbackImage) fallback.additionalImages else additionalImages,
        )
    }

    private suspend fun requestJson(url: String): JSONObject {
        return requestJson {
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()
        }
    }

    private suspend fun requestMapillaryJson(url: String, token: String): JSONObject = requestJson {
        Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Authorization", "OAuth $token")
            .get()
            .build()
    }

    private fun WikimediaContent?.withImages(candidates: List<WikimediaContent>): WikimediaContent? {
        val images = candidates.flatMap(WikimediaContent::images)
            .distinctBy(WikimediaImage::imageUrl)
            .take(MAX_IMAGES_PER_POI)
        val primary = images.firstOrNull() ?: return this
        return (this ?: WikimediaContent()).copy(
            imageUrl = primary.imageUrl,
            imageSourceUrl = primary.imageSourceUrl,
            imageAuthor = primary.imageAuthor,
            imageLicense = primary.imageLicense,
            imageLicenseUrl = primary.imageLicenseUrl,
            additionalImages = images.drop(1),
        )
    }

    private suspend fun requestJson(buildRequest: () -> Request): JSONObject {
        var lastError: IOException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            val request = buildRequest()
            awaitHostRequestSlot(request.url.host)
            val result = httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) return@use JSONObject(body)
                val retryAfter = response.header("Retry-After")?.toLongOrNull()?.times(1_000L)
                lastError = IOException("${request.url.host} HTTP ${response.code}")
                if (response.code == 429 || response.code == 503) {
                    return@use Retry(retryAfter ?: (1_000L shl attempt))
                }
                throw lastError!!
            }
            when (result) {
                is JSONObject -> return result
                is Retry -> delay(result.delayMs.coerceAtMost(10_000L))
            }
        }
        throw lastError ?: IOException("Wikimedia request failed")
    }

    /** Reserves a per-host start slot, then releases the lock before network I/O begins. */
    private suspend fun awaitHostRequestSlot(host: String) {
        val throttle = hostThrottles.getOrPut(host) { HostThrottle() }
        throttle.mutex.withLock {
            val now = System.currentTimeMillis()
            val waitMs = throttle.nextRequestAt - now
            if (waitMs > 0) delay(waitMs)
            throttle.nextRequestAt = System.currentTimeMillis() + REQUEST_INTERVAL_MS
        }
    }

    private data class Retry(val delayMs: Long)

    private class HostThrottle(
        val mutex: Mutex = Mutex(),
        var nextRequestAt: Long = 0L,
    )

    private fun cacheFile(ref: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(ref.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$digest.json")
    }

    private fun loadMemoryCache(ref: String): CacheEntry? {
        val entry = memoryCache[ref] ?: return null
        if (entry.isExpired(System.currentTimeMillis())) {
            memoryCache.remove(ref, entry)
            return null
        }
        return entry
    }

    private fun loadCache(ref: String): CacheEntry? {
        val file = cacheFile(ref)
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val content = WikimediaContent(
                imageUrl = json.nullableString("imageUrl"),
                description = json.nullableString("description"),
                pageUrl = json.nullableString("pageUrl"),
                imageSourceUrl = json.nullableString("imageSourceUrl"),
                imageAuthor = json.nullableString("imageAuthor"),
                imageLicense = json.nullableString("imageLicense"),
                imageLicenseUrl = json.nullableString("imageLicenseUrl"),
                additionalImages = json.optJSONArray("additionalImages").toWikimediaImages(),
            )
            val entry = CacheEntry(content, json.getLong("fetchedAt"))
            if (entry.isExpired(System.currentTimeMillis())) {
                file.delete()
                return null
            }
            entry
        }.getOrElse {
            file.delete()
            null
        }
    }

    private fun storeCache(ref: String, entry: CacheEntry) {
        runCatching {
            val json = JSONObject().apply {
                put("fetchedAt", entry.fetchedAt)
                putNullable("imageUrl", entry.content.imageUrl)
                putNullable("description", entry.content.description)
                putNullable("pageUrl", entry.content.pageUrl)
                putNullable("imageSourceUrl", entry.content.imageSourceUrl)
                putNullable("imageAuthor", entry.content.imageAuthor)
                putNullable("imageLicense", entry.content.imageLicense)
                putNullable("imageLicenseUrl", entry.content.imageLicenseUrl)
                if (entry.content.additionalImages.isNotEmpty()) {
                    put("additionalImages", JSONArray(entry.content.additionalImages.map { image ->
                        JSONObject().apply {
                            put("imageUrl", image.imageUrl)
                            putNullable("imageSourceUrl", image.imageSourceUrl)
                            putNullable("imageAuthor", image.imageAuthor)
                            putNullable("imageLicense", image.imageLicense)
                            putNullable("imageLicenseUrl", image.imageLicenseUrl)
                        }
                    }))
                }
            }
            cacheFile(ref).writeText(json.toString())
        }.onFailure { AppLog.w(TAG, "Failed to cache Wikimedia content", it) }
    }

    private data class CacheEntry(
        val content: WikimediaContent,
        val fetchedAt: Long,
    ) {
        fun isExpired(now: Long): Boolean {
            val ttl = when {
                content.imageUrl == null -> IMAGE_MISS_CACHE_TTL_MS
                content.imageSourceUrl?.contains("mapillary.com") == true ->
                    MAPILLARY_CACHE_TTL_MS
                else -> CACHE_TTL_MS
            }
            return now - fetchedAt > ttl
        }
    }

    private fun JSONObject?.metadataValue(key: String): String? =
        this?.optJSONObject(key)?.optString("value")?.ifBlank { null }

    private fun String.toPlainText(): String =
        Jsoup.parse(this).text().trim()

    private fun JSONObject.nullableString(key: String): String? =
        optString(key).ifBlank { null }

    private fun JSONObject.putNullable(key: String, value: String?) {
        if (value != null) put(key, value)
    }

    private fun JSONArray?.toWikimediaImages(): List<WikimediaImage> {
        this ?: return emptyList()
        return (0 until length()).mapNotNull { index ->
            val json = optJSONObject(index) ?: return@mapNotNull null
            WikimediaImage(
                imageUrl = json.nullableString("imageUrl") ?: return@mapNotNull null,
                imageSourceUrl = json.nullableString("imageSourceUrl"),
                imageAuthor = json.nullableString("imageAuthor"),
                imageLicense = json.nullableString("imageLicense"),
                imageLicenseUrl = json.nullableString("imageLicenseUrl"),
            )
        }
    }

    private companion object {
        const val TAG = "WikimediaRepository"
        const val CACHE_VERSION = 22
        const val MAX_IMAGES_PER_POI = 3
        const val MAX_SEARCH_NAMES = 3
        const val MIN_WIKIDATA_NAME_SCORE = 2
        const val WIKIDATA_EXACT_NAME_SCORE = 4
        const val WIKIDATA_EXACT_UNTYPED_DISTANCE_METERS = 100.0
        const val HOST_IMAGE_MAX_DISTANCE_METERS = 50.0
        const val COMMONS_IMAGE_WIDTH = 960
        const val COMMONS_NEARBY_RADIUS_METERS = 1_000.0
        const val STRONG_IMAGE_TITLE_SCORE = 2
        const val IMAGE_EXACT_PHRASE_BONUS = 3
        const val WEBSITE_HTML_LIMIT_BYTES = 1_000_000L
        val MAPILLARY_BARE_ID = Regex("[A-Za-z0-9_-]{5,}")
        val MAPILLARY_URL_ID = Regex("(?:[?&#](?:pKey|image_key)=)([A-Za-z0-9_-]{5,})")
        val META_TAG = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
        val HTML_ATTRIBUTE = Regex(
            "([\\w:-]+)\\s*=\\s*(['\"])(.*?)\\2",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val IMAGE_SRC_LINK = Regex(
            "<link\\b(?=[^>]*\\brel\\s*=\\s*(['\"])image_src\\1)[^>]*\\bhref\\s*=\\s*(['\"])(.*?)\\2[^>]*>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val WEBSITE_IMAGE_PROPERTIES = setOf(
            "og:image", "og:image:url", "twitter:image", "twitter:image:src",
        )
        val PANORAMAX_ID = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
        )
        val FLICKR_LICENSES = mapOf(
            "4" to ("CC BY 2.0" to "https://creativecommons.org/licenses/by/2.0/"),
            "5" to ("CC BY-SA 2.0" to "https://creativecommons.org/licenses/by-sa/2.0/"),
            "7" to ("No known copyright restrictions" to "https://www.flickr.com/commons/usage/"),
            "9" to ("CC0 1.0" to "https://creativecommons.org/publicdomain/zero/1.0/"),
            "10" to ("Public Domain" to "https://creativecommons.org/publicdomain/mark/1.0/"),
        )
        const val USER_AGENT =
            "mapping-solution/1.0 (https://github.com/lotanbar/mapping-solution)"
        const val REQUEST_INTERVAL_MS = 500L
        const val MAX_ATTEMPTS = 3
        const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000
        const val MAPILLARY_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val IMAGE_MISS_CACHE_TTL_MS = 24L * 60 * 60 * 1000
        val REJECTED_IMAGE_WORDS = listOf(
            "map", "mapa", "karte", "plan", "logo", "flag", "coat of arms", "locator",
        )
        val NAME_MATCH_STOPWORDS = setOf(
            "the", "and", "for", "from", "with", "of", "de", "del", "la", "las", "los",
            "el", "al", "le", "les", "du", "des", "museum", "gallery", "church", "castle",
            "ruins", "site", "center", "centre",
        )
        val WIKIDATA_TYPE_WORDS = mapOf(
            "museum" to setOf("museum"),
            "art-gallery" to setOf("gallery", "museum"),
            "castle" to setOf("castle", "fort", "fortress", "citadel", "palace"),
            "ruins" to setOf("archaeological", "archaeology", "ruins", "ancient", "historic"),
            "monument" to setOf("monument", "memorial", "statue"),
            "gate" to setOf("gate", "gateway"),
            "cave" to setOf("cave", "cavern", "grotto"),
            "waterfall" to setOf("waterfall", "cascade"),
            "natural" to setOf("glacier", "natural"),
            "water" to setOf("spring", "geyser", "geothermal"),
            "lighthouse" to setOf("lighthouse"),
            "observation-tower" to setOf("observatory", "observation"),
            "viewpoint" to setOf("viewpoint", "lookout", "overlook", "observation"),
            "zoo" to setOf("zoo", "zoological", "aquarium"),
            "garden" to setOf("garden", "botanical"),
            "place-of-worship" to setOf(
                "church", "cathedral", "chapel", "basilica", "monastery", "temple", "shrine",
            ),
            "religious-christian" to setOf(
                "church", "cathedral", "chapel", "basilica", "monastery", "abbey",
            ),
            "religious-buddhist" to setOf("temple", "monastery", "pagoda", "stupa"),
            "religious-shinto" to setOf("temple", "shrine"),
        )
        val HOST_LABEL_STOPWORDS = setOf(
            "museum", "gallery", "archives", "university", "institute", "technology",
            "school", "college", "art", "arts", "design",
        )
    }
}
