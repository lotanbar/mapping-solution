package com.mappingsolution.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mappingsolution.data.fs.RouteFileRepository
import com.mappingsolution.data.model.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RouteDetailState(
    val route: Route? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class RouteDetailViewModel @Inject constructor(
    private val routeRepository: RouteFileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val id: String = requireNotNull(savedStateHandle["id"])
    private val _state = MutableStateFlow(RouteDetailState())
    val state: StateFlow<RouteDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { RouteDetailState(routeRepository.getById(id), isLoading = false) }
        }
    }

    fun remove(onRemoved: () -> Unit) {
        val route = _state.value.route ?: return
        viewModelScope.launch {
            routeRepository.deleteByIds(listOf(route.id))
            onRemoved()
        }
    }
}
