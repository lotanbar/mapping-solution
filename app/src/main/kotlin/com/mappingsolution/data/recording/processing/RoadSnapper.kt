package com.mappingsolution.data.recording.processing

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Result of a successful road snap.
 * @param lat       Snapped latitude.
 * @param lng       Snapped longitude.
 * @param highway   OSM highway tag of the road that was snapped to (e.g. "trunk", "service").
 */
data class SnapResult(val lat: Double, val lng: Double, val highway: String)

/**
 * Snaps a GPS position to the nearest road from a pre-fetched [OsmRoadWay] list.
 *
 * Road geometry comes from [OsmRoadCache], which fetches it from the Overpass API
 * and caches it locally. This allows road snapping to continue when the map screen
 * is off (unlike the old `queryRenderedFeatures` approach which required the map view
 * to be on screen).
 */
class RoadSnapper {

    companion object {
        /** Maximum distance from smoothed position to accept a road snap (metres). */
        const val SNAP_RADIUS_METERS = 25.0

        /**
         * Base bearing-penalty at the reference speed ([BEARING_REFERENCE_SPEED_MPS]).
         * Roads running perpendicular to travel at that speed are penalised by this amount.
         */
        private const val BEARING_WEIGHT_METERS = 6.0

        /**
         * Speed at which the base bearing weight applies (m/s).
         * Below [MotionStateEstimator.MIN_BEARING_SPEED_MPS] the bearing is ignored entirely.
         * Above this reference speed the penalty scales up linearly, capped at
         * [MAX_BEARING_SCALE] × [BEARING_WEIGHT_METERS].
         */
        private const val BEARING_REFERENCE_SPEED_MPS = 15.0   // city driving

        /** Maximum bearing-weight multiplier (reached at ~37 m/s = 135 km/h). */
        private const val MAX_BEARING_SCALE = 2.5

        /**
         * Above this speed (36 km/h), pedestrian-only infrastructure — footways, steps,
         * pedestrian streets, cycleways — is hard-excluded from snap candidates.
         * It is physically impossible to travel at car speed on a staircase or footpath.
         */
        private const val EXCLUDE_PEDESTRIAN_ABOVE_MPS = 10.0f  // 36 km/h

        /**
         * Above this speed (54 km/h), off-road / slow surfaces (path, track) are also
         * hard-excluded. These roads top out well below motorway speeds.
         */
        private const val EXCLUDE_PATH_ABOVE_MPS = 15.0f  // 54 km/h

        /**
         * Integer class ranking for OSM highway types; lower = higher-class road.
         * Used by [SmartTrackProcessor] to detect implausible road-class downgrades.
         */
        fun highwayClass(highway: String): Int = when (highway) {
            "motorway"                                              -> 0
            "trunk"                                                -> 1
            "motorway_link", "trunk_link"                          -> 2
            "primary", "primary_link"                              -> 3
            "secondary", "secondary_link"                          -> 4
            "tertiary", "tertiary_link"                            -> 5
            "unclassified", "residential", "road", "living_street" -> 6
            "service"                                              -> 7
            "track", "path", "cycleway"                            -> 8
            "footway", "pedestrian", "steps"                       -> 9
            else                                                   -> 6
        }
    }

