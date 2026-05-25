package com.mappingsolution.data.places

import android.util.Log
import com.mappingsolution.data.model.Poi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OsmPoiRepository @Inject constructor(
    private val api: OsmApiService,
    private val cache: OsmPoiCache,
) {

    private val _pois = MutableStateFlow<List<Poi>>(emptyList())
    val pois: StateFlow<List<Poi>> = _pois.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    @Volatile private var lastFetchedBounds: FetchedBounds? = null
    @Volatile private var lastFetchedZoom: Double? = null

    fun getById(id: String): Poi? = _pois.value.find { it.id == id }

    /** Merges POIs returned from a text search so the detail screen can look them up by ID. */
    fun registerSearchPois(pois: List<Poi>) {
        if (pois.isEmpty()) return
        val existing = _pois.value.associateBy { it.id }.toMutableMap()
        pois.forEach { existing.putIfAbsent(it.id, it) }
        _pois.value = existing.values.toList()
    }

    /**
     * Fetches OSM natural/historic POIs for the given viewport bounds using strip-based fetching.
     * Cache TTL is 30 days — natural features don't move.
     *
     * Only the sub-regions of the new viewport NOT already seen this session are queried
     * (see [computeNewStrips]).  Zoom-in always triggers a full fetch of the smaller viewport
     * so the user gets more detail.  POIs in the scrolling overlap are preserved from
     * [pois] without re-fetching, preventing pins from appearing in already-seen areas.
     */
    suspend fun refreshForViewport(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        zoom: Double,
    ) = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true

            // Zoom-out by more than 1 level: discard high-zoom POIs so the user sees a clean low-zoom fetch.
            val prevZoom = lastFetchedZoom
            if (prevZoom != null && zoom < prevZoom - 1.0) {
                android.util.Log.d("OsmPoiRepo", "refreshForViewport: zoom decreased from $prevZoom → $zoom, clearing POIs")
                _pois.value = emptyList()
                lastFetchedBounds = null
            }
            val centerLat = (north + south) / 2.0
            val centerLng = (east + west) / 2.0
            val cacheKey = "%.2f_%.2f".format(centerLat, centerLng)
            val currentBounds = FetchedBounds(north, south, east, west)
            val prevBounds = lastFetchedBounds

            val cached = cache.load(cacheKey)
            if (cached != null && cached.covers(south, west, north, east)) {
                _pois.value = mergeWithExisting(
                    cached.pois.filter { it.lat in south..north && it.lng in west..east },
                    south, north, east, west,
                )
                lastFetchedBounds = currentBounds
                lastFetchedZoom = zoom
                return@withContext
            }

            val strips = computeNewStrips(currentBounds, prevBounds)
            if (strips.isEmpty()) {
                val cachedPois = cached?.pois
                    ?.filter { it.lat in south..north && it.lng in west..east }
                    ?: emptyList()
                _pois.value = mergeWithExisting(cachedPois, south, north, east, west)
                lastFetchedBounds = currentBounds
                lastFetchedZoom = zoom
                return@withContext
            }

            val allStripPois = mutableListOf<Poi>()
            for (strip in strips) {
                val stripPois = runCatching {
                    api.fetchPois(strip.south, strip.west, strip.north, strip.east)
                }.getOrElse { e -> Log.e("OsmPoiRepo", "Strip fetch failed", e); emptyList() }
                allStripPois += stripPois
            }

            // Deduplicate: cached + existing in-viewport + freshly fetched strip POIs.
            val existingInViewport = _pois.value.filter { it.lat in south..north && it.lng in west..east }
            val combined = (
                (cached?.pois ?: emptyList()).associateBy { it.id } +
                existingInViewport.associateBy { it.id } +
                allStripPois.associateBy { it.id }
            ).values.toList()

            // Store with full viewport bounds so the coverage check passes on return visits.
            cache.store(cacheKey, combined, south, west, north, east)
            _pois.value = mergeWithExisting(
                combined.filter { it.lat in south..north && it.lng in west..east },
                south, north, east, west,
            )
            lastFetchedBounds = currentBounds
            lastFetchedZoom = zoom
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Merges [newPois] with whichever existing POIs are still inside the current
     * viewport, then filters the union to the current viewport.
     * For duplicate IDs, prefers whichever copy has a resolved iconKey so that
     * a cache miss (iconKey = null) never overwrites a freshly-resolved icon.
     */
    private fun mergeWithExisting(
        newPois: List<Poi>,
        south: Double,
        north: Double,
        east: Double,
        west: Double,
    ): List<Poi> {
        val existingById = _pois.value.associateBy { it.id }
        val newById = newPois.associateBy { it.id }
        return (existingById + newById)
            .mapValues { (id, poi) ->
                val existing = existingById[id]
                if (poi.iconKey == null && existing?.iconKey != null) existing else poi
            }
            .values
            .filter { it.lat in south..north && it.lng in west..east }
    }

    /** Clears in-memory POIs and resets session tracking (e.g. when zoomed below threshold). */
    fun clear() {
        _pois.value = emptyList()
        lastFetchedBounds = null
        lastFetchedZoom = null
    }

    /** Called once on app launch to purge stale cache files. Does not refetch. */
    suspend fun evictStaleCacheOnLaunch() = withContext(Dispatchers.IO) {
        runCatching { cache.evictStale() }
            .onFailure { Log.w("OsmPoiRepo", "Cache eviction failed", it) }
    }
}

