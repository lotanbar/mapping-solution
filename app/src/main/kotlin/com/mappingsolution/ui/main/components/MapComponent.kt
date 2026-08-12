package com.mappingsolution.ui.main.components

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.gson.JsonObject
import com.mappingsolution.BuildConfig
import com.mappingsolution.createCircleIcon
import com.mappingsolution.createHexagonIcon
import com.mappingsolution.createPinBitmap
import com.mappingsolution.createSquareIcon
import com.mappingsolution.data.map.MapStyle
import com.mappingsolution.data.model.Group
import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.model.RasterLayer
import com.mappingsolution.data.model.Route
import com.mappingsolution.data.model.RoutePoint
import com.mappingsolution.data.recording.RecordingPoint
import com.mappingsolution.ui.common.IconCatalog
import com.mappingsolution.data.prefs.ViewportPreference
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.HillshadeLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer as MapLibreRasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterDemSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun styleUrl(mapStyle: MapStyle = MapStyle.SATELLITE) = when (mapStyle) {
    MapStyle.SATELLITE ->
        "https://api.maptiler.com/maps/hybrid/style.json?key=${BuildConfig.MAPTILER_API_KEY}"
    MapStyle.TOPO_DARK ->
        "https://api.maptiler.com/maps/outdoor-v2-dark/style.json?key=${BuildConfig.MAPTILER_API_KEY}"
}

private fun createPoiPin(
    colorHex: String,
    painter: Painter,
    density: Density,
    layoutDirection: LayoutDirection
): Bitmap {
    val width = 75
    val height = 101
    val bitmap = createPinBitmap(colorHex, width, height)
    val androidCanvas = android.graphics.Canvas(bitmap)
    val composeCanvas = androidx.compose.ui.graphics.Canvas(androidCanvas)
    val drawScope = CanvasDrawScope()
    val iconSize = width * 0.55f
    val offset = (width - iconSize) / 2f
    
    val pinColor = try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (_: Exception) {
        Color.Blue
    }

    drawScope.draw(density, layoutDirection, composeCanvas, Size(width.toFloat(), height.toFloat())) {
        withTransform({
            translate(offset, (width - iconSize) / 2f) 
        }) {
            with(painter) {
                draw(
                    size = Size(iconSize, iconSize),
                    colorFilter = ColorFilter.tint(pinColor)
                )
            }
        }
    }
    return bitmap
}

private fun createPoiCircle(
    iconKey: String,
    painter: Painter,
    density: Density,
    layoutDirection: LayoutDirection,
    size: Int = 80,
): Bitmap {
    val bitmap = createCircleIcon(iconKey, size = size)
    val androidCanvas = android.graphics.Canvas(bitmap)

    // "marker" is a teardrop pin shape — draw a white dot instead for a clean look
    if (iconKey == "marker") {
        val dotPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
        val cx = size / 2f
        androidCanvas.drawCircle(cx, cx, size * 0.20f, dotPaint)
        return bitmap
    }

    val composeCanvas = androidx.compose.ui.graphics.Canvas(androidCanvas)
    val drawScope = CanvasDrawScope()
    val iconSize = size * 0.55f
    val offset = (size - iconSize) / 2f

    drawScope.draw(density, layoutDirection, composeCanvas, Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
        withTransform({ translate(offset, offset) }) {
            with(painter) {
                draw(
                    size = Size(iconSize, iconSize),
                    colorFilter = ColorFilter.tint(Color.White),
                )
            }
        }
    }
    return bitmap
}

private fun createPoiSquare(
    iconKey: String,
    painter: Painter,
    density: Density,
    layoutDirection: LayoutDirection,
    size: Int = 80,
): Bitmap {
    val bitmap = createSquareIcon(iconKey, size = size)
    val androidCanvas = android.graphics.Canvas(bitmap)

    if (iconKey == "marker") {
        val dotPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
        val cx = size / 2f
        androidCanvas.drawCircle(cx, cx, size * 0.20f, dotPaint)
        return bitmap
    }

    val composeCanvas = androidx.compose.ui.graphics.Canvas(androidCanvas)
    val drawScope = CanvasDrawScope()
    val iconSize = size * 0.55f
    val offset = (size - iconSize) / 2f

    drawScope.draw(density, layoutDirection, composeCanvas, Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
        withTransform({ translate(offset, offset) }) {
            with(painter) {
                draw(
                    size = Size(iconSize, iconSize),
                    colorFilter = ColorFilter.tint(Color.White),
                )
            }
        }
    }
    return bitmap
}

private fun createPoiHexagon(
    iconKey: String,
    painter: Painter,
    density: Density,
    layoutDirection: LayoutDirection,
    size: Int = 80,
): Bitmap {
    val bitmap = createHexagonIcon(iconKey, size = size)
    val androidCanvas = android.graphics.Canvas(bitmap)

    if (iconKey == "marker") {
        val dotPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
        val cx = size / 2f
        androidCanvas.drawCircle(cx, cx, size * 0.20f, dotPaint)
        return bitmap
    }

    val composeCanvas = androidx.compose.ui.graphics.Canvas(androidCanvas)
    val drawScope = CanvasDrawScope()
    val iconSize = size * 0.50f
    val offset = (size - iconSize) / 2f

    drawScope.draw(density, layoutDirection, composeCanvas, Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
        withTransform({ translate(offset, offset) }) {
            with(painter) {
                draw(
                    size = Size(iconSize, iconSize),
                    colorFilter = ColorFilter.tint(Color.White),
                )
            }
        }
    }
    return bitmap
}