    /**
     * Tries to project [smoothLat]/[smoothLng] onto the nearest road segment from [roads].
     *
     * [roads] is the list of OSM ways cached by [OsmRoadCache] for the current tile and its
     * 8 neighbours. This function is pure (no side effects, no thread requirements).
     *
     * @param speedMps          Current speed in m/s. Controls the bearing penalty strength
     *                          and hard type exclusions:
     *                          - Above [EXCLUDE_PEDESTRIAN_ABOVE_MPS] (36 km/h): footways,
     *                            steps, pedestrian streets, cycleways are excluded entirely.
     *                          - Above [EXCLUDE_PATH_ABOVE_MPS] (54 km/h): paths and tracks
     *                            are also excluded.
     * @param travelBearingDeg  GPS heading in degrees (0 = north). When provided, roads
     *                          running perpendicular to travel are penalised in the scoring.
     * @param previousLat       Last emitted track point latitude. When provided together with
     *                          [previousLng] and [maxAllowedJumpMeters], a snap that would
     *                          displace the track more than [maxAllowedJumpMeters] from the
     *                          previous point is rejected to prevent sudden teleports.
     * @param previousLng       Last emitted track point longitude.
     * @param maxAllowedJumpMeters  Maximum acceptable displacement from [previousLat]/[previousLng].
     *
     * @return  [SnapResult] with snapped position and matched highway type, or null if no
     *          suitable road is within [SNAP_RADIUS_METERS].
     */
    fun snap(
        smoothLat: Double,
        smoothLng: Double,
        roads: List<OsmRoadWay>,
        speedMps: Float = 0f,
        travelBearingDeg: Float? = null,
        previousLat: Double? = null,
        previousLng: Double? = null,
        maxAllowedJumpMeters: Double = SNAP_RADIUS_METERS,
    ): SnapResult? {
        if (roads.isEmpty()) return null

        // Walk every segment of every road way.
        // Each segment is scored as: geometric distance + bearing penalty.
        // The bearing penalty discourages snapping to roads running perpendicular
        // to the user's direction of travel (e.g. a cross-street or motorway ramp).
        var bestScore = Double.MAX_VALUE
        var bestDistMeters = Double.MAX_VALUE
        var bestLat = smoothLat
        var bestLng = smoothLng
        var bestHighway = ""

        for (way in roads) {
            // Hard exclusion: pedestrian infrastructure at car speed.
            // It is physically impossible to drive 36+ km/h on a staircase or footpath.
            val hw = way.highway
            if (speedMps >= EXCLUDE_PEDESTRIAN_ABOVE_MPS &&
                (hw == "footway" || hw == "pedestrian" || hw == "steps" || hw == "cycleway")
            ) continue
            if (speedMps >= EXCLUDE_PATH_ABOVE_MPS &&
                (hw == "path" || hw == "track")
            ) continue

            val pts = way.points
            for (i in 0 until pts.size - 1) {
                val (aLat, aLng) = pts[i]
                val (bLat, bLng) = pts[i + 1]
                val (cLat, cLng) = closestPointOnSegment(
                    smoothLat, smoothLng, aLat, aLng, bLat, bLng,
                )
                val dist = haversineMeters(smoothLat, smoothLng, cLat, cLng)

                // Speed-adaptive bearing penalty:
                //   • below MIN_BEARING_SPEED_MPS: GPS heading is unreliable → no penalty
                //   • at reference speed (city): base BEARING_WEIGHT_METERS
                //   • at highway/aviation speed: penalty scales up to MAX_BEARING_SCALE×
                //     so the snapper strongly prefers roads aligned with the direction of travel
                val bearingPenalty = if (travelBearingDeg != null &&
                                         speedMps >= MotionStateEstimator.MIN_BEARING_SPEED_MPS) {
                    val bearingScale = (speedMps / BEARING_REFERENCE_SPEED_MPS)
                        .coerceIn(0.0, MAX_BEARING_SCALE)
                    val effectiveWeight = BEARING_WEIGHT_METERS * bearingScale
                    val segBear = segmentBearing(aLat, aLng, bLat, bLng)
                    val perp = bearingPerpendicularity(travelBearingDeg.toDouble(), segBear)
                    if (perp > 45.0) ((perp - 45.0) / 45.0) * effectiveWeight else 0.0
                } else 0.0

                val score = dist + bearingPenalty
                if (score < bestScore) {
                    bestScore = score
                    bestDistMeters = dist
                    bestLat = cLat
                    bestLng = cLng
                    bestHighway = hw
                }
            }
        }

        if (bestDistMeters > SNAP_RADIUS_METERS) return null

        // Max-jump guard: reject snaps that would teleport the track far beyond
        // where the user actually moved this step (e.g. snapping to an overhead
        // motorway whose 2-D geometry overlaps a street below).
        if (previousLat != null && previousLng != null) {
            val jumpDist = haversineMeters(previousLat, previousLng, bestLat, bestLng)
            if (jumpDist > maxAllowedJumpMeters) return null
        }

        return SnapResult(lat = bestLat, lng = bestLng, highway = bestHighway)
    }

    /** Projects point P onto segment AB, clamping to the segment endpoints. */
    private fun closestPointOnSegment(
        pLat: Double, pLng: Double,
        aLat: Double, aLng: Double,
        bLat: Double, bLng: Double,
    ): Pair<Double, Double> {
        val abLat = bLat - aLat
        val abLng = bLng - aLng
        val dot = abLat * abLat + abLng * abLng
        if (dot == 0.0) return aLat to aLng  // degenerate zero-length segment
        val t = ((pLat - aLat) * abLat + (pLng - aLng) * abLng).div(dot).coerceIn(0.0, 1.0)
        return (aLat + t * abLat) to (aLng + t * abLng)
    }

    /**
     * Compass bearing of segment A→B in degrees [0, 360).
     */
    private fun segmentBearing(
        aLat: Double, aLng: Double,
        bLat: Double, bLng: Double,
    ): Double {
        val dLon = Math.toRadians(bLng - aLng)
        val x = sin(dLon) * cos(Math.toRadians(bLat))
        val y = cos(Math.toRadians(aLat)) * sin(Math.toRadians(bLat)) -
                sin(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * cos(dLon)
        return (Math.toDegrees(atan2(x, y)) + 360) % 360
    }

    /**
     * Returns the perpendicularity [0, 90] between a travel bearing and a road segment bearing.
     * 0 = road runs parallel to travel (best); 90 = road runs perpendicular (worst).
     * Roads are bidirectional, so a 180° flip is treated as 0° difference.
     */
    private fun bearingPerpendicularity(travelBear: Double, segBear: Double): Double {
        var diff = abs(travelBear - segBear) % 360.0
        if (diff > 180.0) diff = 360.0 - diff   // normalise to [0, 180]
        return min(diff, 180.0 - diff)            // bidirectional: [0, 90]
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
