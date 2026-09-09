package com.mappingsolution.ui.recording

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mappingsolution.data.fs.RouteFileRepository
import com.mappingsolution.service.RouteRefinementWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

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

@HiltViewModel
class RouteFinalizeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routeRepository: RouteFileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RouteFinalizeState())
    val state: StateFlow<RouteFinalizeState> = _state.asStateFlow()
    private val workManager = WorkManager.getInstance(context)
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
            workManager.getWorkInfosForUniqueWorkFlow(
                RouteRefinementWorker.uniqueWorkName(routeId)
            ).collect { infos ->
                val info = infos.lastOrNull()
                val active = info?.state == WorkInfo.State.RUNNING ||
                    info?.state == WorkInfo.State.ENQUEUED ||
                    info?.state == WorkInfo.State.BLOCKED
                val phase = info?.progress?.getString(RouteRefinementWorker.KEY_PHASE)
                    ?: if (active) "Queued for refinement" else ""
                val done = info?.progress?.getInt(RouteRefinementWorker.KEY_DONE, 0) ?: 0
                val total = info?.progress?.getInt(RouteRefinementWorker.KEY_TOTAL, 0) ?: 0
                _state.value = _state.value.copy(
                    isRefining = active,
                    isRefined = _state.value.isRefined || info?.state == WorkInfo.State.SUCCEEDED,
                    refinementProgress = phase,
                    refinementProgressFraction = if (total > 0) done.toFloat() / total else 0f,
                )
            }
        }
    }

    fun cancelRefinement() {
        val routeId = _state.value.routeId
        if (routeId.isNotEmpty()) RouteRefinementWorker.cancel(context, routeId)
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
