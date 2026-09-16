package com.mappingsolution.desktop

import com.mappingsolution.data.fs.GroupFileRepository
import com.mappingsolution.data.fs.PoiFileRepository
import com.mappingsolution.data.fs.RouteFileRepository
import com.mappingsolution.data.prefs.HillshadePreference
import com.mappingsolution.data.prefs.MapStylePreference
import com.mappingsolution.data.prefs.ViewportPreference
import com.mappingsolution.data.util.ApiKeys
import com.mappingsolution.data.util.AppLog
import com.mappingsolution.data.util.StorageManager

/** Manual dependency wiring for the desktop app (Android uses Hilt for the same graph). */
internal class AppContainer {
    init {
        AppLog.d("AppContainer", "Data folder: ${DesktopPaths.dataDir}")
    }

    val apiKeys = ApiKeys(mapTiler = BuildConfig.MAPTILER_API_KEY, mapillary = BuildConfig.MAPILLARY_ACCESS_TOKEN)
    val storageManager = StorageManager(DesktopPaths.dataDir, DesktopPaths.cacheDir)
    private val stores = PreferencesStore.factory

    val groupRepository = GroupFileRepository(storageManager, stores)
    val poiRepository = PoiFileRepository(storageManager)
    val routeRepository = RouteFileRepository(storageManager)

    val mapStylePreference = MapStylePreference(stores)
    val hillshadePreference = HillshadePreference(stores)
    val viewportPreference = ViewportPreference(stores)
}
