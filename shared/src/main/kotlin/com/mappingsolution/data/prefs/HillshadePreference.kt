package com.mappingsolution.data.prefs

import com.mappingsolution.data.util.KeyValueStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HillshadePreference @Inject constructor(
    stores: KeyValueStore.Factory,
) {
    private val prefs = stores.open("map_layers")

    fun save(visible: Boolean) {
        prefs.putBoolean("hillshade_visible", visible)
    }

    fun load(): Boolean = prefs.getBoolean("hillshade_visible", true)
}
