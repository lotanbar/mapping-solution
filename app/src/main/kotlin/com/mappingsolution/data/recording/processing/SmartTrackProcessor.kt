package com.mappingsolution.data.recording.processing

import android.location.Location
import com.mappingsolution.data.recording.RecordingPoint
import com.mappingsolution.data.recording.SmoothedSample
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of processing one GPS fix.
 *
 * @param committed Matched track points the online matcher is now confident about. These are the
 *                  authoritative live positions to persist to `points.jsonl`, accumulate distance
 *                  from, and draw as the solid recorded line. Usually 0 or 1, occasionally more.
 * @param smoothed  The raw Kalman-smoothed sample for this fix (null if the fix was rejected by
 *                  the jump guard). Persisted to `smoothed.jsonl` — including the GPS heading,
 *                  speed and accuracy — so the Stop pass can re-match the whole trip from clean
 *                  input *with the same observation features the live matcher uses*, and to
 *                  survive a force-kill.
 * @param head      The provisional live "tip" to draw ahead of [committed] so the on-screen line
 *                  reaches the user's current position while the matcher waits to commit. Not
 *                  persisted. Null when there is no meaningful tip (rejected fix / snap disabled).
 */
data class ProcessResult(
    val committed: List<RecordingPoint>,
    val smoothed: SmoothedSample?,
    val head: RecordingPoint?,
)

/**
 * Orchestrates the smart-track pipeline for each GPS fix:
 *
 *   Raw GPS fix
 *     → [GpsKalmanFilter]      — removes high-frequency noise; process noise scales with speed
 *     → post-Kalman jump guard — discards fixes that land impossibly far from the previous
 *                                smoothed position (threshold is velocity-adaptive)
 *     → [OnlineMapMatcher]     — streaming HMM/Viterbi map-matching against the OSM road graph
 *                                from [OsmRoadCache]; commits points with a few-fix lag so the
 *                                live line follows the correct road through intersections.
 *                                Disabled above [HIGH_SPEED_SNAP_DISABLE_MPS].
 *
 * The greedy per-point `RoadSnapper`/`TrackModeManager` approach this replaces could not reason
 * about road topology or sequence, which structurally produced the square turns, bumps, and
 * intersection jumps that map-matching eliminates.
 *
 * The authoritative final geometry is produced by the full [MapMatcher] pass run on stop over the
 * persisted `smoothed.jsonl`; live commits here are provisional.
 *
 * Accuracy and min-movement pre-filters are applied upstream in `RecordingService`.
 * Drive [process]/[flush] from a single coroutine — the matcher is not thread-safe.
 */
@Singleton
class SmartTrackProcessor @Inject constructor(private val osmRoadCache: OsmRoadCache) {

    private val kalmanFilter = GpsKalmanFilter()
    private val onlineMatcher = OnlineMapMatcher()

    /** Last Kalman-smoothed position, for the post-Kalman jump guard. */
    private var prevSmoothed: Pair<Double, Double>? = null

    /**
     * High-speed map-matching disable state (hysteresis): disabled above
     * [HIGH_SPEED_SNAP_DISABLE_MPS], re-enabled only below [HIGH_SPEED_SNAP_RESUME_MPS].
     * At those speeds raw GPS is already accurate and tile loading can't keep up.
     */
    private var snapDisabled = false

    /** Reset all stateful components. Call when a new recording is started. */
    fun reset() {
        kalmanFilter.reset()
        onlineMatcher.reset()
        prevSmoothed = null
        snapDisabled = false
    }

    /**
     * Force-commit the matcher's remaining window when recording stops or pauses.
     * Must be called from the same coroutine context as [process].
     */
    fun flush(): List<RecordingPoint> =
        onlineMatcher.flush().map { RecordingPoint(ts = it.ts, lat = it.lat, lng = it.lng) }

