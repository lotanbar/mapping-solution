package com.mappingsolution.data.model

import java.util.UUID

data class Route(
    val id: String = UUID.randomUUID().toString(),
    val color: String = "#FFFF5722",
    val name: String,
    val description: String? = null,
    val isVisible: Boolean = true,
    val groupId: String? = null,
    val didUserTapStop: Boolean = false,
    /** Existing/imported routes default to refined; new recordings explicitly start unrefined. */
    val isRefined: Boolean = true,
    val startedAt: Long,
    val stoppedAt: Long? = null,
    val checkpointAt: Long,
    val distanceMeters: Double = 0.0,
    val durationSec: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
