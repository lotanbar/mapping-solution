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

// OSM/imported POIs can remain useful at broad views. Google has its own higher threshold.
const val NEARBY_POI_MIN_ZOOM = 6.0
const val GOOGLE_HIGHLIGHTS_MIN_ZOOM = 6.0
const val GOOGLE_EVERYDAY_MIN_ZOOM = 11.0

const val GOOGLE_PLACES_FETCH_DEBOUNCE_MS = 300L
const val GOOGLE_PLACES_MAX_RESULTS = 10
const val OSM_POI_MAX_RESULTS = 20
const val BULK_POI_MAX_RESULTS = 30          // Imported (bulk) POIs shown per viewport
const val GOOGLE_PLACES_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000   // 7 days
const val GOOGLE_PLACES_FIELD_MASK = "places.id,places.displayName,places.location,places.types"

val GOOGLE_PLACES_INCLUDED_TYPES = listOf(
    // Food and drink
    "restaurant", "cafe", "bar", "bakery", "fast_food_restaurant", "coffee_shop",
    // Fuel
    "gas_station",
    // Culture and history
    "art_gallery", "art_museum", "art_studio", "auditorium", "castle",
    "cultural_landmark", "fountain", "historical_place", "history_museum",
    "monument", "museum", "performing_arts_theater", "sculpture",
    // Attractions, wildlife, and notable nature
    "aquarium", "botanical_garden", "historical_landmark", "national_park",
    "observation_deck", "planetarium", "scenic_spot", "state_park",
    "tourist_attraction", "wildlife_park", "wildlife_refuge", "zoo",
    // Places of worship
    "buddhist_temple", "church", "hindu_temple", "mosque", "shinto_shrine", "synagogue",
)

fun isGoogleDiscoveryType(types: List<String>): Boolean =
    types.any { it in GOOGLE_PLACES_INCLUDED_TYPES }

const val OSM_FETCH_DEBOUNCE_MS = 300L
const val OSM_CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000   // 30 days

/** Google density grows with zoom; other POI sources are not capped here. */
fun googlePoiLimitForZoom(zoom: Double): Int = when {
    zoom < 11.0 -> 3
    zoom < 13.0 -> 6
    else -> GOOGLE_PLACES_MAX_RESULTS
}

