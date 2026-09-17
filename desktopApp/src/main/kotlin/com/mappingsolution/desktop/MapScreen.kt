package com.mappingsolution.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.mappingsolution.data.map.MapLabelPoiFactory
import com.mappingsolution.data.map.MapStyle
import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.model.RoutePoint
import com.mappingsolution.data.places.NEARBY_POI_MIN_ZOOM
import com.mappingsolution.data.places.OSM_POI_GROUP_ID
import com.mappingsolution.data.util.AppLog
import com.mappingsolution.ui.common.IconCatalog
import com.mappingsolution.ui.map.PoiMarkerPainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.spatialk.geojson.toJson
import org.jetbrains.compose.resources.painterResource
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.Feature
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.textOffset
import org.maplibre.compose.expressions.value.ImageValue
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.Anchor
import org.maplibre.compose.layers.FeaturesClickHandler
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.HillshadeLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.sources.TileSetOptions
import org.maplibre.compose.sources.rememberMbtilesUrl
import org.maplibre.compose.sources.rememberRasterDemTileSource
import org.maplibre.compose.sources.rememberRasterTileSource
import java.io.File
import java.net.URI
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

private const val TAG = "MapScreen"

/** Android draws 80 px pins at ~0.8 scale; these sizes match them on screen. */
private val MARKER_SIZE = DpSize(30.dp, 39.dp)
private val BULK_MARKER_SIZE = DpSize(27.dp, 35.dp)

