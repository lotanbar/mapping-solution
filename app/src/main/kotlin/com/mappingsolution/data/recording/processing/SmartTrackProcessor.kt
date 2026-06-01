package com.mappingsolution.data.recording.processing

import android.location.Location
import com.mappingsolution.data.recording.RecordingPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Orchestrates the full smart-track pipeline for each GPS fix:
 *
 *   Raw GPS fix
 *     → [GpsKalmanFilter]      — removes high-frequency noise; process noise scales with speed
 *     → post-Kalman jump guard — discards fixes that land impossibly far from the previous
 *                                processed position (threshold is velocity-adaptive)
 *     → [RoadSnapper]          — uses OSM road geometry from [OsmRoadCache];
 *                                disabled entirely above [HIGH_SPEED_SNAP_DISABLE_MPS]
 *     → [TrackModeManager]     — hysteresis: require 3 consecutive snapped fixes before
 *                                committing to road mode
 *     → spike detector         — buffers 1 fix of delay; if the previous fix jumped out and
 *                                back (ping-pong), it is replaced with a hold at the last
 *                                stable position
 *     → emit [RecordingPoint]  — null means the fix was buffered/discarded; call [flushPending]
 *                                on stop
 *
 * Accuracy and min-movement pre-filters are applied upstream in [RecordingService].
 */
@Singleton
class SmartTrackProcessor @Inject constructor(private val osmRoadCache: OsmRoadCache) {

    private val kalmanFilter = GpsKalmanFilter()
    private val roadSnapper = RoadSnapper()
    private val modeManager = TrackModeManager()

    /**
     * The last position returned to the caller (used by the spike detector to detect returns).
     * Updated only when we actually emit a non-held point.
     */
    private var lastEmittedLatLng: Pair<Double, Double>? = null

    /**
     * The position computed on the PREVIOUS call, buffered for spike-detection.
     * We delay emission by one fix so the spike detector can compare prev→B→current.
     */
    private var pendingLatLng: Pair<Double, Double>? = null
    private var pendingTs: Long = 0L

    /** Last road-snap position accepted by the continuity guard. Null when off-road. */
    private var lastSnappedLatLng: Pair<Double, Double>? = null

    /**
     * High-speed road-snap disable state (hysteresis).
     * Snap is disabled when speed exceeds [HIGH_SPEED_SNAP_DISABLE_MPS] and re-enabled
     * only once speed drops below [HIGH_SPEED_SNAP_RESUME_MPS], preventing oscillation
     * near the threshold.
     */
    private var snapDisabled = false

    /** Reset all stateful components. Call when a new recording is started. */
    fun reset() {
        kalmanFilter.reset()
        modeManager.reset()
        lastEmittedLatLng = null
        pendingLatLng = null
        pendingTs = 0L
        lastSnappedLatLng = null
        snapDisabled = false
    }

    /**
     * Flush the last buffered (pending) point when recording stops.
     * Must be called from the same coroutine context as [process].
     */
    fun flushPending(): RecordingPoint? {
        val pending = pendingLatLng ?: return null
        val ts = pendingTs
        pendingLatLng = null
        lastEmittedLatLng = pending
        return RecordingPoint(ts = ts, lat = pending.first, lng = pending.second)
    }

