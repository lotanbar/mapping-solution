package com.mappingsolution.data.fs

import android.content.Context
import android.util.Log
import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.model.Route
import com.mappingsolution.data.model.RoutePoint
import com.mappingsolution.data.util.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

data class ImportResult(
    val poisImported: Int = 0,
    val routesImported: Int = 0,
    val filesProcessed: Int = 0,
    val filesSkipped: Int = 0,
    val errors: List<String> = emptyList(),
    val validationErrors: List<String> = emptyList(),
) {
    val isValidationFailure: Boolean get() = validationErrors.isNotEmpty()
}

@Singleton
class ImportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val groupRepository: GroupFileRepository,
    private val poiRepository: PoiFileRepository,
    private val routeRepository: RouteFileRepository,
    private val storageManager: StorageManager,
) {

    suspend fun importFolder(
        path: String,
        onProgress: suspend (phase: String, done: Int, total: Int) -> Unit = { _, _, _ -> },
    ): ImportResult = withContext(Dispatchers.IO) {
        val folder = File(path)
        val folderName = folder.name.takeIf { it.isNotEmpty() } ?: "Import"

        val gpxFiles = folder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() == "gpx" }
            ?: emptyList()
        val imagesDir = File(folder, "images").takeIf { it.isDirectory }

        var routesCount = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        val allPois = mutableListOf<Poi>()
        val allRoutes = mutableListOf<Pair<Route, List<RoutePoint>>>()

        // ── Phase 1: Parse ────────────────────────────────────────────────────
        onProgress("Reading GPX file…", 0, 0)
        var lastParseEmit = 0L
        for (file in gpxFiles) {
            try {
                file.inputStream().use { stream ->
                    parseGpx(stream, allPois, allRoutes) { count ->
                        val now = System.currentTimeMillis()
                        if (now - lastParseEmit >= 32) {
                            lastParseEmit = now
                            onProgress("Reading GPX file… $count waypoints found", 0, 0)
                        }
                    }
                }
            } catch (e: Exception) {
                errors.add("${file.name}: ${e.message ?: "parse error"}")
                skipped++
            }
        }

        // ── Phase 2: Validate ─────────────────────────────────────────────────
        if (allPois.isNotEmpty()) {
            onProgress("Validating…", 0, 0)
            val validationErrors = mutableListOf<String>()
            for ((i, poi) in allPois.withIndex()) {
                val rowLabel = "Waypoint ${i + 1} (\"${poi.name}\")"
                if (poi.lat !in -90.0..90.0)
                    validationErrors.add("$rowLabel: latitude ${poi.lat} out of range [-90, 90]")
                if (poi.lng !in -180.0..180.0)
                    validationErrors.add("$rowLabel: longitude ${poi.lng} out of range [-180, 180]")
            }
            if (validationErrors.isNotEmpty()) {
                Log.w("ImportRepository", "Validation failed: ${validationErrors.size} error(s)")
                validationErrors.forEach { Log.w("ImportRepository", "  • $it") }
                return@withContext ImportResult(
                    filesSkipped = skipped,
                    errors = errors,
                    validationErrors = validationErrors,
                )
            }
        }

        // ── Phase 3: Clear previous import + save ─────────────────────────────
        if (allPois.isNotEmpty()) {
            val groupId = groupRepository.purgeAndCreateForImport(folderName, poiRepository) { phase, done, total ->
                onProgress(phase, done, total)
            }
            val total = allPois.size

            // Pre-resolve image filenames so JSONL paths match the actual files on disk.
            // AmudAnan stores originals as _x_200.avif thumbnails, so "photo.jpg" → "photo_x_200.avif".
            val imageIndex = imagesDir?.let { buildImageIndex(it) } ?: emptyMap()
            val resolvedPois = allPois.map { poi ->
                if (imagesDir == null || poi.mediaPaths.isEmpty()) return@map poi
                val resolvedPaths = poi.mediaPaths.map { filename ->
                    resolveImageFile(imagesDir, filename, imageIndex)?.name ?: filename
                }
                poi.copy(mediaPaths = resolvedPaths)
            }

            // Write all POIs as a single bulk_pois.jsonl file (one JSON object per line)
            val bulkFile = storageManager.getBulkPoisFile(folderName.trim(), groupId)
            bulkFile.parentFile?.mkdirs()
            onProgress("Writing to storage…", 0, total)
            var lastEmit = 0L
            bulkFile.bufferedWriter().use { writer ->
                for ((i, poi) in resolvedPois.withIndex()) {
                    writer.write(BulkPoiRepository.serializePoi(poi.copy(groupId = groupId)))
                    writer.newLine()
                    val now = System.currentTimeMillis()
                    if (now - lastEmit >= 32 || i == total - 1) {
                        lastEmit = now
                        onProgress("Writing to storage…", i + 1, total)
                    }
                }
            }

            // Copy images: each POI's referenced files go to its own media dir.
            if (imagesDir != null) {
                val poisWithImages = resolvedPois.filter { it.mediaPaths.isNotEmpty() }
                onProgress("Copying images…", 0, poisWithImages.size)
                var doneCount = 0
                for (poi in poisWithImages) {
                    val destDir = storageManager.getPoiMediaDir(poi.name, poi.id)
                    destDir.mkdirs()
                    for (filename in poi.mediaPaths) {
                        val srcFile = File(imagesDir, filename).takeIf { it.isFile }
                            ?: resolveImageFile(imagesDir, filename, imageIndex)
                            ?: continue
                        val destFile = File(destDir, srcFile.name)
                        try {
                            srcFile.copyTo(destFile, overwrite = true)
                        } catch (e: Exception) {
                            Log.w("ImportRepository", "Failed to copy image '${srcFile.name}': ${e.message}")
                        }
                    }
                    doneCount++
                    onProgress("Copying images…", doneCount, poisWithImages.size)
                }
            }

            groupRepository.markImportComplete(groupId, total)
        }

        if (allRoutes.isNotEmpty()) {
            onProgress("Writing routes to storage…", 0, allRoutes.size)
            for ((route, points) in allRoutes) {
                val routeId = routeRepository.insert(route)
                if (points.isNotEmpty()) routeRepository.appendPoints(routeId, points)
                routesCount++
                onProgress("Writing routes to storage…", routesCount, allRoutes.size)
            }
        }

        ImportResult(
            poisImported = allPois.size,
            routesImported = routesCount,
            filesProcessed = gpxFiles.size - skipped,
            filesSkipped = skipped,
            errors = errors,
        )
    }

    /**
     * Imports a single GPX file without requiring a dedicated folder.
     * Uses the file's basename as the group name and checks for an `images/` directory
     * next to the file (same resolution logic as [importFolder]).
     */
    suspend fun importSingleFile(
        filePath: String,
        onProgress: suspend (phase: String, done: Int, total: Int) -> Unit = { _, _, _ -> },
    ): ImportResult = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.isFile || file.extension.lowercase() != "gpx") {
            return@withContext ImportResult(
                errors = listOf("Not a valid GPX file: ${file.name}")
            )
        }
        val groupName = file.nameWithoutExtension.takeIf { it.isNotEmpty() } ?: "Import"
        val imagesDir = File(file.parentFile, "images").takeIf { it.isDirectory }

        var skipped = 0
        val errors = mutableListOf<String>()
        val allPois = mutableListOf<Poi>()
        val allRoutes = mutableListOf<Pair<Route, List<RoutePoint>>>()

        onProgress("Reading GPX file…", 0, 0)
        var lastParseEmit = 0L
        try {
            file.inputStream().use { stream ->
                parseGpx(stream, allPois, allRoutes) { count ->
                    val now = System.currentTimeMillis()
                    if (now - lastParseEmit >= 32) {
                        lastParseEmit = now
                        onProgress("Reading GPX file… $count waypoints found", 0, 0)
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("${file.name}: ${e.message ?: "parse error"}")
            skipped++
        }

        if (allPois.isNotEmpty()) {
            onProgress("Validating…", 0, 0)
            val validationErrors = mutableListOf<String>()
            for ((i, poi) in allPois.withIndex()) {
                val rowLabel = "Waypoint ${i + 1} (\"${poi.name}\")"
                if (poi.lat !in -90.0..90.0)
                    validationErrors.add("$rowLabel: latitude ${poi.lat} out of range [-90, 90]")
                if (poi.lng !in -180.0..180.0)
                    validationErrors.add("$rowLabel: longitude ${poi.lng} out of range [-180, 180]")
            }
            if (validationErrors.isNotEmpty()) {
                Log.w("ImportRepository", "Single-file validation failed: ${validationErrors.size} error(s)")
                validationErrors.forEach { Log.w("ImportRepository", "  • $it") }
                return@withContext ImportResult(
                    filesSkipped = skipped,
                    errors = errors,
                    validationErrors = validationErrors,
                )
            }
        }

        var routesCount = 0

        if (allPois.isNotEmpty()) {
            val groupId = groupRepository.purgeAndCreateForImport(groupName, poiRepository) { phase, done, total ->
                onProgress(phase, done, total)
            }
            val total = allPois.size
            val imageIndex = imagesDir?.let { buildImageIndex(it) } ?: emptyMap()
            val resolvedPois = allPois.map { poi ->
                if (imagesDir == null || poi.mediaPaths.isEmpty()) return@map poi
                val resolvedPaths = poi.mediaPaths.map { filename ->
                    resolveImageFile(imagesDir, filename, imageIndex)?.name ?: filename
                }
                poi.copy(mediaPaths = resolvedPaths)
            }
            val bulkFile = storageManager.getBulkPoisFile(groupName.trim(), groupId)
            bulkFile.parentFile?.mkdirs()
            onProgress("Writing to storage…", 0, total)
            var lastEmit = 0L
            bulkFile.bufferedWriter().use { writer ->
                for ((i, poi) in resolvedPois.withIndex()) {
                    writer.write(BulkPoiRepository.serializePoi(poi.copy(groupId = groupId)))
                    writer.newLine()
                    val now = System.currentTimeMillis()
                    if (now - lastEmit >= 32 || i == total - 1) {
                        lastEmit = now
                        onProgress("Writing to storage…", i + 1, total)
                    }
                }
            }
            if (imagesDir != null) {
                val poisWithImages = resolvedPois.filter { it.mediaPaths.isNotEmpty() }
                onProgress("Copying images…", 0, poisWithImages.size)
                var doneCount = 0
                for (poi in poisWithImages) {
                    val destDir = storageManager.getPoiMediaDir(poi.name, poi.id)
                    destDir.mkdirs()
                    for (filename in poi.mediaPaths) {
                        val srcFile = File(imagesDir, filename).takeIf { it.isFile }
                            ?: resolveImageFile(imagesDir, filename, imageIndex)
                            ?: continue
                        val destFile = File(destDir, srcFile.name)
                        try {
                            srcFile.copyTo(destFile, overwrite = true)
                        } catch (e: Exception) {
                            Log.w("ImportRepository", "Failed to copy image '${srcFile.name}': ${e.message}")
                        }
                    }
                    doneCount++
                    onProgress("Copying images…", doneCount, poisWithImages.size)
                }
            }
            groupRepository.markImportComplete(groupId, total)
        }

        if (allRoutes.isNotEmpty()) {
            onProgress("Writing routes to storage…", 0, allRoutes.size)
            for ((route, points) in allRoutes) {
                val routeId = routeRepository.insert(route)
                if (points.isNotEmpty()) routeRepository.appendPoints(routeId, points)
                routesCount++
                onProgress("Writing routes to storage…", routesCount, allRoutes.size)
            }
        }

        ImportResult(
            poisImported = allPois.size,
            routesImported = routesCount,
            filesProcessed = if (skipped == 0) 1 else 0,
            filesSkipped = skipped,
            errors = errors,
        )
    }

    // ── GPX parser ────────────────────────────────────────────────────────────

    private suspend fun parseGpx(
        stream: InputStream,
        pois: MutableList<Poi>,
        routes: MutableList<Pair<Route, List<RoutePoint>>>,
        onWaypoint: suspend (count: Int) -> Unit = {},
    ) {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(stream, null)

        var inWpt = false; var inTrk = false; var inRte = false
        var inExtensions = false
        var wptLat = 0.0; var wptLon = 0.0
        var wptName = ""; var wptDesc: String? = null
        var wptEle: Double? = null; var wptTime: Long? = null
        var wptType = ""
        var wptImages = mutableListOf<String>()
        var trkName = ""; var trkDesc: String? = null
        val trkPoints = mutableListOf<RoutePoint>()
        var trkptLat = 0.0; var trkptLon = 0.0; var trkptTime: Long? = null
        val text = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    text.clear()
                    when (parser.name) {
                        "wpt" -> {
                            inWpt = true
                            wptLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            wptLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            wptName = ""; wptDesc = null; wptEle = null; wptTime = null
                            wptType = ""
                            wptImages = mutableListOf()
                        }
                        "trk" -> { inTrk = true; trkName = ""; trkDesc = null; trkPoints.clear() }
                        "rte" -> { inRte = true; trkName = ""; trkDesc = null; trkPoints.clear() }
                        "trkpt", "rtept" -> {
                            trkptLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            trkptLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            trkptTime = null
                        }
                        "extensions" -> if (inWpt) inExtensions = true
                    }
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val t = text.toString().trim()
                    when (parser.name) {
                        "name" -> { if (inWpt) wptName = t else if (inTrk || inRte) trkName = t }
                        "desc" -> { if (inWpt) wptDesc = t.takeIf { it.isNotEmpty() } else if (inTrk || inRte) trkDesc = t.takeIf { it.isNotEmpty() } }
                        "type" -> { if (inWpt) wptType = t }
                        "ele" -> { if (inWpt) wptEle = t.toDoubleOrNull() }
                        "time" -> {
                            val ts = parseIso8601(t)
                            if (inWpt) wptTime = ts else trkptTime = ts
                        }
                        "images" -> {
                            if (inWpt && inExtensions && t.isNotEmpty()) {
                                t.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { wptImages.add(it) }
                            }
                        }
                        "extensions" -> if (inWpt) inExtensions = false
                        "trkpt", "rtept" -> {
                            trkPoints.add(RoutePoint(ts = trkptTime ?: System.currentTimeMillis(), lat = trkptLat, lng = trkptLon))
                        }
                        "wpt" -> {
                            inWpt = false
                            pois.add(
                                Poi(
                                    name = wptName.takeIf { it.isNotEmpty() } ?: "Unnamed POI",
                                    description = wptDesc,
                                    lat = wptLat, lng = wptLon,
                                    elevation = wptEle,
                                    mediaPaths = wptImages.toList(),
                                    iconKey = if (wptType.isNotBlank()) {
                                        com.mappingsolution.data.places.PoiIconResolver
                                            .resolveForImportedType(wptType).takeIf { it != "place" }
                                    } else {
                                        com.mappingsolution.data.places.PoiIconResolver
                                            .resolveForImportedName(wptName, wptDesc ?: "").takeIf { it != "place" }
                                    },
                                    createdAt = wptTime ?: System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis(),
                                )
                            )
                            onWaypoint(pois.size)
                        }
                        "trk" -> {
                            inTrk = false
                            if (trkPoints.isNotEmpty()) routes.add(buildRoute(trkName, trkDesc, trkPoints.toList()))
                        }
                        "rte" -> {
                            inRte = false
                            if (trkPoints.isNotEmpty()) routes.add(buildRoute(trkName, trkDesc, trkPoints.toList()))
                        }
                    }
                    text.clear()
                }
            }
            event = parser.next()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a case-insensitive map from [nameWithoutExtension] → File for all files in [dir].
     * Also adds an entry for each `_x_200` stem stripped back to its original stem, so that
     * a GPX reference like "photo.jpg" can resolve to "photo_x_200.avif".
     */
    private fun buildImageIndex(dir: File): Map<String, File> {
        val index = mutableMapOf<String, File>()
        dir.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            val stem = f.nameWithoutExtension.lowercase()
            index[stem] = f
            // Also index by original stem (strip _x_200 suffix) for fallback resolution
            if (stem.endsWith("_x_200")) {
                val originalStem = stem.removeSuffix("_x_200")
                index.putIfAbsent(originalStem, f)
            }
        }
        return index
    }

    /**
     * Resolves an image file from [imagesDir] by [filename].
     * Tries exact match first, then the `_x_200` thumbnail variant used by AmudAnan.
     */
    private fun resolveImageFile(imagesDir: File, filename: String, index: Map<String, File>): File? {
        val exact = File(imagesDir, filename)
        if (exact.isFile) return exact
        val stem = filename.substringBeforeLast('.').lowercase()
        return index[stem]
    }

    private fun buildRoute(
        name: String,
        desc: String?,
        points: List<RoutePoint>,
    ): Pair<Route, List<RoutePoint>> {
        val start = points.first().ts
        val end = points.last().ts
        val route = Route(
            name = name.takeIf { it.isNotEmpty() } ?: "Unnamed Route",
            description = desc,
            startedAt = start,
            stoppedAt = end,
            checkpointAt = start,
            distanceMeters = calculateDistance(points),
            durationSec = if (end > start) (end - start) / 1000L else 0L,
            didUserTapStop = true,
        )
        return route to points
    }

    private fun calculateDistance(points: List<RoutePoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversineMeters(points[i - 1].lat, points[i - 1].lng, points[i].lat, points[i].lng)
        }
        return total
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun parseIso8601(text: String): Long? = try {
        Instant.parse(text).toEpochMilli()
    } catch (_: Exception) { null }
}
