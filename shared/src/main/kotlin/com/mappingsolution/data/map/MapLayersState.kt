package com.mappingsolution.data.map

import com.mappingsolution.data.fs.RasterLayerRepository
import com.mappingsolution.data.prefs.HillshadePreference
import com.mappingsolution.data.prefs.MapStylePreference
import com.mappingsolution.data.model.RasterLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide singleton that owns the reactive map layer state:
 * - [mapStyle]: which base map is active (satellite vs. vector)
 * - [hillshadeVisible]: whether the hillshade overlay is shown
 * - [rasterLayers]: imported MBTiles layers with individual visibility flags
 *
 * Both [com.mappingsolution.ui.main.MainViewModel] and
 * [com.mappingsolution.ui.library.LibraryViewModel] inject this so changes
 * made in the Library propagate instantly to the map.
 */
@Singleton
class MapLayersState @Inject constructor(
    private val mapStylePreference: MapStylePreference,
    private val hillshadePreference: HillshadePreference,
    private val rasterLayerRepository: RasterLayerRepository,
) {
    val mapStyle: MutableStateFlow<MapStyle> = MutableStateFlow(mapStylePreference.load())
    val hillshadeVisible: MutableStateFlow<Boolean> = MutableStateFlow(hillshadePreference.load())

    val rasterLayers: StateFlow<List<RasterLayer>> = rasterLayerRepository.observeAll()
        .let { flow ->
            val state = MutableStateFlow<List<RasterLayer>>(emptyList())
            CoroutineScope(Dispatchers.Default).launch {
                flow.collect { state.value = it }
            }
            state.asStateFlow()
        }

    /** True when no imported raster layer is visible — base map should be shown. */
    val baseMapVisible: StateFlow<Boolean> = rasterLayerRepository.observeAll()
        .map { layers -> layers.none { it.isVisible } }
        .let { flow ->
            val state = MutableStateFlow(true)
            CoroutineScope(Dispatchers.Default).launch {
                flow.collect { state.value = it }
            }
            state.asStateFlow()
        }

    fun setMapStyle(style: MapStyle) {
        mapStyle.value = style
        mapStylePreference.save(style)
        // Deactivate all imported raster layers — only one map layer active at a time
        CoroutineScope(Dispatchers.IO).launch {
            rasterLayers.value.filter { it.isVisible }.forEach { l ->
                rasterLayerRepository.update(l.copy(isVisible = false))
            }
        }
    }

    fun setHillshadeVisible(visible: Boolean) {
        hillshadeVisible.value = visible
        hillshadePreference.save(visible)
    }

    fun toggleRasterLayerVisibility(id: String) {
        val layer = rasterLayerRepository.findById(id) ?: return
        // Radio-button: tapping an already-visible layer does nothing.
        if (layer.isVisible) return
        CoroutineScope(Dispatchers.IO).launch {
            // Turn on the tapped layer, turn all others off.
            rasterLayers.value.forEach { l ->
                val targetVisible = l.id == id
                if (l.isVisible != targetVisible) {
                    rasterLayerRepository.update(l.copy(isVisible = targetVisible))
                }
            }
        }
    }
}
