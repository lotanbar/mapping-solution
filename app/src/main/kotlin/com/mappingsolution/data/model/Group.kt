package com.mappingsolution.data.model

import java.util.UUID

enum class GroupType { POI, ROUTE, PLAN }

data class Group(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val iconKey: String,
    val color: String,
    /** Shape used to render this group's POIs on the map: "pin", "circle", or "square". */
    val shape: String = "pin",
    val isVisible: Boolean = true,
    val isImported: Boolean = false,
    val importComplete: Boolean = true,
    /** True when this group's POIs are stored as a single bulk_pois.jsonl instead of per-POI folders. */
    val isBulk: Boolean = false,
    /** Number of POIs in a bulk group (stored in the group file to avoid scanning the jsonl). */
    val bulkPoiCount: Int = 0,
    val type: GroupType = GroupType.POI,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
