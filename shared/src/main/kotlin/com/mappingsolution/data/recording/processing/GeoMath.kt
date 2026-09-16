package com.mappingsolution.data.recording.processing

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Small, dependency-free geographic helpers shared by the map-matching components.
 *
 * Distances are in metres. For the short spans involved in projecting a GPS point onto a
 * road segment, a local equirectangular approximation (longitude scaled by cos(latitude))
 * is accurate to well under a metre and far cheaper than repeated haversine calls.
 */
internal object GeoMath {

    const val EARTH_RADIUS_M = 6_371_000.0
    private const val METERS_PER_DEG_LAT = 111_320.0

    /** Great-circle distance between two lat/lng points, in metres. */
    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).let { it * it }
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Compass bearing A→B in degrees [0, 360). */
    fun bearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLon = Math.toRadians(lng2 - lng1)
        val x = sin(dLon) * cos(Math.toRadians(lat2))
        val y = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
        return (Math.toDegrees(atan2(x, y)) + 360.0) % 360.0
    }

    /**
     * Smallest absolute difference between two compass bearings, treated as **bidirectional**
     * (a 180° flip counts as 0°, because a road can be travelled in either direction).
     * Returns a value in [0, 90].
     */
    fun bearingPerpendicularity(b1: Double, b2: Double): Double {
        var diff = abs(b1 - b2) % 360.0
        if (diff > 180.0) diff = 360.0 - diff
        return min(diff, 180.0 - diff)
    }

    /** Result of projecting a point onto a segment: the foot point, the clamped fraction, and the distance. */
    data class Projection(
        val lat: Double,
        val lng: Double,
        /** Fraction along A→B in [0,1] of the foot point. */
        val t: Double,
        /** Distance from the original point to the foot point, in metres. */
        val distMeters: Double,
    )

    /**
     * Projects point P onto segment A→B, clamping the foot point to the segment endpoints.
     * Works in a local metric centred on A so the projection is geometrically correct.
     */
    fun projectOnSegment(
        pLat: Double, pLng: Double,
        aLat: Double, aLng: Double,
        bLat: Double, bLng: Double,
    ): Projection {
        val cosLat = cos(Math.toRadians(aLat))
        // Local metres relative to A.
        val px = (pLng - aLng) * METERS_PER_DEG_LAT * cosLat
        val py = (pLat - aLat) * METERS_PER_DEG_LAT
        val bx = (bLng - aLng) * METERS_PER_DEG_LAT * cosLat
        val by = (bLat - aLat) * METERS_PER_DEG_LAT

        val segLenSq = bx * bx + by * by
        val t = if (segLenSq <= 0.0) 0.0
        else ((px * bx + py * by) / segLenSq).coerceIn(0.0, 1.0)

        val footLat = aLat + t * (bLat - aLat)
        val footLng = aLng + t * (bLng - aLng)
        val dist = haversineMeters(pLat, pLng, footLat, footLng)
        return Projection(footLat, footLng, t, dist)
    }
}