@Composable
fun MapComponent(
    pois: List<Poi> = emptyList(),
    groups: List<Group> = emptyList(),
    routes: List<Route> = emptyList(),
    routePoints: Map<String, List<RoutePoint>> = emptyMap(),
    googlePlaces: List<Poi> = emptyList(),
    osmPois: List<Poi> = emptyList(),
    bulkPois: List<Poi> = emptyList(),
    liveRoutePoints: List<RecordingPoint> = emptyList(),
    liveRouteColor: String = "#FFFF5722",
    flyToLocation: Pair<Double, Double>? = null,
    searchPreviewLocation: Pair<Double, Double>? = null,
    initialCamera: ViewportPreference.SavedCamera? = null,
    mapStyle: MapStyle = MapStyle.SATELLITE,
    hillshadeVisible: Boolean = true,
    rasterLayers: List<RasterLayer> = emptyList(),
    baseMapVisible: Boolean = true,
    osmRoadsGeoJson: FeatureCollection = FeatureCollection.fromFeatures(emptyList<Feature>()),
    onCameraIdle: (lat: Double, lng: Double, zoom: Double, bearing: Double, tilt: Double) -> Unit = { _, _, _, _, _ -> },
    onBoundsChanged: (north: Double, south: Double, east: Double, west: Double, lat: Double, lng: Double, zoom: Double, bearing: Double, tilt: Double) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onPoiTapped: (String) -> Unit = {},
    onRouteTapped: (String) -> Unit = {},
    onGooglePlaceTapped: (String) -> Unit = {},
    onOsmPoiTapped: (String) -> Unit = {},
    onBulkPoiTapped: (String) -> Unit = {},
    onMapReady: (MapLibreMap) -> Unit = {},
    onMapDisposed: () -> Unit = {},
    onMapError: (String) -> Unit = {},
    onDoubleTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val onPoiTappedRef = rememberUpdatedState(onPoiTapped)
    val onRouteTappedRef = rememberUpdatedState(onRouteTapped)
    val onGooglePlaceTappedRef = rememberUpdatedState(onGooglePlaceTapped)
    val onOsmPoiTappedRef = rememberUpdatedState(onOsmPoiTapped)
    val onBulkPoiTappedRef = rememberUpdatedState(onBulkPoiTapped)
    val onCameraIdleRef = rememberUpdatedState(onCameraIdle)
    val onBoundsChangedRef = rememberUpdatedState(onBoundsChanged)
    val onDoubleTapRef = rememberUpdatedState(onDoubleTap)

    MapLibre.getInstance(context)

    val mapView = remember { MapView(context) }
    val mapState = remember { mutableStateOf<MapLibreMap?>(null) }
    val styleReady = remember { mutableStateOf(false) }
    val onMapErrorRef = rememberUpdatedState(onMapError)

    // Pre-create painters for ALL catalog icons (fixed set — composable safe)
    val allIconKeys = remember { IconCatalog.categories.flatMap { it.icons }.map { it.key } }
    val allPainters = allIconKeys.associateWith { painterResource(IconCatalog.iconRes(it)) }
    val placePainterFallback = allPainters["marker"] ?: painterResource(IconCatalog.iconRes("marker"))

    // Painters for group icons (subset of allPainters, kept for groupBitmaps)
    val painters = allPainters

    // Generate bitmaps for each group + default, respecting the group's chosen shape
    val groupBitmaps = remember(groups, painters) {
        val bitmaps = mutableMapOf<String, Bitmap>()
        groups.forEach { group ->
            val painter = painters[group.iconKey] ?: placePainterFallback
            bitmaps[group.id] = when (group.shape) {
                "circle" -> createPoiCircle(group.iconKey, painter, density, layoutDirection)
                "square" -> createPoiSquare(group.iconKey, painter, density, layoutDirection)
                else     -> createPoiPin(group.color, painter, density, layoutDirection) // "pin" default
            }
        }
        bitmaps["default"] = createPoiPin("#2196F3", placePainterFallback, density, layoutDirection)
        bitmaps
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> {
                    mapView.onResume()
                    val locationComponent = mapState.value?.locationComponent
                    if (locationComponent != null) {
                        if (locationComponent.isLocationComponentActivated) {
                            // Re-enable sensors on every resume
                            locationComponent.isLocationComponentEnabled = true
                        } else {
                            // Permission may have been granted since the style loaded; activate now
                            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.ACCESS_FINE_LOCATION
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            val map = mapState.value
                            val style = map?.style
                            if (hasPermission && map != null && style != null && style.isFullyLoaded) {
                                activateLocationComponent(map, style, context, mapView, lifecycleOwner)
                            }
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Snapshot camera position so it can be restored when navigating back
            mapState.value?.cameraPosition?.let { pos ->
                pos.target?.let { target ->
                    onCameraIdleRef.value(target.latitude, target.longitude, pos.zoom, pos.bearing, pos.tilt)
                }
            }
            onMapDisposed()
        }
    }

    // Switch base map style at runtime when mapStyle changes (skips the very first value,
    // which is handled by getMapAsync). On switch: mark styleReady false → set the new style
    // → re-add all custom sources/layers in the callback → styleReady flips true, causing
    // all existing LaunchedEffects to re-fire and repopulate POI/route GeoJSON sources.
    val isFirstStyle = remember { mutableStateOf(true) }
    LaunchedEffect(mapStyle) {
        if (isFirstStyle.value) {
            isFirstStyle.value = false
            return@LaunchedEffect
        }
        val map = mapState.value ?: return@LaunchedEffect
        styleReady.value = false
        map.setStyle(Style.Builder().fromUri(styleUrl(mapStyle))) { style ->
            setupMapStyle(
                style = style,
                map = map,
                context = context,
                mapView = mapView,
                lifecycleOwner = lifecycleOwner,
                groupBitmaps = groupBitmaps,
                placePainterFallback = placePainterFallback,
                density = density,
                layoutDirection = layoutDirection,
                initialCamera = null, // already positioned; don't reset camera on style switch
                styleReady = styleReady,
                onMapReady = {}, // already registered
            )
        }
    }

    // Update style images when groupBitmaps change
    LaunchedEffect(styleReady.value, groupBitmaps) {
        val map = mapState.value ?: return@LaunchedEffect
        if (!styleReady.value) return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        
        groupBitmaps.forEach { (id, bitmap) ->
            style.addImage("pin-$id", bitmap)
        }
    }

    // Re-render POIs whenever data or style readiness changes
    LaunchedEffect(pois, groups, styleReady.value) {
        val map = mapState.value ?: return@LaunchedEffect
        if (!styleReady.value) return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        val source = style.getSource("poi-source") as? GeoJsonSource ?: return@LaunchedEffect

        val hiddenGroupIds = groups.filter { !it.isVisible }.map { it.id }.toSet()
        val features = withContext(Dispatchers.Default) {
            pois.filter { poi ->
                poi.isVisible && (poi.groupId == null || poi.groupId !in hiddenGroupIds)
            }.map { poi ->
                val iconId = "pin-${poi.groupId ?: "default"}"
                val props = JsonObject().apply {
                    addProperty("poiId", poi.id)
                    addProperty("icon-id", iconId)
                    addProperty("name", poi.name)
                }
                Feature.fromGeometry(Point.fromLngLat(poi.lng, poi.lat), props)
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    // Fly to requested location and reset bearing to north
    LaunchedEffect(flyToLocation) {
        val (lat, lng) = flyToLocation ?: return@LaunchedEffect
        val map = mapState.value ?: return@LaunchedEffect
        val camera = CameraPosition.Builder()
            .target(LatLng(lat, lng))
            .zoom(17.0)
            .bearing(0.0)
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(camera), 1200)
    }

    // Search preview: fly to result + show red pin; clear pin when null
    LaunchedEffect(searchPreviewLocation, styleReady.value) {
        val map = mapState.value ?: return@LaunchedEffect
        if (!styleReady.value) return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        val source = style.getSource("search-preview-source") as? GeoJsonSource ?: return@LaunchedEffect
        if (searchPreviewLocation != null) {
            val (lat, lng) = searchPreviewLocation
            source.setGeoJson(
                FeatureCollection.fromFeatures(
                    listOf(Feature.fromGeometry(Point.fromLngLat(lng, lat)))
                )
            )
            val camera = CameraPosition.Builder()
                .target(LatLng(lat, lng))
                .zoom(15.0)
                .bearing(0.0)
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(camera), 900)
        } else {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        }
    }

    // Re-render saved route polylines whenever routes/points/visibility change
    LaunchedEffect(routes, routePoints, styleReady.value) {
        val map = mapState.value ?: return@LaunchedEffect
        if (!styleReady.value) return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        val source = style.getSource("saved-routes-source") as? GeoJsonSource ?: return@LaunchedEffect

        val features = routes.filter { it.isVisible }.mapNotNull { route ->
            val pts = routePoints[route.id] ?: return@mapNotNull null
            if (pts.size < 2) return@mapNotNull null
            val linePoints = pts.map { Point.fromLngLat(it.lng, it.lat) }
            // Strip leading #FF alpha prefix if present (stored as #AARRGGBB, MapLibre needs #RRGGBB)
            val mapColor = route.color.let { c ->
                if (c.length == 9 && c.startsWith("#")) "#${c.substring(3)}" else c
            }
            val props = JsonObject().apply {
                addProperty("routeId", route.id)
                addProperty("routeColor", mapColor)
            }
            Feature.fromGeometry(LineString.fromLngLats(linePoints), props)
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    // Update live route line color when the user changes it mid-recording
    LaunchedEffect(liveRouteColor, styleReady.value) {
        val map = mapState.value ?: return@LaunchedEffect
        if (!styleReady.value) return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        val mapColor = liveRouteColor.let { c ->
            if (c.length == 9 && c.startsWith("#")) "#${c.substring(3)}" else c
        }
        (style.getLayer("live-route-line") as? LineLayer)
            ?.setProperties(PropertyFactory.lineColor(mapColor))
    }

    // Toggle hillshade layer visibility without reloading the map style
    LaunchedEffect(hillshadeVisible, styleReady.value) {
        val map = mapState.value ?: return@LaunchedEffect
        if (!styleReady.value) return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        (style.getLayer("terrain-hillshade") as? HillshadeLayer)
            ?.setProperties(
                PropertyFactory.visibility(
                    if (hillshadeVisible) Property.VISIBLE else Property.NONE
                )
            )
    }

    // Hide all base-map style layers when an imported raster layer is active
    LaunchedEffect(baseMapVisible, styleReady.value) {
        val map = mapState.value ?: return@LaunchedEffect
        if (!styleReady.value) return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        style.layers.forEach { layer ->
            if (layer.id !in BASE_MAP_EXCLUDED_LAYER_IDS && !layer.id.startsWith(RASTER_LAYER_PREFIX)) {
                layer.setProperties(
                    PropertyFactory.visibility(if (baseMapVisible) Property.VISIBLE else Property.NONE)
                )
            }
        }
    }

    // Push OSM road GeoJSON to the map whenever the cache updates
    LaunchedEffect(osmRoadsGeoJson, styleReady.value) {
        val map = mapState.value ?: return@LaunchedEffect
        if (!styleReady.value) return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        (style.getSource("osm-roads-source") as? GeoJsonSource)?.setGeoJson(osmRoadsGeoJson)
    }

    // Sync raster (MBTiles) layers: add new sources/layers, remove deleted ones, update visibility
    LaunchedEffect(rasterLayers, styleReady.value) {
        val map = mapState.value ?: return@LaunchedEffect
        if (!styleReady.value) return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect

        val activeIds = rasterLayers.map { it.id }.toSet()

        // Remove layers/sources that no longer exist
        style.layers
            .filter { it.id.startsWith(RASTER_LAYER_PREFIX) }
            .forEach { layer ->
                val layerId = layer.id.removePrefix(RASTER_LAYER_PREFIX)
                if (layerId !in activeIds) {
                    style.removeLayer(layer.id)
                    style.removeSource("$RASTER_SOURCE_PREFIX$layerId")
                }
            }

        // Add or update each raster layer
        rasterLayers.forEach { rasterLayer ->
            val sourceId = "$RASTER_SOURCE_PREFIX${rasterLayer.id}"
            val layerId = "$RASTER_LAYER_PREFIX${rasterLayer.id}"
            val tileUrl = "http://mbtiles-local/${rasterLayer.id}/{z}/{x}/{y}"

            val existing = style.getLayer(layerId) as? MapLibreRasterLayer
            if (existing != null) {
                // Just toggle visibility
                existing.setProperties(
                    PropertyFactory.visibility(
                        if (rasterLayer.isVisible) Property.VISIBLE else Property.NONE
                    )
                )
            } else {
                // Add source + layer for the first time
                if (style.getSource(sourceId) == null) {
                    val tileSet = TileSet("2.2.0", tileUrl)
                    style.addSource(RasterSource(sourceId, tileSet, 256))
                }
                val newLayer = MapLibreRasterLayer(layerId, sourceId).withProperties(
                    PropertyFactory.visibility(
                        if (rasterLayer.isVisible) Property.VISIBLE else Property.NONE
                    ),
                    PropertyFactory.rasterOpacity(1f),
                )
                // Insert above hillshade, below route lines
                if (style.getLayer("saved-routes-lines") != null) {
                    style.addLayerBelow(newLayer, "saved-routes-lines")
                } else {
                    style.addLayer(newLayer)
                }
            }
        }
    }

    // Re-render live route polyline whenever points change
    LaunchedEffect(liveRoutePoints, styleReady.value) {
        val map = mapState.value ?: return@LaunchedEffect
        if (!styleReady.value) return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        val lineSource = style.getSource("live-route-source") as? GeoJsonSource ?: return@LaunchedEffect
        if (liveRoutePoints.size >= 2) {
            val pts = liveRoutePoints.map { Point.fromLngLat(it.lng, it.lat) }
            lineSource.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(pts)))
        } else {
            lineSource.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        }
    }

    LaunchedEffect(googlePlaces, styleReady.value) {
        val map = mapState.value ?: return@LaunchedEffect
        if (!styleReady.value) return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        val source = style.getSource("google-places-source") as? GeoJsonSource ?: return@LaunchedEffect

        googlePlaces.asSequence()
            .map { it.iconKey?.takeIf(String::isNotBlank) ?: "marker" }
            .distinct()
            .forEach { key ->
                val imageId = "pin-google-$key"
                if (style.getImage(imageId) == null) {
                    val painter = allPainters[key] ?: placePainterFallback
                    style.addImage(imageId, createPoiCircle(key, painter, density, layoutDirection))
                }
            }
        val features = withContext(Dispatchers.Default) {
            googlePlaces.map { poi ->
                val iconId = "pin-google-${poi.iconKey?.takeIf(String::isNotBlank) ?: "marker"}"
                Feature.fromGeometry(
                    Point.fromLngLat(poi.lng, poi.lat),
                    null,
                    poi.id,
                ).apply {
                    addStringProperty("poiId", poi.id)
                    addStringProperty("icon-id", iconId)
                }
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
        style.getLayer("google-places-symbols")?.setProperties(
            PropertyFactory.visibility(if (googlePlaces.isEmpty()) Property.NONE else Property.VISIBLE)
        )
    }

    LaunchedEffect(osmPois, styleReady.value) {
        val map = mapState.value ?: return@LaunchedEffect
        if (!styleReady.value) return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        val source = style.getSource("osm-poi-source") as? GeoJsonSource ?: return@LaunchedEffect

        osmPois.asSequence()
            .map { it.iconKey?.takeIf(String::isNotBlank) ?: "marker" }
            .distinct()
            .forEach { key ->
                val imageId = "pin-osm-$key"
                if (style.getImage(imageId) == null) {
                    val painter = allPainters[key] ?: placePainterFallback
                    style.addImage(imageId, createPoiHexagon(key, painter, density, layoutDirection))
                }
            }
        val features = withContext(Dispatchers.Default) {
            osmPois.map { poi ->
                val iconId = "pin-osm-${poi.iconKey?.takeIf(String::isNotBlank) ?: "marker"}"
                Feature.fromGeometry(
                    Point.fromLngLat(poi.lng, poi.lat),
                    null,
                    poi.id,
                ).apply {
                    addStringProperty("poiId", poi.id)
                    addStringProperty("icon-id", iconId)
                }
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
        style.getLayer("osm-poi-symbols")?.setProperties(
            PropertyFactory.visibility(if (osmPois.isEmpty()) Property.NONE else Property.VISIBLE)
        )
    }

    LaunchedEffect(bulkPois, styleReady.value) {
        val map = mapState.value ?: return@LaunchedEffect
        if (!styleReady.value) return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        val source = style.getSource("bulk-poi-source") as? GeoJsonSource ?: return@LaunchedEffect

        try {
            // Register only missing images. Images remain cached for the lifetime of this style.
            bulkPois.asSequence()
                .map { it.iconKey?.takeIf(String::isNotBlank) ?: "marker" }
                .distinct()
                .forEach { resolvedIcon ->
                val bitmapKey = "pin-bulk-$resolvedIcon"
                if (style.getImage(bitmapKey) == null) {
                    val painter = allPainters[resolvedIcon] ?: placePainterFallback
                    style.addImage(bitmapKey, createPoiSquare(resolvedIcon, painter, density, layoutDirection))
                }
            }

            val features = withContext(Dispatchers.Default) {
                bulkPois.map { poi ->
                    val resolvedIcon = poi.iconKey?.takeIf(String::isNotBlank) ?: "marker"
                    val iconId = "pin-bulk-$resolvedIcon"
                    Feature.fromGeometry(
                        Point.fromLngLat(poi.lng, poi.lat),
                        null,
                        poi.id,
                    ).apply {
                        addStringProperty("poiId", poi.id)
                        addStringProperty("icon-id", iconId)
                    }
                }
            }
            source.setGeoJson(FeatureCollection.fromFeatures(features))
        } catch (e: Exception) {
            android.util.Log.e("MapComponent", "bulkPois: exception in LaunchedEffect", e)
        }
    }

    AndroidView(
        factory = {
            val gestureDetector = android.view.GestureDetector(
                context,
                object : android.view.GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                        onDoubleTapRef.value()
                        return true
                    }
                }
            )
            mapView.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                false
            }
            mapView.addOnDidFailLoadingMapListener {
                onMapErrorRef.value("Map failed to load. Check your API key or connection.")
            }
            mapView.getMapAsync { map ->
                mapState.value = map
                map.uiSettings.isCompassEnabled = false
                map.uiSettings.setDoubleTapGesturesEnabled(false)
                map.uiSettings.setQuickZoomGesturesEnabled(false)

                // Map-level listeners survive style switches; register them only once.
                map.addOnMapClickListener { latLng ->
                    val pt = map.projection.toScreenLocation(latLng)
                    val rect = RectF(pt.x - 24f, pt.y - 24f, pt.x + 24f, pt.y + 24f)
                    // User POIs take highest priority
                    val poiHit = map.queryRenderedFeatures(rect, "poi-symbols")
                    if (poiHit.isNotEmpty()) {
                        val poiId = poiHit[0].getStringProperty("poiId")
                        if (poiId != null) {
                            onPoiTappedRef.value(poiId)
                            return@addOnMapClickListener true
                        }
                    }
                    // Google Places second
                    val googleHit = map.queryRenderedFeatures(rect, "google-places-symbols")
                    if (googleHit.isNotEmpty()) {
                        val placeId = googleHit[0].getStringProperty("poiId")
                        if (placeId != null) {
                            onGooglePlaceTappedRef.value(placeId)
                            return@addOnMapClickListener true
                        }
                    }
                    // Bulk imported POIs third
                    val bulkHit = map.queryRenderedFeatures(rect, "bulk-poi-symbols")
                    if (bulkHit.isNotEmpty()) {
                        val bulkId = bulkHit[0].getStringProperty("poiId")
                        if (bulkId != null) {
                            onBulkPoiTappedRef.value(bulkId)
                            return@addOnMapClickListener true
                        }
                    }
                    // OSM POIs fourth
                    val osmHit = map.queryRenderedFeatures(rect, "osm-poi-symbols")
                    if (osmHit.isNotEmpty()) {
                        val osmId = osmHit[0].getStringProperty("poiId")
                        if (osmId != null) {
                            onOsmPoiTappedRef.value(osmId)
                            return@addOnMapClickListener true
                        }
                    }
                    // Saved routes — wider tolerance for thin lines
                    val routeRect = RectF(pt.x - 30f, pt.y - 30f, pt.x + 30f, pt.y + 30f)
                    val routeHit = map.queryRenderedFeatures(routeRect, "saved-routes-lines")
                    if (routeHit.isNotEmpty()) {
                        val routeId = routeHit[0].getStringProperty("routeId")
                        if (routeId != null) {
                            onRouteTappedRef.value(routeId)
                            return@addOnMapClickListener true
                        }
                    }
                    false
                }
                map.addOnCameraIdleListener {
                    val pos = map.cameraPosition
                    val target = pos.target ?: return@addOnCameraIdleListener
                    onCameraIdleRef.value(
                        target.latitude,
                        target.longitude,
                        pos.zoom,
                        pos.bearing,
                        pos.tilt,
                    )
                    val bounds = map.projection.visibleRegion.latLngBounds
                    onBoundsChangedRef.value(
                        bounds.getLatNorth(),
                        bounds.getLatSouth(),
                        bounds.getLonEast(),
                        bounds.getLonWest(),
                        target.latitude,
                        target.longitude,
                        pos.zoom,
                        pos.bearing,
                        pos.tilt,
                    )
                }

                map.setStyle(Style.Builder().fromUri(styleUrl(mapStyle))) { style ->
                    setupMapStyle(
                        style = style,
                        map = map,
                        context = context,
                        mapView = mapView,
                        lifecycleOwner = lifecycleOwner,
                        groupBitmaps = groupBitmaps,
                        placePainterFallback = placePainterFallback,
                        density = density,
                        layoutDirection = layoutDirection,
                        initialCamera = initialCamera,
                        styleReady = styleReady,
                        onMapReady = onMapReady,
                    )
                }
            }
            mapView
        },
        modifier = modifier,
    )
}

private const val RASTER_SOURCE_PREFIX = "mbtiles-source-"
private const val RASTER_LAYER_PREFIX = "mbtiles-layer-"

/** Custom layers we add on top of the base map style — never hidden by the base-map toggle. */
private val BASE_MAP_EXCLUDED_LAYER_IDS = setOf(
    "terrain-hillshade",
    "osm-roads-line",
    "osm-paths-line",
    "osm-roads-labels",
    "saved-routes-lines",
    "live-route-line",
    "osm-poi-symbols",
    "bulk-poi-symbols",
    "google-places-symbols",
    "poi-symbols",
    "search-preview-symbol",
)

/**
 * Adds all custom sources, layers, images and activates the location component for a newly
 * loaded (or switched) MapLibre style. Safe to call on both first load and style switches.
 * Map-level listeners (click, cameraIdle) are NOT registered here — they survive style switches
 * and are registered once in the getMapAsync callback.
 */
private fun setupMapStyle(
    style: Style,
    map: MapLibreMap,
    context: android.content.Context,
    mapView: MapView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    groupBitmaps: Map<String, Bitmap>,
    placePainterFallback: Painter,
    density: Density,
    layoutDirection: LayoutDirection,
    initialCamera: ViewportPreference.SavedCamera?,
    styleReady: androidx.compose.runtime.MutableState<Boolean>,
    onMapReady: (MapLibreMap) -> Unit,
) {
    // Remove hillshade and contour layers baked into outdoor-v2-dark so they can be
    // added back later as a unified overlay that works across all map styles.
    listOf(
        "Hillshade",
        "Contour index", "Glacier contour index",
        "Contour", "Glacier contour",
        "Contour labels", "Glacier contour labels",
    ).forEach { style.removeLayer(it) }

    // --- GeoJSON sources ---
    style.addSource(GeoJsonSource("poi-source", FeatureCollection.fromFeatures(emptyList<Feature>())))
    style.addSource(GeoJsonSource("saved-routes-source", FeatureCollection.fromFeatures(emptyList<Feature>())))
    style.addSource(GeoJsonSource("live-route-source"))
    style.addSource(GeoJsonSource("osm-poi-source", FeatureCollection.fromFeatures(emptyList<Feature>())))
    style.addSource(GeoJsonSource("bulk-poi-source", FeatureCollection.fromFeatures(emptyList<Feature>())))
    style.addSource(GeoJsonSource("google-places-source", FeatureCollection.fromFeatures(emptyList<Feature>())))
    style.addSource(GeoJsonSource("search-preview-source", FeatureCollection.fromFeatures(emptyList<Feature>())))
    style.addSource(GeoJsonSource("osm-roads-source", FeatureCollection.fromFeatures(emptyList<Feature>())))

    // --- Terrain sources (shared across all map styles) ---
    style.addSource(
        RasterDemSource("terrain-hillshade-source",
            "https://api.maptiler.com/tiles/terrain-rgb-v2/tiles.json?key=${BuildConfig.MAPTILER_API_KEY}",
            256)
    )

    // --- Layers (bottom → top) ---
    // Hillshade is inserted below the first symbol (label) layer so map titles always render on top.
    //
    // Key tuning insight for dark maps: use RGBA colors with per-channel alpha instead of hex.
    // This lets shadows and highlights be tuned independently:
    //   - Shadow at full opacity → deep, pronounced valleys/faces
    //   - Highlight at low opacity (0.25–0.35) → subtle lit slopes without brightening the overall map
    //   - Avoid hex grays for highlight (e.g. #888) — they raise overall brightness uniformly
    //   - exaggeration=1.0 is the MapLibre max; increasing it beyond 1.0 has no effect
    val hillshadeLayer = HillshadeLayer("terrain-hillshade", "terrain-hillshade-source").withProperties(
        PropertyFactory.hillshadeIlluminationDirection(315f),
        PropertyFactory.hillshadeExaggeration(0.5f),
        PropertyFactory.hillshadeShadowColor("rgba(0,0,0,0.5)"),
        PropertyFactory.hillshadeHighlightColor("rgba(255,255,255,0.15)"),
        PropertyFactory.hillshadeAccentColor("rgba(100,100,100,0.2)"),
    )
    val firstSymbolLayerId = style.layers.firstOrNull { it is SymbolLayer }?.id
    if (firstSymbolLayerId != null) {
        style.addLayerBelow(hillshadeLayer, firstSymbolLayerId)
    } else {
        style.addLayer(hillshadeLayer)
    }
    // OSM road overlay — sits above hillshade, below user route lines and POI symbols.
    // Split into two layers: solid lines for driveable roads, dashed for walking/cycling paths.
    val pathTypes = listOf("track", "path", "footway", "cycleway", "steps", "pedestrian")
    val isPathFilter = Expression.any(*pathTypes.map {
        Expression.eq(Expression.get("highway"), Expression.literal(it))
    }.toTypedArray())
    val isRoadFilter = Expression.not(isPathFilter)

    style.addLayer(
        // Driveable roads — solid white lines, width 3px
        LineLayer("osm-roads-line", "osm-roads-source").apply {
            withProperties(
                PropertyFactory.lineColor("rgba(255,255,255,0.9)"),
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineOpacity(0.9f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
            withFilter(isRoadFilter)
            minZoom = 12f
        }
    )
    style.addLayer(
        // Hiking / cycling paths — dashed yellow-white lines, clearly distinguishable
        LineLayer("osm-paths-line", "osm-roads-source").apply {
            withProperties(
                PropertyFactory.lineColor("rgba(255,220,80,0.95)"),
                PropertyFactory.lineWidth(2.5f),
                PropertyFactory.lineOpacity(0.9f),
                PropertyFactory.lineDasharray(arrayOf(4f, 3f)),
                PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
            withFilter(isPathFilter)
            minZoom = 12f
        }
    )
    style.addLayer(
        SymbolLayer("osm-roads-labels", "osm-roads-source").apply {
            withProperties(
                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textSize(13f),
                PropertyFactory.textColor("rgba(255,255,255,0.95)"),
                PropertyFactory.textHaloColor("rgba(0,0,0,0.7)"),
                PropertyFactory.textHaloWidth(1.5f),
                PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
                PropertyFactory.textOptional(true),
            )
            withFilter(Expression.has("name"))
            minZoom = 13f
        }
    )
    style.addLayer(
        LineLayer("saved-routes-lines", "saved-routes-source").withProperties(
            PropertyFactory.lineColor(Expression.get("routeColor")),
            PropertyFactory.lineWidth(3f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    style.addLayer(
        LineLayer("live-route-line", "live-route-source").withProperties(
            PropertyFactory.lineColor("#FF5722"),
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    style.addLayer(
        SymbolLayer("bulk-poi-symbols", "bulk-poi-source").withProperties(
            PropertyFactory.iconImage(Expression.get("icon-id")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.iconOpacity(1f),
            PropertyFactory.iconSize(0.988f),
        )
    )
    style.addLayer(
        SymbolLayer("osm-poi-symbols", "osm-poi-source").withProperties(
            PropertyFactory.iconImage(Expression.get("icon-id")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.iconOpacity(1f),
            PropertyFactory.iconSize(0.806f),
        )
    )
    style.addLayer(
        SymbolLayer("google-places-symbols", "google-places-source").withProperties(
            PropertyFactory.iconImage(Expression.get("icon-id")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.iconOpacity(1f),
            PropertyFactory.iconSize(0.806f),
        )
    )
    style.addLayer(
        SymbolLayer("poi-symbols", "poi-source").withProperties(
            PropertyFactory.iconImage(Expression.get("icon-id")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.iconOpacity(1f),
            PropertyFactory.iconSize(1.131f),
        )
    )
    style.addLayer(
        SymbolLayer("search-preview-symbol", "search-preview-source").withProperties(
            PropertyFactory.iconImage("pin-search-preview"),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.iconOpacity(1f),
            PropertyFactory.iconSize(1.131f),
        )
    )

    // --- POI pin images ---
    groupBitmaps.forEach { (id, bitmap) -> style.addImage("pin-$id", bitmap) }
    // Remote/imported POI images are registered lazily as their data arrives.
    // Distinct red pin used for the search preview marker
    style.addImage("pin-search-preview", createPoiPin("#F44336", placePainterFallback, density, layoutDirection))

    // --- Restore camera (initial load only; null on style switch to keep current position) ---
    initialCamera?.let { cam ->
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(cam.lat, cam.lng))
                    .zoom(cam.zoom)
                    .bearing(cam.bearing)
                    .tilt(cam.tilt)
                    .build()
            )
        )
    }

    // --- Location component ---
    val locationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (locationPermission) {
        activateLocationComponent(map, style, context, mapView, lifecycleOwner)
    }

    styleReady.value = true
    onMapReady(map)
}

private fun activateLocationComponent(
    map: org.maplibre.android.maps.MapLibreMap,
    style: org.maplibre.android.maps.Style,
    context: android.content.Context,
    mapView: org.maplibre.android.maps.MapView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
) {
    val dp = context.resources.displayMetrics.density

    val glowPx = (80 * dp).toInt()
    val glowBmp = android.graphics.Bitmap.createBitmap(glowPx, glowPx, android.graphics.Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(glowBmp).drawCircle(
        glowPx / 2f, glowPx / 2f, glowPx / 2f,
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                glowPx / 2f, glowPx / 2f, glowPx / 2f,
                intArrayOf(0xCC2979FF.toInt(), 0x442979FF.toInt(), 0x002979FF.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
        },
    )

    val conePx = (156 * dp).toInt()
    val coneBmp = android.graphics.Bitmap.createBitmap(conePx, conePx, android.graphics.Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(coneBmp).apply {
        val cx = conePx / 2f
        val coneAngle = 72f
        val halfA = coneAngle / 2.0
        val len = cx * 0.92f
        val innerR = 15f * dp
        val innerFraction = innerR / len
        val conePath = android.graphics.Path().apply {
            moveTo(
                (cx + innerR * Math.cos(Math.toRadians(-90.0 - halfA))).toFloat(),
                (cx + innerR * Math.sin(Math.toRadians(-90.0 - halfA))).toFloat(),
            )
            lineTo(
                (cx + len * Math.cos(Math.toRadians(-90.0 - halfA))).toFloat(),
                (cx + len * Math.sin(Math.toRadians(-90.0 - halfA))).toFloat(),
            )
            arcTo(android.graphics.RectF(cx - len, cx - len, cx + len, cx + len), (-90f - coneAngle / 2f), coneAngle)
            lineTo(
                (cx + innerR * Math.cos(Math.toRadians(-90.0 + halfA))).toFloat(),
                (cx + innerR * Math.sin(Math.toRadians(-90.0 + halfA))).toFloat(),
            )
            arcTo(android.graphics.RectF(cx - innerR, cx - innerR, cx + innerR, cx + innerR), (-90f + coneAngle / 2f), -coneAngle)
            close()
        }
        save()
        clipPath(conePath)
        drawRect(
            0f, 0f, conePx.toFloat(), conePx.toFloat(),
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(
                    cx, cx, len,
                    intArrayOf(0x002979FF.toInt(), 0xEE2979FF.toInt(), 0x882979FF.toInt(), 0x002979FF.toInt()),
                    floatArrayOf(0f, innerFraction, 0.6f, 1f),
                    android.graphics.Shader.TileMode.CLAMP,
                )
            },
        )
        restore()
    }

    val dotPx = (24 * dp).toInt()
    val dotBmp = android.graphics.Bitmap.createBitmap(dotPx, dotPx, android.graphics.Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(dotBmp).apply {
        val cx = dotPx / 2f
        drawCircle(cx, cx, cx, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
        drawCircle(cx, cx, cx - 2.5f * dp, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2979FF.toInt() })
    }

    style.addImage("user-loc-glow", glowBmp)
    style.addImage("user-loc-cone", coneBmp)
    style.addImage("user-loc-dot", dotBmp)

    val locationOptions = org.maplibre.android.location.LocationComponentOptions.builder(context)
        .backgroundName("user-loc-glow")
        .bearingName("user-loc-cone")
        .foregroundName("user-loc-dot")
        .backgroundTintColor(null as Int?)
        .bearingTintColor(null as Int?)
        .foregroundTintColor(null as Int?)
        .backgroundStaleName("user-loc-glow")
        .foregroundStaleName("user-loc-dot")
        .accuracyAlpha(0f)
        .pulseEnabled(false)
        .build()
    val activationOptions = org.maplibre.android.location.LocationComponentActivationOptions
        .builder(context, style)
        .locationComponentOptions(locationOptions)
        .useDefaultLocationEngine(true)
        .build()
    map.locationComponent.activateLocationComponent(activationOptions)
    map.locationComponent.isLocationComponentEnabled = true
    map.locationComponent.cameraMode = org.maplibre.android.location.modes.CameraMode.NONE
    map.locationComponent.renderMode = org.maplibre.android.location.modes.RenderMode.COMPASS
    if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
        mapView.onResume()
    }
}
