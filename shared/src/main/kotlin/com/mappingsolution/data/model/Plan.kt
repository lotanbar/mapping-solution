package com.mappingsolution.data.model

import java.util.UUID

enum class DestinationSource { PERSONAL, IMPORTED, OSM, GOOGLE }

data class PlanDestination(
    val id: String = UUID.randomUUID().toString(),
    val sourceType: DestinationSource,
    val sourceId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
) : java.io.Serializable

data class Plan(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val destinations: List<PlanDestination>,
    val groupId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
