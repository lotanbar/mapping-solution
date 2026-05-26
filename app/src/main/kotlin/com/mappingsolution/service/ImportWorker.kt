package com.mappingsolution.service

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mappingsolution.data.fs.ImportRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val importRepository: ImportRepository,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = buildForegroundInfo("Starting…")

    override suspend fun doWork(): Result {
        val folderPath = inputData.getString(KEY_FOLDER_PATH)
        val filePath = inputData.getString(KEY_FILE_PATH)
        val zipPath = inputData.getString(KEY_ZIP_PATH)

        if (folderPath == null && filePath == null && zipPath == null) return Result.failure(
            workDataOf(KEY_ERRORS to arrayOf("No folder, file, or zip path provided"))
        )

        return try {
            setForeground(buildForegroundInfo("Starting…"))

            val importResult = when {
                zipPath != null -> importRepository.importZipFile(zipPath) { phase, done, total ->
                    val text = if (total > 0) "$phase $done / $total" else phase
                    setForeground(buildForegroundInfo(text))
                    setProgress(workDataOf(KEY_PHASE to phase, KEY_DONE to done, KEY_TOTAL to total))
                }
                filePath != null -> importRepository.importSingleFile(filePath) { phase, done, total ->
                    val text = if (total > 0) "$phase $done / $total" else phase
                    setForeground(buildForegroundInfo(text))
                    setProgress(workDataOf(KEY_PHASE to phase, KEY_DONE to done, KEY_TOTAL to total))
                }
                else -> importRepository.importFolder(folderPath!!) { phase, done, total ->
                    val text = if (total > 0) "$phase $done / $total" else phase
                    setForeground(buildForegroundInfo(text))
                    setProgress(workDataOf(KEY_PHASE to phase, KEY_DONE to done, KEY_TOTAL to total))
                }
            }

            val output = workDataOf(
                KEY_POIS_IMPORTED to importResult.poisImported,
                KEY_ROUTES_IMPORTED to importResult.routesImported,
                KEY_FILES_PROCESSED to importResult.filesProcessed,
                KEY_FILES_SKIPPED to importResult.filesSkipped,
                KEY_ERRORS to importResult.errors.toTypedArray(),
                KEY_VALIDATION_ERRORS to importResult.validationErrors.toTypedArray(),
            )

            if (importResult.isValidationFailure) Result.failure(output) else Result.success(output)
        } catch (e: Exception) {
            android.util.Log.e("ImportWorker", "Import failed with exception", e)
            Result.failure(
                workDataOf(KEY_ERRORS to arrayOf(e.message ?: "Unexpected error: ${e.javaClass.simpleName}"))
            )
        }
    }

    private fun buildForegroundInfo(contentText: String): ForegroundInfo {
        val notification = buildNotification(contentText)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(contentText: String): Notification =
        NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
            .setContentTitle("Importing POIs…")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setSilent(true)
            .build()

    companion object {
        const val NOTIF_ID = 2
        const val NOTIF_CHANNEL_ID = "import_progress"

        const val KEY_FOLDER_PATH = "folder_path"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_ZIP_PATH = "zip_path"
        const val KEY_PHASE = "phase"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_POIS_IMPORTED = "pois_imported"
        const val KEY_ROUTES_IMPORTED = "routes_imported"
        const val KEY_FILES_PROCESSED = "files_processed"
        const val KEY_FILES_SKIPPED = "files_skipped"
        const val KEY_ERRORS = "errors"
        const val KEY_VALIDATION_ERRORS = "validation_errors"
    }
}
