package com.mappingsolution.data.fs

import org.xmlpull.v1.XmlPullParserFactory
import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.model.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlSerializer
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/** Writes GPX exports into [exportsDir]; each platform decides how to share the resulting file. */
class ExportRepository(
    private val exportsDir: File,
    private val poiRepository: PoiFileRepository,
    private val routeRepository: RouteFileRepository,
) {

    /** Export all POIs belonging to the given group IDs. */
    suspend fun exportGroups(groupIds: Set<String>): File? {
        val pois = poiRepository.observeAll().first().filter { it.groupId in groupIds }
        return buildGpx(pois, emptyList())
    }

    /** Export the specific POIs and/or Routes identified by [rowIds]. */
    suspend fun exportRows(rowIds: Set<String>): File? {
        val pois = poiRepository.observeAll().first().filter { it.id in rowIds }
        val routes = routeRepository.observeAll().first().filter { it.id in rowIds }
        return buildGpx(pois, routes)
    }

    // ── GPX builder ───────────────────────────────────────────────────────────

    private suspend fun buildGpx(pois: List<Poi>, routes: List<Route>): File? = withContext(Dispatchers.IO) {
        if (pois.isEmpty() && routes.isEmpty()) return@withContext null
        exportsDir.mkdirs()

        // Remove stale export files before writing a new one
        exportsDir.listFiles()?.forEach { it.delete() }

        val file = File(exportsDir, "export_${System.currentTimeMillis()}.gpx")
        FileOutputStream(file).use { fos ->
            val xs: XmlSerializer = XmlPullParserFactory.newInstance().newSerializer()
            xs.setOutput(fos, "UTF-8")
            xs.startDocument("UTF-8", true)

            xs.startTag("", "gpx")
            xs.attribute("", "version", "1.1")
            xs.attribute("", "creator", "mapping-solution")
            xs.attribute("", "xmlns", "http://www.topografix.com/GPX/1/1")

            for (poi in pois) {
                xs.startTag("", "wpt")
                xs.attribute("", "lat", poi.lat.toString())
                xs.attribute("", "lon", poi.lng.toString())
                xs.elem("name", poi.name)
                poi.description?.let { xs.elem("desc", it) }
                poi.elevation?.let { xs.elem("ele", it.toString()) }
                xs.elem("time", Instant.ofEpochMilli(poi.createdAt).toString())
                xs.endTag("", "wpt")
            }

            for (route in routes) {
                val points = routeRepository.getPoints(route.id)
                if (points.isEmpty()) continue
                xs.startTag("", "trk")
                xs.elem("name", route.name)
                route.description?.let { xs.elem("desc", it) }
                xs.startTag("", "trkseg")
                for (pt in points) {
                    xs.startTag("", "trkpt")
                    xs.attribute("", "lat", pt.lat.toString())
                    xs.attribute("", "lon", pt.lng.toString())
                    xs.elem("time", Instant.ofEpochMilli(pt.ts).toString())
                    xs.endTag("", "trkpt")
                }
                xs.endTag("", "trkseg")
                xs.endTag("", "trk")
            }

            xs.endTag("", "gpx")
            xs.endDocument()
        }

        file
    }
}

private fun XmlSerializer.elem(tag: String, value: String) {
    startTag("", tag); text(value); endTag("", tag)
}
