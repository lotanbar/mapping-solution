package com.mappingsolution.data.fs

import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.model.Route
import com.mappingsolution.data.model.RoutePoint
import com.mappingsolution.data.util.KeyValueStore
import com.mappingsolution.data.util.StorageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Exports a POI and a route to GPX and imports the file into fresh storage, all on a plain JVM. */
class GpxRoundTripTest {

    private class MemoryStore : KeyValueStore {
        private val values = HashMap<String, Any>()
        override fun contains(key: String) = key in values
        override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun getString(key: String, default: String?) = values[key] as? String ?: default
        override fun putString(key: String, value: String) { values[key] = value }
        override fun getLong(key: String, default: Long) = values[key] as? Long ?: default
        override fun putLongs(values: Map<String, Long>) { this.values.putAll(values) }
    }

    @Test
    fun `exported GPX imports back with the same POI and route`() = runBlocking {
        val sourceDir = Files.createTempDirectory("export").toFile()
        val targetDir = Files.createTempDirectory("import").toFile()
        try {
            val source = StorageManager(sourceDir)
            val pois = PoiFileRepository(source)
            val routes = RouteFileRepository(source)
            withTimeout(5_000) { pois.observeAll().first(); routes.observeAll().first() }
            val poi = pois.insert(Poi(name = "Viewpoint", description = "Great view", lat = 31.7767, lng = 35.229))
            val routeId = routes.insert(Route(name = "Loop", startedAt = 1_000L, checkpointAt = 1_000L, didUserTapStop = true))
            routes.appendPoints(routeId, listOf(RoutePoint(1_000L, 31.7683, 35.2137), RoutePoint(61_000L, 31.7767, 35.229)))

            val gpx = ExportRepository(File(sourceDir, "exports"), pois, routes).exportRows(setOf(poi, routeId))
            assertNotNull(gpx)
            val named = File(gpx!!.parentFile, "Trip.gpx").also { gpx.renameTo(it) }

            val target = StorageManager(targetDir)
            val stores = KeyValueStore.Factory { MemoryStore() }
            val targetPois = PoiFileRepository(target)
            val targetRoutes = RouteFileRepository(target)
            val importer = ImportRepository(GroupFileRepository(target, stores), targetPois, targetRoutes, target)
            val result = importer.importSingleFile(named.path)

            assertTrue(result.errors.toString(), result.errors.isEmpty() && !result.isValidationFailure)
            assertEquals(1, result.poisImported)
            assertEquals(1, result.routesImported)
            val importedRoute = targetRoutes.observeAll().first { it.isNotEmpty() }.single()
            assertEquals("Loop", importedRoute.name)
            assertEquals(2, targetRoutes.getPoints(importedRoute.id).size)
        } finally {
            sourceDir.deleteRecursively()
            targetDir.deleteRecursively()
        }
    }
}
