package com.mappingsolution.data.fs

import com.mappingsolution.data.model.DestinationSource
import com.mappingsolution.data.model.Plan
import com.mappingsolution.data.model.PlanDestination
import com.mappingsolution.data.util.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanFileRepository @Inject constructor(private val storageManager: StorageManager) {

    private val _plans = MutableStateFlow<List<Plan>>(emptyList())

    init {
        CoroutineScope(Dispatchers.IO).launch { loadAll() }
    }

    private fun loadAll() {
        val dir = storageManager.getPlansDir()
        val loaded = dir.listFiles { f -> f.isFile && f.extension == "json" }
            ?.mapNotNull { readPlan(it) }
            ?: emptyList()
        _plans.value = loaded.sortedByDescending { it.createdAt }
    }

    fun observeAll(): Flow<List<Plan>> = _plans

    suspend fun getById(id: String): Plan? = _plans.value.find { it.id == id }

    suspend fun insert(plan: Plan): String = withContext(Dispatchers.IO) {
        writePlan(plan)
        _plans.value = (_plans.value + plan).sortedByDescending { it.createdAt }
        plan.id
    }

    suspend fun update(plan: Plan) = withContext(Dispatchers.IO) {
        val old = _plans.value.find { it.id == plan.id }
        // If name changed, delete the old file before writing the new one
        if (old != null && old.name != plan.name) {
            storageManager.getPlanFile(old.name, old.id).delete()
        }
        writePlan(plan)
        _plans.value = _plans.value.map { if (it.id == plan.id) plan else it }
            .sortedByDescending { it.createdAt }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val plan = _plans.value.find { it.id == id } ?: return@withContext
        storageManager.getPlanFile(plan.name, plan.id).delete()
        _plans.value = _plans.value.filter { it.id != id }
    }

    suspend fun deleteByIds(ids: List<String>) = withContext(Dispatchers.IO) {
        val toDelete = _plans.value.filter { it.id in ids }
        toDelete.forEach { storageManager.getPlanFile(it.name, it.id).delete() }
        _plans.value = _plans.value.filter { it.id !in ids }
    }

    suspend fun orphan(ids: List<String>) = withContext(Dispatchers.IO) {
        val updated = _plans.value.map { plan ->
            if (plan.id in ids && plan.groupId != null) {
                val u = plan.copy(groupId = null, updatedAt = System.currentTimeMillis())
                writePlan(u)
                u
            } else plan
        }
        _plans.value = updated.sortedByDescending { it.createdAt }
    }

    suspend fun moveToGroup(ids: List<String>, groupId: String) = withContext(Dispatchers.IO) {
        val updated = _plans.value.map { plan ->
            if (plan.id in ids && plan.groupId == null) {
                val u = plan.copy(groupId = groupId, updatedAt = System.currentTimeMillis())
                writePlan(u)
                u
            } else plan
        }
        _plans.value = updated.sortedByDescending { it.createdAt }
    }

    private fun writePlan(plan: Plan) {
        val destinationsJson = JSONArray().apply {
            plan.destinations.forEach { dest ->
                put(JSONObject().apply {
                    put("id", dest.id)
                    put("sourceType", dest.sourceType.name)
                    put("sourceId", dest.sourceId)
                    put("name", dest.name)
                    put("lat", dest.lat)
                    put("lng", dest.lng)
                })
            }
        }
        val json = JSONObject().apply {
            put("id", plan.id)
            put("name", plan.name)
            put("destinations", destinationsJson)
            plan.groupId?.let { put("groupId", it) }
            put("createdAt", plan.createdAt)
            put("updatedAt", plan.updatedAt)
        }
        storageManager.getPlanFile(plan.name, plan.id).writeText(json.toString())
    }

    private fun readPlan(file: java.io.File): Plan? = try {
        val json = JSONObject(file.readText())
        val destinationsArray = json.optJSONArray("destinations") ?: JSONArray()
        val destinations = (0 until destinationsArray.length()).mapNotNull { i ->
            try {
                val d = destinationsArray.getJSONObject(i)
                PlanDestination(
                    id = d.getString("id"),
                    sourceType = DestinationSource.valueOf(d.getString("sourceType")),
                    sourceId = d.getString("sourceId"),
                    name = d.getString("name"),
                    lat = d.getDouble("lat"),
                    lng = d.getDouble("lng"),
                )
            } catch (_: Exception) { null }
        }
        Plan(
            id = json.getString("id"),
            name = json.getString("name"),
            destinations = destinations,
            groupId = json.optString("groupId").takeIf { it.isNotEmpty() },
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
        )
    } catch (_: Exception) { null }
}
