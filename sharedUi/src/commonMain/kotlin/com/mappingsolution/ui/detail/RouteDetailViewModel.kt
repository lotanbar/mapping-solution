package com.mappingsolution.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mappingsolution.data.fs.RouteFileRepository
import com.mappingsolution.data.model.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RouteDetailState(
    val route: Route? = null,
    val isLoading: Boolean = true,
)

open class RouteDetailViewModel(
    private val routeRepository: RouteFileRepository,
    private val id: String,
) : ViewModel() {
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
