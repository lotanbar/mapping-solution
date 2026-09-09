package com.mappingsolution.ui.poi

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mappingsolution.data.fs.BulkPoiRepository
import com.mappingsolution.data.fs.GroupFileRepository
import com.mappingsolution.data.fs.PoiFileRepository
import com.mappingsolution.data.model.DestinationSource
import com.mappingsolution.data.model.Group
import com.mappingsolution.data.model.GroupType
import com.mappingsolution.data.model.MediaUtils
import com.mappingsolution.data.model.PlanDestination
import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.places.OSM_POI_GROUP_ID
import com.mappingsolution.data.places.OsmPoiRepository
import com.mappingsolution.data.places.WikimediaContent
import com.mappingsolution.data.util.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class UnifiedPoiMedia(
    val path: String,
    val isPersonal: Boolean,
)

data class UnifiedPoiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val kind: PoiScreenKind = PoiScreenKind.CREATION,
    val sourcePoi: Poi? = null,
    val bookmark: Poi? = null,
    val sourceGroup: Group? = null,
    val personalGroup: Group? = null,
    val sourceDescription: String = "",
    val personalNote: String = "",
    val media: List<UnifiedPoiMedia> = emptyList(),
    val wikimedia: WikimediaContent? = null,
    val isEditing: Boolean = false,
    val draftTitle: String = "",
    val draftDescription: String = "",
    val draftNote: String = "",
    val draftGroupId: String? = null,
    val draftPersonalMedia: List<String> = emptyList(),
    val titleError: String? = null,
    val error: String? = null,
) {
    val actions: PoiActionAvailability get() = poiActionAvailability(kind, isEditing)
    val isCreation: Boolean get() = kind == PoiScreenKind.CREATION
    val isCreated: Boolean get() = kind == PoiScreenKind.CREATED
    val isStarred: Boolean get() = kind == PoiScreenKind.STARRED_IMPORTED || kind == PoiScreenKind.STARRED_OSM
    val isExternal: Boolean get() = !isCreation && !isCreated
}

