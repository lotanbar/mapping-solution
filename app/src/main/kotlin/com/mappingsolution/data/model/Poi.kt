package com.mappingsolution.data.model

import java.util.UUID

data class Poi(
    val id: String = UUID.randomUUID().toString(),
    val groupId: String? = null,
    val name: String,
    val description: String? = null,
    val lat: Double,
    val lng: Double,
    val elevation: Double? = null,
    val mediaPaths: List<String> = emptyList(),
    val isVisible: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val iconKey: String? = null,
    /** Raw image reference extracted from OSM tags (direct URL, File:Name.jpg, or lang:Article). */
    val wikiRef: String? = null,
)
