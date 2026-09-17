package com.mappingsolution.desktop

import com.mappingsolution.data.fs.BulkPoiRepository
import com.mappingsolution.data.fs.ExportRepository
import com.mappingsolution.data.fs.GroupFileRepository
import com.mappingsolution.data.fs.ImportRepository
import com.mappingsolution.data.fs.PlanFileRepository
import com.mappingsolution.data.fs.PoiFileRepository
import com.mappingsolution.data.fs.RasterLayerRepository
import com.mappingsolution.data.fs.RouteFileRepository
import com.mappingsolution.data.map.MapLayersState
import com.mappingsolution.data.map.SearchPreviewState
import com.mappingsolution.data.places.OsmApiService
import com.mappingsolution.data.places.OsmPoiCache
import com.mappingsolution.data.places.OsmPoiRepository
import com.mappingsolution.data.places.ViewportPoiLoader
import com.mappingsolution.data.places.WikimediaRepository
import com.mappingsolution.data.prefs.HillshadePreference
import com.mappingsolution.data.prefs.MapStylePreference
import com.mappingsolution.data.prefs.ViewportPreference
import com.mappingsolution.data.recording.RecordingRepository
import com.mappingsolution.data.search.SearchRepository
import com.mappingsolution.data.recording.processing.OsmRoadCache
import com.mappingsolution.data.util.ApiKeys
import com.mappingsolution.data.util.AppLog
import com.mappingsolution.data.util.StorageManager
import com.mappingsolution.ui.library.GroupFormViewModel
import com.mappingsolution.ui.library.LibraryViewModel
import com.mappingsolution.ui.recording.RouteFinalizeViewModel
import com.mappingsolution.ui.searchnplan.SearchNPlanViewModel
import com.mappingsolution.ui.poi.PoiScreenArgs
import com.mappingsolution.ui.poi.UnifiedPoiViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Manual dependency wiring for the desktop app (Android uses Hilt for the same graph). */
internal class AppContainer {
    init {
        AppLog.d("AppContainer", "Data folder: ${DesktopPaths.dataDir}")
    }

    val apiKeys = ApiKeys(mapTiler = BuildConfig.MAPTILER_API_KEY, mapillary = BuildConfig.MAPILLARY_ACCESS_TOKEN)
    val storageManager = StorageManager(DesktopPaths.dataDir, DesktopPaths.cacheDir)
    private val stores = PreferencesStore.factory
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    val groupRepository = GroupFileRepository(storageManager, stores)
    val poiRepository = PoiFileRepository(storageManager)
    val routeRepository = RouteFileRepository(storageManager)
    val planRepository = PlanFileRepository(storageManager)
    val bulkPoiRepository = BulkPoiRepository(storageManager)
    val rasterLayerRepository = RasterLayerRepository(storageManager)
    val exportRepository = ExportRepository(File(DesktopPaths.dataDir, "exports"), poiRepository, routeRepository)
    val importRepository = ImportRepository(groupRepository, poiRepository, routeRepository, storageManager)
    val wikimediaRepository = WikimediaRepository(storageManager, httpClient, apiKeys)
    private val osmApiService = OsmApiService(httpClient)
    val osmPoiRepository = OsmPoiRepository(osmApiService, OsmPoiCache(storageManager), wikimediaRepository)
    val searchRepository = SearchRepository(poiRepository, bulkPoiRepository, groupRepository, osmApiService)
    val searchPreviewState = SearchPreviewState()
    val recordingRepository = RecordingRepository(routeRepository, OsmRoadCache(httpClient))

    val mapStylePreference = MapStylePreference(stores)
    val hillshadePreference = HillshadePreference(stores)
    val viewportPreference = ViewportPreference(stores)
    val mapLayersState = MapLayersState(mapStylePreference, hillshadePreference, rasterLayerRepository)

    /** Application-lifetime scope for background loading shared across screens. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val viewportPoiLoader = ViewportPoiLoader(osmPoiRepository, bulkPoiRepository, appScope)

    init {
        appScope.launch { osmPoiRepository.evictStaleCacheOnLaunch() }
    }

    val libraryJobs = DesktopLibraryJobs(importRepository, recordingRepository, rasterLayerRepository, storageManager)

    fun newPoiViewModel(args: PoiScreenArgs) = UnifiedPoiViewModel(
        poiRepository = poiRepository,
        bulkPoiRepository = bulkPoiRepository,
        groupRepository = groupRepository,
        osmPoiRepository = osmPoiRepository,
        storageManager = storageManager,
        args = args,
        openPhoto = { path -> File(path).inputStream() },
    )

    fun newGroupFormViewModel(groupId: String?) = GroupFormViewModel(groupRepository, groupId)

    fun newRouteFinalizeViewModel() = RouteFinalizeViewModel(routeRepository, libraryJobs)

    /** Live search screens by navigation instance, so POI details can add destinations to them. */
    val searchViewModels = mutableMapOf<Long, SearchNPlanViewModel>()

    fun newSearchViewModel(planId: String?) = SearchNPlanViewModel(
        searchRepository = searchRepository,
        planRepository = planRepository,
        loadCamera = viewportPreference::load,
        searchPreviewState = searchPreviewState,
        osmPoiRepository = osmPoiRepository,
        loadedPlanId = planId,
    )

    fun newLibraryViewModel() = LibraryViewModel(
        groupRepository, poiRepository, routeRepository, planRepository, exportRepository,
        osmPoiRepository, bulkPoiRepository, mapLayersState, rasterLayerRepository, libraryJobs,
    )
}
