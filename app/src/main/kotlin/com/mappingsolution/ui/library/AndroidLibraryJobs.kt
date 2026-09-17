package com.mappingsolution.ui.library

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mappingsolution.data.fs.ImportResult
import com.mappingsolution.service.ImportWorker
import com.mappingsolution.service.MbtilesImportWorker
import com.mappingsolution.service.RouteRefinementWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** [LibraryJobs] backed by WorkManager, so imports and refinements survive leaving the app. */
@Singleton
class AndroidLibraryJobs @Inject constructor(
    @ApplicationContext private val context: Context,
) : LibraryJobs {

    private val workManager = WorkManager.getInstance(context)

    override val refinementProgress: Flow<Map<String, RefinementProgress>> =
        workManager.getWorkInfosByTagFlow(RouteRefinementWorker.TAG).map { infos ->
            infos.filter { it.state.isActive() }.mapNotNull { info ->
                val routeId = info.tags.firstOrNull { it.startsWith(RouteRefinementWorker.ROUTE_TAG_PREFIX) }
                    ?.removePrefix(RouteRefinementWorker.ROUTE_TAG_PREFIX) ?: return@mapNotNull null
                val phase = info.progress.getString(RouteRefinementWorker.KEY_PHASE)
                    ?: if (info.state == WorkInfo.State.RUNNING) "Refining…" else "Queued for refinement"
                val done = info.progress.getInt(RouteRefinementWorker.KEY_DONE, 0)
                val total = info.progress.getInt(RouteRefinementWorker.KEY_TOTAL, 0)
                routeId to RefinementProgress(
                    text = if (total > 0) "$phase — ${done * 100 / total}%" else phase,
                    fraction = if (total > 0) done.toFloat() / total else 0f,
                )
            }.toMap()
        }

    override fun refineRoute(routeId: String) = RouteRefinementWorker.enqueue(context, routeId)

    override fun cancelRefinement(routeId: String) = RouteRefinementWorker.cancel(context, routeId)

    // ── GPX / ZIP / folder import ────────────────────────────────────────

    /** The work request this process started; null means "reconnect to whatever is running". */
    private val currentImportId = MutableStateFlow<UUID?>(null)
    /** Last work record whose result was dismissed — hidden until pruneWork() removes it. */
    private val dismissedImportId = MutableStateFlow<UUID?>(null)
    private var shownImportId: UUID? = null
    private val startingImport = MutableStateFlow<ImportJob?>(null)

    override val importJob: Flow<ImportJob?> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(IMPORT_WORK_NAME),
        currentImportId,
        dismissedImportId,
        startingImport,
    ) { infos, current, dismissed, starting ->
        val info = pick(infos, current, dismissed)
        shownImportId = info?.id
        when {
            info == null -> starting.takeIf { current != null }
            info.state == WorkInfo.State.CANCELLED -> null
            else -> info.toImportJob()
        }
    }

    override fun importFolder(path: String) = enqueueImport(ImportWorker.KEY_FOLDER_PATH, path, File(path).name)

    override fun importZip(path: String) = enqueueImport(ImportWorker.KEY_ZIP_PATH, path, importLabel(path))

    override fun importGpx(path: String) = enqueueImport(ImportWorker.KEY_FILE_PATH, path, importLabel(path))

    override fun dismissImportResult() {
        dismissedImportId.value = currentImportId.value ?: shownImportId
        currentImportId.value = null
        workManager.pruneWork()
    }

    private fun enqueueImport(key: String, path: String, label: String) {
        val request = OneTimeWorkRequestBuilder<ImportWorker>()
            .setInputData(workDataOf(key to path))
            .addTag("$TAG_FOLDER_PREFIX$label")
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        startingImport.value = ImportJob(label, "Starting…", 0f, isRunning = true)
        dismissedImportId.value = null
        currentImportId.value = request.id
        workManager.enqueueUniqueWork(IMPORT_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun importLabel(path: String) = File(path).nameWithoutExtension.ifEmpty { "Import" }

    private fun WorkInfo.toImportJob(): ImportJob {
        val label = tags.firstOrNull { it.startsWith(TAG_FOLDER_PREFIX) }?.removePrefix(TAG_FOLDER_PREFIX)
        return when (state) {
            WorkInfo.State.SUCCEEDED -> ImportJob(
                label, "", 0f, isRunning = false,
                result = ImportResult(
                    poisImported = outputData.getInt(ImportWorker.KEY_POIS_IMPORTED, 0),
                    routesImported = outputData.getInt(ImportWorker.KEY_ROUTES_IMPORTED, 0),
                    filesProcessed = outputData.getInt(ImportWorker.KEY_FILES_PROCESSED, 0),
                    filesSkipped = outputData.getInt(ImportWorker.KEY_FILES_SKIPPED, 0),
                    errors = outputData.getStringArray(ImportWorker.KEY_ERRORS)?.toList() ?: emptyList(),
                ),
            )
            WorkInfo.State.FAILED -> ImportJob(
                label, "", 0f, isRunning = false,
                result = ImportResult(
                    filesSkipped = outputData.getInt(ImportWorker.KEY_FILES_SKIPPED, 0),
                    errors = outputData.getStringArray(ImportWorker.KEY_ERRORS)?.toList() ?: emptyList(),
                    validationErrors = outputData.getStringArray(ImportWorker.KEY_VALIDATION_ERRORS)?.toList()
                        ?: emptyList(),
                ),
            )
            else -> {
                val phase = progress.getString(ImportWorker.KEY_PHASE)
                val done = progress.getInt(ImportWorker.KEY_DONE, 0)
                val total = progress.getInt(ImportWorker.KEY_TOTAL, 0)
                ImportJob(
                    label = label,
                    progressText = phase?.let { if (total > 0) "$it — ${done * 100 / total}%" else it } ?: "Starting…",
                    progressFraction = if (total > 0) done.toFloat() / total else 0f,
                    isRunning = true,
                )
            }
        }
    }

    // ── MBTiles import ───────────────────────────────────────────────────

    private val currentMbtilesId = MutableStateFlow<UUID?>(null)
    private val dismissedMbtilesId = MutableStateFlow<UUID?>(null)
    private var shownMbtilesId: UUID? = null

    override val mbtilesJob: Flow<MbtilesJob?> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(MBTILES_IMPORT_WORK_NAME),
        currentMbtilesId,
        dismissedMbtilesId,
    ) { infos, current, dismissed ->
        val info = pick(infos, current, dismissed)
        shownMbtilesId = info?.id
        when {
            info == null -> MbtilesJob("Starting…", 0f, isRunning = true).takeIf { current != null }
            info.state == WorkInfo.State.CANCELLED -> null
            info.state == WorkInfo.State.SUCCEEDED -> MbtilesJob(
                "", 0f, isRunning = false,
                result = MbtilesImportResult.Success(info.outputData.getString(MbtilesImportWorker.KEY_LAYER_NAME) ?: ""),
            )
            info.state == WorkInfo.State.FAILED -> MbtilesJob(
                "", 0f, isRunning = false,
                result = MbtilesImportResult.Failure(
                    info.outputData.getString(MbtilesImportWorker.KEY_ERROR) ?: "Import failed"
                ),
            )
            else -> {
                val copied = info.progress.getLong(MbtilesImportWorker.KEY_BYTES_COPIED, 0L)
                val total = info.progress.getLong(MbtilesImportWorker.KEY_BYTES_TOTAL, -1L)
                val text = when {
                    copied <= 0 -> "Starting…"
                    total > 0 -> "Copying… %.1f MB / %.1f MB".format(copied / 1_048_576.0, total / 1_048_576.0)
                    else -> "Copying… %.1f MB".format(copied / 1_048_576.0)
                }
                MbtilesJob(text, if (total > 0) copied.toFloat() / total else 0f, isRunning = true)
            }
        }
    }

    override fun importMbtiles(source: String) {
        val request = OneTimeWorkRequestBuilder<MbtilesImportWorker>()
            .setInputData(workDataOf(MbtilesImportWorker.KEY_URI to source))
            .build()
        dismissedMbtilesId.value = null
        currentMbtilesId.value = request.id
        workManager.enqueueUniqueWork(MBTILES_IMPORT_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun dismissMbtilesImportResult() {
        dismissedMbtilesId.value = currentMbtilesId.value ?: shownMbtilesId
        currentMbtilesId.value = null
        workManager.pruneWork()
    }

    /** Prefers the request we started; otherwise active work, then the latest undismissed record. */
    private fun pick(infos: List<WorkInfo>, current: UUID?, dismissed: UUID?): WorkInfo? = when {
        current != null -> infos.firstOrNull { it.id == current }
        else -> infos.firstOrNull { it.id != dismissed && it.state.isActive() }
            ?: infos.lastOrNull { it.id != dismissed }
    }

    private fun WorkInfo.State.isActive() =
        this == WorkInfo.State.RUNNING || this == WorkInfo.State.ENQUEUED || this == WorkInfo.State.BLOCKED

    private companion object {
        const val IMPORT_WORK_NAME = "poi_import"
        const val TAG_FOLDER_PREFIX = "folder:"
        const val MBTILES_IMPORT_WORK_NAME = "mbtiles_import"
    }
}
