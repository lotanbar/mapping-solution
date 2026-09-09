package com.mappingsolution.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUtilsTest {
    @Test
    fun `only photo and audio references are supported`() {
        assertTrue(MediaUtils.isSupported("photo.jpg"))
        assertTrue(MediaUtils.isSupported("recording.m4a"))
        assertFalse(MediaUtils.isSupported("clip.mp4"))
        assertFalse(MediaUtils.isSupported("movie.webm"))
        assertFalse(MediaUtils.isSupported("https://example.com/movie.mp4"))
    }
}
