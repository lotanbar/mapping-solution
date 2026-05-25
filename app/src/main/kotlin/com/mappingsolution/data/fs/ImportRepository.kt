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
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
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

        val gpxFiles = folder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() == "gpx" }
            ?: emptyList()
        val imagesDir = File(folder, "images").takeIf { it.isDirectory }

        // For a single-GPX folder (including ZIP imports), name the group after the GPX file
        // so it matches any previously-imported group of the same name and triggers purge+replace.
        val folderName = if (gpxFiles.size == 1) {
            gpxFiles.first().nameWithoutExtension
        } else {
            folder.name.takeIf { it.isNotEmpty() } ?: "Import"
        }

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

        // ── Phase 3: Save ─────────────────────────────────────────────────────
        if (allPois.isNotEmpty()) {
            // Pre-resolve image filenames so JSONL paths match the actual files on disk.
            // AmudAnan stores originals as _x_200.avif thumbnails, so "photo.jpg" → "photo_x_200.avif".
            val imageIndex = imagesDir?.let { buildImageIndex(it) } ?: emptyMap()
            val resolvedPois = allPois.map { poi ->
                if (poi.mediaPaths.isEmpty()) return@map poi
                if (imagesDir == null) return@map poi.copy(mediaPaths = emptyList())
                poi.copy(mediaPaths = poi.mediaPaths.map { filename ->
                    resolveImageFile(imagesDir, filename, imageIndex)?.name ?: filename
                })
            }
            saveImport(folderName, resolvedPois, imagesDir, imageIndex, onProgress)
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
            val imageIndex = imagesDir?.let { buildImageIndex(it) } ?: emptyMap()
            val resolvedPois = allPois.map { poi ->
                if (poi.mediaPaths.isEmpty()) return@map poi
                if (imagesDir == null) return@map poi.copy(mediaPaths = emptyList())
                poi.copy(mediaPaths = poi.mediaPaths.map { filename ->
                    resolveImageFile(imagesDir, filename, imageIndex)?.name ?: filename
                })
            }
            saveImport(groupName, resolvedPois, imagesDir, imageIndex, onProgress)
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

    // ── ZIP import ────────────────────────────────────────────────────────────

    /**
     * Imports a ZIP file that contains a GPX file and an optional `images/` folder.
     * The ZIP is extracted to a temp directory, then processed with [importFolder].
     */
    suspend fun importZipFile(
        zipPath: String,
        onProgress: suspend (phase: String, done: Int, total: Int) -> Unit = { _, _, _ -> },
    ): ImportResult = withContext(Dispatchers.IO) {
        val zipFile = File(zipPath)
        if (!zipFile.isFile || zipFile.extension.lowercase() != "zip") {
            return@withContext ImportResult(errors = listOf("Not a valid ZIP file: ${zipFile.name}"))
        }

        val tempDir = File(context.cacheDir, "zip_import_${System.currentTimeMillis()}")
        try {
            onProgress("Reading ZIP index…", 0, 0)
            val totalEntries = ZipFile(zipFile).use { it.size() }
            onProgress("Extracting ZIP…", 0, totalEntries)
            extractZip(zipFile, tempDir) { done -> onProgress("Extracting ZIP…", done, totalEntries) }
            importFolder(tempDir.absolutePath, onProgress)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private suspend fun extractZip(
        zipFile: File,
        destDir: File,
        onEntry: suspend (done: Int) -> Unit = {},
    ) {
        destDir.mkdirs()
        val destCanonical = destDir.canonicalPath
        var extracted = 0
        var lastEmit = 0L
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryFile = File(destDir, entry.name)
                // Guard against path-traversal attacks
                if (!entryFile.canonicalPath.startsWith(destCanonical + File.separator) &&
                    entryFile.canonicalPath != destCanonical) {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }
                if (entry.isDirectory) {
                    entryFile.mkdirs()
                } else {
                    entryFile.parentFile?.mkdirs()
                    entryFile.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                extracted++
                val now = System.currentTimeMillis()
                if (now - lastEmit >= 32) {
                    lastEmit = now
                    onEntry(extracted)
                }
                entry = zis.nextEntry
            }
        }
    }

    // ── GPX parser ────────────────────────────────────────────────────────────

    /**
     * Core save logic used by both [importFolder] and [importSingleFile].
     * Detects re-import vs first import via [GroupFileRepository.prepareForReimport].
     */
    private suspend fun saveImport(
        groupName: String,
        resolvedPois: List<Poi>,
        imagesDir: File?,
        imageIndex: Map<String, File>,
        onProgress: suspend (phase: String, done: Int, total: Int) -> Unit,
    ) {
        val reimportInfo = groupRepository.prepareForReimport(groupName)
        Log.i("ImportRepository", "saveImport: groupName='$groupName' reimport=${reimportInfo != null} pois=${resolvedPois.size}")

        if (reimportInfo != null) {
            // ── SMART RE-IMPORT ──────────────────────────────────────────────
            val (groupId, storedName) = reimportInfo
            val bulkFile = storageManager.getBulkPoisFile(storedName.trim(), groupId)
            bulkFile.parentFile?.mkdirs()

            // Count existing lines for "Comparing" phase progress %
            val existingLineCount = if (bulkFile.isFile) {
                bulkFile.bufferedReader().use { r -> var n = 0; while (r.readLine() != null) n++; n }
            } else 0
            Log.i("ImportRepository", "Re-import: existingLines=$existingLineCount bulkFile=${bulkFile.path}")

            // Load existing POIs, keyed by identity (name|lat|lng)
            val existingByKey = HashMap<String, Poi>(existingLineCount * 2)
            if (existingLineCount > 0) {
                onProgress("Comparing with previous import…", 0, existingLineCount)
                var compareCount = 0
                var compareLastEmit = 0L
                bulkFile.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        runCatching {
                            val poi = BulkPoiRepository.deserializePoi(line!!)
                            existingByKey["${poi.name}|${poi.lat}|${poi.lng}"] = poi
                        }
                        compareCount++
                        val now = System.currentTimeMillis()
                        if (now - compareLastEmit >= 32 || compareCount == existingLineCount) {
                            compareLastEmit = now
                            onProgress("Comparing with previous import…", compareCount, existingLineCount)
                        }
                    }
                }
            }

            // Categorize incoming POIs; assign stable IDs for matched ones
            val poisToWrite = ArrayList<Poi>(resolvedPois.size)
            val addedPoiIds = HashSet<String>()
            val updatedWithImageChanges = ArrayList<Poi>()

            for (incoming in resolvedPois) {
                val key = "${incoming.name}|${incoming.lat}|${incoming.lng}"
                val existing = existingByKey[key]
                val finalPoi = if (existing != null) {
                    incoming.copy(id = existing.id, groupId = groupId)
                } else {
                    incoming.copy(groupId = groupId).also { addedPoiIds.add(it.id) }
                }
                poisToWrite.add(finalPoi)
                if (existing != null && existing.mediaPaths.sorted() != incoming.mediaPaths.sorted()) {
                    updatedWithImageChanges.add(finalPoi)
                }
            }

            // Removed POIs: in existing but not in incoming
            val incomingKeys = resolvedPois.mapTo(HashSet(resolvedPois.size * 2)) { "${it.name}|${it.lat}|${it.lng}" }
            val removedPois = existingByKey.values.filter { "${it.name}|${it.lat}|${it.lng}" !in incomingKeys }

            Log.i("ImportRepository", "Diff: added=${addedPoiIds.size} removed=${removedPois.size} updatedImages=${updatedWithImageChanges.size} unchanged=${resolvedPois.size - addedPoiIds.size - updatedWithImageChanges.size}")

            // Write new JSONL
            val total = poisToWrite.size
            onProgress("Writing to storage…", 0, total)
            var writeLastEmit = 0L
            bulkFile.bufferedWriter().use { writer ->
                for ((i, poi) in poisToWrite.withIndex()) {
                    writer.write(BulkPoiRepository.serializePoi(poi))
                    writer.newLine()
                    val now = System.currentTimeMillis()
                    if (now - writeLastEmit >= 32 || i == total - 1) {
                        writeLastEmit = now
                        onProgress("Writing to storage…", i + 1, total)
                    }
                }
            }

            // Delete removed POIs' image dirs
            for (poi in removedPois) {
                storageManager.getPoiMediaDir(poi.name, poi.id).deleteRecursively()
            }

            // Image sync: added POIs (copy all) + updated POIs with mediaPaths changes (sync by filename+size)
            val toSync = ArrayList<Poi>(addedPoiIds.size + updatedWithImageChanges.size).also {
                it.addAll(poisToWrite.filter { p -> p.id in addedPoiIds })
                it.addAll(updatedWithImageChanges)
            }
            if (toSync.isEmpty()) {
                Log.i("ImportRepository", "Images up to date — nothing to sync")
                onProgress("Images up to date", 0, 0)
            } else {
                Log.i("ImportRepository", "Syncing images for ${toSync.size} POIs (${addedPoiIds.size} added, ${updatedWithImageChanges.size} updated)")
                onProgress("Syncing images…", 0, toSync.size)
                var syncCount = 0
                for (poi in toSync) {
                    if (imagesDir != null) {
                        val destDir = storageManager.getPoiMediaDir(poi.name, poi.id)
                        if (poi.id in addedPoiIds) {
                            destDir.mkdirs()
                            for (filename in poi.mediaPaths) {
                                val srcFile = File(imagesDir, filename).takeIf { it.isFile }
                                    ?: resolveImageFile(imagesDir, filename, imageIndex)
                                    ?: continue
                                runCatching { srcFile.copyTo(File(destDir, srcFile.name), overwrite = true) }
                                    .onFailure { Log.w("ImportRepository", "Failed to copy '${srcFile.name}': ${it.message}") }
                            }
                        } else {
                            syncPoiImages(imagesDir, destDir, poi.mediaPaths, imageIndex)
                        }
                    }
                    syncCount++
                    onProgress("Syncing images…", syncCount, toSync.size)
                }
            }

            groupRepository.markImportComplete(groupId, total)

        } else {
            // ── FIRST IMPORT ────────────────────────────────────────────────
            Log.i("ImportRepository", "First import for group='$groupName', pois=${resolvedPois.size}")
            val groupId = groupRepository.purgeAndCreateForImport(groupName, poiRepository) { phase, done, total ->
                onProgress(phase, done, total)
            }
            val total = resolvedPois.size

            val bulkFile = storageManager.getBulkPoisFile(groupName.trim(), groupId)
            bulkFile.parentFile?.mkdirs()
            onProgress("Writing to storage…", 0, total)
            var writeLastEmit = 0L
            bulkFile.bufferedWriter().use { writer ->
                for ((i, poi) in resolvedPois.withIndex()) {
                    writer.write(BulkPoiRepository.serializePoi(poi.copy(groupId = groupId)))
                    writer.newLine()
                    val now = System.currentTimeMillis()
                    if (now - writeLastEmit >= 32 || i == total - 1) {
                        writeLastEmit = now
                        onProgress("Writing to storage…", i + 1, total)
                    }
                }
            }

            if (imagesDir != null) {
                val poisWithImages = resolvedPois.filter { it.mediaPaths.isNotEmpty() }
                Log.i("ImportRepository", "Copying images for ${poisWithImages.size} POIs")
                onProgress("Copying images…", 0, poisWithImages.size)
                var doneCount = 0
                for (poi in poisWithImages) {
                    val destDir = storageManager.getPoiMediaDir(poi.name, poi.id)
                    destDir.mkdirs()
                    for (filename in poi.mediaPaths) {
                        val srcFile = File(imagesDir, filename).takeIf { it.isFile }
                            ?: resolveImageFile(imagesDir, filename, imageIndex)
                            ?: continue
                        runCatching { srcFile.copyTo(File(destDir, srcFile.name), overwrite = true) }
                            .onFailure { Log.w("ImportRepository", "Failed to copy image '${srcFile.name}': ${it.message}") }
                    }
                    doneCount++
                    onProgress("Copying images…", doneCount, poisWithImages.size)
                }
            }

            groupRepository.markImportComplete(groupId, total)
        }
    }

    /**
     * - Deletes files in [destDir] not in [expectedFilenames]
     * - Copies files from [srcDir] that are missing or differ in size (incoming wins)
     * - Skips files with matching filename AND size
     */
    private fun syncPoiImages(
        srcDir: File,
        destDir: File,
        expectedFilenames: List<String>,
        imageIndex: Map<String, File>,
    ) {
        destDir.mkdirs()
        val expected = expectedFilenames.toHashSet()

        // Delete stale files no longer referenced
        destDir.listFiles()?.forEach { f -> if (f.name !in expected) f.delete() }

        // Copy new or changed files
        for (filename in expectedFilenames) {
            val srcFile = File(srcDir, filename).takeIf { it.isFile }
                ?: resolveImageFile(srcDir, filename, imageIndex)
                ?: continue
            val destFile = File(destDir, srcFile.name)
            if (destFile.isFile && destFile.length() == srcFile.length()) continue
            runCatching { srcFile.copyTo(destFile, overwrite = true) }
                .onFailure { Log.w("ImportRepository", "Failed to sync '${srcFile.name}': ${it.message}") }
        }
    }

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