@OptIn(FlowPreview::class)
@Composable
internal fun MapScreen(
    container: AppContainer,
    mapCenter: MapCenter,
    /** Width covered by the side panel on the left; "map center" means the center of what's visible. */
    visibleLeft: Dp,
    /** [type] is the POI screen type: `poi` for stored POIs, `osm_poi` for OpenStreetMap. */
    onOpenPoi: (type: String, id: String) -> Unit,
    onOpenRoute: (routeId: String) -> Unit,
    /** Clicks on the map that did not hit a POI or route. */
    onMapClick: () -> Unit,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups by container.groupRepository.observeAll().collectAsState(emptyList())
    val pois by container.poiRepository.observeAll().collectAsState(emptyList())
    val osmPois by container.osmPoiRepository.pois.collectAsState()
    val bulkPois by container.bulkPoiRepository.poisInViewport.collectAsState()
    val routes by container.routeRepository.observeAll().collectAsState(emptyList())
    val routePoints by produceState(emptyMap<String, List<RoutePoint>>(), routes) {
        value = routes.filter { it.didUserTapStop }.associate { it.id to container.routeRepository.getPoints(it.id) }
    }

    // Shared with the library screen, which can change both too.
    val style by container.mapLayersState.mapStyle.collectAsState()
    val hillshade by container.mapLayersState.hillshadeVisible.collectAsState()
    val rasterLayers by container.mapLayersState.rasterLayers.collectAsState()

    val personalMarkers = remember(pois, groups) { MapGeoJson.personalPois(pois, groups) }
    val osmGroupVisible = groups.find { it.id == OSM_POI_GROUP_ID }?.isVisible ?: true
    val osmMarkers = remember(osmPois, pois, osmGroupVisible) { MapGeoJson.osmPois(osmPois, pois, osmGroupVisible) }
    val bulkMarkers = remember(bulkPois) { bulkPois.map { it to MapGeoJson.markerId(it.iconKey) } }
    val markerIds = remember(personalMarkers, osmMarkers, bulkMarkers) {
        (personalMarkers + osmMarkers + bulkMarkers).map { it.second }.toSortedSet() + MapGeoJson.markerId(null)
    }
    val routesJson = remember(routes, routePoints) { MapGeoJson.routes(routes, routePoints) }
    val mapTilerKey = container.apiKeys.mapTiler
    // Keeps showing the previous style while the next one downloads.
    val loadedStyle by produceState<LoadedStyle?>(null, style, mapTilerKey) {
        value = withContext(Dispatchers.IO) { loadBaseStyle(style, mapTilerKey) }
    }
    var myLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var locating by remember { mutableStateOf(false) }

    val mapState = rememberMapState(
        baseStyle = loadedStyle?.baseStyle ?: BaseStyle.Json(EMPTY_STYLE),
        initialCameraPosition = remember {
            container.viewportPreference.load()?.let {
                CameraPosition(target = Position(it.lng, it.lat), zoom = it.zoom, bearing = it.bearing, tilt = it.tilt)
            } ?: CameraPosition(target = Position(35.0, 31.5), zoom = 7.0)
        },
    ) {
        val routeSource = rememberGeoJsonSource(GeoJsonData.JsonString(routesJson))
        val personalSource = rememberGeoJsonSource(GeoJsonData.JsonString(MapGeoJson.points(personalMarkers)))
        val osmSource = rememberGeoJsonSource(GeoJsonData.JsonString(MapGeoJson.points(osmMarkers)))
        val bulkSource = rememberGeoJsonSource(GeoJsonData.JsonString(MapGeoJson.points(bulkMarkers)))
        val terrain = rememberRasterDemTileSource(
            "https://api.maptiler.com/tiles/terrain-rgb-v2/tiles.json?key=$mapTilerKey",
            tileSize = 256, // As on Android; the tile size changes how fine the shading looks.
        )

        // One marker image per icon/border combination currently on the map.
        val markerImages = markerIds.associateWith { id ->
            key(id) {
                val (border, iconKey) = id.split('|', limit = 2)
                val icon = if (iconKey == "marker") null else painterResource(IconCatalog.iconRes(iconKey))
                remember(icon) {
                    PoiMarkerPainter(iconKey, icon, borderColor = if (border == "star") Color(0xFFFFC107) else Color.White)
                }
            }
        }
        fun markerImage(size: DpSize): Expression<ImageValue> = switch(
            input = Feature["icon"].asString(),
            cases = markerImages.map { (id, painter) -> case(id, image(painter, size = size)) },
            fallback = image(markerImages.getValue(MapGeoJson.markerId(null)), size = size),
        )
        fun openPoi(list: List<Pair<Poi, String>>, type: String): FeaturesClickHandler = { features ->
            val id = features.firstOrNull()?.properties?.get("id")?.toString()?.trim('"')
            list.find { it.first.id == id }?.let { (poi, _) ->
                AppLog.d(TAG, "Selected POI '${poi.name}'")
                onOpenPoi(type, poi.id)
            }
            ClickResult.Consume
        }

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
        // Imported MBTiles overlays: above hillshade, below routes and markers (as on Android).
        rasterLayers.forEach { layer ->
            key(layer.id) {
                val url by rememberMbtilesUrl(File(layer.filePath).toURI().toString())
                url?.let { mbtiles ->
                    val source = rememberRasterTileSource(
                        tiles = listOf(mbtiles),
                        options = TileSetOptions(minZoom = layer.minZoom, maxZoom = layer.maxZoom),
                    )
                    RasterLayer(id = "raster-layer-${layer.id}", source = source, visible = layer.isVisible)
                }
            }
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
                routes.find { it.id == id }?.let { route ->
                    AppLog.d(TAG, "Selected route '${route.name}'")
                    onOpenRoute(route.id)
                }
                ClickResult.Consume
            },
        )
        myLocation?.let { (lat, lng) ->
            val locationSource = rememberGeoJsonSource(GeoJsonData.JsonString(MapGeoJson.point(lat, lng)))
            CircleLayer(
                id = "my-location",
                source = locationSource,
                color = const(Color(0xFF2196F3)),
                radius = const(7.dp),
                strokeColor = const(Color.White),
                strokeWidth = const(2.dp),
            )
        }
        MarkerLayer("bulk-poi-symbols", bulkSource, markerImage(BULK_MARKER_SIZE), opacity = 0.6f, onClick = openPoi(bulkMarkers, "poi"))
        MarkerLayer("osm-poi-symbols", osmSource, markerImage(MARKER_SIZE), onClick = openPoi(osmMarkers, "osm_poi"))
        MarkerLayer("poi-symbols", personalSource, markerImage(MARKER_SIZE), onClick = openPoi(personalMarkers, "poi"))
        SymbolLayer(
            id = "poi-labels",
            source = personalSource,
            minZoom = 11f,
            textField = format(span(Feature["name"].asString())),
            textFont = const(listOf(const("Noto Sans Regular"))),
            textColor = const(Color.White),
            textHaloColor = const(Color.Black),
            textHaloWidth = const(1.dp),
            textSize = const(1.em),
            textOffset = textOffset(0.em, 0.6.em),
            textAnchor = const(SymbolAnchor.Top),
        )
    }

    LaunchedEffect(style) { AppLog.d(TAG, "Map style $style") }
    LaunchedEffect(personalMarkers.size, osmMarkers.size, bulkMarkers.size) {
        AppLog.d(TAG, "Markers: ${personalMarkers.size} personal, ${osmMarkers.size} OSM, ${bulkMarkers.size} imported")
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
            val bounds = mapState.getVisibleBounds() ?: return@collect
            container.viewportPoiLoader.onCameraIdle(
                zoom = camera.zoom,
                north = bounds.northeast.latitude,
                south = bounds.southwest.latitude,
                east = bounds.northeast.longitude,
                west = bounds.southwest.longitude,
                groups = groups,
            )
        }
    }

    val scope = rememberCoroutineScope()
    DevAutomation.cameraMover = { lat, lng, zoom ->
        scope.launch { mapState.animateCameraPosition(CameraPosition(target = Position(lng, lat), zoom = zoom)) }
    }
    DevAutomation.featureLocator = { kind, name ->
        val position = when (kind) {
            "poi" -> pois.find { it.name == name }?.let { Position(it.lng, it.lat) }
            "osm" -> osmPois.find { it.name == name }?.let { Position(it.lng, it.lat) }
            "route" -> routes.find { it.name == name }
                ?.let { routePoints[it.id] }
                ?.let { points -> points[points.size / 2] }
                ?.let { Position(it.lng, it.lat) }
            else -> null
        }
        // Markers hang above their point; aim at the pin head, not the tip.
        position?.let(mapState::screenLocationFromPosition)?.let {
            if (kind == "route") it else it.copy(y = it.y - MARKER_SIZE.height * 0.6f)
        }
    }

    /** Opens a named base-map label (town, peak, park…) the way Android does, else reports a plain map click. */
    fun onBackgroundClick(offset: DpOffset) {
        val labelLayers = loadedStyle?.labelLayers.orEmpty()
        scope.launch {
            val hitBox = DpRect(offset.x - 12.dp, offset.y - 12.dp, offset.x + 12.dp, offset.y + 12.dp)
            var poi: Poi? = null
            for ((layerId, sourceLayer) in labelLayers) {
                poi = mapState.queryRenderedFeatures(hitBox, setOf(layerId)).firstNotNullOfOrNull { feature ->
                    MapLabelPoiFactory.fromRenderedFeature(org.maplibre.geojson.Feature.fromJson(feature.toJson()), sourceLayer)
                }
                if (poi != null) break
            }
            if (poi != null) {
                AppLog.d(TAG, "Selected map label '${poi.name}'")
                container.osmPoiRepository.registerTransientPoi(poi)
                onOpenPoi("osm_poi", poi.id)
            } else {
                onMapClick()
            }
        }
    }

    /** Double click flies to the computer's location, as double tap does on Android. */
    fun goToMyLocation() {
        fun flyTo(location: Pair<Double, Double>) = scope.launch {
            val current = mapState.cameraPosition
            mapState.animateCameraPosition(
                current.copy(target = Position(location.second, location.first), zoom = maxOf(current.zoom, CURRENT_LOCATION_ZOOM)),
            )
        }
        // The OS lookup takes a few seconds: jump to the last fix straight away, then refine.
        myLocation?.let(::flyTo) ?: showMessage("Finding your location…")
        if (locating) return
        locating = true
        scope.launch {
            val location = DesktopLocation.current()
            locating = false
            if (location == null) {
                showMessage("Couldn't get your location. Check that location services are on.")
                return@launch
            }
            val moved = location != myLocation
            myLocation = location
            if (moved) flyTo(location)
        }
    }
    DevAutomation.locator = ::goToMyLocation

    var mapSize by remember { mutableStateOf(DpSize.Zero) }
    val density = LocalDensity.current
    Box(modifier.onSizeChanged { mapSize = with(density) { DpSize(it.width.toDp(), it.height.toDp()) } }) {
        if (loadedStyle != null) MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            state = mapState,
            interactions = MapInteractions(MapInteractions.Standard) {
                callbacks {
                    click { onUnhandled { event -> AppLog.d(TAG, "Map click"); onBackgroundClick(event.screenOffset); ClickResult.Consume } }
                    doubleClick { onEvent { AppLog.d(TAG, "Map double-click"); goToMyLocation(); ClickResult.Consume } }
                }
            },
        )
    }
    mapCenter.get = {
        val visibleCenter = DpOffset(visibleLeft + (mapSize.width - visibleLeft) / 2, mapSize.height / 2)
        runCatching { mapState.positionFromScreenLocation(visibleCenter) }.getOrNull()
            ?.let { it.latitude to it.longitude }
    }
}

