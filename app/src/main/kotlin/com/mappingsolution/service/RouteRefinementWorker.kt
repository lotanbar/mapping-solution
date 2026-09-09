package com.mappingsolution.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mappingsolution.MainActivity
import com.mappingsolution.data.recording.RecordingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

@HiltWorker
class RouteRefinementWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val recordingRepository: RecordingRepository,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = buildForegroundInfo("Starting…")

    override suspend fun doWork(): Result {
        val routeId = inputData.getString(KEY_ROUTE_ID)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Missing route ID"))

        return try {
            if (!recordingRepository.isReadyForRefinement(routeId)) {
                return if (runAttemptCount < MAX_RETRIES) Result.retry()
                else Result.failure(workDataOf(KEY_ERROR to "Recording has not finished saving"))
            }
            setForeground(buildForegroundInfo("Starting…"))
            val distance = recordingRepository.mapMatchTrack(routeId) { phase, done, total ->
                val progress = if (total > 0) " — ${done * 100 / total}%" else ""
                val text = "$phase$progress"
                setProgress(workDataOf(KEY_PHASE to phase, KEY_DONE to done, KEY_TOTAL to total))
                setForeground(buildForegroundInfo(text))
            }
            recordingRepository.completeRefinement(routeId, distance)
            Result.success()
        } catch (e: IOException) {
            if (runAttemptCount < MAX_RETRIES) Result.retry()
            else Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Road data unavailable")))
        } catch (e: Exception) {
            android.util.Log.e("RouteRefinementWorker", "Route refinement failed", e)
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Route refinement failed")))
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

    private fun buildNotification(contentText: String): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
            .setContentTitle("Refining route…")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                WorkManager.getInstance(context).createCancelPendingIntent(id),
            )
            .build()
    }

    companion object {
        const val NOTIF_ID = 4
        const val NOTIF_CHANNEL_ID = "route_refinement"
        const val TAG = "route_refinement"
        const val ROUTE_TAG_PREFIX = "route_refinement_id:"
        const val KEY_ROUTE_ID = "route_id"
        const val KEY_PHASE = "phase"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val MAX_RETRIES = 3

        fun uniqueWorkName(routeId: String) = "route-refinement-$routeId"

        fun enqueue(context: Context, routeId: String) {
            val request = OneTimeWorkRequestBuilder<RouteRefinementWorker>()
                .setInputData(workDataOf(KEY_ROUTE_ID to routeId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(TAG)
                .addTag("$ROUTE_TAG_PREFIX$routeId")
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(routeId),
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context, routeId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(routeId))
        }
    }
}
