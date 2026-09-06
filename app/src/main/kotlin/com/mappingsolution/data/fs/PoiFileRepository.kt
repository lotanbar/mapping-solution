package com.mappingsolution.data.fs

import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.util.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoiFileRepository @Inject constructor(private val storageManager: StorageManager) {

    private val _pois = MutableStateFlow<List<Poi>>(emptyList())

    init {
        CoroutineScope(Dispatchers.IO).launch { loadAll() }
    }

    private fun loadAll() {
        val pois = mutableListOf<Poi>()

        // User POIs: groups/<groupFolder>/<poiFolder>/poi.json
        storageManager.getGroupsDir().listFiles { f -> f.isDirectory }
            ?.forEach { groupDir ->
                groupDir.listFiles { f -> f.isDirectory }
                    ?.forEach { poiDir ->
                        val jsonFile = File(poiDir, "poi.json")
                        if (jsonFile.exists()) readPoi(jsonFile)?.let { pois.add(it) }
                    }
            }

        // Orphan POIs: pois/orphans/<poiFolder>/poi.json
        File(storageManager.getPoisDir(), "orphans")
            .listFiles { f -> f.isDirectory }
            ?.forEach { poiDir ->
                val jsonFile = File(poiDir, "poi.json")
                if (jsonFile.exists()) readPoi(jsonFile)?.let { pois.add(it) }
            }

        _pois.value = pois.sortedBy { it.createdAt }
    }

    fun observeAll(): Flow<List<Poi>> = _pois
    fun observeByGroup(groupId: String): Flow<List<Poi>> = _pois.map { list -> list.filter { it.groupId == groupId } }
    fun observeOrphans(): Flow<List<Poi>> = _pois.map { list -> list.filter { it.groupId == null } }

    suspend fun countByGroup(groupId: String): Int = _pois.value.count { it.groupId == groupId }

    suspend fun getIdsByGroup(groupId: String): List<String> = _pois.value.filter { it.groupId == groupId }.map { it.id }

    suspend fun getById(id: String): Poi? = _pois.value.find { it.id == id }

    suspend fun insert(poi: Poi): String = withContext(Dispatchers.IO) {
        val newPoi = poi.copy(id = if (poi.id.isEmpty()) UUID.randomUUID().toString() else poi.id)
        writePoi(newPoi)
        _pois.value = (_pois.value + newPoi).sortedBy { it.createdAt }
        newPoi.id
    }

    /** Writes all POIs to disk then does a single StateFlow update. Use for bulk imports.
     *  Optional [transform] is applied to each POI just before writing, avoiding a separate prep pass. */
    suspend fun insertBatch(
        pois: List<Poi>,
        transform: (Poi) -> Poi = { it },
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<String> = withContext(Dispatchers.IO) {
        val total = pois.size
        val newPois = ArrayList<Poi>(total)
        var lastEmit = 0L
        for ((i, raw) in pois.withIndex()) {
            val poi = transform(raw.copy(id = if (raw.id.isEmpty()) UUID.randomUUID().toString() else raw.id))
            writePoi(poi)
            newPois.add(poi)
            val now = System.currentTimeMillis()
            if (now - lastEmit >= 32 || i == total - 1) {
                onProgress(i + 1, total)
                lastEmit = now
            }
        }
        _pois.value = (_pois.value + newPois).sortedBy { it.createdAt }
        newPois.map { it.id }
    }

    suspend fun update(poi: Poi) = withContext(Dispatchers.IO) {
        val old = _pois.value.find { it.id == poi.id }
        val nameChanged = old != null && old.name != poi.name
        val groupChanged = old != null && old.groupId != poi.groupId
        if (old != null && (nameChanged || groupChanged)) {
            storageManager.movePoiFolder(
                oldName = old.name, newName = poi.name,
                poiId = poi.id,
                oldGroupId = old.groupId, newGroupId = poi.groupId,
            )
        }
        val updated = poi.copy(updatedAt = System.currentTimeMillis())
        writePoi(updated)
        _pois.value = _pois.value.map { if (it.id == poi.id) updated else it }
    }

    suspend fun delete(poi: Poi) = withContext(Dispatchers.IO) {
        storageManager.deletePoiFolder(poi.name, poi.id, poi.groupId)
        _pois.value = _pois.value.filter { it.id != poi.id }
    }

    suspend fun deleteByIds(
        ids: List<String>,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ) = withContext(Dispatchers.IO) {
        val toDelete = _pois.value.filter { it.id in ids }
        val total = toDelete.size
        var lastEmit = 0L
        for ((i, poi) in toDelete.withIndex()) {
            storageManager.deletePoiFolder(poi.name, poi.id, poi.groupId)
            val now = System.currentTimeMillis()
            if (now - lastEmit >= 32 || i == total - 1) {
                onProgress(i + 1, total)
                lastEmit = now
            }
        }
        _pois.value = _pois.value.filter { it.id !in ids }
    }

    suspend fun orphan(ids: List<String>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        _pois.value = _pois.value.map { poi ->
            if (poi.id in ids) {
                storageManager.movePoiFolder(
                    oldName = poi.name, newName = poi.name,
                    poiId = poi.id,
                    oldGroupId = poi.groupId, newGroupId = null,
                )
                poi.copy(groupId = null, updatedAt = now).also { writePoi(it) }
            } else poi
        }
    }

    suspend fun moveToGroup(ids: List<String>, groupId: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        _pois.value = _pois.value.map { poi ->
            if (poi.id in ids) {
                storageManager.movePoiFolder(
                    oldName = poi.name, newName = poi.name,
                    poiId = poi.id,
                    oldGroupId = poi.groupId, newGroupId = groupId,
                )
                poi.copy(groupId = groupId, updatedAt = now).also { writePoi(it) }
            } else poi
        }
    }

    private fun writePoi(poi: Poi) {
        val json = JSONObject().apply {
            put("id", poi.id)
            poi.groupId?.let { put("groupId", it) }
            put("name", poi.name)
            poi.description?.let { put("description", it) }
            put("lat", poi.lat)
            put("lng", poi.lng)
            poi.elevation?.let { put("elevation", it) }
            put("mediaPaths", JSONArray(poi.mediaPaths))
            put("isVisible", poi.isVisible)
            put("createdAt", poi.createdAt)
            put("updatedAt", poi.updatedAt)
            poi.iconKey?.let { put("iconKey", it) }
            poi.wikiRef?.let { put("wikiRef", it) }
            if (poi.imageSearchNames.isNotEmpty()) put("imageSearchNames", JSONArray(poi.imageSearchNames))
            if (poi.imageRefs.isNotEmpty()) put("imageRefs", JSONArray(poi.imageRefs))
            poi.savedSource?.let { put("savedSource", it.name) }
            poi.sourceId?.let { put("sourceId", it) }
            poi.sourceGroupId?.let { put("sourceGroupId", it) }
        }
        storageManager.getPoiFile(poi.name, poi.id, poi.groupId).writeText(json.toString())
    }

    private fun readPoi(file: File): Poi? = try {
        val json = JSONObject(file.readText())
        val mediaArr = json.optJSONArray("mediaPaths")
        val mediaPaths = if (mediaArr != null) List(mediaArr.length()) { mediaArr.getString(it) } else emptyList()
        val imageSearchNames = json.optJSONArray("imageSearchNames")?.let { array ->
            List(array.length()) { array.getString(it) }
        }.orEmpty()
        val imageRefs = json.optJSONArray("imageRefs")?.let { array ->
            List(array.length()) { array.getString(it) }
        }.orEmpty()
        Poi(
            id = json.getString("id"),
            groupId = json.optString("groupId").takeIf { it.isNotEmpty() },
            name = json.getString("name"),
            description = json.optString("description").takeIf { it.isNotEmpty() },
            lat = json.getDouble("lat"),
            lng = json.getDouble("lng"),
            elevation = if (json.has("elevation")) json.getDouble("elevation") else null,
            mediaPaths = mediaPaths,
            isVisible = json.optBoolean("isVisible", true),
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
            iconKey = json.optString("iconKey").takeIf { it.isNotEmpty() },
            wikiRef = json.optString("wikiRef").takeIf { it.isNotEmpty() },
            imageSearchNames = imageSearchNames,
            imageRefs = imageRefs,
            savedSource = json.optString("savedSource").takeIf { it.isNotEmpty() }?.let { value ->
                runCatching { com.mappingsolution.data.model.DestinationSource.valueOf(value) }.getOrNull()
            },
            sourceId = json.optString("sourceId").takeIf { it.isNotEmpty() },
            sourceGroupId = json.optString("sourceGroupId").takeIf { it.isNotEmpty() },
        )
    } catch (_: Exception) { null }
}