/** Lets controls outside the map read where the camera currently points. */
internal class MapCenter {
    var get: () -> Pair<Double, Double>? = { null }
}

@Composable
private fun MarkerLayer(
    id: String,
    source: GeoJsonSource,
    icon: Expression<ImageValue>,
    opacity: Float = 1f,
    onClick: FeaturesClickHandler,
) {
    SymbolLayer(
        id = id,
        source = source,
        minZoom = (NEARBY_POI_MIN_ZOOM + 0.01).toFloat(),
        iconImage = icon,
        iconAnchor = const(SymbolAnchor.Bottom),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        iconOpacity = const(opacity),
        onClick = onClick,
    )
}

private fun styleUrl(style: MapStyle, key: String): String {
    val path = when (style) {
        MapStyle.SATELLITE -> "hybrid"
        MapStyle.TOPO_DARK -> "outdoor-v2-dark"
    }
    return "https://api.maptiler.com/maps/$path/style.json?key=$key"
}

private const val CURRENT_LOCATION_ZOOM = 16.0

private const val EMPTY_STYLE = """{"version":8,"sources":{},"layers":[]}"""

/**
 * Layers built into MapTiler's outdoor style. Android removes them too and draws its own tuned
 * hillshade on every style, so they would otherwise shade the terrain twice.
 */
