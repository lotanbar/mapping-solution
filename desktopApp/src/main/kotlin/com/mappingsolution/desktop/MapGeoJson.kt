package com.mappingsolution.desktop

import com.mappingsolution.data.model.DestinationSource
import com.mappingsolution.data.model.Group
import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.model.Route
import com.mappingsolution.data.model.RoutePoint
import org.json.JSONArray
import org.json.JSONObject

/** Builds the GeoJSON documents the desktop map renders from shared data models. */
internal object MapGeoJson {
    private const val DEFAULT_COLOR = "#FF5722"

    /** Marker image ID: `<border>|<iconKey>`, where border is `plain` or `star` (gold outline). */
    fun markerId(iconKey: String?, starred: Boolean = false): String =
        "${if (starred) "star" else "plain"}|${iconKey?.takeIf(String::isNotBlank) ?: "marker"}"

    /** Personal POIs, mirroring the Android map's visibility rules. */
    fun personalPois(pois: List<Poi>, groups: List<Group>): List<Pair<Poi, String>> {
        val groupsById = groups.associateBy { it.id }
        val hiddenGroupIds = groups.filter { !it.isVisible }.map { it.id }.toSet()
        val importedGroupIds = groups.filter { it.isImported }.map { it.id }.toSet()
        // An imported POI the user starred is shown once, as the star.
        val starredImportedIds = pois.filter { it.savedSource == DestinationSource.IMPORTED }.mapNotNull { it.sourceId }.toSet()
        return pois.filter { poi ->
            val hiddenByStar = poi.savedSource == null && poi.groupId in importedGroupIds && poi.id in starredImportedIds
            poi.isVisible && !hiddenByStar && (poi.groupId == null || poi.groupId !in hiddenGroupIds)
        }.map { poi ->
            val icon = if (poi.savedSource != null) markerId(poi.iconKey, starred = true)
            else markerId(poi.groupId?.let(groupsById::get)?.iconKey)
            poi to icon
        }
    }

    /** OSM POIs in view, minus those the user starred (shown as personal stars instead). */
    fun osmPois(osmPois: List<Poi>, personalPois: List<Poi>, osmGroupVisible: Boolean): List<Pair<Poi, String>> {
        if (!osmGroupVisible) return emptyList()
        val starredOsmIds = personalPois.filter { it.savedSource == DestinationSource.OSM }.mapNotNull { it.sourceId }.toSet()
        return osmPois.filterNot { it.id in starredOsmIds }.map { it to markerId(it.iconKey) }
    }

    fun point(lat: Double, lng: Double): String = collection(
        JSONArray().put(
            feature(
                geometry = JSONObject().put("type", "Point").put("coordinates", JSONArray().put(lng).put(lat)),
                properties = JSONObject(),
            )
        )
    )

    fun points(markers: List<Pair<Poi, String>>): String {
        val features = JSONArray()
        for ((poi, icon) in markers) {
            features.put(
                feature(
                    geometry = JSONObject().put("type", "Point").put("coordinates", JSONArray().put(poi.lng).put(poi.lat)),
                    properties = JSONObject().put("id", poi.id).put("name", poi.name).put("icon", icon),
                )
            )
        }
        return collection(features)
    }

    fun routes(routes: List<Route>, points: Map<String, List<RoutePoint>>): String {
        val features = JSONArray()
        for (route in routes) {
            val line = points[route.id]?.takeIf { it.size >= 2 } ?: continue
            if (!route.isVisible) continue
            val coordinates = JSONArray()
            line.forEach { coordinates.put(JSONArray().put(it.lng).put(it.lat)) }
            features.put(
                feature(
                    geometry = JSONObject().put("type", "LineString").put("coordinates", coordinates),
                    properties = JSONObject()
                        .put("id", route.id)
                        .put("name", route.name)
                        .put("color", cssColor(route.color)),
                )
            )
        }
        return collection(features)
    }

    /** Stored colors are Android ARGB hex (`#AARRGGBB`); MapLibre expects CSS `#RRGGBB`. */
    fun cssColor(argb: String?): String = when {
        argb == null -> DEFAULT_COLOR
        argb.length == 9 && argb.startsWith("#") -> "#" + argb.substring(3)
        argb.length == 7 && argb.startsWith("#") -> argb
        else -> DEFAULT_COLOR
    }

    private fun feature(geometry: JSONObject, properties: JSONObject) =
        JSONObject().put("type", "Feature").put("geometry", geometry).put("properties", properties)

    private fun collection(features: JSONArray) =
        JSONObject().put("type", "FeatureCollection").put("features", features).toString()
}
