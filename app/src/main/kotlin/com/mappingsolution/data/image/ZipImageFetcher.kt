package com.mappingsolution.data.image

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.ForwardingSource
import okio.buffer
import okio.source
import java.util.zip.ZipFile

/**
 * Coil [Fetcher] that serves images on demand from a zip file without extracting them.
 *
 * URI format: `zip:///absolute/path/to/file.zip?entry=images/filename.avif`
 *
 * A new [ZipFile] is opened per request; Coil's memory and disk cache ensure each
 * image is only decoded once in practice.
 */
class ZipImageFetcher(
    private val data: Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val zipPath = data.path ?: error("ZipImageFetcher: no zip path in URI $data")
        val entryName = data.getQueryParameter("entry") ?: error("ZipImageFetcher: no entry param in URI $data")

        val zipFile = ZipFile(zipPath)
        return try {
            val entry = zipFile.getEntry(entryName)
                ?: error("ZipImageFetcher: entry '$entryName' not found in $zipPath")

            val inputStream = zipFile.getInputStream(entry)

            // Wrap in a source that also closes the ZipFile when Coil closes the source.
            val closingSource = object : ForwardingSource(inputStream.source()) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        zipFile.close()
                    }
                }
            }.buffer()

            SourceResult(
                source = ImageSource(source = closingSource, context = options.context),
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
            if (data.scheme != "zip") return null
            return ZipImageFetcher(data, options)
        }
    }

    companion object {
        /** Builds a zip:// URI for [filename] (without the `images/` prefix) inside [zipPath]. */
        fun uriFor(zipPath: String, filename: String): Uri =
            Uri.Builder()
                .scheme("zip")
                .authority("")
                .path(zipPath)
                .appendQueryParameter("entry", "images/$filename")
                .build()
    }
}
