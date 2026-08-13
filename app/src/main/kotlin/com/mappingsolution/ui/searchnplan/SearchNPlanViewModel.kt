package com.mappingsolution.ui.searchnplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mappingsolution.data.fs.PlanFileRepository
import com.mappingsolution.data.map.MapHolder
import com.mappingsolution.data.map.SearchPreviewState
import com.mappingsolution.data.model.DestinationSource
import com.mappingsolution.data.model.Plan
import com.mappingsolution.data.model.PlanDestination
import com.mappingsolution.data.model.SearchResult
import com.mappingsolution.data.places.OsmPoiRepository
import com.mappingsolution.data.search.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SearchNPlanViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val planRepository: PlanFileRepository,
    private val mapHolder: MapHolder,
    private val searchPreviewState: SearchPreviewState,
    private val osmPoiRepository: OsmPoiRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Non-null when this screen was opened from an existing Library plan. */
    private val loadedPlanId: String? = savedStateHandle.get<String>("planId")

    /** True when the screen was opened from the Library with pre-filled destinations. */
    val openedFromLibrary: Boolean = loadedPlanId != null

    val searchQuery = MutableStateFlow("")

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results.asStateFlow()

    val isLoading = MutableStateFlow(false)

    private val _destinations = MutableStateFlow<List<PlanDestination>>(emptyList())
    val destinations: StateFlow<List<PlanDestination>> = _destinations.asStateFlow()

    private val _activeRowIndex = MutableStateFlow<Int?>(null)
    val activeRowIndex: StateFlow<Int?> = _activeRowIndex.asStateFlow()

    /** Emits once when a plan is successfully saved — composable should navigate back. */
    private val _savedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val savedEvent: Flow<Unit> = _savedEvent

    private val _loadedPlanName = MutableStateFlow<String?>(null)
    val loadedPlanName: StateFlow<String?> = _loadedPlanName.asStateFlow()

    init {
        // Pre-fill destinations when opened from the Library
        loadedPlanId?.let { planId ->
            viewModelScope.launch {
                val plan = planRepository.getById(planId)
                plan?.let {
                    _destinations.value = it.destinations
                    _loadedPlanName.value = it.name
                }
            }
        }

        viewModelScope.launch {
            searchQuery.collectLatest { query ->
                if (query.length < 3) {
                    _results.value = emptyList()
                    isLoading.value = false
                    return@collectLatest
                }
                delay(300)
                val camera = mapHolder.loadCamera()
                val lat = camera?.lat ?: 0.0
                val lng = camera?.lng ?: 0.0
                isLoading.value = true
                try {
                    val results = searchRepository.search(
                        query = query,
                        userLat = lat,
                        userLng = lng,
                        viewSouth = lat - 0.5,
                        viewWest = lng - 0.5,
                        viewNorth = lat + 0.5,
                        viewEast = lng + 0.5,
                        skipRemote = camera == null,
                    )
                    _results.value = results
                    // Register remote results so ItemDetailViewModel can look them up by ID
                    osmPoiRepository.registerSearchPois(
                        results.filterIsInstance<SearchResult.OsmPoi>().map { it.poi }
                    )
                } finally {
                    isLoading.value = false
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        searchQuery.value = query
    }

    fun activateRow(index: Int, prefill: String = "") {
        _activeRowIndex.value = index
        searchQuery.value = prefill
        _results.value = emptyList()
        isLoading.value = false
    }

    fun deactivateRow() {
        _activeRowIndex.value = null
        searchQuery.value = ""
        _results.value = emptyList()
        isLoading.value = false
    }

    fun addDestination(result: SearchResult) {
        val activeIdx = _activeRowIndex.value ?: return
        val source = when (result) {
            is SearchResult.PersonalPoi -> DestinationSource.PERSONAL
            is SearchResult.ImportedPoi -> DestinationSource.IMPORTED
            is SearchResult.OsmPoi -> DestinationSource.OSM
        }
        val dest = PlanDestination(
            sourceType = source,
            sourceId = result.poi.id,
            name = result.poi.name,
            lat = result.poi.lat,
            lng = result.poi.lng,
        )
        _destinations.update { list ->
            if (activeIdx >= list.size) {
                list + dest
            } else {
                list.toMutableList().apply { set(activeIdx, dest) }
            }
        }
        deactivateRow()
    }

    fun removeDestination(id: String) {
        _destinations.update { list -> list.filter { it.id != id } }
    }

    /** Adds a destination returned from the ItemDetailScreen when fromSearch=true. */
    fun addDestinationFromDetail(dest: PlanDestination) {
        val activeIdx = _activeRowIndex.value
        _destinations.update { list ->
            if (activeIdx == null || activeIdx >= list.size) {
                list + dest
            } else {
                list.toMutableList().apply { set(activeIdx, dest) }
            }
        }
        deactivateRow()
    }

    fun moveDestination(from: Int, to: Int) {
        _destinations.update { list ->
            if (from !in list.indices || to !in list.indices) return@update list
            list.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    /** Updates the map preview pin to the tapped result's location. */
    fun selectResultForPreview(result: SearchResult) {
        searchPreviewState.previewLocation.value = result.poi.lat to result.poi.lng
    }

    /** Clears the map preview pin — called when this screen leaves the back stack. */
    fun clearPreview() {
        searchPreviewState.previewLocation.value = null
    }

    override fun onCleared() {
        super.onCleared()
        clearPreview()
    }

    /** Saves the destination list as a named plan. Emits [savedEvent] when done. */    fun savePlan(nameOverride: String? = null) {
        val name = nameOverride
            ?: _loadedPlanName.value
            ?: "Plan — ${SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date())}"
        viewModelScope.launch {
            val dests = _destinations.value
            if (loadedPlanId != null) {
                val existing = planRepository.getById(loadedPlanId)
                if (existing != null) {
                    planRepository.update(
                        existing.copy(
                            name = name,
                            destinations = dests,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                    _savedEvent.emit(Unit)
                    return@launch
                }
            }
            planRepository.insert(Plan(name = name, destinations = dests))
            _savedEvent.emit(Unit)
        }
    }
}

