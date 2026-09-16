package com.mappingsolution.desktop

import com.mappingsolution.data.model.Group
import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.model.Route
import com.mappingsolution.data.model.RoutePoint
import org.json.JSONArray
import org.json.JSONObject

/** Builds the GeoJSON documents the desktop map renders from shared data models. */
internal object MapGeoJson {
    private const val DEFAULT_COLOR = "#FF5722"

    fun pois(pois: List<Poi>, groups: List<Group>): String {
        val groupsById = groups.associateBy { it.id }
        val features = JSONArray()
        for (poi in pois) {
            val group = poi.groupId?.let(groupsById::get)
            if (!poi.isVisible || group?.isVisible == false) continue
            features.put(
                feature(
                    geometry = JSONObject().put("type", "Point").put("coordinates", JSONArray().put(poi.lng).put(poi.lat)),
                    properties = JSONObject()
                        .put("id", poi.id)
                        .put("name", poi.name)
                        .put("color", cssColor(group?.color)),
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
