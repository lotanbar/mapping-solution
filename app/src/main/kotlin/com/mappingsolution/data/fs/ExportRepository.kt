package com.mappingsolution.data.fs

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.core.content.FileProvider
import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.model.Route
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlSerializer
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val poiRepository: PoiFileRepository,
    private val routeRepository: RouteFileRepository,
) {
    private val exportsDir: File
        get() = File(context.filesDir, "exports").also { it.mkdirs() }

    /** Export all POIs belonging to the given group IDs. */
    suspend fun exportGroups(groupIds: Set<String>): Uri? {
        val pois = poiRepository.observeAll().first().filter { it.groupId in groupIds }
        return buildGpx(pois, emptyList())
    }

    /** Export the specific POIs and/or Routes identified by [rowIds]. */
    suspend fun exportRows(rowIds: Set<String>): Uri? {
        val pois = poiRepository.observeAll().first().filter { it.id in rowIds }
        val routes = routeRepository.observeAll().first().filter { it.id in rowIds }
        return buildGpx(pois, routes)
    }

    // ── GPX builder ───────────────────────────────────────────────────────────

    private suspend fun buildGpx(pois: List<Poi>, routes: List<Route>): Uri? = withContext(Dispatchers.IO) {
        if (pois.isEmpty() && routes.isEmpty()) return@withContext null

        // Remove stale export files before writing a new one
        exportsDir.listFiles()?.forEach { it.delete() }

        val file = File(exportsDir, "export_${System.currentTimeMillis()}.gpx")
        FileOutputStream(file).use { fos ->
            val xs: XmlSerializer = Xml.newSerializer()
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

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}

private fun XmlSerializer.elem(tag: String, value: String) {
    startTag("", tag); text(value); endTag("", tag)
}