    /**
     * Process one GPS fix through the pipeline.
     *
     * @param speedMps Current speed estimate (m/s). Drives adaptive Kalman noise, jump-guard
     *                 sizing, the bearing prior, and the high-speed disable.
     */
    fun process(location: Location, speedMps: Float = 0f): ProcessResult {
        val accuracy = if (location.hasAccuracy()) location.accuracy else MAX_ACCURACY_FALLBACK
        val nowMs = System.currentTimeMillis()

        val (smoothLat, smoothLng) = kalmanFilter.process(
            lat = location.latitude,
            lng = location.longitude,
            accuracyMeters = accuracy,
            timestampMs = nowMs,
            speedMps = speedMps,
        )

        // Velocity-adaptive post-Kalman jump guard: discard fixes that teleport implausibly far
        // (would poison the filter). Base 120 m at rest, growing with speed.
        val prev = prevSmoothed
        if (prev != null) {
            val maxKalmanJump = maxOf(
                MAX_POST_KALMAN_JUMP_BASE_METERS,
                (speedMps * EXPECTED_GPS_INTERVAL_SEC * JUMP_SAFETY_FACTOR).toDouble(),
            )
            if (haversineMeters(prev.first, prev.second, smoothLat, smoothLng) > maxKalmanJump) {
                kalmanFilter.reset()
                return ProcessResult(emptyList(), smoothed = null, head = null)
            }
        }
        prevSmoothed = smoothLat to smoothLng
        val smoothedPoint = RecordingPoint(ts = nowMs, lat = smoothLat, lng = smoothLng)
        // Carry the GPS heading/speed/accuracy alongside the smoothed position so the Stop pass
        // re-matches with the same observation features the live matcher uses (bearing/speed are
        // the strongest cues for which road and which turn at a junction).
        val smoothedSample = SmoothedSample(
            ts = nowMs,
            lat = smoothLat,
            lng = smoothLng,
            bearingDeg = if (location.hasBearing()) location.bearing.toDouble() else null,
            speedMps = speedMps.toDouble(),
            accuracyMeters = accuracy.toDouble(),
        )

        // High-speed disable with hysteresis.
        if (speedMps > HIGH_SPEED_SNAP_DISABLE_MPS) snapDisabled = true
        else if (speedMps < HIGH_SPEED_SNAP_RESUME_MPS) snapDisabled = false

        if (snapDisabled) {
            // Flush any in-flight matched window, then commit the smoothed point directly.
            val committed = flush().toMutableList()
            committed.add(smoothedPoint)
            return ProcessResult(committed, smoothed = smoothedSample, head = null)
        }

        val obs = MatchObservation(
            ts = nowMs,
            lat = smoothLat,
            lng = smoothLng,
            accuracyMeters = accuracy.toDouble(),
            bearingDeg = if (location.hasBearing()) location.bearing.toDouble() else null,
            speedMps = speedMps.toDouble(),
        )
        val graph = osmRoadCache.graphAround(smoothLat, smoothLng)
        val committed = onlineMatcher.add(obs, graph)
            .map { RecordingPoint(ts = it.ts, lat = it.lat, lng = it.lng) }

        return ProcessResult(committed, smoothed = smoothedSample, head = smoothedPoint)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val sinDLat = Math.sin(dLat / 2)
        val sinDLon = Math.sin(dLon / 2)
        val a = sinDLat * sinDLat +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinDLon * sinDLon
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    private companion object {
        const val MAX_ACCURACY_FALLBACK = 20f

        /** Base maximum plausible Kalman jump at zero speed (metres). ~150 km/h × ~2.7 s. */
        const val MAX_POST_KALMAN_JUMP_BASE_METERS = 120.0

        /** Conservative expected GPS update interval used in the velocity-adaptive jump guard. */
        const val EXPECTED_GPS_INTERVAL_SEC = 3.0f

        /** Safety multiplier applied on top of expected movement distance. */
        const val JUMP_SAFETY_FACTOR = 2.0f

        /** Speed above which map-matching is disabled (m/s). 144 km/h. */
        const val HIGH_SPEED_SNAP_DISABLE_MPS = 40.0f

        /** Speed below which map-matching is re-enabled after being disabled (m/s). 108 km/h. */
        const val HIGH_SPEED_SNAP_RESUME_MPS = 30.0f
    }
}
