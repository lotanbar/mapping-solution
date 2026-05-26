package com.mappingsolution.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mappingsolution.data.fs.BulkPoiRepository
import com.mappingsolution.data.fs.ExportRepository
import com.mappingsolution.data.fs.GroupFileRepository
import com.mappingsolution.data.fs.ImportResult
import com.mappingsolution.data.fs.PlanFileRepository
import com.mappingsolution.data.fs.PoiFileRepository
import com.mappingsolution.data.fs.RouteFileRepository
import com.mappingsolution.data.fs.RasterLayerRepository
import com.mappingsolution.data.map.MapLayersState
import com.mappingsolution.data.map.MapStyle
import com.mappingsolution.data.model.Group
import com.mappingsolution.data.model.GroupType
import com.mappingsolution.data.model.Plan
import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.model.RasterLayer
import com.mappingsolution.data.model.Route
import com.mappingsolution.data.places.GOOGLE_PLACES_GROUP_ID
import com.mappingsolution.data.places.GooglePlacesRepository
import com.mappingsolution.data.places.OSM_POI_GROUP_ID
import com.mappingsolution.data.places.OsmPoiRepository
import com.mappingsolution.service.ImportWorker
import com.mappingsolution.service.MbtilesImportWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed class DeleteGroupResult {
    object Done : DeleteGroupResult()
    data class HasItems(val poiCount: Int) : DeleteGroupResult()
}

sealed class MbtilesImportResult {
    data class Success(val layerName: String) : MbtilesImportResult()
    data class Failure(val error: String) : MbtilesImportResult()
}

sealed interface LibrarySelectionMode {
    data object None : LibrarySelectionMode
    data class GroupSelection(val selectedIds: Set<String> = emptySet()) : LibrarySelectionMode
    data class RowSelection(val selectedIds: Set<String> = emptySet()) : LibrarySelectionMode
    data class RasterLayerSelection(val selectedIds: Set<String> = emptySet()) : LibrarySelectionMode
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val groupRepository: GroupFileRepository,
    private val poiRepository: PoiFileRepository,
    private val routeRepository: RouteFileRepository,
    private val planRepository: PlanFileRepository,
    private val exportRepository: ExportRepository,
    private val googlePlacesRepository: GooglePlacesRepository,
    private val osmPoiRepository: OsmPoiRepository,
    private val bulkPoiRepository: BulkPoiRepository,
    private val mapLayersState: MapLayersState,
    private val rasterLayerRepository: RasterLayerRepository,
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    // ── Map layer state ───────────────────────────────────────────────────

    val mapStyle: StateFlow<MapStyle> = mapLayersState.mapStyle
    val hillshadeVisible: StateFlow<Boolean> = mapLayersState.hillshadeVisible
    val rasterLayers: StateFlow<List<RasterLayer>> = mapLayersState.rasterLayers

    fun setMapStyle(style: MapStyle) = mapLayersState.setMapStyle(style)
    fun toggleHillshade() = mapLayersState.setHillshadeVisible(!mapLayersState.hillshadeVisible.value)
    fun toggleRasterLayerVisibility(id: String) = mapLayersState.toggleRasterLayerVisibility(id)
    fun deleteSelectedRasterLayers() {
        val ids = (_selectionMode.value as? LibrarySelectionMode.RasterLayerSelection)?.selectedIds ?: return
        viewModelScope.launch {
            ids.forEach { rasterLayerRepository.delete(it) }
            clearSelection()
        }
    }

    // ── Raw data ──────────────────────────────────────────────────────────

    private val _allGroups = groupRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _allPois = poiRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _allRoutes = routeRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _allPlans = planRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All groups unfiltered — used for the group-picker dialog in un-orphan. */
    val allGroupsUnfiltered: StateFlow<List<Group>> = _allGroups

    // ── Places group counts + visibility ─────────────────────────────────

