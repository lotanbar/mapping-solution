package com.mappingsolution.desktop

import com.mappingsolution.data.fs.ImportRepository
import com.mappingsolution.data.fs.ImportResult
import com.mappingsolution.data.fs.RasterLayerRepository
import com.mappingsolution.data.model.RasterLayer
import com.mappingsolution.data.recording.RecordingRepository
import com.mappingsolution.data.util.AppLog
import com.mappingsolution.data.util.StorageManager
import com.mappingsolution.ui.library.ImportJob
import com.mappingsolution.ui.library.LibraryJobs
import com.mappingsolution.ui.library.MbtilesImportResult
import com.mappingsolution.ui.library.MbtilesJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.sql.DriverManager
import java.util.UUID

/** [LibraryJobs] on application-lifetime coroutines; work stops when the desktop app exits. */
internal class DesktopLibraryJobs(
    private val importRepository: ImportRepository,
    private val recordingRepository: RecordingRepository,
    private val rasterLayerRepository: RasterLayerRepository,
    private val storageManager: StorageManager,
) : LibraryJobs {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Route refinement ─────────────────────────────────────────────────

    private val _refinementProgress = MutableStateFlow<Map<String, String>>(emptyMap())
    override val refinementProgress: StateFlow<Map<String, String>> = _refinementProgress.asStateFlow()
    private val refinementJobs = mutableMapOf<String, Job>()

    override fun refineRoute(routeId: String) {
        if (refinementJobs[routeId]?.isActive == true) return
        _refinementProgress.update { it + (routeId to "Queued for refinement") }
        refinementJobs[routeId] = scope.launch {
            try {
                val distance = recordingRepository.mapMatchTrack(routeId) { phase, done, total ->
                    val text = if (total > 0) "$phase — ${done * 100 / total}%" else phase
                    _refinementProgress.update { it + (routeId to text) }
                }
                recordingRepository.completeRefinement(routeId, distance)
            } catch (e: Exception) {
                AppLog.e(TAG, "Route refinement failed for $routeId", e)
            } finally {
                _refinementProgress.update { it - routeId }
            }
        }
    }

    override fun cancelRefinement(routeId: String) {
        refinementJobs.remove(routeId)?.cancel()
        _refinementProgress.update { it - routeId }
    }

    // ── GPX / ZIP / folder import ────────────────────────────────────────

    private val _importJob = MutableStateFlow<ImportJob?>(null)
    override val importJob: StateFlow<ImportJob?> = _importJob.asStateFlow()

    override fun importFolder(path: String) = runImport(File(path).name) { progress ->
        importRepository.importFolder(path, progress)
    }

    override fun importZip(path: String) = runImport(label(path)) { progress ->
        importRepository.importZipFile(path, progress)
    }

    override fun importGpx(path: String) = runImport(label(path)) { progress ->
        importRepository.importSingleFile(path, progress)
    }

    override fun dismissImportResult() {
        _importJob.value = null
    }

    private fun runImport(
        label: String,
        block: suspend (suspend (String, Int, Int) -> Unit) -> ImportResult,
    ) {
        if (_importJob.value?.isRunning == true) return
        _importJob.value = ImportJob(label, "Starting…", 0f, isRunning = true)
        scope.launch {
            val result = try {
                block { phase, done, total ->
                    _importJob.value = ImportJob(
                        label = label,
                        progressText = if (total > 0) "$phase — ${done * 100 / total}%" else phase,
                        progressFraction = if (total > 0) done.toFloat() / total else 0f,
                        isRunning = true,
                    )
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Import of $label failed", e)
                ImportResult(errors = listOf(e.message ?: "Import failed"))
            }
            _importJob.value = ImportJob(label, "", 0f, isRunning = false, result = result)
        }
    }

    private fun label(path: String) = File(path).nameWithoutExtension.ifEmpty { "Import" }

    // ── MBTiles import ───────────────────────────────────────────────────

    private val _mbtilesJob = MutableStateFlow<MbtilesJob?>(null)
    override val mbtilesJob: StateFlow<MbtilesJob?> = _mbtilesJob.asStateFlow()

    override fun importMbtiles(source: String) {
        if (_mbtilesJob.value?.isRunning == true) return
        _mbtilesJob.value = MbtilesJob("Starting…", 0f, isRunning = true)
        scope.launch {
            _mbtilesJob.value = try {
                MbtilesJob("", 0f, isRunning = false, result = MbtilesImportResult.Success(copyMbtiles(File(source))))
            } catch (e: Exception) {
                AppLog.e(TAG, "MBTiles import failed", e)
                MbtilesJob("", 0f, isRunning = false, result = MbtilesImportResult.Failure(e.message ?: "Import failed"))
            }
        }
    }

    override fun dismissMbtilesImportResult() {
        _mbtilesJob.value = null
    }

    /** Mirrors the Android worker: copy with progress, read metadata, replace a same-named layer. */
    private suspend fun copyMbtiles(source: File): String {
        val total = source.length()
        val temp = storageManager.getMbtilesTempFile()
        try {
            source.inputStream().use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(65_536)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        _mbtilesJob.value = MbtilesJob(
                            "Copying… %.1f MB / %.1f MB".format(copied / 1_048_576.0, total / 1_048_576.0),
                            if (total > 0) copied.toFloat() / total else 0f,
                            isRunning = true,
                        )
                    }
                }
            }
            val metadata = readMetadata(temp)
            val layerName = metadata["name"]?.trim()?.takeIf { it.isNotEmpty() }
                ?: source.nameWithoutExtension.ifEmpty { "Untitled Layer" }
            rasterLayerRepository.findByName(layerName)?.let { rasterLayerRepository.delete(it.id) }
            val id = UUID.randomUUID().toString()
            val finalFile = storageManager.getMbtilesFile(layerName, id)
            if (!temp.renameTo(finalFile)) {
                temp.copyTo(finalFile, overwrite = true)
                temp.delete()
            }
            rasterLayerRepository.insert(
                RasterLayer(
                    id = id,
                    name = layerName,
                    filePath = finalFile.absolutePath,
                    isVisible = true,
                    minZoom = metadata["minzoom"]?.toIntOrNull() ?: 0,
                    maxZoom = metadata["maxzoom"]?.toIntOrNull() ?: 22,
                )
            )
            return layerName
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun readMetadata(file: File): Map<String, String> =
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT name, value FROM metadata").use { rows ->
                    buildMap { while (rows.next()) put(rows.getString(1), rows.getString(2)) }
                }
            }
        }

    private companion object {
        const val TAG = "DesktopLibraryJobs"
    }
}
