package com.mappingsolution.data.fs

import com.mappingsolution.data.model.Route
import com.mappingsolution.data.model.RoutePoint
import com.mappingsolution.data.util.StorageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

/** Runs on a plain JVM, proving the file-backed data layer needs no Android runtime. */
class RouteFileRepositoryTest {

    @Test
    fun `routes and points survive a reload from disk`() = runBlocking {
        val baseDir = Files.createTempDirectory("routes").toFile()
        try {
            val storage = StorageManager(baseDir)
            val writer = RouteFileRepository(storage)
            withTimeout(5_000) { writer.observeAll().first() }
            val id = writer.insert(Route(name = "Morning walk", startedAt = 1_000L, checkpointAt = 1_000L))
            val points = listOf(RoutePoint(ts = 1L, lat = 31.77, lng = 35.21), RoutePoint(ts = 2L, lat = 31.78, lng = 35.22))
            writer.appendPoints(id, points)

            val reader = RouteFileRepository(storage)
            val loaded = withTimeout(5_000) { reader.observeAll().first { it.isNotEmpty() } }

            assertEquals(listOf("Morning walk"), loaded.map { it.name })
            assertEquals(points, reader.getPoints(id))
        } finally {
            baseDir.deleteRecursively()
        }
    }
}
