package com.mappingsolution.data.fs

import com.mappingsolution.data.model.Route
import com.mappingsolution.data.model.RoutePoint
import com.mappingsolution.data.recording.SmoothedSample
import com.mappingsolution.data.util.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteFileRepository @Inject constructor(private val storageManager: StorageManager) {

    private val _routes = MutableStateFlow<List<Route>>(emptyList())

    init {
        CoroutineScope(Dispatchers.IO).launch { loadAll() }
    }

    private fun loadAll() {
        val dir = storageManager.getRecordingsDir()
        val loaded = dir.listFiles { f -> f.isDirectory }
            ?.mapNotNull { recordingDir ->
                val jsonFile = File(recordingDir, "recording.json")
                if (jsonFile.exists()) readRoute(jsonFile)?.let { it to recordingDir } else null
            }
            ?: emptyList()

        // Deduplicate by ID: if two dirs share the same route ID keep the newest,
        // delete the stale one. This cleans up leftover dirs from incomplete renames.
        val deduped = loaded
            .groupBy { (route, _) -> route.id }
            .values
            .map { group ->
                if (group.size > 1) {
                    val sorted = group.sortedByDescending { (route, _) -> route.updatedAt }
                    sorted.drop(1).forEach { (_, staleDir) -> staleDir.deleteRecursively() }
                    sorted.first().first
                } else {
                    group.first().first
                }
            }

        _routes.value = deduped.sortedBy { it.startedAt }
    }

    fun observeAll(): Flow<List<Route>> = _routes

    suspend fun getIncomplete(): List<Route> = _routes.value.filter { !it.didUserTapStop }

    suspend fun getById(id: String): Route? = _routes.value.find { it.id == id }

    suspend fun insert(route: Route): String = withContext(Dispatchers.IO) {
        val newRoute = route.copy(id = if (route.id.isEmpty()) UUID.randomUUID().toString() else route.id)
        writeRoute(newRoute)
        _routes.value = (_routes.value + newRoute).sortedBy { it.startedAt }
        newRoute.id
    }

    suspend fun updateFields(id: String, name: String, description: String?, color: String) = withContext(Dispatchers.IO) {
        val existing = _routes.value.find { it.id == id } ?: return@withContext
        if (existing.name != name) storageManager.renameRecordingFolder(existing.name, name, id)
        val updated = existing.copy(name = name, description = description, color = color, updatedAt = System.currentTimeMillis())
        writeRoute(updated)
        _routes.value = _routes.value.map { if (it.id == id) updated else it }
    }

    suspend fun update(route: Route) = withContext(Dispatchers.IO) {
        val old = _routes.value.find { it.id == route.id }
        if (old != null && old.name != route.name) {
            storageManager.renameRecordingFolder(old.name, route.name, route.id)
        }
        val updated = route.copy(updatedAt = System.currentTimeMillis())
        writeRoute(updated)
        _routes.value = _routes.value.map { if (it.id == route.id) updated else it }
    }

    suspend fun delete(route: Route) = withContext(Dispatchers.IO) {
        storageManager.deleteRecordingFolder(route.name, route.id)
        _routes.value = _routes.value.filter { it.id != route.id }
    }

    suspend fun deleteByIds(ids: List<String>) = withContext(Dispatchers.IO) {
        val toDelete = _routes.value.filter { it.id in ids }
        toDelete.forEach { storageManager.deleteRecordingFolder(it.name, it.id) }
        _routes.value = _routes.value.filter { it.id !in ids }
    }

    suspend fun orphan(ids: List<String>) = withContext(Dispatchers.IO) {
        val updated = _routes.value.map { route ->
            if (route.id in ids && route.groupId != null) {
                val u = route.copy(groupId = null, updatedAt = System.currentTimeMillis())
                writeRoute(u)
                u
            } else route
        }
        _routes.value = updated
    }

    suspend fun moveToGroup(ids: List<String>, groupId: String) = withContext(Dispatchers.IO) {
        val updated = _routes.value.map { route ->
            if (route.id in ids && route.groupId == null) {
                val u = route.copy(groupId = groupId, updatedAt = System.currentTimeMillis())
                writeRoute(u)
                u
            } else route
        }
        _routes.value = updated
    }

    /** Updates distance and duration on disk without renaming the recording folder. */
    suspend fun checkpoint(routeId: String, distanceMeters: Double, durationSec: Long) = withContext(Dispatchers.IO) {
        val route = _routes.value.find { it.id == routeId } ?: return@withContext
        val updated = route.copy(
            distanceMeters = distanceMeters,
            durationSec = durationSec,
            checkpointAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        writeRoute(updated)
        _routes.value = _routes.value.map { if (it.id == routeId) updated else it }
    }

    suspend fun appendPoints(routeId: String, points: List<RoutePoint>) = withContext(Dispatchers.IO) {
        if (points.isEmpty()) return@withContext
        val route = _routes.value.find { it.id == routeId } ?: return@withContext
        val file = storageManager.getRecordingPointsFile(route.name, routeId)
        FileWriter(file, true).use { writer ->
            for (pt in points) {
                writer.write("""{"ts":${pt.ts},"lat":${pt.lat},"lng":${pt.lng}}""")
                writer.write("\n")
            }
        }
    }

    /**
     * Atomically replaces the entire points file with [points].
     *
     * Writes to a sibling `.tmp` file first, then renames it over the target so a crash
     * or power loss during the write cannot corrupt the existing data.
     */
    suspend fun replacePoints(routeId: String, points: List<RoutePoint>) = withContext(Dispatchers.IO) {
        if (points.isEmpty()) return@withContext
        val route = _routes.value.find { it.id == routeId } ?: return@withContext
        val target = storageManager.getRecordingPointsFile(route.name, routeId)
        val tmp = File(target.parent, "${target.name}.tmp")
        try {
            FileWriter(tmp, false).use { writer ->
                for (pt in points) {
                    writer.write("""{"ts":${pt.ts},"lat":${pt.lat},"lng":${pt.lng}}""")
                    writer.write("\n")
                }
            }
            // On Linux/Android this rename is atomic within the same filesystem.
            tmp.renameTo(target)
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    suspend fun getPoints(routeId: String): List<RoutePoint> = withContext(Dispatchers.IO) {
        val route = _routes.value.find { it.id == routeId } ?: return@withContext emptyList()
        val file = storageManager.getRecordingPointsFile(route.name, routeId)
        if (!file.exists()) return@withContext emptyList()
        file.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    val json = JSONObject(line)
                    RoutePoint(ts = json.getLong("ts"), lat = json.getDouble("lat"), lng = json.getDouble("lng"))
                } catch (_: Exception) { null }
            }
    }

    /** Appends raw Kalman-smoothed samples (position + GPS heading/speed/accuracy) to
     *  `smoothed.jsonl` — the input for the Stop map-match pass. */
    suspend fun appendSmoothedSamples(routeId: String, samples: List<SmoothedSample>) = withContext(Dispatchers.IO) {
        if (samples.isEmpty()) return@withContext
        val route = _routes.value.find { it.id == routeId } ?: return@withContext
        val file = storageManager.getRecordingSmoothedFile(route.name, routeId)
        FileWriter(file, true).use { writer ->
            for (s in samples) {
                val brng = s.bearingDeg?.let { ""","brng":$it""" } ?: ""
                writer.write("""{"ts":${s.ts},"lat":${s.lat},"lng":${s.lng}$brng,"spd":${s.speedMps},"acc":${s.accuracyMeters}}""")
                writer.write("\n")
            }
        }
    }

    /** Reads `smoothed.jsonl`. Heading/speed/accuracy fall back to [SmoothedSample] defaults for
     *  older recordings whose smoothed file stored only ts/lat/lng. */
    suspend fun getSmoothedSamples(routeId: String): List<SmoothedSample> = withContext(Dispatchers.IO) {
        val route = _routes.value.find { it.id == routeId } ?: return@withContext emptyList()
        val file = storageManager.getRecordingSmoothedFile(route.name, routeId)
        if (!file.exists()) return@withContext emptyList()
        file.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    val json = JSONObject(line)
                    SmoothedSample(
                        ts = json.getLong("ts"),
                        lat = json.getDouble("lat"),
                        lng = json.getDouble("lng"),
                        bearingDeg = if (json.has("brng")) json.getDouble("brng") else null,
                        speedMps = json.optDouble("spd", 0.0),
                        accuracyMeters = json.optDouble("acc", 12.0),
                    )
                } catch (_: Exception) { null }
            }
    }

    private fun writeRoute(route: Route) {
        val json = JSONObject().apply {
            put("id", route.id)
            put("color", route.color)
            put("name", route.name)
            route.description?.let { put("description", it) }
            put("isVisible", route.isVisible)
            route.groupId?.let { put("groupId", it) }
            put("didUserTapStop", route.didUserTapStop)
            put("startedAt", route.startedAt)
            route.stoppedAt?.let { put("stoppedAt", it) }
            put("checkpointAt", route.checkpointAt)
            put("distanceMeters", route.distanceMeters)
            put("durationSec", route.durationSec)
            put("createdAt", route.createdAt)
            put("updatedAt", route.updatedAt)
        }
        storageManager.getRecordingFile(route.name, route.id).writeText(json.toString())
    }

    private fun readRoute(file: File): Route? = try {
        val json = JSONObject(file.readText())
        Route(
            id = json.getString("id"),
            color = json.optString("color", "#FFFF5722"),
            name = json.getString("name"),
            description = json.optString("description").takeIf { it.isNotEmpty() },
            isVisible = json.optBoolean("isVisible", true),
            groupId = json.optString("groupId").takeIf { it.isNotEmpty() },
            didUserTapStop = json.optBoolean("didUserTapStop", false),
            startedAt = json.getLong("startedAt"),
            stoppedAt = if (json.has("stoppedAt")) json.getLong("stoppedAt") else null,
            checkpointAt = json.getLong("checkpointAt"),
            distanceMeters = json.optDouble("distanceMeters", 0.0),
            durationSec = json.optLong("durationSec", 0L),
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
        )
    } catch (_: Exception) { null }
}