@HiltViewModel
class UnifiedPoiViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val poiRepository: PoiFileRepository,
    private val bulkPoiRepository: BulkPoiRepository,
    private val groupRepository: GroupFileRepository,
    private val osmPoiRepository: OsmPoiRepository,
    private val storageManager: StorageManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val requestedType = savedStateHandle.get<String>("type")
    private val requestedId = savedStateHandle.get<String>("id")
    private val creationLat = savedStateHandle.get<String>("lat")?.toDoubleOrNull() ?: 0.0
    private val creationLng = savedStateHandle.get<String>("lng")?.toDoubleOrNull() ?: 0.0

    private val _state = MutableStateFlow(UnifiedPoiState())
    val state: StateFlow<UnifiedPoiState> = _state.asStateFlow()

    val groups: StateFlow<List<Group>> = groupRepository.observeAll()
        .map { groups ->
            groups.filter { it.type == GroupType.POI && !it.isImported && !it.isBulk && it.id != OSM_POI_GROUP_ID }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            if (requestedId == null) loadCreation() else loadExisting(requestedType.orEmpty(), requestedId)
        }
    }

    private fun loadCreation() {
        val poi = Poi(id = "", name = "", lat = creationLat, lng = creationLng)
        _state.value = UnifiedPoiState(
            isLoading = false,
            kind = PoiScreenKind.CREATION,
            sourcePoi = poi,
            isEditing = true,
        )
    }

    private suspend fun loadExisting(type: String, id: String) {
        if (type == "osm_poi") {
            val osm = osmPoiRepository.getById(id)
            if (osm == null) return notFound()
            val bookmark = findBookmark(osm.id, DestinationSource.OSM)
            publishExternal(osm, DestinationSource.OSM, bookmark)
            enrichOsm(osm)
            return
        }

        val stored = poiRepository.getById(id)
        if (stored?.savedSource != null) {
            val source = loadBookmarkedSource(stored)
            publishExternal(source, stored.savedSource, stored)
            if (stored.savedSource == DestinationSource.OSM) {
                osmPoiRepository.registerSearchPois(listOf(source))
                enrichOsm(source)
            }
            return
        }

        val source = stored ?: bulkPoiRepository.getById(id) ?: return notFound()
        val sourceGroup = source.groupId?.let { groupRepository.getById(it) }
        val isImported = stored == null || sourceGroup?.isImported == true || sourceGroup?.isBulk == true
        if (isImported) {
            publishExternal(source, DestinationSource.IMPORTED, findBookmark(source.id, DestinationSource.IMPORTED))
        } else {
            publishCreated(source, sourceGroup)
        }
    }

    private suspend fun loadBookmarkedSource(bookmark: Poi): Poi {
        val sourceId = requireNotNull(bookmark.sourceId)
        return when (bookmark.savedSource) {
            DestinationSource.OSM -> osmPoiRepository.getById(sourceId)
            DestinationSource.IMPORTED -> poiRepository.getById(sourceId) ?: bulkPoiRepository.getById(sourceId)
            else -> null
        } ?: bookmark.copy(
            id = sourceId,
            groupId = bookmark.sourceGroupId,
            description = PoiDescriptionCodec.decode(bookmark.description).source.ifBlank { null },
            mediaPaths = emptyList(),
            savedSource = null,
            sourceId = null,
            sourceGroupId = null,
        )
    }

    private suspend fun findBookmark(sourceId: String, source: DestinationSource): Poi? =
        poiRepository.observeAll().first().find { it.sourceId == sourceId && it.savedSource == source }

    private suspend fun publishCreated(poi: Poi, group: Group?) {
        val media = personalMedia(poi).map { UnifiedPoiMedia(it, true) }
        _state.value = UnifiedPoiState(
            isLoading = false,
            kind = PoiScreenKind.CREATED,
            sourcePoi = poi,
            sourceGroup = group,
            personalGroup = group,
            sourceDescription = poi.description.orEmpty(),
            media = media,
            isEditing = false,
            draftTitle = poi.name,
            draftDescription = poi.description.orEmpty(),
            draftGroupId = poi.groupId,
            draftPersonalMedia = media.map { it.path },
        )
    }

    private suspend fun publishExternal(source: Poi, sourceType: DestinationSource, bookmark: Poi?) {
        val sourceGroup = source.groupId?.let { groupRepository.getById(it) }
        val parts = PoiDescriptionCodec.decode(bookmark?.description ?: source.description)
        val sourceDescription = source.description ?: parts.source
        val sourceMedia = sourceMedia(source, sourceGroup)
        val personalMedia = bookmark?.let { personalMedia(it) }.orEmpty()
        val starred = bookmark != null
        val kind = when (sourceType) {
            DestinationSource.OSM -> if (starred) PoiScreenKind.STARRED_OSM else PoiScreenKind.OSM
            else -> if (starred) PoiScreenKind.STARRED_IMPORTED else PoiScreenKind.IMPORTED
        }
        _state.value = UnifiedPoiState(
            isLoading = false,
            kind = kind,
            sourcePoi = source,
            bookmark = bookmark,
            sourceGroup = sourceGroup,
            personalGroup = bookmark?.groupId?.let { groupRepository.getById(it) },
            sourceDescription = sourceDescription.orEmpty(),
            personalNote = parts.personalNote,
            media = sourceMedia.map { UnifiedPoiMedia(it, false) } + personalMedia.map { UnifiedPoiMedia(it, true) },
            isEditing = false,
            draftTitle = source.name,
            draftNote = parts.personalNote,
            draftGroupId = bookmark?.groupId,
            draftPersonalMedia = personalMedia,
        )
    }

    private fun notFound() {
        _state.update { it.copy(isLoading = false, error = "POI not found") }
    }

    private fun personalMedia(poi: Poi): List<String> {
        val dir = storageManager.getPoiMediaDir(poi.name, poi.id)
        return poi.mediaPaths
            .filter(MediaUtils::isSupported)
            .map { File(dir, it).absolutePath }
            .filter { File(it).isFile }
    }

    private fun sourceMedia(poi: Poi, group: Group?): List<String> {
        val supported = poi.mediaPaths.filter(MediaUtils::isSupported)
        return when {
            group?.sourceZipPath != null -> supported.map {
                com.mappingsolution.data.image.ZipImageFetcher.uriFor(group.sourceZipPath, it).toString()
            }
            else -> {
                val dir = storageManager.getPoiMediaDir(poi.name, poi.id)
                supported.map { File(dir, it).absolutePath }.filter { File(it).isFile }
            }
        }
    }

    private fun enrichOsm(poi: Poi) {
        viewModelScope.launch {
            val content = runCatching { osmPoiRepository.fetchWikimediaContent(poi.id) }.getOrNull() ?: return@launch
            _state.update { current ->
                val description = current.sourceDescription.ifBlank { content.description.orEmpty() }
                val remoteImage = content.imageUrl?.let { UnifiedPoiMedia(it, false) }
                current.copy(
                    sourceDescription = description,
                    media = listOfNotNull(remoteImage) + current.media.filterNot { !it.isPersonal && it.path.startsWith("http") },
                    wikimedia = content,
                )
            }
        }
    }

    fun startEditing() {
        val current = _state.value
        if (!current.actions.editOrSave || current.isEditing) return
        _state.update {
            it.copy(
                isEditing = true,
                draftTitle = it.sourcePoi?.name.orEmpty(),
                draftDescription = it.sourceDescription,
                draftNote = it.personalNote,
                draftGroupId = it.bookmark?.groupId ?: if (it.isCreated) it.sourcePoi?.groupId else null,
                draftPersonalMedia = it.media.filter(UnifiedPoiMedia::isPersonal).map(UnifiedPoiMedia::path),
                titleError = null,
                error = null,
            )
        }
    }

    fun discardEditing() {
        val current = _state.value
        if (!current.isEditing || current.isCreation) return
        current.draftPersonalMedia
            .filter { it.startsWith(storageManager.getTempDir().absolutePath) }
            .forEach { runCatching { File(it).delete() } }
        _state.update {
            it.copy(
                isEditing = false,
                draftTitle = it.sourcePoi?.name.orEmpty(),
                draftDescription = it.sourceDescription,
                draftNote = it.personalNote,
                draftGroupId = it.bookmark?.groupId ?: if (it.isCreated) it.sourcePoi?.groupId else null,
                draftPersonalMedia = it.media.filter(UnifiedPoiMedia::isPersonal).map(UnifiedPoiMedia::path),
                titleError = null,
                error = null,
            )
        }
    }

    fun onTitleChange(value: String) = _state.update { it.copy(draftTitle = value, titleError = null) }
    fun onDescriptionChange(value: String) = _state.update { it.copy(draftDescription = value) }
    fun onNoteChange(value: String) = _state.update { it.copy(draftNote = value) }
    fun onGroupChange(value: String?) = _state.update { it.copy(draftGroupId = value) }

    fun addPhoto(uri: Uri, capturedFile: File? = null) {
        if (!_state.value.isEditing) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val target = File(storageManager.getTempDir(), "poi_photo_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Photo could not be opened" }
                    target.outputStream().use(input::copyTo)
                }
                capturedFile?.delete()
                _state.update { it.copy(draftPersonalMedia = it.draftPersonalMedia + target.absolutePath, error = null) }
            }.onFailure { error ->
                capturedFile?.delete()
                _state.update { it.copy(error = error.message ?: "Could not add photo") }
            }
        }
    }

    fun removeDraftPhoto(path: String) {
        if (!_state.value.isEditing) return
        _state.update { it.copy(draftPersonalMedia = it.draftPersonalMedia - path) }
        if (path.startsWith(storageManager.getTempDir().absolutePath)) runCatching { File(path).delete() }
    }

    fun save() {
        val current = _state.value
        if (!current.isEditing || current.isSaving) return
        if ((current.isCreation || current.isCreated) && current.draftTitle.isBlank()) {
            _state.update { it.copy(titleError = "Title is required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                if (current.isCreation) saveCreation(current)
                else if (current.isCreated) saveCreated(current)
                else saveBookmarkEdits(current)
            }.onFailure { error ->
                _state.update { it.copy(isSaving = false, error = error.message ?: "Could not save POI") }
            }
        }
    }

    private suspend fun saveCreation(current: UnifiedPoiState) {
        val draft = requireNotNull(current.sourcePoi).copy(
            id = UUID.randomUUID().toString(),
            name = current.draftTitle.trim(),
            description = current.draftDescription.trim().ifBlank { null },
            groupId = current.draftGroupId,
        )
        poiRepository.insert(draft)
        val filenames = finalizePersonalMedia(current.draftPersonalMedia, draft)
        val saved = draft.copy(mediaPaths = filenames)
        poiRepository.update(saved)
        publishCreated(saved, current.draftGroupId?.let { groupRepository.getById(it) })
    }

    private suspend fun saveCreated(current: UnifiedPoiState) {
        val existing = requireNotNull(current.sourcePoi)
        val filenames = finalizePersonalMedia(current.draftPersonalMedia, existing)
        deleteRemovedMedia(existing, filenames)
        val saved = existing.copy(
            name = current.draftTitle.trim(),
            description = current.draftDescription.trim().ifBlank { null },
            groupId = current.draftGroupId,
            mediaPaths = filenames,
        )
        poiRepository.update(saved)
        publishCreated(saved, current.draftGroupId?.let { groupRepository.getById(it) })
    }

    private suspend fun saveBookmarkEdits(current: UnifiedPoiState) {
        val existing = requireNotNull(current.bookmark)
        val filenames = finalizePersonalMedia(current.draftPersonalMedia, existing)
        deleteRemovedMedia(existing, filenames)
        val saved = existing.copy(
            description = PoiDescriptionCodec.encode(current.sourceDescription, current.draftNote),
            groupId = current.draftGroupId,
            mediaPaths = filenames,
        )
        poiRepository.update(saved)
        publishExternal(requireNotNull(current.sourcePoi), requireNotNull(saved.savedSource), saved)
        if (saved.savedSource == DestinationSource.OSM) enrichOsm(requireNotNull(current.sourcePoi))
    }

    private suspend fun finalizePersonalMedia(paths: List<String>, owner: Poi): List<String> = withContext(Dispatchers.IO) {
        val dir = storageManager.getPoiMediaDir(owner.name, owner.id)
        paths.mapNotNull { path ->
            if (!MediaUtils.isSupported(path)) return@mapNotNull null
            val file = File(path)
            if (!file.isFile) return@mapNotNull null
            if (file.parentFile?.canonicalPath == dir.canonicalPath) return@mapNotNull file.name
            val target = File(dir, uniqueName(dir, file.name))
            file.copyTo(target, overwrite = false)
            if (file.absolutePath.startsWith(storageManager.getTempDir().absolutePath)) file.delete()
            target.name
        }
    }

    private fun uniqueName(dir: File, preferred: String): String {
        if (!File(dir, preferred).exists()) return preferred
        val extension = preferred.substringAfterLast('.', "")
        val stem = preferred.substringBeforeLast('.', preferred)
        return "${stem}_${UUID.randomUUID()}${if (extension.isEmpty()) "" else ".$extension"}"
    }

    private fun deleteRemovedMedia(existing: Poi, retained: List<String>) {
        val dir = storageManager.getPoiMediaDir(existing.name, existing.id)
        existing.mediaPaths.filter { it !in retained }.forEach { runCatching { File(dir, it).delete() } }
    }

    fun star() {
        val current = _state.value
        if (!current.actions.star || current.isStarred || current.isSaving) return
        val source = current.sourcePoi ?: return
        val sourceType = if (current.kind == PoiScreenKind.OSM) DestinationSource.OSM else DestinationSource.IMPORTED
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            runCatching {
                val bookmark = source.copy(
                    id = UUID.randomUUID().toString(),
                    groupId = null,
                    description = PoiDescriptionCodec.encode(current.sourceDescription, null),
                    mediaPaths = emptyList(),
                    savedSource = sourceType,
                    sourceId = source.id,
                    sourceGroupId = source.groupId,
                    isVisible = true,
                )
                poiRepository.insert(bookmark)
                publishExternal(source, sourceType, bookmark)
                if (sourceType == DestinationSource.OSM) enrichOsm(source)
            }.onFailure { error -> _state.update { it.copy(isSaving = false, error = error.message ?: "Could not star POI") } }
        }
    }

    fun hasPersonalDataToLose(): Boolean {
        val current = _state.value
        return requiresUnstarConfirmation(
            personalNote = current.personalNote,
            groupId = current.bookmark?.groupId,
            personalPhotoCount = current.media.count(UnifiedPoiMedia::isPersonal),
        )
    }

    fun unstar(onDone: () -> Unit = {}) {
        val current = _state.value
        val bookmark = current.bookmark ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            runCatching {
                poiRepository.delete(bookmark)
                val sourceType = requireNotNull(bookmark.savedSource)
                publishExternal(requireNotNull(current.sourcePoi), sourceType, null)
                if (sourceType == DestinationSource.OSM) enrichOsm(requireNotNull(current.sourcePoi))
                onDone()
            }.onFailure { error -> _state.update { it.copy(isSaving = false, error = error.message ?: "Could not unstar POI") } }
        }
    }

    fun removeCreated(onRemoved: () -> Unit) {
        val poi = _state.value.sourcePoi ?: return
        if (_state.value.kind != PoiScreenKind.CREATED) return
        viewModelScope.launch {
            poiRepository.delete(poi)
            onRemoved()
        }
    }

    fun destination(): PlanDestination? {
        val poi = _state.value.sourcePoi ?: return null
        if (poi.id.isBlank()) return null
        val source = when (_state.value.kind) {
            PoiScreenKind.OSM, PoiScreenKind.STARRED_OSM -> DestinationSource.OSM
            PoiScreenKind.IMPORTED, PoiScreenKind.STARRED_IMPORTED -> DestinationSource.IMPORTED
            else -> DestinationSource.PERSONAL
        }
        return PlanDestination(sourceType = source, sourceId = poi.id, name = poi.name, lat = poi.lat, lng = poi.lng)
    }

    override fun onCleared() {
        _state.value.draftPersonalMedia
            .filter { it.startsWith(storageManager.getTempDir().absolutePath) }
            .forEach { runCatching { File(it).delete() } }
        super.onCleared()
    }
}
