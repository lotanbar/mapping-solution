package com.mappingsolution.data.places

import android.util.Log
import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.prefs.GooglePoiCategoryPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sqrt

private const val QUOTA_RESET_WINDOW_MS = 24L * 60 * 60 * 1000   // 24 h circuit-breaker window

@Singleton
class GooglePlacesRepository @Inject constructor(
    private val api: PlacesApiService,
    private val cache: GooglePlacesCache,
    private val categoryPreference: GooglePoiCategoryPreference,
) {

    private val _pois = MutableStateFlow<List<Poi>>(emptyList())
    val pois: StateFlow<List<Poi>> = _pois.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isQuotaExhausted = MutableStateFlow(false)
    val isQuotaExhausted: StateFlow<Boolean> = _isQuotaExhausted.asStateFlow()

    @Volatile private var quotaExhaustedUntil: Long = 0L
    @Volatile private var lastFetchedBounds: FetchedBounds? = null
    @Volatile private var lastFetchedZoom: Double? = null

    private val detailCache = ConcurrentHashMap<String, PlaceDetail>()

    fun getById(id: String): Poi? = _pois.value.find { it.id == id }

    /** Merges POIs returned from a text search so the detail screen can look them up by ID. */
    fun registerSearchPois(pois: List<Poi>) {
        if (pois.isEmpty()) return
        val existing = _pois.value.associateBy { it.id }.toMutableMap()
        pois.forEach { existing.putIfAbsent(it.id, it) }
        _pois.value = existing.values.toList()
    }

    /**
     * Fetches photo URLs and contact info for a place in a single API call, cached in memory.
     */
    suspend fun fetchPlaceDetail(placeId: String): PlaceDetail = withContext(Dispatchers.IO) {
        detailCache[placeId]?.let { return@withContext it }
        val detail = api.fetchPlaceDetail(placeId)
        detailCache[placeId] = detail
        detail
    }

    /**
     * Searches for a Google Place matching [name] near [lat]/[lng] (100 m radius),
     * then fetches its contact details. Returns null if no match or fetch fails.
     * Used to enrich OSM POIs with website/phone data.
     */
    suspend fun findContactInfoNear(
        name: String,
        lat: Double,
        lng: Double,
    ): PlaceContactInfo? = withContext(Dispatchers.IO) {
        val matches = runCatching {
            api.searchText(name, lat, lng, radiusMeters = 100.0)
        }.getOrElse { return@withContext null }
        val placeId = matches.firstOrNull()?.id ?: return@withContext null
        api.fetchPlaceContactInfo(placeId)
    }

    /**
     * Fetches Google Places POIs for the given viewport bounds using strip-based fetching.
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
        Log.d("GooglePlacesRepo", "refreshForViewport: zoom=$zoom N=$north S=$south E=$east W=$west")
        val showDiscovery = categoryPreference.showDiscovery.value
        val showOther = categoryPreference.showOther.value
        if (!showDiscovery && !showOther) {
            clear()
            return@withContext
        }
        // Circuit breaker: stop all calls while quota is exhausted.
        if (System.currentTimeMillis() < quotaExhaustedUntil) {
            Log.w("GooglePlacesRepo", "Quota exhausted — skipping fetch until reset")
            return@withContext
        }
        try {
            _isLoading.value = true
            val displayLimit = googlePoiLimitForZoom(zoom)

            // Any meaningful zoom-out replaces the detailed set instead of preserving it.
            val prevZoom = lastFetchedZoom
            val zoomedOut = prevZoom != null && zoom < prevZoom - 0.15
            if (zoomedOut) {
                android.util.Log.d("GooglePlacesRepo", "refreshForViewport: zoom decreased from $prevZoom → $zoom, clearing POIs")
                _pois.value = emptyList()
                lastFetchedBounds = null
            }
            val centerLat = (north + south) / 2.0
            val centerLng = (east + west) / 2.0
            val cacheKey = "%.2f_%.2f_%s".format(centerLat, centerLng, categoryPreference.modeKey())
            val currentBounds = FetchedBounds(north, south, east, west)
            val prevBounds = if (zoomedOut) null else lastFetchedBounds

            val cached = cache.load(cacheKey)
            val cachedInView = cached
                ?.filter { it.lat in south..north && it.lng in west..east }
                ?: emptyList()

            // A zoom-out must fetch popularity-ranked results for the new, wider area.
            val rankedCached = if (zoomedOut) emptyList() else cachedInView.take(displayLimit)
            val remaining = maxOf(0, displayLimit - rankedCached.size)
            if (remaining == 0) {
                _pois.value = rankedCached
                lastFetchedBounds = currentBounds
                lastFetchedZoom = zoom
                return@withContext
            }

            // Compute only the sub-regions not yet seen in this session.
            val strips = computeNewStrips(currentBounds, prevBounds)
            Log.d("GooglePlacesRepo", "strips to fetch: ${strips.size} (prevBounds=$prevBounds)")
            if (strips.isEmpty()) {
                _pois.value = if (zoomedOut) rankedCached else
                    mergeWithExisting(rankedCached, south, north, east, west).take(displayLimit)
                lastFetchedBounds = currentBounds
                lastFetchedZoom = zoom
                return@withContext
            }

            // Distribute remaining quota evenly across strips; each strip → one API call (max 20).
            val existingInViewport = if (zoomedOut) emptyList() else
                _pois.value.filter { it.lat in south..north && it.lng in west..east }
            val quota = maxOf(0, displayLimit - existingInViewport.size)
            val maxPerStrip = minOf(20, ceil(quota.toDouble() / strips.size).toInt())

            Log.d("GooglePlacesRepo", "quota=$quota maxPerStrip=$maxPerStrip existingInViewport=${existingInViewport.size}")
            val allStripPois = mutableListOf<Poi>()
            if (maxPerStrip > 0) {
                for (strip in strips) {
                    val stripCenterLat = (strip.north + strip.south) / 2.0
                    val stripCenterLng = (strip.east + strip.west) / 2.0
                    val halfLatDeg = (strip.north - strip.south) / 2.0
                    val halfLngDeg = (strip.east - strip.west) / 2.0
                    val radiusMeters = sqrt(
                        (halfLatDeg * 111_000).let { it * it } +
                        (halfLngDeg * 111_000 * cos(Math.toRadians(stripCenterLat))).let { it * it }
                    )
                    try {
                        val cappedRadius = radiusMeters.coerceAtMost(50_000.0)
                        Log.d("GooglePlacesRepo", "fetching strip center=(${stripCenterLat},${stripCenterLng}) radius=$cappedRadius")
                        allStripPois += api.fetchNearby(
                            stripCenterLat, stripCenterLng, cappedRadius, maxPerStrip,
                            showDiscovery = showDiscovery,
                            showOther = showOther,
                        )
                    } catch (e: QuotaExceededException) {
                        Log.w("GooglePlacesRepo", "Quota exhausted — activating circuit breaker for 24 h")
                        quotaExhaustedUntil = System.currentTimeMillis() + QUOTA_RESET_WINDOW_MS
                        _isQuotaExhausted.value = true
                        break
                    } catch (e: Exception) {
                        Log.e("GooglePlacesRepo", "Strip fetch failed", e)
                    }
                }
            }

            // Deduplicate: cached + existing in-viewport + freshly fetched strip POIs.
            val combined = if (zoomedOut) {
                (allStripPois.associateBy { it.id } +
                    (cached ?: emptyList()).associateBy { it.id }).values.toList()
            } else {
                ((cached ?: emptyList()).associateBy { it.id } +
                    existingInViewport.associateBy { it.id } +
                    allStripPois.associateBy { it.id }).values.toList()
            }

            Log.d("GooglePlacesRepo", "fetch complete: ${allStripPois.size} new POIs, combined=${combined.size}, inViewport=${combined.count { it.lat in south..north && it.lng in west..east }}")
            cache.store(cacheKey, combined)
            val rankedInView = combined
                .filter { it.lat in south..north && it.lng in west..east }
                .take(displayLimit)
            _pois.value = if (zoomedOut) rankedInView else
                mergeWithExisting(rankedInView, south, north, east, west).take(displayLimit)
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
            .onFailure { Log.w("GooglePlacesRepo", "Cache eviction failed", it) }
    }
}

