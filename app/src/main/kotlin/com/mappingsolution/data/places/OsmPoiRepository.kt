package com.mappingsolution.data.places

import android.util.Log
import com.mappingsolution.data.model.Poi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OsmPoiRepository @Inject constructor(
    private val api: OsmApiService,
    private val cache: OsmPoiCache,
    private val wikimediaRepository: WikimediaRepository,
) {

    private val _pois = MutableStateFlow<List<Poi>>(emptyList())
    val pois: StateFlow<List<Poi>> = _pois.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val activeLoads = AtomicInteger(0)

    @Volatile private var lastFetchedBounds: FetchedBounds? = null
    @Volatile private var fetchedPois: List<Poi> = emptyList()

    fun getById(id: String): Poi? = _pois.value.find { it.id == id }

    /** Resolves reusable Wikimedia image/summary metadata for an OSM POI. */
    suspend fun fetchWikimediaContent(id: String): WikimediaContent? = withContext(Dispatchers.IO) {
        val poi = getById(id) ?: return@withContext null
        wikimediaRepository.getContent(poi)
    }

    /** Merges POIs returned from a text search so the detail screen can look them up by ID. */
    fun registerSearchPois(pois: List<Poi>) {
        if (pois.isEmpty()) return
        val existing = _pois.value.associateBy { it.id }.toMutableMap()
        pois.forEach { existing.putIfAbsent(it.id, it) }
        _pois.value = existing.values.toList()
    }

    /**
     * Fetches exploration POIs for the viewport, with buffered in-memory coverage and a 30-day
     * persistent cache. All matching POIs in the viewport are published without density or
     * count limits; the caller controls the minimum zoom.
     *
     * Only the sub-regions of the new viewport NOT already seen this session are queried
     * (see [computeNewStrips]). POIs in scrolling overlap are preserved without re-fetching.
     */
    suspend fun refreshForViewport(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        zoom: Double,
        includeNatural: Boolean = true,
    ): Boolean = withContext(Dispatchers.IO) {
        val totalStart = System.currentTimeMillis()
        var registeredLoad = false
        try {
            val currentBounds = FetchedBounds(north, south, east, west)
            val memoryBounds = lastFetchedBounds
            if (memoryBounds?.covers(currentBounds) == true) {
                _pois.value = fetchedPois.filter { it.lat in south..north && it.lng in west..east }
                Log.d("OsmPoiRepo", "MEMORY HIT: ${_pois.value.size} POIs — no network fetch")
                return@withContext true
            }

            activeLoads.incrementAndGet()
            registeredLoad = true
            _isLoading.value = true
            val centerLat = (north + south) / 2.0
            val centerLng = (east + west) / 2.0
            val cacheKey = "%.2f_%.2f_%s".format(
                centerLat,
                centerLng,
                if (includeNatural) "natural" else "historic",
            )
            val prevBounds = lastFetchedBounds

            Log.d("OsmPoiRepo", "refreshForViewport zoom=%.1f bounds=[N=${"%.4f".format(north)} S=${"%.4f".format(south)} E=${"%.4f".format(east)} W=${"%.4f".format(west)}] cacheKey=$cacheKey prevBounds=$prevBounds".format(zoom))

            val cacheStart = System.currentTimeMillis()
            // The center-keyed file is overwhelmingly the common hit. Trying it first avoids
            // reading and parsing every cache file on every camera idle event.
            val keyedCache = cache.load(cacheKey)
            val cached = keyedCache?.takeIf { it.covers(south, west, north, east) }
                ?: cache.loadCovering(south, west, north, east)
                ?: keyedCache
            Log.d("OsmPoiRepo", "cache.load took ${System.currentTimeMillis() - cacheStart}ms — hit=${cached != null} covers=${cached?.covers(south, west, north, east)}")

            if (cached != null && cached.covers(south, west, north, east)) {
                val filtered = cached.pois.filter { it.lat in south..north && it.lng in west..east }
                Log.d("OsmPoiRepo", "CACHE HIT: ${filtered.size} POIs in viewport (${cached.pois.size} in cache file) — total ${System.currentTimeMillis() - totalStart}ms")
                _pois.value = filtered
                fetchedPois = cached.pois
                lastFetchedBounds = FetchedBounds(
                    north = cached.fetchedNorth,
                    south = cached.fetchedSouth,
                    east = cached.fetchedEast,
                    west = cached.fetchedWest,
                )
                return@withContext true
            }

            // A nearby cache entry may not cover the entire viewport, but its overlap is still
            // useful. Render it immediately while only the missing area is fetched.
            val immediatelyVisible = (
                (cached?.pois ?: emptyList()).associateBy { it.id } +
                    fetchedPois.associateBy { it.id }
                ).values.filter { it.lat in south..north && it.lng in west..east }
            if (immediatelyVisible.isNotEmpty()) {
                _pois.value = immediatelyVisible
            }

            // Fetch a modest buffer around the screen so normal panning and returning from a
            // detail page stay inside memory instead of immediately hitting Overpass again.
            val fetchBounds = currentBounds.expanded(0.20)
            val strips = computeNewStrips(fetchBounds, prevBounds)
            Log.d("OsmPoiRepo", "computeNewStrips → ${strips.size} strip(s): ${strips.map { "[N=${"%.4f".format(it.north)} S=${"%.4f".format(it.south)} E=${"%.4f".format(it.east)} W=${"%.4f".format(it.west)}]" }}")

            if (strips.isEmpty()) {
                val cachedPois = cached?.pois
                    ?.filter { it.lat in south..north && it.lng in west..east }
                    ?: emptyList()
                Log.d("OsmPoiRepo", "No new strips needed; using ${cachedPois.size} cached POIs — total ${System.currentTimeMillis() - totalStart}ms")
                _pois.value = cachedPois
                lastFetchedBounds = currentBounds
                return@withContext true
            }

            val fetchStart = System.currentTimeMillis()
            val basePoisById = (
                (cached?.pois ?: emptyList()).associateBy { it.id } +
                    fetchedPois.associateBy { it.id }
                ).toMutableMap()
            val resultMutex = Mutex()
            val stripResults = coroutineScope {
                strips.mapIndexed { idx, strip ->
                    async {
                        val stripStart = System.currentTimeMillis()
                        val result = runCatching {
                            api.fetchPois(
                                strip.south, strip.west, strip.north, strip.east,
                                includeNatural = includeNatural,
                            )
                        }.getOrElse { e ->
                            if (e is CancellationException) throw e
                            Log.e("OsmPoiRepo", "Strip $idx fetch failed", e)
                            null
                        }
                        Log.d("OsmPoiRepo", "Strip $idx returned ${result?.size ?: "failure"} in ${System.currentTimeMillis() - stripStart}ms")

                        // Publish each completed strip instead of keeping the map empty until the
                        // slowest request finishes.
                        if (result != null) {
                            val visible = resultMutex.withLock {
                                result.forEach { basePoisById[it.id] = it }
                                basePoisById.values.filter {
                                    it.lat in south..north && it.lng in west..east
                                }
                            }
                            _pois.value = visible
                        }
                        result
                    }
                }.awaitAll()
            }

            // A network/server failure is not an empty map. Keep any existing pins and retry
            // later; most importantly, never cache the failed viewport as a valid empty result.
            if (stripResults.any { it == null }) {
                fetchedPois = basePoisById.values.toList()
                Log.w("OsmPoiRepo", "OSM fetch incomplete; preserving existing POIs and skipping cache")
                return@withContext false
            }
            val allStripPois = stripResults.filterNotNull().flatten()
            Log.d("OsmPoiRepo", "All ${strips.size} strip(s) fetched in ${System.currentTimeMillis() - fetchStart}ms — total raw POIs: ${allStripPois.size}")

            // Deduplicate: cached + existing in-viewport + freshly fetched strip POIs.
            val combined = (
                (cached?.pois ?: emptyList()).associateBy { it.id } +
                fetchedPois.associateBy { it.id } +
                allStripPois.associateBy { it.id }
            ).values.toList()

            val cacheWriteStart = System.currentTimeMillis()
            cache.store(
                cacheKey,
                combined,
                fetchBounds.south,
                fetchBounds.west,
                fetchBounds.north,
                fetchBounds.east,
            )
            Log.d("OsmPoiRepo", "cache.store took ${System.currentTimeMillis() - cacheWriteStart}ms — stored ${combined.size} POIs")

            val inViewport = combined.filter { it.lat in south..north && it.lng in west..east }
            _pois.value = inViewport
            fetchedPois = combined
            Log.d("OsmPoiRepo", "refreshForViewport DONE — ${inViewport.size} POIs shown, total time ${System.currentTimeMillis() - totalStart}ms")
            lastFetchedBounds = fetchBounds
            true
        } finally {
            if (registeredLoad && activeLoads.decrementAndGet() == 0) {
                _isLoading.value = false
            }
        }
    }

    /** Hides markers below the zoom threshold without throwing away reusable viewport data. */
    fun hide() {
        _pois.value = emptyList()
    }

    /** Fully clears in-memory POIs and session coverage. */
    fun clear() {
        _pois.value = emptyList()
        fetchedPois = emptyList()
        lastFetchedBounds = null
    }

    /** Called once on app launch to purge stale cache files. Does not refetch. */
    suspend fun evictStaleCacheOnLaunch() = withContext(Dispatchers.IO) {
        runCatching { cache.evictStale() }
            .onFailure { Log.w("OsmPoiRepo", "Cache eviction failed", it) }
    }
}

