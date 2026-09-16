package com.mappingsolution.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.mappingsolution.data.map.MapStyle
import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.model.Route
import com.mappingsolution.data.model.RoutePoint
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.Feature
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.dsl.textOffset
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.Anchor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.HillshadeLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.sources.rememberRasterDemTileSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

private sealed interface Selection {
    data class PoiSelection(val poi: Poi) : Selection
    data class RouteSelection(val route: Route) : Selection
}

@OptIn(FlowPreview::class)
@Composable
internal fun MapScreen(container: AppContainer) {
    val groups by container.groupRepository.observeAll().collectAsState(emptyList())
    val pois by container.poiRepository.observeAll().collectAsState(emptyList())
    val routes by container.routeRepository.observeAll().collectAsState(emptyList())
    val routePoints by produceState(emptyMap<String, List<RoutePoint>>(), routes) {
        value = routes.filter { it.didUserTapStop }.associate { it.id to container.routeRepository.getPoints(it.id) }
    }

    var style by remember { mutableStateOf(container.mapStylePreference.load()) }
    var hillshade by remember { mutableStateOf(container.hillshadePreference.load()) }
    var selection by remember { mutableStateOf<Selection?>(null) }

    val poisJson = remember(pois, groups) { MapGeoJson.pois(pois, groups) }
    val routesJson = remember(routes, routePoints) { MapGeoJson.routes(routes, routePoints) }
    val mapTilerKey = container.apiKeys.mapTiler

    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(styleUrl(style, mapTilerKey)),
        initialCameraPosition = remember {
            container.viewportPreference.load()?.let {
                CameraPosition(target = Position(it.lng, it.lat), zoom = it.zoom, bearing = it.bearing, tilt = it.tilt)
            } ?: CameraPosition(target = Position(35.0, 31.5), zoom = 7.0)
        },
    ) {
        val poiSource = rememberGeoJsonSource(GeoJsonData.JsonString(poisJson))
        val routeSource = rememberGeoJsonSource(GeoJsonData.JsonString(routesJson))
        val terrain = rememberRasterDemTileSource("https://api.maptiler.com/tiles/terrain-rgb-v2/tiles.json?key=$mapTilerKey")

        // Same tuning as the Android app, below the first label layer so titles stay readable.
        Anchor.Below({ it.type == "symbol" }) {
            HillshadeLayer(
                id = "terrain-hillshade",
                source = terrain,
                visible = hillshade,
                illuminationDirection = const(315f),
                exaggeration = const(0.5f),
                shadowColor = const(Color(0f, 0f, 0f, 0.5f)),
                highlightColor = const(Color(1f, 1f, 1f, 0.15f)),
                accentColor = const(Color(100 / 255f, 100 / 255f, 100 / 255f, 0.2f)),
            )
        }
        LineLayer(
            id = "saved-routes-lines",
            source = routeSource,
            color = Feature["color"].convertToColor(),
            width = const(4.dp),
            cap = const(LineCap.Round),
            join = const(LineJoin.Round),
            hitPadding = 6.dp,
            onClick = { features ->
                val id = features.firstOrNull()?.properties?.get("id")?.toString()?.trim('"')
                routes.find { it.id == id }?.let { selection = Selection.RouteSelection(it) }
                ClickResult.Consume
            },
        )
        CircleLayer(
            id = "poi-circles",
            source = poiSource,
            color = Feature["color"].convertToColor(),
            radius = const(7.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp),
            onClick = { features ->
                val id = features.firstOrNull()?.properties?.get("id")?.toString()?.trim('"')
                pois.find { it.id == id }?.let { selection = Selection.PoiSelection(it) }
                ClickResult.Consume
            },
        )
        SymbolLayer(
            id = "poi-labels",
            source = poiSource,
            minZoom = 11f,
            textField = format(span(Feature["name"].asString())),
            textFont = const(listOf(const("Noto Sans Regular"))),
            textColor = const(Color.White),
            textHaloColor = const(Color.Black),
            textHaloWidth = const(1.dp),
            textSize = const(1.em),
            textOffset = textOffset(0.em, 1.4.em),
        )
    }

    LaunchedEffect(mapState) {
        snapshotFlow { mapState.cameraPosition }.drop(1).debounce(1_000).collect { camera ->
            container.viewportPreference.save(
                lat = camera.target.latitude,
                lng = camera.target.longitude,
                zoom = camera.zoom,
                bearing = camera.bearing,
                tilt = camera.tilt,
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        MaplibreMap(modifier = Modifier.fillMaxSize(), state = mapState)
        Card(Modifier.padding(16.dp).width(300.dp).align(Alignment.TopStart)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MappingSolution", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${pois.size} POIs · ${routes.size} routes · ${groups.size} groups",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapStyle.entries.forEach { option ->
                        FilterChip(
                            selected = style == option,
                            onClick = {
                                style = option
                                container.mapStylePreference.save(option)
                            },
                            label = { Text(if (option == MapStyle.SATELLITE) "Satellite" else "Topo dark") },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hillshading", Modifier.weight(1f))
                    Switch(checked = hillshade, onCheckedChange = {
                        hillshade = it
                        container.hillshadePreference.save(it)
                    })
                }
                when (val selected = selection) {
                    is Selection.PoiSelection -> SelectionDetails(
                        title = selected.poi.name,
                        subtitle = groups.find { it.id == selected.poi.groupId }?.name ?: "No group",
                        body = selected.poi.description,
                    )
                    is Selection.RouteSelection -> SelectionDetails(
                        title = selected.route.name,
                        subtitle = "%.2f km".format(selected.route.distanceMeters / 1000),
                        body = selected.route.description,
                    )
                    null -> Text("Click a POI or route for details", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SelectionDetails(title: String, subtitle: String, body: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
        body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

private fun styleUrl(style: MapStyle, key: String): String {
    val path = when (style) {
        MapStyle.SATELLITE -> "hybrid"
        MapStyle.TOPO_DARK -> "outdoor-v2-dark"
    }
    return "https://api.maptiler.com/maps/$path/style.json?key=$key"
}
