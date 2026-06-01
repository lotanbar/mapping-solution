package com.mappingsolution.data.migration

import android.content.Context
import android.content.SharedPreferences
import com.mappingsolution.data.places.GOOGLE_PLACES_GROUP_ID
import com.mappingsolution.data.places.OSM_POI_GROUP_ID
import com.mappingsolution.data.util.StorageManager
import org.json.JSONObject
import java.io.File

/**
 * One-time idempotent migration from storage-v1 to storage-v2 layout.
 *
 * V1 layout:
 *   groups/<name>.json             ← all group metadata (user, bulk-import, system)
 *   pois/<name>_<id8>/poi.json     ← user POIs (flat, not nested by group)
 *   pois/<name>_<id8>/bulk_pois.jsonl ← bulk imported POIs
 *
 * V2 layout:
 *   groups/<name>_<id8>/group.json         ← user-created groups only
 *   groups/<name>_<id8>/<poi>_<id8>/poi.json ← user POIs nested inside their group
 *   pois/<name>_<id8>/manifest.json        ← bulk imported group metadata
 *   pois/<name>_<id8>/bulk_pois.jsonl      ← unchanged
 *   pois/orphans/<poi>_<id8>/poi.json      ← POIs with no group
 *
 * System groups (Google Places, OSM) are removed from disk; their isVisible is
 * migrated to SharedPreferences.
 *
 * The migration is gated by a `.storage_v2` marker file and safe to run multiple
 * times (skips already-moved items).
 */
class StorageV2Migration(
    private val context: Context,
    private val storageManager: StorageManager,
) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("group_visibility", Context.MODE_PRIVATE)
    }

    fun run() {
        val groupsDir = storageManager.getGroupsDir()
        val poisDir = storageManager.getPoisDir()

        // ── Step 1: read and categorize all legacy group files ─────────────────
        val legacyGroupFiles = groupsDir.listFiles { f -> f.isFile && f.extension == "json" }
            ?: emptyArray()

        val systemIds = setOf(GOOGLE_PLACES_GROUP_ID, OSM_POI_GROUP_ID)

        for (file in legacyGroupFiles) {
            val json = try { JSONObject(file.readText()) } catch (_: Exception) { continue }
            val id = json.optString("id").takeIf { it.isNotEmpty() } ?: continue
            val name = json.optString("name").takeIf { it.isNotEmpty() } ?: continue
            val isBulk = json.optBoolean("isBulk", false)
            val isVisible = json.optBoolean("isVisible", true)

            when {
                // System groups: migrate isVisible to SharedPrefs and delete file
                id in systemIds -> {
                    prefs.edit().putBoolean(id, isVisible).apply()
                    file.delete()
                }

                // Bulk-imported groups: move metadata to pois/<name>_<id8>/manifest.json
                isBulk -> {
                    val manifest = storageManager.getBulkManifestFile(name, id)
                    if (!manifest.exists()) {
                        manifest.parentFile?.mkdirs()
                        file.copyTo(manifest, overwrite = false)
                    }
                    file.delete()
                }

                // User-created groups: create groups/<name>_<id8>/group.json
                else -> {
                    val dest = storageManager.getGroupFile(name, id)
                    if (!dest.exists()) {
                        dest.parentFile?.mkdirs()
                        file.copyTo(dest, overwrite = false)
                    }
                    file.delete()
                }
            }
        }

        // ── Step 2: collect groupId → (name, dir) for user groups (needed to route POIs) ──
        val groupDirById = mutableMapOf<String, Pair<String, File>>() // id → (name, groupDir)
        groupsDir.listFiles { f -> f.isDirectory }?.forEach { groupDir ->
            val groupJson = File(groupDir, "group.json")
            if (!groupJson.exists()) return@forEach
            try {
                val json = JSONObject(groupJson.readText())
                val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return@forEach
                val name = json.optString("name").takeIf { it.isNotEmpty() } ?: return@forEach
                groupDirById[id] = name to groupDir
            } catch (_: Exception) {}
        }

        // ── Step 3: move user POI folders from pois/<poi>_<id8>/ into group dirs ──
        poisDir.listFiles { f -> f.isDirectory && f.name != "orphans" }?.forEach { poiDir ->
            val poiJson = File(poiDir, "poi.json")
            if (!poiJson.exists()) return@forEach // bulk group folder or already moved

            val json = try { JSONObject(poiJson.readText()) } catch (_: Exception) { return@forEach }
            val poiId = json.optString("id").takeIf { it.isNotEmpty() } ?: return@forEach
            val poiName = json.optString("name").takeIf { it.isNotEmpty() } ?: return@forEach
            val groupId = json.optString("groupId").takeIf { it.isNotEmpty() }

            val destDir = if (groupId != null && groupDirById.containsKey(groupId)) {
                val (_, groupDir) = groupDirById[groupId]!!
                File(groupDir, storageManager.poiFolderName(poiName, poiId))
            } else {
                // Orphan: no group or group not found
                File(poisDir, "orphans/${storageManager.poiFolderName(poiName, poiId)}")
            }

            if (destDir.canonicalPath != poiDir.canonicalPath && !destDir.exists()) {
                destDir.parentFile?.mkdirs()
                if (!poiDir.renameTo(destDir)) {
                    poiDir.copyRecursively(destDir, overwrite = false)
                    poiDir.deleteRecursively()
                } else {
                    if (poiDir.exists()) poiDir.deleteRecursively()
                }
            }
        }
    }
}
