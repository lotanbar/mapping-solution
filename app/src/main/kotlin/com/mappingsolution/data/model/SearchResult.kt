package com.mappingsolution.data.model

sealed class SearchResult {
    abstract val poi: Poi
    data class PersonalPoi(override val poi: Poi) : SearchResult()
    data class ImportedPoi(override val poi: Poi) : SearchResult()
    data class OsmPoi(override val poi: Poi) : SearchResult()
}
