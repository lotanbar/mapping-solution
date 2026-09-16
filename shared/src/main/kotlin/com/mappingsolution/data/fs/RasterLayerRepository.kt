package com.mappingsolution.data.fs

import com.mappingsolution.data.util.AppLog
import com.mappingsolution.data.model.RasterLayer
import com.mappingsolution.data.util.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RasterLayerRepository @Inject constructor(
    private val storageManager: StorageManager,
) {

    private val _layers = MutableStateFlow<List<RasterLayer>>(emptyList())

    private val indexFile: File
        get() = File(storageManager.getMbtilesDir(), "layers.json")

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                loadAll()
            } catch (e: Exception) {
                AppLog.e("RasterLayerRepository", "Failed to load layers index", e)
            }
        }
    }

    fun observeAll(): Flow<List<RasterLayer>> = _layers

    suspend fun insert(layer: RasterLayer) = withContext(Dispatchers.IO) {
        val updated = _layers.value + layer
        _layers.value = updated
        persist(updated)
    }

    suspend fun update(layer: RasterLayer) = withContext(Dispatchers.IO) {
        val updated = _layers.value.map { if (it.id == layer.id) layer else it }
        _layers.value = updated
        persist(updated)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val existing = _layers.value.find { it.id == id }
        if (existing != null) {
            File(existing.filePath).takeIf { it.exists() }?.delete()
        }
        val updated = _layers.value.filter { it.id != id }
        _layers.value = updated
        persist(updated)
    }

    /** Finds an existing layer by name (case-insensitive). Used for purge-and-replace on re-import. */
    fun findByName(name: String): RasterLayer? =
        _layers.value.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun findById(id: String): RasterLayer? =
        _layers.value.firstOrNull { it.id == id }

    private fun loadAll() {
        if (!indexFile.exists()) {
            _layers.value = emptyList()
            return
        }
        val json = JSONArray(indexFile.readText())
        val layers = (0 until json.length()).mapNotNull { i ->
            try {
                fromJson(json.getJSONObject(i))
            } catch (e: Exception) {
                AppLog.w("RasterLayerRepository", "Skipping malformed layer entry at index $i", e)
                null
            }
        }
        _layers.value = layers
    }

    private fun persist(layers: List<RasterLayer>) {
        val json = JSONArray()
        layers.forEach { json.put(toJson(it)) }
        indexFile.parentFile?.mkdirs()
        indexFile.writeText(json.toString(2))
    }

    private fun toJson(layer: RasterLayer): JSONObject = JSONObject().apply {
        put("id", layer.id)
        put("name", layer.name)
        put("filePath", layer.filePath)
        put("isVisible", layer.isVisible)
        put("minZoom", layer.minZoom)
        put("maxZoom", layer.maxZoom)
        put("createdAt", layer.createdAt)
    }

    private fun fromJson(json: JSONObject): RasterLayer = RasterLayer(
        id = json.getString("id"),
        name = json.getString("name"),
        filePath = json.getString("filePath"),
        isVisible = json.optBoolean("isVisible", true),
        minZoom = json.optInt("minZoom", 0),
        maxZoom = json.optInt("maxZoom", 22),
        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
    )
}
