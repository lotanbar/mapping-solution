package com.mappingsolution.data.prefs

import com.mappingsolution.data.util.KeyValueStore
import com.mappingsolution.data.map.MapStyle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapStylePreference @Inject constructor(
    stores: KeyValueStore.Factory,
) {
    private val prefs = stores.open("map_style")

    fun save(style: MapStyle) {
        prefs.putString("style", style.name)
    }

    fun load(): MapStyle =
        prefs.getString("style", null)
            ?.let { runCatching { MapStyle.valueOf(it) }.getOrNull() }
            ?: MapStyle.SATELLITE
}
