package com.mappingsolution.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.desktop.ProvideMapPresentationHost
import org.maplibre.compose.desktop.rememberAwtComposeMapPresentationHost
import org.maplibre.compose.expressions.dsl.Feature
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.textOffset
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.Anchor
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

private enum class SpikeStyle(val label: String, val path: String) {
    Hybrid("Satellite", "hybrid"),
    Outdoor("Outdoor dark", "outdoor-v2-dark"),
}

// Sample data mirroring the Android app's POI and saved-route layers.
private const val POIS_GEOJSON = """
{"type":"FeatureCollection","features":[
 {"type":"Feature","properties":{"name":"Trailhead","id":"poi-1"},"geometry":{"type":"Point","coordinates":[35.2137,31.7683]}},
 {"type":"Feature","properties":{"name":"Viewpoint","id":"poi-2"},"geometry":{"type":"Point","coordinates":[35.2290,31.7767]}}
]}"""

private const val ROUTE_GEOJSON = """
{"type":"FeatureCollection","features":[
 {"type":"Feature","properties":{"name":"Sample route","id":"route-1"},"geometry":{"type":"LineString","coordinates":[
  [35.2137,31.7683],[35.2180,31.7710],[35.2230,31.7735],[35.2290,31.7767]]}}
]}"""

private object PinPainter : Painter() {
    override val intrinsicSize = Size(64f, 64f)
    override fun DrawScope.onDraw() {
        val r = size.minDimension / 2
        drawCircle(Color.White, radius = r, center = Offset(r, r))
    }
}

fun main() = application {
    val windowState = remember { WindowState(size = DpSize(1280.dp, 800.dp)) }
    Window(onCloseRequest = ::exitApplication, state = windowState, title = "MappingSolution desktop spike") {
        ProvideMapPresentationHost(host = rememberAwtComposeMapPresentationHost(window)) {
            MaterialTheme { SpikeScreen() }
        }
    }
}

@Composable
private fun SpikeScreen() {
    var style by remember { mutableStateOf(SpikeStyle.Hybrid) }
    var hillshade by remember { mutableStateOf(true) }
    var clicked by remember { mutableStateOf("Click a POI or the route") }

    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(
            "https://api.maptiler.com/maps/${style.path}/style.json?key=${Config.MAPTILER_API_KEY}"
        ),
        initialCameraPosition = CameraPosition(target = Position(35.2210, 31.7725), zoom = 14.0),
    ) {
        val poiPainter = remember { PinPainter }
        val pois = rememberGeoJsonSource(GeoJsonData.JsonString(POIS_GEOJSON))
        val routes = rememberGeoJsonSource(GeoJsonData.JsonString(ROUTE_GEOJSON))
        val terrain = rememberRasterDemTileSource(
            "https://api.maptiler.com/tiles/terrain-rgb-v2/tiles.json?key=${Config.MAPTILER_API_KEY}"
        )

        // Same tuning as the Android app (MapComponent.kt), below the first label layer.
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
            source = routes,
            color = const(Color(0xFFFF5722)),
            width = const(5.dp),
            cap = const(LineCap.Round),
            join = const(LineJoin.Round),
            hitPadding = 6.dp,
            onClick = { features ->
                clicked = "Route: ${features.firstOrNull()?.properties?.get("name")}"
                ClickResult.Consume
            },
        )
        SymbolLayer(
            id = "poi-symbols",
            source = pois,
            iconImage = image(poiPainter, size = DpSize(32.dp, 32.dp), drawAsSdf = true),
            iconColor = const(Color(0xFFFF5722)),
            iconAllowOverlap = const(true),
            textField = format(span(Feature["name"].asString())),
            textFont = const(listOf(const("Noto Sans Regular"))),
            textColor = const(Color.White),
            textSize = const(1.1.em),
            textOffset = textOffset(0.em, 1.6.em),
            onClick = { features ->
                clicked = "POI: ${features.firstOrNull()?.properties?.get("name")}"
                ClickResult.Consume
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        MaplibreMap(modifier = Modifier.fillMaxSize(), state = mapState)
        Card(Modifier.padding(16.dp).width(260.dp).align(Alignment.TopStart)) {
            Column(Modifier.padding(12.dp)) {
                Text(clicked)
                Row {
                    SpikeStyle.entries.forEach { s ->
                        Button(onClick = { style = s }, Modifier.padding(end = 8.dp, top = 8.dp)) { Text(s.label) }
                    }
                }
                Button(onClick = { hillshade = !hillshade }, Modifier.padding(top = 8.dp)) {
                    Text(if (hillshade) "Hide hillshade" else "Show hillshade")
                }
            }
        }
    }
}
