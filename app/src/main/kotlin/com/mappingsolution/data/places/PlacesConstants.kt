package com.mappingsolution.data.places

/**
 * Represents a geographic viewport as four bounding coordinates.
 * Used by [computeNewStrips] to track which areas have already been fetched
 * during the current session.
 */
data class FetchedBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
)

fun FetchedBounds.covers(other: FetchedBounds): Boolean =
    south <= other.south && north >= other.north && west <= other.west && east >= other.east

fun FetchedBounds.expanded(fraction: Double): FetchedBounds {
    val latPadding = (north - south) * fraction
    val lngPadding = (east - west) * fraction
    return FetchedBounds(
        north = (north + latPadding).coerceAtMost(90.0),
        south = (south - latPadding).coerceAtLeast(-90.0),
        east = (east + lngPadding).coerceAtMost(180.0),
        west = (west - lngPadding).coerceAtLeast(-180.0),
    )
}

/**
 * Returns the sub-regions of [new] that are NOT already covered by [prev].
 *
 * - prev = null        → listOf(new)  — first load or after clear()
 * - new fully inside prev → listOf(new)  — zoom-in: user wants more detail at closer zoom
 * - no lat/lng overlap → listOf(new)
 * - partial/full-contain overlap → up to 4 non-overlapping strips covering new minus prev
 */
fun computeNewStrips(new: FetchedBounds, prev: FetchedBounds?): List<FetchedBounds> {
    if (prev == null) return listOf(new)

    // Zoom-in: new is entirely inside prev → fetch the full new viewport for detail.
    if (new.north <= prev.north && new.south >= prev.south &&
        new.east <= prev.east && new.west >= prev.west
    ) return listOf(new)

    val overlapN = minOf(new.north, prev.north)
    val overlapS = maxOf(new.south, prev.south)
    val overlapE = minOf(new.east, prev.east)
    val overlapW = maxOf(new.west, prev.west)

    // No overlap at all → fetch the full new viewport.
    if (overlapN <= overlapS || overlapE <= overlapW) return listOf(new)

    val strips = mutableListOf<FetchedBounds>()
    if (new.north > prev.north)
        strips += FetchedBounds(north = new.north, south = prev.north, east = new.east, west = new.west)
    if (new.south < prev.south)
        strips += FetchedBounds(north = prev.south, south = new.south, east = new.east, west = new.west)
    if (new.east > prev.east)
        strips += FetchedBounds(north = overlapN, south = overlapS, east = new.east, west = prev.east)
    if (new.west < prev.west)
        strips += FetchedBounds(north = overlapN, south = overlapS, east = prev.west, west = new.west)

    return if (strips.isEmpty()) listOf(new) else strips
}

const val OSM_POI_GROUP_ID = "osm-poi-group"

const val NEARBY_POI_MIN_ZOOM = 6.0

const val OSM_FETCH_DEBOUNCE_MS = 300L
const val OSM_CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000   // 30 days

/** Broad exploration views get more candidates; close views stay intentionally focused. */
fun osmPoiLimitForZoom(zoom: Double): Int = when {
    zoom < 9.0 -> 60
    zoom < 12.0 -> 40
    else -> 20
}

