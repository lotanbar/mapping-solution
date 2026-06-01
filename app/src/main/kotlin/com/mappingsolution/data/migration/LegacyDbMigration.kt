package com.mappingsolution.data.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mappingsolution.data.model.Group
import com.mappingsolution.data.model.Poi
import com.mappingsolution.data.model.Route
import com.mappingsolution.data.util.StorageManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

class LegacyDbMigration(private val context: Context, private val storageManager: StorageManager) {

    private val dbPath = context.getDatabasePath("mapping_solution.db").absolutePath

    fun run() {
        if (!File(dbPath).exists()) return

        val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)

        try {
            val groupIdMap = migrateGroups(db)
            migratePois(db, groupIdMap)
            migrateRoutes(db)
        } finally {
            db.close()
        }
    }

    private fun migrateGroups(db: SQLiteDatabase): Map<Long, String> {
        val idMap = mutableMapOf<Long, String>()
        val cursor = db.rawQuery("SELECT id, name, description, iconKey, color, isVisible, createdAt, updatedAt FROM groups", null)
        cursor.use {
            while (it.moveToNext()) {
                val oldId = it.getLong(0)
                val group = Group(
                    name = it.getString(1),
                    description = it.getString(2),
                    iconKey = it.getString(3),
                    color = it.getString(4),
                    isVisible = it.getInt(5) != 0,
                    createdAt = it.getLong(6),
                    updatedAt = it.getLong(7),
                )
                idMap[oldId] = group.id
                writeGroup(group)
            }
        }
        return idMap
    }

    private fun migratePois(db: SQLiteDatabase, groupIdMap: Map<Long, String>) {
        val cursor = db.rawQuery(
            "SELECT id, groupId, name, description, lat, lng, elevation, mediaPaths, isVisible, createdAt, updatedAt FROM pois", null
        )
        cursor.use {
            while (it.moveToNext()) {
                val oldId = it.getLong(0)
                val oldGroupId = if (it.isNull(1)) null else it.getLong(1)
                val oldMediaPathsJson = it.getString(7) ?: "[]"

                val newMediaFilenames = migratePoiMedia(oldId, oldMediaPathsJson)

                val poi = Poi(
                    groupId = oldGroupId?.let { gid -> groupIdMap[gid] },
                    name = it.getString(2),
                    description = it.getString(3),
                    lat = it.getDouble(4),
                    lng = it.getDouble(5),
                    elevation = if (it.isNull(6)) null else it.getDouble(6),
                    mediaPaths = newMediaFilenames,
                    isVisible = it.getInt(8) != 0,
                    createdAt = it.getLong(9),
                    updatedAt = it.getLong(10),
                )
                writePoi(poi)
            }
        }
    }

    private fun migratePoiMedia(oldPoiId: Long, mediaPathsJson: String): List<String> {
        val filenames = mutableListOf<String>()
        try {
            val arr = JSONArray(mediaPathsJson)
            for (i in 0 until arr.length()) {
                filenames.add(arr.getString(i))
            }
        } catch (_: Exception) {}
        return filenames
    }

    private fun migrateRoutes(db: SQLiteDatabase) {
        val cursor = db.rawQuery(
            "SELECT id, color, name, description, isVisible, didUserTapStop, startedAt, stoppedAt, checkpointAt, distanceMeters, durationSec, createdAt, updatedAt FROM routes",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                val oldRouteId = it.getLong(0)
                val route = Route(
                    color = it.getString(1) ?: "#FFFF5722",
                    name = it.getString(2),
                    description = it.getString(3),
                    isVisible = it.getInt(4) != 0,
                    didUserTapStop = it.getInt(5) != 0,
                    startedAt = it.getLong(6),
                    stoppedAt = if (it.isNull(7)) null else it.getLong(7),
                    checkpointAt = it.getLong(8),
                    distanceMeters = it.getDouble(9),
                    durationSec = it.getLong(10),
                    createdAt = it.getLong(11),
                    updatedAt = it.getLong(12),
                )
                writeRoute(route)
                migrateRoutePoints(db, oldRouteId, route)
            }
        }
    }

    private fun migrateRoutePoints(db: SQLiteDatabase, oldRouteId: Long, newRoute: Route) {
        val cursor = db.rawQuery(
            "SELECT ts, lat, lng FROM route_points WHERE routeId = ? ORDER BY orderIndex ASC",
            arrayOf(oldRouteId.toString())
        )
        val pointsFile = storageManager.getRecordingPointsFile(newRoute.name, newRoute.id)
        FileWriter(pointsFile, true).use { writer ->
            cursor.use {
                while (it.moveToNext()) {
                    val ts = it.getLong(0)
                    val lat = it.getDouble(1)
                    val lng = it.getDouble(2)
                    writer.write("""{"ts":$ts,"lat":$lat,"lng":$lng}""")
                    writer.write("\n")
                }
            }
        }
    }

    private fun writeGroup(group: Group) {
        val json = JSONObject().apply {
            put("id", group.id)
            put("name", group.name)
            group.description?.let { put("description", it) }
            put("iconKey", group.iconKey)
            put("color", group.color)
            put("isVisible", group.isVisible)
            put("createdAt", group.createdAt)
            put("updatedAt", group.updatedAt)
        }
        storageManager.getGroupFile(group.name, group.id).writeText(json.toString())
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
        }
        storageManager.getPoiFile(poi.name, poi.id, poi.groupId).writeText(json.toString())
    }

    private fun writeRoute(route: Route) {
        val json = JSONObject().apply {
            put("id", route.id)
            put("color", route.color)
            put("name", route.name)
            route.description?.let { put("description", it) }
            put("isVisible", route.isVisible)
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
}
