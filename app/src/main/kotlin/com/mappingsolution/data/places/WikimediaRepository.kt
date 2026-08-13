package com.mappingsolution.data.places

import android.content.Context
import android.text.Html
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class WikimediaContent(
    val imageUrl: String? = null,
    val description: String? = null,
    val pageUrl: String? = null,
    val imageSourceUrl: String? = null,
    val imageAuthor: String? = null,
    val imageLicense: String? = null,
    val imageLicenseUrl: String? = null,
) {
    val hasContent: Boolean get() = imageUrl != null || description != null

    val imageCredit: String?
        get() = listOfNotNull(
            imageAuthor?.takeIf { it.isNotBlank() },
            imageLicense?.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifBlank { null }
}

/**
 * Resolves OSM wikipedia/wikidata/wikimedia_commons references into reusable content.
 * Results are cached for 30 days. Network calls are serialized and limited to 2/second,
 * with an identifying User-Agent and Retry-After handling for Wikimedia's public APIs.
 */
@Singleton
class WikimediaRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val httpClient: OkHttpClient,
) {
    private val cacheDir = File(context.cacheDir, "wikimedia_poi_cache").also { it.mkdirs() }
    private val memoryCache = ConcurrentHashMap<String, WikimediaContent>()
    private val resolveMutex = Mutex()
    private val requestMutex = Mutex()
    private var lastRequestAt = 0L

    suspend fun getContent(ref: String): WikimediaContent? = withContext(Dispatchers.IO) {
        memoryCache[ref]?.let { return@withContext it.takeIf(WikimediaContent::hasContent) }
        loadCache(ref)?.let {
            memoryCache[ref] = it
            return@withContext it.takeIf(WikimediaContent::hasContent)
        }

        resolveMutex.withLock {
            memoryCache[ref]?.let { return@withLock it.takeIf(WikimediaContent::hasContent) }
            loadCache(ref)?.let {
                memoryCache[ref] = it
                return@withLock it.takeIf(WikimediaContent::hasContent)
            }

            val resolved = try {
                resolve(ref)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve Wikimedia reference $ref", e)
                return@withLock null
            }
            val cached = resolved ?: WikimediaContent()
            memoryCache[ref] = cached
            storeCache(ref, cached)
            cached.takeIf(WikimediaContent::hasContent)
        }
    }

    private suspend fun resolve(ref: String): WikimediaContent? = when {
        ref.startsWith("File:") -> resolveCommonsFile(ref)
        ref.matches(Regex("Q\\d+")) -> resolveWikidata(ref)
        ref.contains(':') && !ref.startsWith("http") -> resolveWikipedia(ref)
        else -> null
    }

    private suspend fun resolveWikipedia(ref: String): WikimediaContent? {
        val separator = ref.indexOf(':')
        if (separator <= 0 || separator == ref.lastIndex) return null
        val language = ref.substring(0, separator)
        val title = ref.substring(separator + 1)
        val url = "https://$language.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("prop", "extracts|pageprops|info")
            .addQueryParameter("exintro", "1")
            .addQueryParameter("explaintext", "1")
            .addQueryParameter("redirects", "1")
            .addQueryParameter("inprop", "url")
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
        val wikidata = wikidataId?.let { resolveWikidata(it, includeDescription = false) }
        return WikimediaContent(
            imageUrl = wikidata?.imageUrl,
            description = description,
            pageUrl = pageUrl,
            imageSourceUrl = wikidata?.imageSourceUrl,
            imageAuthor = wikidata?.imageAuthor,
            imageLicense = wikidata?.imageLicense,
            imageLicenseUrl = wikidata?.imageLicenseUrl,
        )
    }

    private suspend fun resolveWikidata(
        id: String,
        includeDescription: Boolean = true,
    ): WikimediaContent? {
        val json = requestJson("https://www.wikidata.org/wiki/Special:EntityData/$id.json")
        val entity = json.optJSONObject("entities")?.optJSONObject(id) ?: return null
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
        val image = filename?.let { resolveCommonsFile("File:$it") }
        return WikimediaContent(
            imageUrl = image?.imageUrl,
            description = description,
            pageUrl = "https://www.wikidata.org/wiki/$id",
            imageSourceUrl = image?.imageSourceUrl,
            imageAuthor = image?.imageAuthor,
            imageLicense = image?.imageLicense,
            imageLicenseUrl = image?.imageLicenseUrl,
        )
    }

    private suspend fun resolveCommonsFile(fileRef: String): WikimediaContent? {
        val url = "https://commons.wikimedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url|extmetadata")
            .addQueryParameter("iiurlwidth", "1600")
            .addQueryParameter("titles", fileRef)
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .build()
        val page = requestJson(url.toString())
            .optJSONObject("query")
            ?.optJSONArray("pages")
            ?.optJSONObject(0)
            ?: return null
        val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return null
        val metadata = info.optJSONObject("extmetadata")
        return WikimediaContent(
            imageUrl = info.optString("thumburl").ifBlank { null }
                ?: info.optString("url").ifBlank { null },
            imageSourceUrl = info.optString("descriptionurl").ifBlank { null },
            imageAuthor = metadata.metadataValue("Artist")?.toPlainText(),
            imageLicense = metadata.metadataValue("LicenseShortName")?.toPlainText(),
            imageLicenseUrl = metadata.metadataValue("LicenseUrl"),
        )
    }

    private suspend fun requestJson(url: String): JSONObject {
        var lastError: IOException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            val result = requestMutex.withLock {
                val waitMs = REQUEST_INTERVAL_MS - (System.currentTimeMillis() - lastRequestAt)
                if (waitMs > 0) delay(waitMs)
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    lastRequestAt = System.currentTimeMillis()
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) return@withLock JSONObject(body)
                    val retryAfter = response.header("Retry-After")?.toLongOrNull()?.times(1_000L)
                    lastError = IOException("Wikimedia HTTP ${response.code}")
                    if (response.code == 429 || response.code == 503) {
                        return@withLock Retry(retryAfter ?: (1_000L shl attempt))
                    }
                    throw lastError!!
                }
            }
            when (result) {
                is JSONObject -> return result
                is Retry -> delay(result.delayMs.coerceAtMost(10_000L))
            }
        }
        throw lastError ?: IOException("Wikimedia request failed")
    }

    private data class Retry(val delayMs: Long)

    private fun cacheFile(ref: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(ref.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$digest.json")
    }

    private fun loadCache(ref: String): WikimediaContent? {
        val file = cacheFile(ref)
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            if (System.currentTimeMillis() - json.getLong("fetchedAt") > CACHE_TTL_MS) {
                file.delete()
                return null
            }
            WikimediaContent(
                imageUrl = json.nullableString("imageUrl"),
                description = json.nullableString("description"),
                pageUrl = json.nullableString("pageUrl"),
                imageSourceUrl = json.nullableString("imageSourceUrl"),
                imageAuthor = json.nullableString("imageAuthor"),
                imageLicense = json.nullableString("imageLicense"),
                imageLicenseUrl = json.nullableString("imageLicenseUrl"),
            )
        }.getOrElse {
            file.delete()
            null
        }
    }

    private fun storeCache(ref: String, content: WikimediaContent) {
        runCatching {
            val json = JSONObject().apply {
                put("fetchedAt", System.currentTimeMillis())
                putNullable("imageUrl", content.imageUrl)
                putNullable("description", content.description)
                putNullable("pageUrl", content.pageUrl)
                putNullable("imageSourceUrl", content.imageSourceUrl)
                putNullable("imageAuthor", content.imageAuthor)
                putNullable("imageLicense", content.imageLicense)
                putNullable("imageLicenseUrl", content.imageLicenseUrl)
            }
            cacheFile(ref).writeText(json.toString())
        }.onFailure { Log.w(TAG, "Failed to cache Wikimedia content", it) }
    }

    private fun JSONObject?.metadataValue(key: String): String? =
        this?.optJSONObject(key)?.optString("value")?.ifBlank { null }

    private fun String.toPlainText(): String =
        Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    private fun JSONObject.nullableString(key: String): String? =
        optString(key).ifBlank { null }

    private fun JSONObject.putNullable(key: String, value: String?) {
        if (value != null) put(key, value)
    }

    private companion object {
        const val TAG = "WikimediaRepository"
        const val USER_AGENT =
            "mapping-solution/1.0 (https://github.com/lotanbar/mapping-solution)"
        const val REQUEST_INTERVAL_MS = 500L
        const val MAX_ATTEMPTS = 3
        const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000
    }
}
