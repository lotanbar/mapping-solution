package com.mappingsolution.data.recording.processing

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Post-recording track smoother.
 *
 * Applies a conservative 3-point Gaussian-weighted average to reduce residual lateral jitter
 * without distorting the shape of the route.
 *
 * Three guards prevent geometry corruption:
 *
 *  - **On-road guard** : never blends a point the map-matcher snapped onto a road. Those already
 *                        lie on the OSM road centreline; averaging them with neighbours only pulls
 *                        them *off* the road on gentle bends (the "bumps"). Smoothing is therefore
 *                        limited to off-road passthrough stretches (trails, parking, GPS outages).
 *  - **Gap guard**     : never blends across a stationary suppression gap
 *                        (consecutive point timestamps more than [MAX_GAP_MS] apart).
 *  - **Curvature guard**: skips any point where the incoming/outgoing bearing change
 *                        exceeds [MAX_TURN_DEG], preserving turns and switchbacks as recorded.
 *
 * Only lat/lng are modified; timestamps and the on-road flag are preserved unchanged.
 * The caller is responsible for atomic file replacement (write to .tmp, then rename).
 */
object TrackSmoother {

    /** Weights for the 3-point kernel [prev, centre, next]. Must sum to 1. */
    private val KERNEL = doubleArrayOf(0.25, 0.50, 0.25)

    /**
     * Maximum time gap between consecutive stored points across which smoothing
     * is applied.  Larger gaps indicate a stationary suppression period and should
     * not be bridged.
     */
    private const val MAX_GAP_MS = 30_000L

    /**
     * Maximum bearing change at the centre point (degrees) for smoothing to apply.
     * Points at sharper turns are left untouched.
     */
    private const val MAX_TURN_DEG = 30.0

    /**
     * Returns a new list with lat/lng smoothed.  The input list is not mutated.
     * The first and last points are always returned as-is (no boundary artefacts).
     */
    fun smooth(points: List<MatchedPoint>): List<MatchedPoint> {
        if (points.size < 3) return points
        val out = points.toMutableList()
        for (i in 1 until points.size - 1) {
            val prev = points[i - 1]
            val curr = points[i]
            val next = points[i + 1]

            // On-road guard: matched points already sit on the road centreline — leave them be.
            if (curr.onRoad) continue

            // Gap guard
            if (curr.ts - prev.ts > MAX_GAP_MS || next.ts - curr.ts > MAX_GAP_MS) continue

            // Curvature guard
            val brngIn  = bearing(prev.lat, prev.lng, curr.lat, curr.lng)
            val brngOut = bearing(curr.lat, curr.lng, next.lat, next.lng)
            if (angleDiff(brngIn, brngOut) > MAX_TURN_DEG) continue

            out[i] = curr.copy(
                lat = KERNEL[0] * prev.lat + KERNEL[1] * curr.lat + KERNEL[2] * next.lat,
                lng = KERNEL[0] * prev.lng + KERNEL[1] * curr.lng + KERNEL[2] * next.lng,
            )
        }
        return out
    }

    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val x = sin(dLon) * cos(Math.toRadians(lat2))
        val y = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
        return (Math.toDegrees(atan2(x, y)) + 360) % 360
    }

    private fun angleDiff(a: Double, b: Double): Double {
        var d = Math.abs(a - b) % 360.0
        if (d > 180.0) d = 360.0 - d
        return d
    }
}
