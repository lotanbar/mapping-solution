package com.mappingsolution.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.mappingsolution.MainActivity
import com.mappingsolution.data.recording.RecordingEvent
import com.mappingsolution.data.recording.RecordingPoint
import com.mappingsolution.data.recording.RecordingRepository
import com.mappingsolution.data.recording.RecordingState
import com.mappingsolution.data.recording.processing.MotionState
import com.mappingsolution.data.recording.processing.MotionStateEstimator
import com.mappingsolution.data.recording.processing.OsmRoadCache
import com.mappingsolution.data.recording.processing.SmartTrackProcessor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var recordingRepository: RecordingRepository
    @Inject lateinit var smartTrackProcessor: SmartTrackProcessor
    @Inject lateinit var osmRoadCache: OsmRoadCache

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null
    private val pendingPoints = mutableListOf<RecordingPoint>()
    private var flushedPointCount = 0
    private var lastLocation: Location? = null
    private var lastLocationAcceptedMs = 0L   // wall-clock time of the last accepted fix
    private var lastEmittedPoint: RecordingPoint? = null
    private val isStopping = AtomicBoolean(false)

    // --- Motion-quality state ---
    private var recordingStartedAtMs = 0L
    private val motionEstimator = MotionStateEstimator()
    private var postWarmupSettleCount = 0

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onNewLocation(location)
        override fun onProviderDisabled(provider: String) {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val anyEnabled = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .any { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            if (!anyEnabled) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID, buildNotification("Recording paused", "Location unavailable — re-enable GPS"))
            }
        }
        override fun onProviderEnabled(provider: String) {
            // Refresh the notification text to drop any "location unavailable" warning
            val st = recordingRepository.state.value as? RecordingState.Active ?: return
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification("Recording route…", st.autoName))
        }
    }

    companion object {
        const val ACTION_START = "com.mappingsolution.recording.START"
        const val ACTION_PAUSE = "com.mappingsolution.recording.PAUSE"
        const val ACTION_RESUME = "com.mappingsolution.recording.RESUME"
        const val ACTION_STOP = "com.mappingsolution.recording.STOP"
        const val ACTION_RESUME_INCOMPLETE = "com.mappingsolution.recording.RESUME_INCOMPLETE"
        const val NOTIF_CHANNEL_ID = "recording_channel"
        const val NOTIF_ID = 1001
        private const val EXTRA_ROUTE_ID = "route_id"

        /** Only accept GPS fixes with an accuracy circle ≤ this value (meters). */
        private const val MAX_ACCURACY_METERS = 50f
        /** Minimum displacement before we count movement (filters stationary jitter). */
        private const val MIN_MOVEMENT_METERS = 5.0

        /** Discard all fixes for this long after recording starts, giving the GPS chip time to lock. */
        private const val WARMUP_DURATION_MS = 15_000L

        /**
         * Reject a fix if it implies faster-than-this travel from the previous accepted fix.
         * Set high enough for commercial aviation (~250 m/s) while still catching GPS teleports
         * caused by bad fixes or provider switches.
         */
        private const val MAX_SPEED_MPS = 300.0  // 1 080 km/h

        /**
         * Fixes to discard immediately after the warmup window expires.
         * The spike detector needs at least one prior emitted point to function; skipping
         * a couple of fixes after warmup ensures it has history before committing any point.
         */
        private const val POST_WARMUP_SETTLE_FIXES = 2

        /** Location update interval/distance when battery saver is OFF. */
        private const val NORMAL_INTERVAL_MS = 2_000L
        private const val NORMAL_MIN_DIST_M = 2f

        /** Degraded interval/distance when battery saver is ON. */
        private const val BATTERY_SAVER_INTERVAL_MS = 5_000L
        private const val BATTERY_SAVER_MIN_DIST_M = 10f

        fun startIntent(context: Context) = Intent(context, RecordingService::class.java).apply { action = ACTION_START }
        fun pauseIntent(context: Context) = Intent(context, RecordingService::class.java).apply { action = ACTION_PAUSE }
        fun resumeIntent(context: Context) = Intent(context, RecordingService::class.java).apply { action = ACTION_RESUME }
        fun stopIntent(context: Context) = Intent(context, RecordingService::class.java).apply { action = ACTION_STOP }
        fun resumeIncompleteIntent(context: Context, routeId: String) =
            Intent(context, RecordingService::class.java).apply {
                action = ACTION_RESUME_INCOMPLETE
                putExtra(EXTRA_ROUTE_ID, routeId)
            }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification("Recording route…", "Starting…"))
                scope.launch(Dispatchers.IO) { handleStart() }
            }
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STOP -> scope.launch(Dispatchers.IO) { handleStop() }
            ACTION_RESUME_INCOMPLETE -> {
                val routeId = intent.getStringExtra(EXTRA_ROUTE_ID) ?: run { stopSelf(); return START_STICKY }
                startForeground(NOTIF_ID, buildNotification("Resuming recording…", "Loading…"))
                scope.launch(Dispatchers.IO) { handleResumeIncomplete(routeId) }
            }
            null -> {
                // Service restarted by OS (START_STICKY) — repository state is still in memory.
                val current = recordingRepository.state.value
                if (current is RecordingState.Active) {
                    startForeground(NOTIF_ID, buildNotification("Recording route…", current.autoName))
                    startLocationUpdates()
                    startNotificationTicker()
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private suspend fun handleStart() {
        isStopping.set(false)
        val (routeId, name) = recordingRepository.createRoute()
        val now = System.currentTimeMillis()
        smartTrackProcessor.reset()
        lastLocation = null
        lastEmittedPoint = null
        recordingStartedAtMs = now
        resetMotionState()
        recordingRepository.updateState(
            RecordingState.Active(
                routeId = routeId,
                autoName = name,
                startedAtMs = now,
            )
        )
        startLocationUpdates()
        startNotificationTicker()
    }

    private suspend fun handleResumeIncomplete(routeId: String) {
        smartTrackProcessor.reset()
        lastLocation = null
        lastEmittedPoint = null
        pendingPoints.clear()
        flushedPointCount = 0
        recordingStartedAtMs = System.currentTimeMillis()
        resetMotionState()
        recordingRepository.resumeIncomplete(routeId)
        val st = recordingRepository.state.value as? RecordingState.Active ?: run { stopSelf(); return }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification("Recording route…", st.autoName))
        startLocationUpdates()
        startNotificationTicker()
    }

    private fun handlePause() {
        stopLocationUpdates()
        val current = recordingRepository.state.value as? RecordingState.Active ?: return
        flushPendingPoints(current.routeId)
        recordingRepository.updateState(current.copy(pausedSinceMs = System.currentTimeMillis()))
    }

    private fun handleResume() {
        val current = recordingRepository.state.value as? RecordingState.Active ?: return
        val now = System.currentTimeMillis()
        val pausedDuration = if (current.pausedSinceMs != null) now - current.pausedSinceMs else 0L
        recordingRepository.updateState(
            current.copy(
                totalPausedMs = current.totalPausedMs + pausedDuration,
                pausedSinceMs = null,
            )
        )
        // GPS should still be locked after a short pause — skip warmup and settling,
        // but clear stationary state.
        recordingStartedAtMs = now - WARMUP_DURATION_MS
        resetMotionState()
        postWarmupSettleCount = POST_WARMUP_SETTLE_FIXES
        startLocationUpdates()
    }

    private suspend fun handleStop() {
        if (!isStopping.compareAndSet(false, true)) return
        stopLocationUpdates()
        val current = recordingRepository.state.value as? RecordingState.Active ?: run { stopSelf(); return }
        // Flush the spike-detector's buffered pending point (the last GPS fix is always held
        // back by one cycle to enable spike detection; release it now that recording is done).
        smartTrackProcessor.flushPending()?.let { pendingPoints.add(it) }
        // Queue any remaining in-memory points through the serialized write scope.
        if (pendingPoints.isNotEmpty()) {
            val toFlush = pendingPoints.toList()
            pendingPoints.clear()
            recordingRepository.persistPoints(current.routeId, toFlush)
            flushedPointCount += toFlush.size
        }
        // Block until every previously queued async write has finished.  This must happen
        // before finalizeStop renames the recording folder; otherwise in-flight writes
        // targeting the old path will fail silently and their points will be lost.
        recordingRepository.awaitPendingWrites()
        // Apply post-recording smoothing: gentle 3-point Gaussian pass that is gap-aware
        // and curvature-aware.  Works on the now-complete file; writes atomically.
        runCatching { recordingRepository.smoothTrack(current.routeId) }
        val now = System.currentTimeMillis()
        val durationSec = current.elapsedMs(now) / 1000L
        recordingRepository.finalizeStop(current.routeId, current.distanceMeters, durationSec)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val batterySaver = pm.isPowerSaveMode
        val intervalMs = if (batterySaver) BATTERY_SAVER_INTERVAL_MS else NORMAL_INTERVAL_MS
        val minDist = if (batterySaver) BATTERY_SAVER_MIN_DIST_M else NORMAL_MIN_DIST_M

        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // Only register GPS_PROVIDER: Network/WiFi positioning carries accuracy claims that
        // pass the 50 m gate but can still be 100+ m off, injecting chaotic fixes into the
        // pipeline.  The GPS chip alone gives sufficient quality and consistency.
        runCatching {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, intervalMs, minDist,
                    locationListener, Looper.getMainLooper()
                )
            }
        }

        if (batterySaver) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val st = recordingRepository.state.value as? RecordingState.Active
            nm.notify(NOTIF_ID, buildNotification("Recording (battery saver)", st?.autoName ?: "Reduced GPS accuracy"))
        }
    }

    private fun stopLocationUpdates() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        runCatching { lm.removeUpdates(locationListener) }
    }

    private fun onNewLocation(location: Location) {
        val current = recordingRepository.state.value as? RecordingState.Active ?: return
        if (current.isPaused) return

        // Pre-filter 1: reject inaccurate fixes before entering the pipeline
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_METERS) return

        // Pre-filter 2: warm-up — discard fixes until the GPS chip has had time to stabilise
        if (System.currentTimeMillis() - recordingStartedAtMs < WARMUP_DURATION_MS) return

        // Post-warmup settling: discard the first few fixes after warmup so the spike
        // detector accumulates enough history before committing any points.
        if (postWarmupSettleCount < POST_WARMUP_SETTLE_FIXES) {
            postWarmupSettleCount++
            return
        }

        val nowMs = System.currentTimeMillis()

        // Pre-filter 3: motion-state gate (speed-based stationary detection).
        // Evaluated before the min-movement positional dedup so every fix contributes
        // to the speed estimate, regardless of whether it passes the position filter.
        if (motionEstimator.update(location, nowMs) == MotionState.STATIONARY) return

        val last = lastLocation
        val rawDist = if (last != null)
            haversineMeters(last.latitude, last.longitude, location.latitude, location.longitude)
        else 0.0

        // Pre-filter 4: reject micro-jitter based on raw GPS displacement
        if (last != null && rawDist < MIN_MOVEMENT_METERS) return

        // Pre-filter 5: reject physically impossible speed jumps.
        // Use actual wall-clock time between onNewLocation calls — NOT elapsedRealtimeNanos,
        // which can be stale (negative or inflated) for NETWORK provider fixes, silently
        // bypassing the check and allowing multi-km teleports through.
        if (last != null) {
            val dtSec = (nowMs - lastLocationAcceptedMs) / 1000.0
            if (dtSec > 0.0 && rawDist / dtSec > MAX_SPEED_MPS) return
        }

        lastLocation = location
        lastLocationAcceptedMs = nowMs

        // Non-blocking: trigger OSM tile fetch for the current position if not cached.
        osmRoadCache.ensureLoaded(location.latitude, location.longitude)

        // Compute the best available speed estimate to thread through the pipeline.
        // Prefer the GPS Doppler speed; fall back to displacement / elapsed time.
        val speedMps: Float = motionEstimator.reliableSpeed(location)
            ?: if (last != null && lastLocationAcceptedMs > 0L) {
                val dtSec = (nowMs - lastLocationAcceptedMs).coerceAtLeast(1L) / 1000.0
                (rawDist / dtSec).toFloat()
            } else 0f

        scope.launch {
            // Run Kalman → road snap → mode hysteresis → spike detector on the captured location.
            // Returns null when the fix is being buffered (first fix) or was discarded.
            val point = smartTrackProcessor.process(location, speedMps) ?: return@launch

            // Accumulate distance from the last emitted (smoothed/snapped) point
            val prevEmit = lastEmittedPoint
            val addedDistance = if (prevEmit != null)
                haversineMeters(prevEmit.lat, prevEmit.lng, point.lat, point.lng)
            else 0.0

            // Deduplicate: skip points within 0.5 m of the last emitted point
            // (e.g. GPS_PROVIDER and NETWORK_PROVIDER both delivering the same cached fix).
            if (prevEmit != null && addedDistance < 0.5) return@launch

            lastEmittedPoint = point

            // Re-read state; it may have changed while awaiting the road query
            val st = recordingRepository.state.value as? RecordingState.Active ?: return@launch
            if (st.isPaused) return@launch

            pendingPoints.add(point)
            val newPoints = st.points + point
            val newDistance = st.distanceMeters + addedDistance
            recordingRepository.updateState(st.copy(points = newPoints, distanceMeters = newDistance))

            if (pendingPoints.size >= 20) {
                flushPendingPoints(st.routeId)
            }
        }
    }

    private fun flushPendingPoints(routeId: String) {
        if (pendingPoints.isEmpty()) return
        val toFlush = pendingPoints.toList()
        pendingPoints.clear()
        recordingRepository.persistPoints(routeId, toFlush)
        flushedPointCount += toFlush.size

        // Checkpoint distance and duration to disk so they survive a force-kill.
        val st = recordingRepository.state.value as? RecordingState.Active ?: return
        val durationSec = st.elapsedMs(System.currentTimeMillis()) / 1000L
        recordingRepository.checkpointRoute(routeId, st.distanceMeters, durationSec)
    }

    private fun resetMotionState() {
        postWarmupSettleCount = 0
        motionEstimator.reset()
    }

    private fun startNotificationTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                delay(1000L)
                val st = recordingRepository.state.value
                if (st !is RecordingState.Active) break
                val elapsed = formatElapsed(st.elapsedMs(System.currentTimeMillis()))
                val dist = formatDistance(st.distanceMeters)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID, buildNotification("Recording: $elapsed", dist))
            }
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun formatElapsed(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    private fun formatDistance(meters: Double): String =
        if (meters >= 1000) "%.2f km".format(meters / 1000) else "%.0f m".format(meters)

    override fun onDestroy() {
        tickerJob?.cancel()
        super.onDestroy()
    }
}
