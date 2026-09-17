package com.mappingsolution.data.places

import com.mappingsolution.data.fs.BulkPoiRepository
import com.mappingsolution.data.model.Group
import com.mappingsolution.data.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Loads OSM and imported (bulk) POIs for the visible map area whenever the camera settles.
 * Shared by the Android and desktop maps so both fetch with the same zoom gate and debounce.
 */
class ViewportPoiLoader(
    private val osmPoiRepository: OsmPoiRepository,
    private val bulkPoiRepository: BulkPoiRepository,
    private val scope: CoroutineScope,
) {
    private var osmRefreshJob: Job? = null
    private var bulkRefreshJob: Job? = null

    /** Last viewport bounds for which the OSM POI fetch actually succeeded. */
    @Volatile private var lastSuccessfulBounds: FetchedBounds? = null

    fun onCameraIdle(zoom: Double, north: Double, south: Double, east: Double, west: Double, groups: List<Group>) {
        if (zoom <= NEARBY_POI_MIN_ZOOM) {
            osmRefreshJob?.cancel()
            bulkRefreshJob?.cancel()
            osmPoiRepository.hide()
            bulkPoiRepository.clear()
            lastSuccessfulBounds = null
            return
        }

        // Skip re-fetching when the viewport hasn't meaningfully changed (e.g. returning from a
        // POI detail screen). 0.001° ≈ 110 m — well above MapLibre's floating-point jitter.
        val newBounds = FetchedBounds(north, south, east, west)
        val prev = lastSuccessfulBounds
        if (prev != null &&
            abs(newBounds.north - prev.north) < BOUNDS_EPSILON &&
            abs(newBounds.south - prev.south) < BOUNDS_EPSILON &&
            abs(newBounds.east - prev.east) < BOUNDS_EPSILON &&
            abs(newBounds.west - prev.west) < BOUNDS_EPSILON
        ) {
            AppLog.d(TAG, "onCameraIdle: SKIPPED — bounds unchanged (within $BOUNDS_EPSILON°)")
            return
        }
        AppLog.d(TAG, "onCameraIdle: PROCEEDING — zoom=$zoom prev=$prev new=$newBounds")
        osmRefreshJob?.cancel()
        osmRefreshJob = scope.launch {
            delay(OSM_FETCH_DEBOUNCE_MS)
            val succeeded = osmPoiRepository.refreshForViewport(north, south, east, west, zoom, includeNatural = true)
            if (succeeded) lastSuccessfulBounds = newBounds
        }

        val bulkGroups = groups.filter { it.isBulk && it.importComplete }
        if (bulkGroups.isNotEmpty()) {
            bulkRefreshJob?.cancel()
            bulkRefreshJob = scope.launch {
                bulkPoiRepository.refreshForViewport(bulkGroups, north, south, east, west)
            }
        }
    }

    private companion object {
        const val TAG = "ViewportPoiLoader"
        const val BOUNDS_EPSILON = 0.001
    }
}
