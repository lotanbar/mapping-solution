package com.mappingsolution.data.model

import android.media.MediaMetadataRetriever

/** Reads an audio file's duration with the platform decoder; null when it can't be read. */
object AudioDuration {
    fun read(path: String): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
