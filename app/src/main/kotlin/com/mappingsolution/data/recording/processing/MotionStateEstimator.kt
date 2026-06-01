package com.mappingsolution.data.recording.processing

import android.location.Location
import android.os.Build
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class MotionState { STATIONARY, MOVING }

/**
 * Determines whether the user is stationary or moving.
 *
 * Primary signal  : [Location.speed] (GPS Doppler velocity, available on all modern devices).
 * Fallback signal : cumulative path-length over a rolling time window, used when GPS speed
 *                   is absent or its accuracy is too poor to trust.
 *
 * Hysteresis is **time-based**, not fix-count-based, so behaviour is consistent across
 * normal (2 s) and battery-saver (5 s) GPS update rates and across all vehicle speeds.
 *
 *   MOVING → STATIONARY : speed stays below [STOP_SPEED_MPS]  for [STOP_CONFIRM_MS].
 *   STATIONARY → MOVING : speed stays above [START_SPEED_MPS] for [START_CONFIRM_MS].
 *
 * The estimator starts in [MotionState.MOVING] so recording never misses the beginning
 * of a trip.
 */
class MotionStateEstimator {

    companion object {
        /** Speed below which the stationary countdown begins (m/s). 1.4 km/h. */
        const val STOP_SPEED_MPS = 0.4f
        /** Speed above which the moving countdown begins (m/s). 2.9 km/h — slow walk, car pulling away. */
        const val START_SPEED_MPS = 0.8f

        /** Sustained duration at low speed required to declare STATIONARY. */
        const val STOP_CONFIRM_MS = 8_000L
        /** Sustained duration at higher speed required to declare MOVING. */
        const val START_CONFIRM_MS = 3_000L

        /**
         * Max acceptable speed-measurement error (m/s). Speeds with a reported accuracy
         * worse than this are discarded and the fallback path-length method is used instead.
         * Available on API 26+; older devices fall back automatically.
         */
        private const val SPEED_ACCURACY_LIMIT_MPS = 2.0f

        /** Positional-accuracy gate above which GPS speed is considered unreliable. */
        private const val MAX_ACCURACY_FOR_SPEED_M = 30f

        /** Rolling window length for the fallback path-length method. */
        private const val FALLBACK_WINDOW_MS = 12_000L

        /** Path-length below which the device is called stationary in the fallback. */
        private const val FALLBACK_STOP_PATH_M = 4.0

        /** Path-length above which the device is called moving in the fallback. */
        private const val FALLBACK_START_PATH_M = 12.0

        /** Minimum distinct fixes before the fallback makes any decision. */
        private const val FALLBACK_MIN_FIXES = 4

        /**
         * Below this speed the GPS bearing is considered unreliable (the chip hasn't built
         * a valid velocity vector yet).  Road-snapping callers should not apply a bearing
         * penalty for speeds at or below this value.
         */
        const val MIN_BEARING_SPEED_MPS = 2.0f
    }

    var state: MotionState = MotionState.MOVING
        private set

    // --- Speed-based timers ---
    private var lowSpeedSinceMs = 0L
    private var highSpeedSinceMs = 0L

    // --- Fallback: rolling position window ---
    private data class TimedPos(val ts: Long, val lat: Double, val lng: Double)
    private val fallbackWindow = ArrayDeque<TimedPos>()

    fun reset() {
        state = MotionState.MOVING
        lowSpeedSinceMs = 0L
        highSpeedSinceMs = 0L
        fallbackWindow.clear()
    }

    /**
     * Evaluate a new GPS fix and return the updated [MotionState].
     *
     * Must be called for every fix that passes accuracy/warmup filters, even those
     * that will later be dropped by the min-movement gate — the speed signal is
     * independent of positional displacement.
     *
     * @param nowMs  wall-clock millis at time of fix ([System.currentTimeMillis])
     */
    fun update(location: Location, nowMs: Long): MotionState {
        val speed = reliableSpeed(location)
        if (speed != null) {
            evaluateSpeed(speed, nowMs)
        } else {
            evaluateFallback(location, nowMs)
        }
        return state
    }

    /**
     * Returns the GPS Doppler speed in m/s if it is considered reliable for this fix,
     * or null when the fallback path-length method should be used instead.
     */
    fun reliableSpeed(location: Location): Float? {
        if (!location.hasSpeed()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (location.hasSpeedAccuracy() &&
                location.speedAccuracyMetersPerSecond > SPEED_ACCURACY_LIMIT_MPS) return null
        }
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_FOR_SPEED_M) return null
        return location.speed
    }

    // ── private helpers ───────────────────────────────────────────────────────────────────────

    private fun evaluateSpeed(speed: Float, nowMs: Long) {
        when {
            speed < STOP_SPEED_MPS -> {
                if (lowSpeedSinceMs == 0L) lowSpeedSinceMs = nowMs
                highSpeedSinceMs = 0L
                if (state == MotionState.MOVING && nowMs - lowSpeedSinceMs >= STOP_CONFIRM_MS) {
                    state = MotionState.STATIONARY
                    lowSpeedSinceMs = 0L
                }
            }
            speed > START_SPEED_MPS -> {
                if (highSpeedSinceMs == 0L) highSpeedSinceMs = nowMs
                lowSpeedSinceMs = 0L
                if (state == MotionState.STATIONARY && nowMs - highSpeedSinceMs >= START_CONFIRM_MS) {
                    state = MotionState.MOVING
                    highSpeedSinceMs = 0L
                }
            }
            else -> {
                // Dead zone between STOP and START thresholds — preserve both timers so
                // a transient speed blip in the dead zone doesn't reset a confirmed trend.
            }
        }
    }

    private fun evaluateFallback(location: Location, nowMs: Long) {
        // Expire stale entries
        while (fallbackWindow.isNotEmpty() && nowMs - fallbackWindow.first().ts > FALLBACK_WINDOW_MS)
            fallbackWindow.removeFirst()
        fallbackWindow.addLast(TimedPos(nowMs, location.latitude, location.longitude))

        if (fallbackWindow.size < FALLBACK_MIN_FIXES) return

        var pathLength = 0.0
        for (i in 1 until fallbackWindow.size) {
            val a = fallbackWindow[i - 1]
            val b = fallbackWindow[i]
            pathLength += haversine(a.lat, a.lng, b.lat, b.lng)
        }

        when {
            pathLength < FALLBACK_STOP_PATH_M && state == MotionState.MOVING ->
                state = MotionState.STATIONARY
            pathLength > FALLBACK_START_PATH_M && state == MotionState.STATIONARY ->
                state = MotionState.MOVING
        }
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