    /**
     * Process one GPS fix through the full pipeline.
     *
     * @param speedMps  Current speed estimate in m/s. Drives adaptive Kalman noise,
     *                  bearing penalty scaling, jump guard sizing, and high-speed snap disable.
     *
     * Returns a [RecordingPoint] to emit, or **null** if this fix was buffered or discarded.
     * Null means: do nothing this cycle; the position will appear on the next call (or at stop
     * via [flushPending]).
     */
    suspend fun process(location: Location, speedMps: Float = 0f): RecordingPoint? {
        val accuracy = if (location.hasAccuracy()) location.accuracy else MAX_ACCURACY_FALLBACK
        val nowMs = System.currentTimeMillis()

        val (smoothLat, smoothLng) = kalmanFilter.process(
            lat = location.latitude,
            lng = location.longitude,
            accuracyMeters = accuracy,
            timestampMs = nowMs,
            speedMps = speedMps,
        )

        // Velocity-adaptive post-Kalman jump guard.
        // At low speed the guard stays at the base 120 m limit; at high speed it grows
        // proportionally so legitimate movement at highway / aviation speeds is not discarded.
        // Formula: max(BASE, speed × EXPECTED_INTERVAL_SEC × JUMP_SAFETY_FACTOR)
        val prevProcessed = pendingLatLng
        if (prevProcessed != null) {
            val maxKalmanJump = maxOf(
                MAX_POST_KALMAN_JUMP_BASE_METERS,
                (speedMps * EXPECTED_GPS_INTERVAL_SEC * JUMP_SAFETY_FACTOR).toDouble()
            )
            val smoothDist = haversineMeters(prevProcessed.first, prevProcessed.second, smoothLat, smoothLng)
            if (smoothDist > maxKalmanJump) {
                kalmanFilter.reset()
                return null
            }
        }

        // Travel bearing from the device (degrees, 0 = north). Available when moving.
        val travelBearing: Float? = if (location.hasBearing()) location.bearing else null

        val maxJumpMeters: Double = if (prevProcessed != null) {
            val rawMove = haversineMeters(prevProcessed.first, prevProcessed.second, smoothLat, smoothLng)
            max(rawMove * MAX_JUMP_MULTIPLIER, MIN_JUMP_FLOOR_METERS)
        } else {
            RoadSnapper.SNAP_RADIUS_METERS
        }

        // High-speed snap disable with hysteresis:
        //   enter disabled mode at HIGH_SPEED_SNAP_DISABLE_MPS (144 km/h)
        //   re-enable only once speed drops below HIGH_SPEED_SNAP_RESUME_MPS (108 km/h)
        if (speedMps > HIGH_SPEED_SNAP_DISABLE_MPS) snapDisabled = true
        else if (speedMps < HIGH_SPEED_SNAP_RESUME_MPS) snapDisabled = false

        val rawSnapped: Pair<Double, Double>? = if (!snapDisabled) {
            val roads = osmRoadCache.getRoadsSync(smoothLat, smoothLng)
            if (roads.isNotEmpty()) {
                runCatching {
                    roadSnapper.snap(
                        smoothLat = smoothLat,
                        smoothLng = smoothLng,
                        roads = roads,
                        speedMps = speedMps,
                        travelBearingDeg = travelBearing,
                        previousLat = prevProcessed?.first,
                        previousLng = prevProcessed?.second,
                        maxAllowedJumpMeters = maxJumpMeters,
                    )
                }.getOrNull()
            } else null
        } else null

        // Snap continuity guard: reject snaps that jump much further than the GPS actually
        // moved since the last accepted snap. Prevents oscillation between parallel road
        // features when the smoothed position sits equidistant between two roads.
        val snapped: Pair<Double, Double>? = if (rawSnapped != null) {
            val prevSnap = lastSnappedLatLng
            val prevProc = prevProcessed
            if (prevSnap != null && prevProc != null) {
                val snapJump = haversineMeters(prevSnap.first, prevSnap.second, rawSnapped.first, rawSnapped.second)
                val gpsMove = haversineMeters(prevProc.first, prevProc.second, smoothLat, smoothLng)
                if (snapJump > maxOf(gpsMove * SNAP_CONTINUITY_MULTIPLIER, SNAP_CONTINUITY_FLOOR_METERS)) null
                else rawSnapped
            } else rawSnapped
        } else null

        // Update hysteresis. Mode switches to ROAD only after ROAD_ENTER_COUNT consecutive
        // snapped fixes, preventing a single rogue snap from jumping the track.
        val mode = modeManager.onSnappedResult(snapped != null)

        // Track the last accepted snap; clear when off-road so a stale reference
        // doesn't constrain the first snap of the next road entry.
        if (snapped != null) lastSnappedLatLng = snapped
        else if (mode == TrackMode.OFF_ROAD) lastSnappedLatLng = null

        val (finalLat, finalLng) = if (mode == TrackMode.ROAD && snapped != null) {
            snapped
        } else {
            smoothLat to smoothLng
        }

        val currentLatLng = finalLat to finalLng

        // ── Spike detector (1-fix delay) ──────────────────────────────────────────────────────
        val toEmit: RecordingPoint?
        if (prevProcessed == null) {
            toEmit = null
        } else {
            val prevEmitted = lastEmittedLatLng
            val isSpike = prevEmitted != null && run {
                val dAB = haversineMeters(prevEmitted.first, prevEmitted.second, prevProcessed.first, prevProcessed.second)
                val dBC = haversineMeters(prevProcessed.first, prevProcessed.second, currentLatLng.first, currentLatLng.second)
                val dAC = haversineMeters(prevEmitted.first, prevEmitted.second, currentLatLng.first, currentLatLng.second)
                dAB > SPIKE_MIN_METERS && dBC > SPIKE_MIN_METERS &&
                        dAC < maxOf(dAB, dBC) * SPIKE_RETURN_RATIO
            }
            if (isSpike) {
                val held = prevEmitted!!
                toEmit = RecordingPoint(ts = pendingTs, lat = held.first, lng = held.second)
            } else {
                lastEmittedLatLng = prevProcessed
                toEmit = RecordingPoint(ts = pendingTs, lat = prevProcessed.first, lng = prevProcessed.second)
            }
        }

        pendingLatLng = currentLatLng
        pendingTs = nowMs

        return toEmit
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

        /** The snap destination must be within this multiplier × actual GPS movement. */
        const val MAX_JUMP_MULTIPLIER = 3.0

        /** Minimum floor for the road-snap jump guard. */
        const val MIN_JUMP_FLOOR_METERS = 15.0

        /**
         * Base maximum plausible Kalman jump at zero speed (metres).
         * Sized for 150 km/h × ~2.7 s average GPS interval = ~112 m.
         * At higher speeds the limit grows proportionally via [JUMP_SAFETY_FACTOR].
         */
        const val MAX_POST_KALMAN_JUMP_BASE_METERS = 120.0

        /**
         * Conservative expected GPS update interval used in the velocity-adaptive jump guard.
         * Set to 3 s (between the 2 s normal and 5 s battery-saver intervals) so the guard
         * is not too tight with a slightly delayed fix.
         */
        const val EXPECTED_GPS_INTERVAL_SEC = 3.0f

        /**
         * Safety multiplier applied on top of expected movement distance.
         * 2.0 means: allow up to 2× the expected distance at current speed before
         * calling a fix a Kalman-poisoning teleport.
         */
        const val JUMP_SAFETY_FACTOR = 2.0f

        /**
         * Speed above which road snapping is disabled (m/s). 144 km/h.
         * At this speed GPS is already accurate enough; snapping to tile geometry
         * would introduce false corrections and map-tile loading can't keep up.
         */
        const val HIGH_SPEED_SNAP_DISABLE_MPS = 40.0f

        /**
         * Speed below which road snapping is re-enabled after being disabled (m/s). 108 km/h.
         * Lower than [HIGH_SPEED_SNAP_DISABLE_MPS] to add hysteresis and prevent oscillation.
         */
        const val HIGH_SPEED_SNAP_RESUME_MPS = 30.0f

        const val SPIKE_MIN_METERS = 12.0
        const val SPIKE_RETURN_RATIO = 0.45

        const val SNAP_CONTINUITY_MULTIPLIER = 2.0
        const val SNAP_CONTINUITY_FLOOR_METERS = 15.0
    }
}
