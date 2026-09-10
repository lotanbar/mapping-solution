package com.mappingsolution.data.map

import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.places.OSM_POI_GROUP_ID
import com.mappingsolution.data.places.PoiIconResolver
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Geometry
import org.maplibre.geojson.GeometryCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.MultiPoint
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

/** Converts a named, rendered MapTiler label into the transient POI used by the detail screen. */
object MapLabelPoiFactory {
    private const val MAPTILER_SOURCE_ID = "maptiler_planet"

    private val eligibleSourceLayers = setOf(
        "place",
        "aerodrome_label",
        "waterway",
        "water_name",
        "poi",
        "outdoor_poi",
        "mountain_peak",
        "park",
    )

    /** Custom app layers and road, contour, trail, and route labels deliberately fail this test. */
    fun isEligibleLayer(sourceId: String?, sourceLayer: String?): Boolean =
        sourceId == MAPTILER_SOURCE_ID && sourceLayer in eligibleSourceLayers

    fun fromRenderedFeature(feature: Feature, sourceLayer: String): Poi? {
        if (sourceLayer !in eligibleSourceLayers) return null
        val names = extractNames(feature)
        val name = names.firstOrNull() ?: return null
        val coordinate = canonicalCoordinate(feature.geometry()) ?: return null
        val featureClass = feature.stringProperty("class")
            ?: feature.stringProperty("subclass")
            ?: ""
        val iconKey = iconFor(sourceLayer, featureClass, name)
        val stableKey = listOf(
            sourceLayer,
            feature.id().orEmpty(),
            featureClass.lowercase(Locale.ROOT),
            name.lowercase(Locale.ROOT),
            String.format(Locale.US, "%.6f", coordinate.latitude()),
            String.format(Locale.US, "%.6f", coordinate.longitude()),
        ).joinToString("|")
        val id = "map_label_${UUID.nameUUIDFromBytes(stableKey.toByteArray(StandardCharsets.UTF_8))}"
        val wikiRef = feature.stringProperty("wikipedia")?.takeIf { ':' in it && !it.startsWith("http") }
            ?: feature.stringProperty("wikidata")?.takeIf { it.matches(Regex("Q\\d+")) }

        return Poi(
            id = id,
            groupId = OSM_POI_GROUP_ID,
            name = name,
            lat = coordinate.latitude(),
            lng = coordinate.longitude(),
            elevation = feature.numberProperty("ele"),
            iconKey = iconKey,
            wikiRef = wikiRef,
            imageSearchNames = names,
        )
    }

    private fun extractNames(feature: Feature): List<String> {
        val properties = feature.properties() ?: return emptyList()
        val preferredKeys = listOf("name", "name:en", "name:latin", "name_int")
        val aliasKeys = properties.keySet().filter { key ->
            key == "short_name" || key == "loc_name" || key == "old_name" ||
                key == "official_name" || key == "alt_name" ||
                key.startsWith("name:") || key.startsWith("short_name:") ||
                key.startsWith("loc_name:") || key.startsWith("old_name:") ||
                key.startsWith("official_name:") || key.startsWith("alt_name:")
        }.sorted()

        return (preferredKeys + aliasKeys).asSequence()
            .mapNotNull { feature.stringProperty(it) }
            .flatMap { it.split(';').asSequence() }
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .toList()
    }

    private fun iconFor(sourceLayer: String, featureClass: String, name: String): String {
        val normalized = featureClass.lowercase(Locale.ROOT).replace('-', '_')
        return when (sourceLayer) {
            "aerodrome_label" -> "airport"
            "waterway", "water_name" -> if (normalized == "waterfall") "waterfall" else "water"
            "mountain_peak" -> if (normalized == "volcano") "volcano" else "mountain"
            "park" -> "park"
            "place" -> when (normalized) {
                "city", "capital", "town" -> "town"
                "village", "hamlet", "isolated_dwelling", "neighbourhood", "neighborhood", "suburb", "quarter" -> "village"
                "island" -> "natural"
                else -> "marker"
            }
            "poi", "outdoor_poi" -> when (normalized) {
                "park", "playground" -> "park"
                "peak" -> "mountain"
                "picnic_site" -> "picnic-site"
                "fast_food" -> "fast-food"
                "town_hall" -> "information"
                "lodging" -> "lodging"
                "campsite" -> "campsite"
                "shelter" -> "shelter"
                "attraction" -> "attraction"
                "zoo" -> "zoo"
                "museum" -> "museum"
                "hospital" -> "hospital"
                "pharmacy" -> "pharmacy"
                "restaurant" -> "restaurant"
                "cafe" -> "cafe"
                else -> PoiIconResolver.resolveForImported(featureClass, name, "")
            }
            else -> "marker"
        }
    }

    private fun Feature.stringProperty(key: String): String? = runCatching {
        getProperty(key)?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf(String::isNotBlank)
    }.getOrNull()

    private fun Feature.numberProperty(key: String): Double? = runCatching {
        getProperty(key)?.takeUnless { it.isJsonNull }?.asDouble
    }.getOrNull()

    private fun canonicalCoordinate(geometry: Geometry?): Point? {
        if (geometry is Point) return geometry.takeIf { it.latitude().isValidLatitude() && it.longitude().isValidLongitude() }
        val points = geometry?.points().orEmpty()
        if (points.isEmpty()) return null
        val valid = points.filter { it.latitude().isValidLatitude() && it.longitude().isValidLongitude() }
        if (valid.isEmpty()) return null
        val latitude = (valid.minOf(Point::latitude) + valid.maxOf(Point::latitude)) / 2.0
        val longitude = (valid.minOf(Point::longitude) + valid.maxOf(Point::longitude)) / 2.0
        return Point.fromLngLat(longitude, latitude)
    }

    private fun Geometry.points(): List<Point> = when (this) {
        is Point -> listOf(this)
        is MultiPoint -> coordinates()
        is LineString -> coordinates()
        is MultiLineString -> coordinates().flatten()
        is Polygon -> coordinates().flatten()
        is MultiPolygon -> coordinates().flatten().flatten()
        is GeometryCollection -> geometries().flatMap { it.points() }
        else -> emptyList()
    }

    private fun Double.isValidLatitude() = isFinite() && this in -90.0..90.0
    private fun Double.isValidLongitude() = isFinite() && this in -180.0..180.0
}
