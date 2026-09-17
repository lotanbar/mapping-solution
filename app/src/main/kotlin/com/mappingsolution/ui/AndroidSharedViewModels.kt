package com.mappingsolution.ui

import androidx.lifecycle.SavedStateHandle
import com.mappingsolution.data.fs.PlanFileRepository
import com.mappingsolution.data.map.MapHolder
import com.mappingsolution.data.map.SearchPreviewState
import com.mappingsolution.data.places.OsmPoiRepository
import com.mappingsolution.data.search.SearchRepository
import com.mappingsolution.ui.searchnplan.SearchNPlanViewModel
import com.mappingsolution.data.fs.GroupFileRepository
import com.mappingsolution.data.fs.RouteFileRepository
import com.mappingsolution.ui.detail.RouteDetailViewModel
import com.mappingsolution.ui.library.AndroidLibraryJobs
import com.mappingsolution.ui.library.GroupFormViewModel
import com.mappingsolution.ui.recording.RouteFinalizeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// Hilt entry points for ViewModels shared with the desktop app via :sharedUi.

@HiltViewModel
class AndroidGroupFormViewModel @Inject constructor(
    groupRepository: GroupFileRepository,
    savedStateHandle: SavedStateHandle,
) : GroupFormViewModel(groupRepository, savedStateHandle.get<String>("groupId"))

@HiltViewModel
class AndroidRouteDetailViewModel @Inject constructor(
    routeRepository: RouteFileRepository,
    savedStateHandle: SavedStateHandle,
) : RouteDetailViewModel(routeRepository, requireNotNull(savedStateHandle["id"]))

@HiltViewModel
class AndroidRouteFinalizeViewModel @Inject constructor(
    routeRepository: RouteFileRepository,
    jobs: AndroidLibraryJobs,
) : RouteFinalizeViewModel(routeRepository, jobs)

@HiltViewModel
class AndroidSearchNPlanViewModel @Inject constructor(
    searchRepository: SearchRepository,
    planRepository: PlanFileRepository,
    mapHolder: MapHolder,
    searchPreviewState: SearchPreviewState,
    osmPoiRepository: OsmPoiRepository,
    savedStateHandle: SavedStateHandle,
) : SearchNPlanViewModel(
    searchRepository = searchRepository,
    planRepository = planRepository,
    loadCamera = mapHolder::loadCamera,
    searchPreviewState = searchPreviewState,
    osmPoiRepository = osmPoiRepository,
    loadedPlanId = savedStateHandle.get<String>("planId"),
)