    val googlePlaceCount: StateFlow<Int> = googlePlacesRepository.pois
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val osmPoiCount: StateFlow<Int> = osmPoiRepository.pois
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val googlePlacesGroup: StateFlow<Group?> = _allGroups
        .map { groups -> groups.find { it.id == GOOGLE_PLACES_GROUP_ID } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val osmPoiGroup: StateFlow<Group?> = _allGroups
        .map { groups -> groups.find { it.id == OSM_POI_GROUP_ID } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleGooglePlacesVisibility() {
        val group = googlePlacesGroup.value ?: return
        viewModelScope.launch { groupRepository.setVisibility(group.id, !group.isVisible) }
    }

    fun toggleOsmPoisVisibility() {
        val group = osmPoiGroup.value ?: return
        viewModelScope.launch { groupRepository.setVisibility(group.id, !group.isVisible) }
    }

    // ── Search ────────────────────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ── Collapse state ────────────────────────────────────────────────────

    private val _expandedGroups = MutableStateFlow<Set<String>>(emptySet())
    val expandedGroups: StateFlow<Set<String>> = _expandedGroups.asStateFlow()

    // ── Selection mode ────────────────────────────────────────────────────

    private val _selectionMode = MutableStateFlow<LibrarySelectionMode>(LibrarySelectionMode.None)
    val selectionMode: StateFlow<LibrarySelectionMode> = _selectionMode.asStateFlow()

    // ── Filtered lists ────────────────────────────────────────────────────

    private fun makeFilteredGroupFlow(type: GroupType) = combine(
        _allGroups, _allPois, _allRoutes, _allPlans, _searchQuery,
    ) { groups, pois, routes, plans, query ->
        val userGroups = groups.filter {
            it.id != GOOGLE_PLACES_GROUP_ID && it.id != OSM_POI_GROUP_ID && it.type == type
        }
        if (query.isBlank()) return@combine userGroups
        val poisByGroup = pois.groupBy { it.groupId }
        val routesByGroup = routes.groupBy { it.groupId }
        val plansByGroup = plans.groupBy { it.groupId }
        userGroups.filter { g ->
            g.name.contains(query, ignoreCase = true) ||
                poisByGroup[g.id]?.any { it.name.contains(query, ignoreCase = true) } == true ||
                routesByGroup[g.id]?.any { it.name.contains(query, ignoreCase = true) } == true ||
                plansByGroup[g.id]?.any { it.name.contains(query, ignoreCase = true) } == true
        }
    }

    /** POI groups shown in the library (excludes seeded places groups). */
    val filteredPoiGroups: StateFlow<List<Group>> =
        makeFilteredGroupFlow(GroupType.POI)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Route groups shown in the library. */
    val filteredRouteGroups: StateFlow<List<Group>> =
        makeFilteredGroupFlow(GroupType.ROUTE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Plan groups shown in the library. */
    val filteredPlanGroups: StateFlow<List<Group>> =
        makeFilteredGroupFlow(GroupType.PLAN)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All user groups (all types, unfiltered by search) — used for empty-state check. */
    val filteredAllGroups: StateFlow<List<Group>> = _allGroups
        .map { groups -> groups.filter { it.id != GOOGLE_PLACES_GROUP_ID && it.id != OSM_POI_GROUP_ID } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All grouped POIs keyed by groupId (unfiltered by search, for collapse rendering). */
    val poisByGroup: StateFlow<Map<String, List<Poi>>> = _allPois
        .map { pois -> pois.filter { it.groupId != null }.groupBy { it.groupId!! } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Grouped routes keyed by groupId (unfiltered by search, for collapse rendering). */
    val routesByGroup: StateFlow<Map<String, List<Route>>> = _allRoutes
        .map { routes -> routes.filter { it.groupId != null }.groupBy { it.groupId!! } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Grouped plans keyed by groupId (unfiltered by search, for collapse rendering). */
    val plansByGroup: StateFlow<Map<String, List<Plan>>> = _allPlans
        .map { plans -> plans.filter { it.groupId != null }.groupBy { it.groupId!! } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Orphaned POIs (no group) filtered by search. */
    val filteredOrphanedPois: StateFlow<List<Poi>> = combine(
        _allPois, _searchQuery,
    ) { pois, query ->
        val orphans = pois.filter { it.groupId == null }
        if (query.isBlank()) orphans else orphans.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Orphaned routes (no group) filtered by search. */
    val filteredOrphanedRoutes: StateFlow<List<Route>> = combine(
        _allRoutes, _searchQuery,
    ) { routes, query ->
        val orphans = routes.filter { it.groupId == null }
        if (query.isBlank()) orphans else orphans.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Orphaned plans (no group) filtered by search. */
    val filteredOrphanedPlans: StateFlow<List<Plan>> = combine(
        _allPlans, _searchQuery,
    ) { plans, query ->
        val orphans = plans.filter { it.groupId == null }
        if (query.isBlank()) orphans else orphans.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Groups to show in the "move to group" picker, filtered to only the types of orphaned
     * items currently selected (so you don't assign a route to a POI group, etc.).
     */
    val groupPickerGroups: StateFlow<List<Group>> = combine(
        _selectionMode, _allPois, _allRoutes, _allPlans, _allGroups,
    ) { mode, pois, routes, plans, allGroups ->
        val ids = (mode as? LibrarySelectionMode.RowSelection)?.selectedIds ?: return@combine emptyList()
        val types = buildSet {
            if (pois.any { it.id in ids && it.groupId == null }) add(GroupType.POI)
            if (routes.any { it.id in ids && it.groupId == null }) add(GroupType.ROUTE)
            if (plans.any { it.id in ids && it.groupId == null }) add(GroupType.PLAN)
        }
        allGroups.filter {
            it.id != GOOGLE_PLACES_GROUP_ID && it.id != OSM_POI_GROUP_ID && it.type in types
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())


    // ── Row-selection action availability ─────────────────────────────────

    /** True when selection contains any item that already has a group (can be orphaned). */
    val canOrphanSelection: StateFlow<Boolean> = combine(
        _selectionMode, _allPois, _allRoutes, _allPlans,
    ) { mode, pois, routes, plans ->
        val ids = (mode as? LibrarySelectionMode.RowSelection)?.selectedIds ?: return@combine false
        pois.any { it.id in ids && it.groupId != null } ||
            routes.any { it.id in ids && it.groupId != null } ||
            plans.any { it.id in ids && it.groupId != null }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** True when selection contains any ungrouped item (can be moved to a group). */
    val canUnorphanSelection: StateFlow<Boolean> = combine(
        _selectionMode, _allPois, _allRoutes, _allPlans,
    ) { mode, pois, routes, plans ->
        val ids = (mode as? LibrarySelectionMode.RowSelection)?.selectedIds ?: return@combine false
        pois.any { it.id in ids && it.groupId == null } ||
            routes.any { it.id in ids && it.groupId == null } ||
            plans.any { it.id in ids && it.groupId == null }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ── Search ────────────────────────────────────────────────────────────

    fun onSearchQuery(query: String) { _searchQuery.value = query }

    // ── Collapse ──────────────────────────────────────────────────────────

    fun toggleCollapse(groupId: String) {
        _expandedGroups.update { if (groupId in it) it - groupId else it + groupId }
    }

    // ── Selection ─────────────────────────────────────────────────────────

    fun enterGroupSelection(groupId: String) {
        _selectionMode.value = LibrarySelectionMode.GroupSelection(setOf(groupId))
    }

    fun enterRowSelection(id: String) {
        _selectionMode.value = LibrarySelectionMode.RowSelection(setOf(id))
    }

    fun enterRasterLayerSelection(id: String) {
        _selectionMode.value = LibrarySelectionMode.RasterLayerSelection(setOf(id))
    }

    fun toggleGroupSelection(groupId: String) {
        val current = _selectionMode.value as? LibrarySelectionMode.GroupSelection ?: return
        val updated = if (groupId in current.selectedIds) current.selectedIds - groupId
                      else current.selectedIds + groupId
        _selectionMode.value = if (updated.isEmpty()) LibrarySelectionMode.None
                                else current.copy(selectedIds = updated)
    }

    fun toggleRowSelection(id: String) {
        val current = _selectionMode.value as? LibrarySelectionMode.RowSelection ?: return
        val updated = if (id in current.selectedIds) current.selectedIds - id
                      else current.selectedIds + id
        _selectionMode.value = if (updated.isEmpty()) LibrarySelectionMode.None
                                else current.copy(selectedIds = updated)
    }

    fun toggleRasterLayerSelection(id: String) {
        val current = _selectionMode.value as? LibrarySelectionMode.RasterLayerSelection ?: return
        val updated = if (id in current.selectedIds) current.selectedIds - id
                      else current.selectedIds + id
        _selectionMode.value = if (updated.isEmpty()) LibrarySelectionMode.None
                                else current.copy(selectedIds = updated)
    }

    fun clearSelection() { _selectionMode.value = LibrarySelectionMode.None }

    // ── Visibility ────────────────────────────────────────────────────────

    fun toggleGroupVisibility(group: Group) {
        viewModelScope.launch { groupRepository.setVisibility(group.id, !group.isVisible) }
    }

    fun togglePoiVisibility(poi: Poi) {
        viewModelScope.launch { poiRepository.update(poi.copy(isVisible = !poi.isVisible)) }
    }

    fun toggleRouteVisibility(route: Route) {
        viewModelScope.launch { routeRepository.update(route.copy(isVisible = !route.isVisible)) }
    }

    // ── Group multi-select actions ────────────────────────────────────────

    /** Delete the selected groups and all their POIs. */
    fun deleteSelectedGroupsWithItems() {
        val ids = (_selectionMode.value as? LibrarySelectionMode.GroupSelection)?.selectedIds ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val selectedGroups = _allGroups.value.filter { it.id in ids }
            val bulkIds = selectedGroups.filter { it.isBulk }.map { it.id }.toSet()
            val regularIds = ids - bulkIds

            // Delete regular POIs the normal way
            val poisToDelete = _allPois.value.filter { it.groupId in regularIds }.map { it.id }
            if (poisToDelete.isNotEmpty()) {
                poiRepository.deleteByIds(poisToDelete) { done, total ->
                    reportProgress("Deleting…", done, total)
                }
            }
            // Delete bulk group folders wholesale
            selectedGroups.filter { it.isBulk }.forEach { group ->
                bulkPoiRepository.deleteGroup(group.name, group.id)
            }
            selectedGroups.forEach { groupRepository.delete(it) }
            clearProgress()
            clearSelection()
        }
    }

    /** Delete the selected groups; their POIs, routes, and plans become orphaned. */
    fun orphanSelectedGroups() {
        val ids = (_selectionMode.value as? LibrarySelectionMode.GroupSelection)?.selectedIds ?: return
        viewModelScope.launch {
            _isBusy.value = true
            reportProgress("Orphaning…", 0, 0)
            val selectedGroups = _allGroups.value.filter { it.id in ids }
            val regularIds = selectedGroups.filter { !it.isBulk }.map { it.id }.toSet()
            // Bulk groups have no individual POIs in poiRepository — just delete the group record
            poiRepository.orphan(_allPois.value.filter { it.groupId in regularIds }.map { it.id })
            routeRepository.orphan(_allRoutes.value.filter { it.groupId in regularIds }.map { it.id })
            planRepository.orphan(_allPlans.value.filter { it.groupId in regularIds }.map { it.id })
            selectedGroups.forEach { groupRepository.delete(it) }
            clearProgress()
            clearSelection()
        }
    }

    // ── Row multi-select actions ───────────────────────────────────────────

    /** Delete selected POIs, routes, and/or plans. */
    fun deleteSelectedRows() {
        val ids = (_selectionMode.value as? LibrarySelectionMode.RowSelection)?.selectedIds ?: return
        viewModelScope.launch {
            poiRepository.deleteByIds(ids.toList())
            routeRepository.deleteByIds(ids.toList())
            planRepository.deleteByIds(ids.toList())
            clearSelection()
        }
    }

    /** Remove group assignment from all grouped items in the selection. */
    fun orphanSelectedRows() {
        val ids = (_selectionMode.value as? LibrarySelectionMode.RowSelection)?.selectedIds ?: return
        viewModelScope.launch {
            val groupedPois = _allPois.value.filter { it.id in ids && it.groupId != null }.map { it.id }
            if (groupedPois.isNotEmpty()) poiRepository.orphan(groupedPois)
            val groupedRoutes = _allRoutes.value.filter { it.id in ids && it.groupId != null }.map { it.id }
            if (groupedRoutes.isNotEmpty()) routeRepository.orphan(groupedRoutes)
            val groupedPlans = _allPlans.value.filter { it.id in ids && it.groupId != null }.map { it.id }
            if (groupedPlans.isNotEmpty()) planRepository.orphan(groupedPlans)
            clearSelection()
        }
    }

    /** Move ungrouped items in the selection to the given group. */
    fun moveSelectedRowsToGroup(groupId: String) {
        val ids = (_selectionMode.value as? LibrarySelectionMode.RowSelection)?.selectedIds ?: return
        viewModelScope.launch {
            val orphanPois = _allPois.value.filter { it.id in ids && it.groupId == null }.map { it.id }
            if (orphanPois.isNotEmpty()) poiRepository.moveToGroup(orphanPois, groupId)
            val orphanRoutes = _allRoutes.value.filter { it.id in ids && it.groupId == null }.map { it.id }
            if (orphanRoutes.isNotEmpty()) routeRepository.moveToGroup(orphanRoutes, groupId)
            val orphanPlans = _allPlans.value.filter { it.id in ids && it.groupId == null }.map { it.id }
            if (orphanPlans.isNotEmpty()) planRepository.moveToGroup(orphanPlans, groupId)
            clearSelection()
        }
    }

    // ── Plans ──────────────────────────────────────────────────────────────

    fun deletePlan(id: String) {
        viewModelScope.launch { planRepository.delete(id) }
    }

    // ── Import ────────────────────────────────────────────────────────────

    private val _isBusy = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _importingFolderName = MutableStateFlow<String?>(null)
    val importingFolderName: StateFlow<String?> = _importingFolderName.asStateFlow()

    private val _importProgressText = MutableStateFlow("")
    val importProgressText: StateFlow<String> = _importProgressText.asStateFlow()

    private val _importProgressFraction = MutableStateFlow(0f)
    val importProgressFraction: StateFlow<Float> = _importProgressFraction.asStateFlow()

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    // ID of the work request we most recently enqueued (or reconnected to).
    private var currentWorkId: UUID? = null
    // ID of the last work record whose result was dismissed — filtered from re-emissions
    // before WorkManager's async pruneWork() removes it from the database.
    private var dismissedWorkId: UUID? = null

    init {
        // Reconnect to any import that was already running when this ViewModel was created
        // (e.g. user navigated away mid-import and returned to the Library screen).
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(IMPORT_WORK_NAME).collect { infos ->
                val info = when {
                    // We started (or already reconnected to) a specific work request.
                    currentWorkId != null -> infos.firstOrNull { it.id == currentWorkId }
                    // Reconnection: prefer active work, then fall back to the most recent
                    // terminal record — but never show a result we already dismissed.
                    else -> infos.firstOrNull {
                        it.id != dismissedWorkId &&
                            (it.state == WorkInfo.State.RUNNING ||
                                it.state == WorkInfo.State.ENQUEUED ||
                                it.state == WorkInfo.State.BLOCKED)
                    } ?: infos.lastOrNull { it.id != dismissedWorkId }
                }
                handleWorkInfo(info)
            }
        }
        // Reconnect to any in-flight MBTiles import when this ViewModel is (re)created
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(MBTILES_IMPORT_WORK_NAME).collect { infos ->
                val info = when {
                    currentMbtilesWorkId != null -> infos.firstOrNull { it.id == currentMbtilesWorkId }
                    else -> infos.firstOrNull {
                        it.id != dismissedMbtilesWorkId &&
                            (it.state == WorkInfo.State.RUNNING ||
                                it.state == WorkInfo.State.ENQUEUED ||
                                it.state == WorkInfo.State.BLOCKED)
                    } ?: infos.lastOrNull { it.id != dismissedMbtilesWorkId }
                }
                handleMbtilesWorkInfo(info)
            }
        }
    }

    private fun handleWorkInfo(info: WorkInfo?) {
        if (info == null) return
        when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                if (currentWorkId == null) currentWorkId = info.id
                _isBusy.value = true
            }
            WorkInfo.State.RUNNING -> {
                if (currentWorkId == null) currentWorkId = info.id
                _isBusy.value = true
                val folderName = info.tags.firstOrNull { it.startsWith(TAG_FOLDER_PREFIX) }
                    ?.removePrefix(TAG_FOLDER_PREFIX)
                if (folderName != null) _importingFolderName.value = folderName
                val phase = info.progress.getString(ImportWorker.KEY_PHASE) ?: return
                val done = info.progress.getInt(ImportWorker.KEY_DONE, 0)
                val total = info.progress.getInt(ImportWorker.KEY_TOTAL, 0)
                reportProgress(phase, done, total)
            }
            WorkInfo.State.SUCCEEDED -> {
                if (currentWorkId == null) currentWorkId = info.id
                val data = info.outputData
                _importResult.value = ImportResult(
                    poisImported = data.getInt(ImportWorker.KEY_POIS_IMPORTED, 0),
                    routesImported = data.getInt(ImportWorker.KEY_ROUTES_IMPORTED, 0),
                    filesProcessed = data.getInt(ImportWorker.KEY_FILES_PROCESSED, 0),
                    filesSkipped = data.getInt(ImportWorker.KEY_FILES_SKIPPED, 0),
                    errors = data.getStringArray(ImportWorker.KEY_ERRORS)?.toList() ?: emptyList(),
                )
                clearProgress()
            }
            WorkInfo.State.FAILED -> {
                if (currentWorkId == null) currentWorkId = info.id
                val data = info.outputData
                _importResult.value = ImportResult(
                    filesSkipped = data.getInt(ImportWorker.KEY_FILES_SKIPPED, 0),
                    errors = data.getStringArray(ImportWorker.KEY_ERRORS)?.toList() ?: emptyList(),
                    validationErrors = data.getStringArray(ImportWorker.KEY_VALIDATION_ERRORS)?.toList() ?: emptyList(),
                )
                clearProgress()
            }
            WorkInfo.State.CANCELLED -> {
                if (currentWorkId == null || currentWorkId == info.id) {
                    currentWorkId = null
                    clearProgress()
                }
            }
        }
    }

    private fun clearProgress() {
        _importProgressText.value = ""
        _importProgressFraction.value = 0f
        _importingFolderName.value = null
        _isBusy.value = false
    }

    private fun reportProgress(phase: String, done: Int, total: Int) {
        val pct = if (total > 0) " — ${done * 100 / total}%" else ""
        _importProgressText.value = "$phase$pct"
        _importProgressFraction.value = if (total > 0) done.toFloat() / total else 0f
    }

    fun importFromFolder(path: String) {
        val folderName = java.io.File(path).name
        val request = OneTimeWorkRequestBuilder<ImportWorker>()
            .setInputData(workDataOf(ImportWorker.KEY_FOLDER_PATH to path))
            .addTag("$TAG_FOLDER_PREFIX$folderName")
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        currentWorkId = request.id
        dismissedWorkId = null
        _importResult.value = null
        _isBusy.value = true
        _importingFolderName.value = folderName
        _importProgressText.value = "Starting…"
        _importProgressFraction.value = 0f
        workManager.enqueueUniqueWork(IMPORT_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun importZipFile(filePath: String) {
        val fileName = java.io.File(filePath).nameWithoutExtension.takeIf { it.isNotEmpty() } ?: "Import"
        val request = OneTimeWorkRequestBuilder<ImportWorker>()
            .setInputData(workDataOf(ImportWorker.KEY_ZIP_PATH to filePath))
            .addTag("$TAG_FOLDER_PREFIX$fileName")
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        currentWorkId = request.id
        dismissedWorkId = null
        _importResult.value = null
        _isBusy.value = true
        _importingFolderName.value = fileName
        _importProgressText.value = "Starting…"
        _importProgressFraction.value = 0f
        workManager.enqueueUniqueWork(IMPORT_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun importSingleGpxFile(filePath: String) {
        val fileName = java.io.File(filePath).nameWithoutExtension.takeIf { it.isNotEmpty() } ?: "Import"
        val request = OneTimeWorkRequestBuilder<ImportWorker>()
            .setInputData(workDataOf(ImportWorker.KEY_FILE_PATH to filePath))
            .addTag("$TAG_FOLDER_PREFIX$fileName")
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        currentWorkId = request.id
        dismissedWorkId = null
        _importResult.value = null
        _isBusy.value = true
        _importingFolderName.value = fileName
        _importProgressText.value = "Starting…"
        _importProgressFraction.value = 0f
        workManager.enqueueUniqueWork(IMPORT_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun dismissImportResult() {
        dismissedWorkId = currentWorkId
        currentWorkId = null
        _importResult.value = null
        workManager.pruneWork()
    }

    // ── MBTiles import ────────────────────────────────────────────────────

    private val _isMbtilesImporting = MutableStateFlow(false)
    val isMbtilesImporting: StateFlow<Boolean> = _isMbtilesImporting.asStateFlow()

    private val _mbtilesImportProgressText = MutableStateFlow("")
    val mbtilesImportProgressText: StateFlow<String> = _mbtilesImportProgressText.asStateFlow()

    private val _mbtilesImportProgressFraction = MutableStateFlow(0f)
    val mbtilesImportProgressFraction: StateFlow<Float> = _mbtilesImportProgressFraction.asStateFlow()

    private val _mbtilesImportResult = MutableStateFlow<MbtilesImportResult?>(null)
    val mbtilesImportResult: StateFlow<MbtilesImportResult?> = _mbtilesImportResult.asStateFlow()

    private var currentMbtilesWorkId: UUID? = null
    private var dismissedMbtilesWorkId: UUID? = null

    private fun handleMbtilesWorkInfo(info: WorkInfo?) {
        if (info == null) return
        when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                if (currentMbtilesWorkId == null) currentMbtilesWorkId = info.id
                _isMbtilesImporting.value = true
            }
            WorkInfo.State.RUNNING -> {
                if (currentMbtilesWorkId == null) currentMbtilesWorkId = info.id
                _isMbtilesImporting.value = true
                val bytesCopied = info.progress.getLong(MbtilesImportWorker.KEY_BYTES_COPIED, 0L)
                val bytesTotal = info.progress.getLong(MbtilesImportWorker.KEY_BYTES_TOTAL, -1L)
                if (bytesCopied > 0) {
                    val copiedMb = bytesCopied / 1_048_576.0
                    _mbtilesImportProgressText.value = if (bytesTotal > 0) {
                        val totalMb = bytesTotal / 1_048_576.0
                        "Copying… %.1f MB / %.1f MB".format(copiedMb, totalMb)
                    } else {
                        "Copying… %.1f MB".format(copiedMb)
                    }
                    _mbtilesImportProgressFraction.value =
                        if (bytesTotal > 0) bytesCopied.toFloat() / bytesTotal else 0f
                }
            }
            WorkInfo.State.SUCCEEDED -> {
                if (currentMbtilesWorkId == null) currentMbtilesWorkId = info.id
                val layerName = info.outputData.getString(MbtilesImportWorker.KEY_LAYER_NAME)
                _mbtilesImportResult.value = MbtilesImportResult.Success(layerName ?: "")
                clearMbtilesProgress()
            }
            WorkInfo.State.FAILED -> {
                if (currentMbtilesWorkId == null) currentMbtilesWorkId = info.id
                val error = info.outputData.getString(MbtilesImportWorker.KEY_ERROR) ?: "Import failed"
                _mbtilesImportResult.value = MbtilesImportResult.Failure(error)
                clearMbtilesProgress()
            }
            WorkInfo.State.CANCELLED -> {
                if (currentMbtilesWorkId == null || currentMbtilesWorkId == info.id) {
                    currentMbtilesWorkId = null
                    clearMbtilesProgress()
                }
            }
        }
    }

    private fun clearMbtilesProgress() {
        _mbtilesImportProgressText.value = ""
        _mbtilesImportProgressFraction.value = 0f
        _isMbtilesImporting.value = false
    }

    fun importMbtilesFile(uri: Uri) {
        val request = OneTimeWorkRequestBuilder<MbtilesImportWorker>()
            .setInputData(workDataOf(MbtilesImportWorker.KEY_URI to uri.toString()))
            .build()
        currentMbtilesWorkId = request.id
        dismissedMbtilesWorkId = null
        _mbtilesImportResult.value = null
        _isMbtilesImporting.value = true
        _mbtilesImportProgressText.value = "Starting…"
        _mbtilesImportProgressFraction.value = 0f
        workManager.enqueueUniqueWork(MBTILES_IMPORT_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun dismissMbtilesImportResult() {
        dismissedMbtilesWorkId = currentMbtilesWorkId
        currentMbtilesWorkId = null
        _mbtilesImportResult.value = null
        workManager.pruneWork()
    }

    companion object {
        private const val IMPORT_WORK_NAME = "poi_import"
        private const val TAG_FOLDER_PREFIX = "folder:"
        private const val MBTILES_IMPORT_WORK_NAME = "mbtiles_import"
    }

    // ── Export ────────────────────────────────────────────────────────────

    private val _exportUri = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val exportUri = _exportUri.asSharedFlow()

    fun exportSelectedGroups() {
        val ids = (_selectionMode.value as? LibrarySelectionMode.GroupSelection)?.selectedIds ?: return
        viewModelScope.launch {
            val uri = exportRepository.exportGroups(ids) ?: return@launch
            _exportUri.tryEmit(uri)
        }
    }

    fun exportSelectedRows() {
        val ids = (_selectionMode.value as? LibrarySelectionMode.RowSelection)?.selectedIds ?: return
        viewModelScope.launch {
            val uri = exportRepository.exportRows(ids) ?: return@launch
            _exportUri.tryEmit(uri)
        }
    }
}
