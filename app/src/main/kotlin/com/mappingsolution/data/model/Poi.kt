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
    /** Wikimedia reference extracted from OSM tags (article, Wikidata item, file, or category). */
    val wikiRef: String? = null,
    /** Alternate OSM names used only to find matching descriptions and photos. */
    val imageSearchNames: List<String> = emptyList(),
    /** Exact image references supplied by OSM (Panoramax, KartaView, Flickr, etc.). */
    val imageRefs: List<String> = emptyList(),
)
