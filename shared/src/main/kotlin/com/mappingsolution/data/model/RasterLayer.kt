package com.mappingsolution.data.model

data class RasterLayer(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    /** Absolute path to the copied `.mbtiles` file in app-private external storage. */
    val filePath: String,
    val isVisible: Boolean = true,
    val minZoom: Int = 0,
    val maxZoom: Int = 22,
    val createdAt: Long = System.currentTimeMillis(),
)
