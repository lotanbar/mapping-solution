package com.mappingsolution.data.recording.processing

/**
 * Pure-Kotlin 2D Kalman filter for GPS track smoothing.
 *
 * Two independent 1-D filters (one per axis) each track
 * state = [position, velocity]. Measurement noise R is set
 * dynamically from Location.accuracy so a shakier fix is trusted less.
 *
 * Process noise is **speed-adaptive**: at low speeds (walking) the filter is tight
 * and smooth; at higher speeds the noise is scaled up so the filter tracks the
 * rapidly-changing position without lag-induced corner-cutting.
 */
class GpsKalmanFilter {

    // Base process noise variances (degrees² and (deg/s)²) — tuned for walking speed.
    private val PROCESS_NOISE_POS = 1e-8
    private val PROCESS_NOISE_VEL = 1e-6

    /**
     * Speed at which the process noise starts scaling (m/s).
     * Below this speed the base noise values are used unchanged (scale = 1×).
     */
    private val NOISE_SCALE_REFERENCE_SPEED = 3.0   // m/s — brisk walk / slow jog

    /** Maximum noise multiplier cap (reached at reference × [MAX_NOISE_SCALE] m/s). */
    private val MAX_NOISE_SCALE = 16.0               // hit at 48 m/s ≈ 173 km/h

    // Per-axis state: x[0] = position (deg), x[1] = velocity (deg/s)
    private val latX = doubleArrayOf(0.0, 0.0)
    private val latP = Array(2) { r -> DoubleArray(2) { c -> if (r == c) 1.0 else 0.0 } }

    private val lngX = doubleArrayOf(0.0, 0.0)
    private val lngP = Array(2) { r -> DoubleArray(2) { c -> if (r == c) 1.0 else 0.0 } }

    private var lastTimestampMs = 0L
    private var initialized = false

    fun reset() {
        initialized = false
        lastTimestampMs = 0L
    }

    /**
     * Feed a raw GPS fix and get back the Kalman-smoothed position.
     *
     * @param accuracyMeters  Location.accuracy (1-sigma, metres)
     * @param timestampMs     System.currentTimeMillis() at fix time
     * @param speedMps        Current speed estimate (m/s). Used to scale process noise so
     *                        the filter is tight at walking speed and responsive at highway speed.
     */
    fun process(
        lat: Double,
        lng: Double,
        accuracyMeters: Float,
        timestampMs: Long,
        speedMps: Float = 0f,
    ): Pair<Double, Double> {
        if (!initialized) {
            latX[0] = lat; latX[1] = 0.0
            lngX[0] = lng; lngX[1] = 0.0
            latP[0][0] = 1.0; latP[0][1] = 0.0; latP[1][0] = 0.0; latP[1][1] = 1.0
            lngP[0][0] = 1.0; lngP[0][1] = 0.0; lngP[1][0] = 0.0; lngP[1][1] = 1.0
            lastTimestampMs = timestampMs
            initialized = true
            return lat to lng
        }

        val dt = ((timestampMs - lastTimestampMs) / 1000.0).coerceIn(0.5, 30.0)
        lastTimestampMs = timestampMs

        // Scale process noise with speed: the filter is tight at walking pace and
        // progressively more responsive as the user accelerates, preventing the lag
        // and corner-cutting that occur at highway and aviation speeds.
        val noiseScale = (speedMps / NOISE_SCALE_REFERENCE_SPEED).coerceIn(1.0, MAX_NOISE_SCALE)
        val effectivePosNoise = PROCESS_NOISE_POS * noiseScale
        val effectiveVelNoise = PROCESS_NOISE_VEL * noiseScale

        // Convert accuracy from metres to degrees, then square for variance
        val accuracyDeg = accuracyMeters / 111_111.0
        val r = accuracyDeg * accuracyDeg

        val smoothLat = updateAxis(latX, latP, lat, dt, r, effectivePosNoise, effectiveVelNoise)
        val smoothLng = updateAxis(lngX, lngP, lng, dt, r, effectivePosNoise, effectiveVelNoise)

        return smoothLat to smoothLng
    }

    /**
     * One step of the 1-D Kalman filter with state [pos, vel].
     * Mutates [x] and [p] in place, returns the updated position.
     *
     * State-transition F = [[1, dt], [0, 1]]
     * Measurement matrix H = [1, 0]
     */
    private fun updateAxis(
        x: DoubleArray,
        p: Array<DoubleArray>,
        z: Double,
        dt: Double,
        r: Double,
        posNoise: Double,
        velNoise: Double,
    ): Double {
        // Predict
        val predPos = x[0] + x[1] * dt
        val predVel = x[1]

        val pp00 = p[0][0] + dt * (p[1][0] + p[0][1]) + dt * dt * p[1][1] + posNoise
        val pp01 = p[0][1] + dt * p[1][1]
        val pp10 = p[1][0] + dt * p[1][1]
        val pp11 = p[1][1] + velNoise

        // Update
        val s = pp00 + r           // innovation covariance
        val k0 = pp00 / s          // Kalman gain for position
        val k1 = pp10 / s          // Kalman gain for velocity
        val innov = z - predPos

        x[0] = predPos + k0 * innov
        x[1] = predVel + k1 * innov

        p[0][0] = (1.0 - k0) * pp00
        p[0][1] = (1.0 - k0) * pp01
        p[1][0] = pp10 - k1 * pp00
        p[1][1] = pp11 - k1 * pp01

        return x[0]
    }
}
