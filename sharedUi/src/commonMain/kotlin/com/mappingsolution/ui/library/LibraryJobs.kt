package com.mappingsolution.ui.library

import com.mappingsolution.data.fs.ImportResult
import kotlinx.coroutines.flow.Flow

/** Progress of one route refinement. */
data class RefinementProgress(val text: String, val fraction: Float)

/** A GPX/ZIP/folder import as shown in the library; [result] is set once it finishes. */
data class ImportJob(
    val label: String?,
    val progressText: String,
    val progressFraction: Float,
    val isRunning: Boolean,
    val result: ImportResult? = null,
)

/** An MBTiles import as shown in the library; [result] is set once it finishes. */
data class MbtilesJob(
    val progressText: String,
    val progressFraction: Float,
    val isRunning: Boolean,
    val result: MbtilesImportResult? = null,
)

/**
 * Long-running library work that must outlive the screen (Android: WorkManager; desktop:
 * application coroutines). Every flow reflects work started from any screen instance.
 */
interface LibraryJobs {
    /** Active (queued or running) route refinements keyed by route ID. */
    val refinementProgress: Flow<Map<String, RefinementProgress>>
    val importJob: Flow<ImportJob?>
    val mbtilesJob: Flow<MbtilesJob?>

    fun refineRoute(routeId: String)
    fun cancelRefinement(routeId: String)

    fun importFolder(path: String)
    fun importZip(path: String)
    fun importGpx(path: String)
    fun dismissImportResult()

    /** [source] is a platform file reference (Android: file URI string; desktop: file path). */
    fun importMbtiles(source: String)
    fun dismissMbtilesImportResult()
}