private val BUILT_IN_TERRAIN_LAYERS = setOf(
    "Hillshade",
    "Contour index", "Glacier contour index",
    "Contour", "Glacier contour",
    "Contour labels", "Glacier contour labels",
)

/**
 * A base style plus its clickable label layers, topmost first, mapped to their vector source layer.
 * Query results don't say which layer a feature came from, so each label layer is queried alone.
 */
private class LoadedStyle(val baseStyle: BaseStyle, val labelLayers: Map<String, String>)

/** Downloads the style without its built-in terrain layers; falls back to the plain URL. */
private fun loadBaseStyle(style: MapStyle, key: String): LoadedStyle {
    val url = styleUrl(style, key)
    return runCatching {
        val json = JSONObject(URI(url).toURL().readText())
        val layers = json.getJSONArray("layers")
        val kept = JSONArray()
        val labelLayers = LinkedHashMap<String, String>()
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            if (layer.optString("id") in BUILT_IN_TERRAIN_LAYERS) continue
            kept.put(layer)
            val sourceLayer = layer.optString("source-layer")
            if (layer.optString("type") == "symbol" && MapLabelPoiFactory.isEligibleLayer(layer.optString("source"), sourceLayer)) {
                labelLayers[layer.getString("id")] = sourceLayer
            }
        }
        AppLog.d(TAG, "Style $style: removed ${layers.length() - kept.length()} built-in terrain layers, ${labelLayers.size} label layers")
        json.put("layers", kept)
        LoadedStyle(BaseStyle.Json(json.toString()), labelLayers.entries.reversed().associate { it.key to it.value })
    }.getOrElse { error ->
        AppLog.w(TAG, "Could not preprocess style $style, using it as is: $error")
        LoadedStyle(BaseStyle.Uri(url), emptyMap())
    }
}
