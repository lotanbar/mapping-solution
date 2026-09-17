package com.mappingsolution.ui.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.Options
import okhttp3.OkHttpClient
import okio.ForwardingSource
import okio.buffer
import okio.source
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/** Builds the app-wide Coil image loader shared by Android and desktop. */
object AppImageLoader {

    fun create(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                // A User-Agent keeps Wikimedia Commons from answering image downloads with 403.
                add(OkHttpNetworkFetcherFactory(callFactory = { httpClient }))
                add(ZipImageFetcher.Factory())
            }
            .build()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "mapping-solution/1.0 (https://github.com/lotanbar/mapping-solution)")
                        .build()
                )
            }
            .build()
    }
}

/**
 * Serves images on demand from a zip file without extracting them.
 *
 * URI format: `zip://image?path=<url-encoded zip path>&entry=<url-encoded entry name>`; the path
 * lives in a query parameter so Windows drive letters survive URI parsing.
 */
class ZipImageFetcher(
    private val zipPath: String,
    private val entryName: String,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val zipFile = ZipFile(zipPath)
        return try {
            val entry = zipFile.getEntry(entryName)
                ?: error("ZipImageFetcher: entry '$entryName' not found in $zipPath")
            // Close the ZipFile together with the entry stream once Coil finishes reading.
            val source = object : ForwardingSource(zipFile.getInputStream(entry).source()) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        zipFile.close()
                    }
                }
            }.buffer()
            SourceFetchResult(
                source = ImageSource(source = source, fileSystem = options.fileSystem),
                mimeType = null,
                dataSource = DataSource.DISK,
            )
        } catch (e: Exception) {
            zipFile.close()
            throw e
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != SCHEME) return null
            val params = parseQuery(data.query ?: return null)
            return ZipImageFetcher(params["path"] ?: return null, params["entry"] ?: return null, options)
        }
    }

    companion object {
        private const val SCHEME = "zip"

        /** Builds a zip URI for [filename] (without the `images/` prefix) inside [zipPath]. */
        fun uriFor(zipPath: String, filename: String): String =
            "$SCHEME://image?path=${encode(zipPath)}&entry=${encode("images/$filename")}"

        fun isZipUri(path: String): Boolean = path.startsWith("$SCHEME://")

        private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

        private fun parseQuery(query: String): Map<String, String> =
            query.split('&').filter { '=' in it }.associate {
                val (key, value) = it.split('=', limit = 2)
                key to URLDecoder.decode(value, Charsets.UTF_8)
            }
    }
}
