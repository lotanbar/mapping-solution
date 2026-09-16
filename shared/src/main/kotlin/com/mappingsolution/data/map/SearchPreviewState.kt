package com.mappingsolution.data.map

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide singleton that carries the lat/lng of the search result the user
 * last tapped in SearchNPlanScreen. MainViewModel reads it to drive the map
 * camera and temporary preview pin; SearchNPlanViewModel writes it.
 */
@Singleton
class SearchPreviewState @Inject constructor() {
    val previewLocation: MutableStateFlow<Pair<Double, Double>?> = MutableStateFlow(null)
}
