package com.mappingsolution.data.search

import com.mappingsolution.data.fs.BulkPoiRepository
import com.mappingsolution.data.fs.GroupFileRepository
import com.mappingsolution.data.fs.PoiFileRepository
import com.mappingsolution.data.model.SearchResult
import com.mappingsolution.data.places.OsmApiService
import com.mappingsolution.data.places.PlacesApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val poiFileRepository: PoiFileRepository,
    private val bulkPoiRepository: BulkPoiRepository,
    private val groupFileRepository: GroupFileRepository,
    private val placesApiService: PlacesApiService,
    private val osmApiService: OsmApiService,
) {

    /**
     * Fans out a text query to all 4 sources in parallel.
     *
     * Personal and Imported POIs are searched in-memory instantly.
     * OSM (Nominatim) and Google Places are queried over the network, biased to
     * [userLat]/[userLng] within a bounding box of roughly ±0.5°.
     *
     * Set [skipRemote] = true when no GPS fix is available, to avoid irrelevant
     * results near (0, 0).
     */
    suspend fun search(
        query: String,
        userLat: Double = 0.0,
        userLng: Double = 0.0,
        viewSouth: Double = -0.5,
        viewWest: Double = -0.5,
        viewNorth: Double = 0.5,
        viewEast: Double = 0.5,
        skipRemote: Boolean = false,
    ): List<SearchResult> = coroutineScope {
        val q = query.trim()

        val personal = async(Dispatchers.Default) {
            poiFileRepository.observeAll().first()
                .filter { it.name.contains(q, ignoreCase = true) }
                .sortedWith(compareBy(
                    { !it.name.startsWith(q, ignoreCase = true) },
                    { it.name.lowercase() },
                ))
                .map { SearchResult.PersonalPoi(it) }
        }

        val imported = async(Dispatchers.IO) {
            val bulkGroups = groupFileRepository.observeAll().first()
                .filter { it.isBulk && it.isVisible }
            bulkPoiRepository.searchByName(q, bulkGroups)
                .map { SearchResult.ImportedPoi(it) }
        }

        val osm = async(Dispatchers.IO) {
            if (skipRemote) return@async emptyList()
            try {
                osmApiService.searchText(q, viewSouth, viewWest, viewNorth, viewEast)
                    .map { SearchResult.OsmPoi(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        }

        val google = async(Dispatchers.IO) {
            if (skipRemote) return@async emptyList()
            try {
                placesApiService.searchText(q, userLat, userLng)
                    .map { SearchResult.GooglePlace(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        }

        personal.await() + imported.await() + osm.await() + google.await()
    }
}
