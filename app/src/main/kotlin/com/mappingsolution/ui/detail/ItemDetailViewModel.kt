package com.mappingsolution.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mappingsolution.data.fs.BulkPoiRepository
import com.mappingsolution.data.fs.GroupFileRepository
import com.mappingsolution.data.fs.PoiFileRepository
import com.mappingsolution.data.fs.RouteFileRepository
import com.mappingsolution.data.model.DestinationSource
import com.mappingsolution.data.model.Group
import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.model.Route
import com.mappingsolution.data.places.GooglePlacesRepository
import com.mappingsolution.data.places.OsmPoiRepository
import com.mappingsolution.data.places.PlaceDetail
import com.mappingsolution.data.util.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface DetailItem {
    data class PoiDetail(
        val poi: Poi,
        val group: Group?,
        val mediaPaths: List<String>,
        val isReadOnly: Boolean = false,
        val sourceType: DestinationSource = DestinationSource.PERSONAL,
    ) : DetailItem

    data class RouteDetail(val route: Route) : DetailItem
}

data class ItemDetailState(
    val item: DetailItem? = null,
    val isLoading: Boolean = true,
    val website: String? = null,
    val phone: String? = null,
    val isContactInfoLoading: Boolean = false,
)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val poiRepository: PoiFileRepository,
    private val bulkPoiRepository: BulkPoiRepository,
    private val routeRepository: RouteFileRepository,
    private val groupRepository: GroupFileRepository,
    private val googlePlacesRepository: GooglePlacesRepository,
    private val osmPoiRepository: OsmPoiRepository,
    private val storageManager: StorageManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val type: String = requireNotNull(savedStateHandle.get<String>("type"))
    private val id: String = requireNotNull(savedStateHandle.get<String>("id"))

    private val _state = MutableStateFlow(ItemDetailState())
    val state: StateFlow<ItemDetailState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            when (type) {
                "poi" -> loadPoi()
                "route" -> loadRoute()
                "google_place" -> loadGooglePlace()
                "osm_poi" -> loadOsmPoi()
                else -> _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadPoi() {
        val personalPoi = poiRepository.getById(id)
        val poi = personalPoi ?: bulkPoiRepository.getById(id) ?: run {
            _state.update { it.copy(isLoading = false) }
            return
        }
        val isBulk = personalPoi == null
        val group = poi.groupId?.let { groupRepository.getById(it) }
        val mediaDir = storageManager.getPoiMediaDir(poi.name, poi.id)
        val absolutePaths = when {
            isBulk && group?.sourceZipPath != null -> {
                // Zip-backed import: resolve filenames to zip:// URIs for on-demand loading
                val zipPath = group.sourceZipPath
                poi.mediaPaths.map { filename ->
                    com.mappingsolution.data.image.ZipImageFetcher.uriFor(zipPath, filename).toString()
                }
            }
            isBulk -> {
                // File-backed import: scan the per-group media directory
                mediaDir.listFiles()?.sortedBy { it.name }?.map { it.absolutePath } ?: emptyList()
            }
            else -> {
                poi.mediaPaths.map { filename -> mediaDir.absolutePath + "/" + filename }
            }
        }
        _state.update {
            ItemDetailState(
                item = DetailItem.PoiDetail(
                    poi = poi,
                    group = group,
                    mediaPaths = absolutePaths,
                    isReadOnly = isBulk,
                    sourceType = if (isBulk) DestinationSource.IMPORTED else DestinationSource.PERSONAL,
                ),
                isLoading = false,
            )
        }
    }

    private suspend fun loadGooglePlace() {
        val poi = googlePlacesRepository.getById(id) ?: run {
            _state.update { it.copy(isLoading = false) }
            return
        }
        val group = poi.groupId?.let { groupRepository.getById(it) }
        val detail = runCatching {
            googlePlacesRepository.fetchPlaceDetail(id)
        }.getOrElse { PlaceDetail() }
        _state.update {
            ItemDetailState(
                item = DetailItem.PoiDetail(
                    poi = poi,
                    group = group,
                    mediaPaths = detail.photoUrls,
                    isReadOnly = true,
                    sourceType = DestinationSource.GOOGLE,
                ),
                isLoading = false,
                website = detail.contact?.website,
                phone = detail.contact?.phone,
                isContactInfoLoading = false,
            )
        }
    }

    private suspend fun loadOsmPoi() {
        val poi = osmPoiRepository.getById(id) ?: run {
            _state.update { it.copy(isLoading = false) }
            return
        }
        val group = poi.groupId?.let { groupRepository.getById(it) }
        val imageUrl = runCatching { osmPoiRepository.fetchImageUrl(id) }.getOrElse { null }
        _state.update {
            ItemDetailState(
                item = DetailItem.PoiDetail(
                    poi = poi,
                    group = group,
                    mediaPaths = listOfNotNull(imageUrl),
                    isReadOnly = true,
                    sourceType = DestinationSource.OSM,
                ),
                isLoading = false,
                isContactInfoLoading = true,
            )
        }
        val contact = runCatching {
            googlePlacesRepository.findContactInfoNear(poi.name, poi.lat, poi.lng)
        }.getOrElse { null }
        _state.update { it.copy(website = contact?.website, phone = contact?.phone, isContactInfoLoading = false) }
    }

    private suspend fun loadRoute() {
        val route = routeRepository.getById(id) ?: run {
            _state.update { it.copy(isLoading = false) }
            return
        }
        _state.update {
            ItemDetailState(item = DetailItem.RouteDetail(route = route), isLoading = false)
        }
    }

    fun deletePoi(onDeleted: () -> Unit) {
        val detail = (_state.value.item as? DetailItem.PoiDetail) ?: return
        viewModelScope.launch {
            poiRepository.delete(detail.poi)
            onDeleted()
        }
    }

    fun deleteRoute(onDeleted: () -> Unit) {
        val detail = (_state.value.item as? DetailItem.RouteDetail) ?: return
        viewModelScope.launch {
            routeRepository.deleteByIds(listOf(detail.route.id))
            onDeleted()
        }
    }

    fun removePoiMediaItem(index: Int) {
        val detail = (_state.value.item as? DetailItem.PoiDetail) ?: return
        viewModelScope.launch {
            val newPaths = detail.mediaPaths.toMutableList()
            if (index in newPaths.indices) {
                val pathToRemove = newPaths.removeAt(index)
                runCatching { File(pathToRemove).delete() }
                val newFilenames = newPaths.map { File(it).name }
                poiRepository.update(detail.poi.copy(mediaPaths = newFilenames))
                _state.update {
                    it.copy(item = detail.copy(mediaPaths = newPaths))
                }
            }
        }
    }
}
