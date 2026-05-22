package com.mappingsolution.data.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppFileLogger"
private const val MAX_LOG_SIZE_BYTES = 2 * 1024 * 1024L  // 2 MB
private const val LOG_FILE_NAME = "app_log.txt"
private const val LOG_FILE_ROTATED_NAME = "app_log_old.txt"

/**
 * File-based logger that appends structured log entries to a file in app-private
 * external storage. Rotates the log file once it exceeds [MAX_LOG_SIZE_BYTES].
 *
 * Stored at: `<app-external-files>/logs/app_log.txt`
 * (app-private, not accessible to other apps, not backed up)
 */
@Singleton
class AppFileLogger @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val logsDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs")
            .also { it.mkdirs() }

    val logFile: File
        get() = File(logsDir, LOG_FILE_NAME)

    fun d(tag: String, message: String) = append("D", tag, message, null)
    fun i(tag: String, message: String) = append("I", tag, message, null)
    fun w(tag: String, message: String, throwable: Throwable? = null) = append("W", tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = append("E", tag, message, throwable)

    private fun append(level: String, tag: String, message: String, throwable: Throwable?) {
        Log.println(
            when (level) {
                "D" -> Log.DEBUG; "I" -> Log.INFO; "W" -> Log.WARN; else -> Log.ERROR
            },
            tag, message
        )
        throwable?.let { Log.e(tag, message, it) }

        scope.launch {
            mutex.withLock {
                try {
                    val file = logFile
                    if (file.exists() && file.length() >= MAX_LOG_SIZE_BYTES) {
                        val rotated = File(logsDir, LOG_FILE_ROTATED_NAME)
                        rotated.delete()
                        file.renameTo(rotated)
                    }
                    val ts = dateFormat.format(Date())
                    val sb = StringBuilder()
                    sb.append("$ts $level/$tag: $message\n")
                    throwable?.let { t ->
                        sb.append("  Exception: ${t.javaClass.name}: ${t.message}\n")
                        t.stackTrace.take(20).forEach { frame ->
                            sb.append("    at $frame\n")
                        }
                    }
                    file.appendText(sb.toString())
                } catch (ex: Exception) {
                    Log.w(TAG, "Failed to write to log file: ${ex.message}")
                }
            }
        }
    }
}
