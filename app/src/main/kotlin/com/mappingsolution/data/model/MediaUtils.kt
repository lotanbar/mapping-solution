package com.mappingsolution.data.model

import android.media.MediaMetadataRetriever
import java.io.File

object MediaUtils {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "heic", "heif", "bmp")
    private val audioExtensions = setOf("mp3", "mpeg", "m4a", "x-m4a", "wav", "x-wav", "aac", "amr", "ogg", "flac")

    fun createMediaItem(path: String, index: Int): MediaItem {
        return MediaItem(
            id = index.toString(),
            path = path,
            type = getMediaType(path),
            durationMs = getDuration(path)
        )
    }

    fun getDuration(path: String): Long? {
        if (!isAudio(path)) return null
        
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    fun isAudio(path: String): Boolean {
        return extension(path) in audioExtensions
    }

    /** Legacy unsupported media references are silently ignored at every load/import boundary. */
    fun isSupported(path: String): Boolean {
        val extension = extension(path)
        val extensionIsSupported = extension in imageExtensions || extension in audioExtensions
        val isExtensionlessRemoteImage =
            (path.startsWith("http://") || path.startsWith("https://")) && extension.isEmpty()
        return extensionIsSupported || isExtensionlessRemoteImage
    }

    private fun extension(path: String): String = path.substringBefore('?').substringAfterLast('.', "").lowercase()

    fun getMediaType(path: String): MediaType {
        return when {
            isAudio(path) -> MediaType.AUDIO
            else -> MediaType.PHOTO
        }
    }
}
