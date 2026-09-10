package com.mappingsolution.data.map

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

class MapLabelPoiFactoryTest {
    @Test
    fun `both style label sources are eligible`() {
        listOf(
            "place", "aerodrome_label", "waterway", "water_name", "poi",
            "outdoor_poi", "mountain_peak", "park",
        ).forEach { sourceLayer ->
            assertTrue(sourceLayer, MapLabelPoiFactory.isEligibleLayer("maptiler_planet", sourceLayer))
        }
    }

    @Test
    fun `streets contours trails and custom app layers are excluded`() {
        listOf("transportation_name", "contour", "trail").forEach { sourceLayer ->
            assertFalse(MapLabelPoiFactory.isEligibleLayer("maptiler_planet", sourceLayer))
        }
        assertFalse(MapLabelPoiFactory.isEligibleLayer("saved-routes-source", "place"))
        assertFalse(MapLabelPoiFactory.isEligibleLayer("poi-source", "poi"))
    }

    @Test
    fun `unnamed features are ignored`() {
        assertNull(MapLabelPoiFactory.fromRenderedFeature(feature(className = "city"), "place"))
    }

    @Test
    fun `name and aliases are retained for enrichment`() {
        val feature = feature("Jerusalem", "city").apply {
            addStringProperty("name:en", "Jerusalem")
            addStringProperty("name:he", "ירושלים")
            addStringProperty("alt_name", "Al-Quds;Yerushalayim")
        }

        val poi = requireNotNull(MapLabelPoiFactory.fromRenderedFeature(feature, "place"))

        assertEquals("Jerusalem", poi.name)
        assertEquals(
            listOf("Jerusalem", "Al-Quds", "Yerushalayim", "ירושלים"),
            poi.imageSearchNames,
        )
    }

    @Test
    fun `point coordinates and deterministic IDs are preserved`() {
        val first = feature("Haifa", "city", lat = 32.794, lng = 34.989)
        val second = feature("Haifa", "city", lat = 32.794, lng = 34.989)

        val firstPoi = requireNotNull(MapLabelPoiFactory.fromRenderedFeature(first, "place"))
        val secondPoi = requireNotNull(MapLabelPoiFactory.fromRenderedFeature(second, "place"))

        assertEquals(32.794, firstPoi.lat, 0.0)
        assertEquals(34.989, firstPoi.lng, 0.0)
        assertEquals(firstPoi.id, secondPoi.id)
        assertTrue(firstPoi.id.startsWith("map_label_"))
    }

    @Test
    fun `line labels use the canonical geometry center`() {
        val properties = JsonObject().apply {
            addProperty("name", "Jordan River")
            addProperty("class", "river")
        }
        val feature = Feature.fromGeometry(
            LineString.fromLngLats(
                listOf(Point.fromLngLat(35.0, 31.0), Point.fromLngLat(36.0, 33.0)),
            ),
            properties,
        )

        val poi = requireNotNull(MapLabelPoiFactory.fromRenderedFeature(feature, "waterway"))

        assertEquals(32.0, poi.lat, 0.0)
        assertEquals(35.5, poi.lng, 0.0)
        assertEquals("water", poi.iconKey)
    }

    @Test
    fun `representative label classes map to expected icons`() {
        val cases = listOf(
            Triple("place", "city", "town"),
            Triple("place", "country", "marker"),
            Triple("place", "state", "marker"),
            Triple("water_name", "lake", "water"),
            Triple("poi", "park", "park"),
            Triple("mountain_peak", "peak", "mountain"),
            Triple("aerodrome_label", "regional", "airport"),
            Triple("poi", "museum", "museum"),
        )

        cases.forEach { (sourceLayer, featureClass, expectedIcon) ->
            val poi = MapLabelPoiFactory.fromRenderedFeature(
                feature("Named label", featureClass),
                sourceLayer,
            )
            assertNotNull("$sourceLayer/$featureClass", poi)
            assertEquals("$sourceLayer/$featureClass", expectedIcon, poi?.iconKey)
        }
    }

    private fun feature(
        name: String? = null,
        className: String,
        lat: Double = 31.778,
        lng: Double = 35.235,
    ): Feature {
        val properties = JsonObject().apply {
            name?.let { addProperty("name", it) }
            addProperty("class", className)
        }
        return Feature.fromGeometry(Point.fromLngLat(lng, lat), properties)
    }
}
