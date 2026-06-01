package com.mappingsolution.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mappingsolution.data.fs.BulkPoiRepository
import com.mappingsolution.data.fs.GroupFileRepository
import com.mappingsolution.data.fs.PoiFileRepository
import com.mappingsolution.data.fs.RouteFileRepository
import com.mappingsolution.data.map.MapHolder
import com.mappingsolution.data.map.MapLayersState
import com.mappingsolution.data.map.MapStyle
import com.mappingsolution.data.map.SearchPreviewState
import com.mappingsolution.data.model.Group
import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.model.Route
import com.mappingsolution.data.model.RoutePoint
import com.mappingsolution.data.places.FetchedBounds
import com.mappingsolution.data.places.GOOGLE_PLACES_FETCH_DEBOUNCE_MS
import com.mappingsolution.data.places.GooglePlacesRepository
import com.mappingsolution.data.places.NEARBY_POI_MIN_ZOOM
import com.mappingsolution.data.places.OSM_FETCH_DEBOUNCE_MS
import com.mappingsolution.data.places.OsmPoiRepository
import com.mappingsolution.data.prefs.ViewportPreference
import com.mappingsolution.data.recording.processing.OsmRoadCache
import dagger.hilt.android.lifecycle.HiltViewModel
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    groupRepository: GroupFileRepository,
    poiRepository: PoiFileRepository,
    private val routeRepository: RouteFileRepository,
    val mapHolder: MapHolder,
    val googlePlacesRepository: GooglePlacesRepository,
    val osmPoiRepository: OsmPoiRepository,
    val bulkPoiRepository: BulkPoiRepository,
    private val mapLayersState: MapLayersState,
    private val searchPreviewState: SearchPreviewState,
    val osmRoadCache: OsmRoadCache,
) : ViewModel() {

    val groups: StateFlow<List<Group>> = groupRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pois: StateFlow<List<Poi>> = poiRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val routes: StateFlow<List<Route>> = routeRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** OSM road geometry for the current map area — drives the road overlay layer. */
    val osmRoadsGeoJson: StateFlow<FeatureCollection> = osmRoadCache.roadsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeatureCollection.fromFeatures(emptyList<Feature>()))

    /** Routes that were not properly stopped (app killed / battery died during recording). */
    val incompleteRoutes: StateFlow<List<Route>> = routeRepository.observeAll()
        .map { routes -> routes.filter { !it.didUserTapStop } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Points for all visible completed routes, keyed by route ID. Used to render polylines on the map. */
    val routePoints: StateFlow<Map<String, List<RoutePoint>>> = routeRepository.observeAll()
        .flatMapLatest { routes ->
            flow {
                val result = routes
                    .filter { it.isVisible && it.didUserTapStop }
                    .associate { route -> route.id to routeRepository.getPoints(route.id) }
                emit(result)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Reads last known viewport — always fresh (in-memory first, then disk). */
    val initialCamera: ViewportPreference.SavedCamera? get() = mapHolder.loadCamera()

    /** True while either POI source is actively fetching from network. */
    val isPoisLoading: StateFlow<Boolean> = combine(
        googlePlacesRepository.isLoading,
        osmPoiRepository.isLoading,
        bulkPoiRepository.isLoading,
    ) { g, o, b -> g || o || b }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val bulkPois: StateFlow<List<Poi>> = bulkPoiRepository.poisInViewport
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mapStyle: MutableStateFlow<MapStyle> = mapLayersState.mapStyle
    val hillshadeVisible: MutableStateFlow<Boolean> = mapLayersState.hillshadeVisible
    val rasterLayers: StateFlow<List<com.mappingsolution.data.model.RasterLayer>> = mapLayersState.rasterLayers
    val baseMapVisible: StateFlow<Boolean> = mapLayersState.baseMapVisible

    /** Lat/lng of the search result last tapped in SearchNPlan — drives map camera + preview pin. */
    val searchPreviewLocation: StateFlow<Pair<Double, Double>?> =
        searchPreviewState.previewLocation
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleMapStyle() {
        val next = if (mapLayersState.mapStyle.value == MapStyle.SATELLITE) MapStyle.TOPO_DARK else MapStyle.SATELLITE
        mapLayersState.setMapStyle(next)
    }

    private var googleRefreshJob: Job? = null
    private var osmRefreshJob: Job? = null
    private var bulkRefreshJob: Job? = null

    /** Last viewport bounds for which POI fetches were dispatched. */
    @Volatile private var lastDispatchedBounds: FetchedBounds? = null

    /** Tolerance in degrees (~110 m) used to consider two viewports identical. */
    private val BOUNDS_EPSILON = 0.001

    init {
        viewModelScope.launch {
            googlePlacesRepository.evictStaleCacheOnLaunch()
            osmPoiRepository.evictStaleCacheOnLaunch()
        }
    }

    fun saveCameraPosition(lat: Double, lng: Double, zoom: Double, bearing: Double, tilt: Double) {
        mapHolder.saveCamera(lat, lng, zoom, bearing, tilt)
    }

    /**
     * Called whenever the map camera becomes idle. Saves the position and, when zoomed in
     * enough, triggers debounced POI fetches for both sources.
     */
    fun onCameraChanged(
        lat: Double, lng: Double, zoom: Double, bearing: Double, tilt: Double,
        north: Double, south: Double, east: Double, west: Double,
    ) {
        saveCameraPosition(lat, lng, zoom, bearing, tilt)

        if (zoom < NEARBY_POI_MIN_ZOOM) {
            googleRefreshJob?.cancel()
            osmRefreshJob?.cancel()
            bulkRefreshJob?.cancel()
            googlePlacesRepository.clear()
            osmPoiRepository.clear()
            bulkPoiRepository.clear()
            lastDispatchedBounds = null
            return
        }

        // Skip re-fetching if the viewport hasn't meaningfully changed (e.g. returning from a
        // POI detail screen — same map position, no new data to load).
        // Epsilon of 0.001° ≈ 110 m — well above MapLibre's floating-point jitter on resume.
        val newBounds = FetchedBounds(north, south, east, west)
        val prev = lastDispatchedBounds
        if (prev != null &&
            kotlin.math.abs(newBounds.north - prev.north) < BOUNDS_EPSILON &&
            kotlin.math.abs(newBounds.south - prev.south) < BOUNDS_EPSILON &&
            kotlin.math.abs(newBounds.east  - prev.east)  < BOUNDS_EPSILON &&
            kotlin.math.abs(newBounds.west  - prev.west)  < BOUNDS_EPSILON
        ) {
            android.util.Log.d("MainViewModel", "onCameraChanged: SKIPPED — bounds unchanged (within ${BOUNDS_EPSILON}°)")
            return
        }
        android.util.Log.d("MainViewModel", "onCameraChanged: PROCEEDING — zoom=$zoom prev=$prev new=$newBounds")
        lastDispatchedBounds = newBounds

        googleRefreshJob?.cancel()
        googleRefreshJob = viewModelScope.launch {
            delay(GOOGLE_PLACES_FETCH_DEBOUNCE_MS)
            googlePlacesRepository.refreshForViewport(north, south, east, west, zoom)
        }

        osmRefreshJob?.cancel()
        osmRefreshJob = viewModelScope.launch {
            delay(OSM_FETCH_DEBOUNCE_MS)
            osmPoiRepository.refreshForViewport(north, south, east, west, zoom)
        }

        val allGroups = groups.value
        val bulkGroups = allGroups.filter { it.isBulk && it.importComplete }
        android.util.Log.d("MainViewModel", "onCameraChanged: zoom=$zoom, total groups=${allGroups.size}, bulk+complete=${bulkGroups.size}")
        allGroups.filter { it.isBulk }.forEach { g ->
            android.util.Log.d("MainViewModel", "  bulk group '${g.name}' importComplete=${g.importComplete} isVisible=${g.isVisible}")
        }
        if (bulkGroups.isNotEmpty()) {
            bulkRefreshJob?.cancel()
            bulkRefreshJob = viewModelScope.launch {
                bulkPoiRepository.refreshForViewport(bulkGroups, north, south, east, west)
            }
        }
    }
}