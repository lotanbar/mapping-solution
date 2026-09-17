package com.mappingsolution.ui.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mappingsolution.data.fs.RouteFileRepository
import com.mappingsolution.ui.library.LibraryJobs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class RouteFinalizeState(
    val routeId: String = "",
    val name: String = "",
    val description: String = "",
    val color: String = "#FFFF5722",
    val distanceMeters: Double = 0.0,
    val isSaving: Boolean = false,
    val isRefined: Boolean = true,
    val isRefining: Boolean = false,
    val refinementProgress: String = "",
    val refinementProgressFraction: Float = 0f,
)

open class RouteFinalizeViewModel(
    private val routeRepository: RouteFileRepository,
    private val jobs: LibraryJobs,
) : ViewModel() {

    private val _state = MutableStateFlow(RouteFinalizeState())
    val state: StateFlow<RouteFinalizeState> = _state.asStateFlow()
    private var refinementObserver: Job? = null

    fun load(routeId: String) {
        viewModelScope.launch {
            val route = routeRepository.getById(routeId) ?: return@launch
            _state.value = RouteFinalizeState(
                routeId = route.id,
                name = route.name,
                description = route.description ?: "",
                color = route.color,
                distanceMeters = route.distanceMeters,
                isRefined = route.isRefined,
            )
            observeRefinement(routeId)
        }
    }

    private fun observeRefinement(routeId: String) {
        refinementObserver?.cancel()
        refinementObserver = viewModelScope.launch {
            combine(jobs.refinementProgress, routeRepository.observeAll()) { progress, routes ->
                progress[routeId] to (routes.find { it.id == routeId }?.isRefined == true)
            }.collect { (progress, refinedOnDisk) ->
                _state.value = _state.value.copy(
                    isRefining = progress != null,
                    isRefined = _state.value.isRefined || refinedOnDisk,
                    refinementProgress = progress?.text.orEmpty(),
                    refinementProgressFraction = progress?.fraction ?: 0f,
                )
            }
        }
    }

    fun cancelRefinement() {
        val routeId = _state.value.routeId
        if (routeId.isNotEmpty()) jobs.cancelRefinement(routeId)
    }

    fun onNameChange(value: String) { _state.value = _state.value.copy(name = value) }
    fun onDescriptionChange(value: String) { _state.value = _state.value.copy(description = value) }
    fun onColorChange(color: String) { _state.value = _state.value.copy(color = color) }

    fun save(onDone: () -> Unit) {
        val st = _state.value
        if (st.routeId.isEmpty()) return
        viewModelScope.launch {
            _state.value = st.copy(isSaving = true)
            routeRepository.updateFields(
                id = st.routeId,
                name = st.name.trim(),
                description = st.description.trim().ifEmpty { null },
                color = st.color,
            )
            onDone()
        }
    }
}
