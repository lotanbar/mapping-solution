package com.mappingsolution.data.model

object MediaUtils {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "heic", "heif", "bmp")
    private val audioExtensions = setOf("mp3", "mpeg", "m4a", "x-m4a", "wav", "x-wav", "aac", "amr", "ogg", "flac")

    /** [durationOf] reads an audio file's length; platforms without a decoder can omit it. */
    fun createMediaItem(path: String, index: Int, durationOf: (String) -> Long? = { null }): MediaItem {
        return MediaItem(
            id = index.toString(),
            path = path,
            type = getMediaType(path),
            durationMs = if (isAudio(path)) durationOf(path) else null
        )
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
