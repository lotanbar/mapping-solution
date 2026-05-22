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

const val GOOGLE_PLACES_GROUP_ID = "google-places-group"
const val OSM_POI_GROUP_ID = "osm-poi-group"

// Zoom threshold — applies to BOTH sources
const val NEARBY_POI_MIN_ZOOM = 8.0

const val GOOGLE_PLACES_FETCH_DEBOUNCE_MS = 800L
const val GOOGLE_PLACES_MAX_RESULTS = 20     // Google POIs shown per viewport (one API page)
const val GOOGLE_PLACES_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000   // 7 days
const val GOOGLE_PLACES_FIELD_MASK = "places.id,places.displayName,places.location,places.types"

val GOOGLE_PLACES_INCLUDED_TYPES = listOf(
    "restaurant", "cafe", "bar", "bakery", "fast_food_restaurant", "coffee_shop",
    "bank", "atm",
    "pharmacy", "hospital",
    "supermarket", "shopping_mall", "convenience_store",
    "hotel", "lodging",
    "gas_station",
    "gym", "beauty_salon",
)

const val OSM_FETCH_DEBOUNCE_MS = 800L
const val OSM_CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000   // 30 days

// Viewport display caps:
//   Total = 40  →  Google: 20  +  Overpass/Imported: 20
//   Imported (bulk) takes up to 10 of the 20 Overpass slots;
//   Overpass gets the remainder (at least 10, up to 20 when no imported POIs).
const val TOTAL_POI_VIEWPORT_LIMIT = 40
const val OSM_VIEWPORT_LIMIT = 20          // max Overpass POIs when no imported POIs present
const val IMPORTED_POI_VIEWPORT_LIMIT = 10 // imported POIs may claim up to this many OSM slots
