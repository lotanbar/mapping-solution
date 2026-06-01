package com.mappingsolution.data.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** App-private external storage root — for non-image data (JSON, JSONL, recordings, exports). */
    val rootDir: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "mapping-solution-assets")

    /**
     * App-private external storage — for image/media files.
     * Unlike public external storage, the FUSE/MediaProvider layer does NOT restrict
     * writing image file types (AVIF, JPEG, …) here; no media permission required.
     * Falls back to internal storage if external is unavailable.
     */
    private val mediaRootDir: File
        get() = (context.getExternalFilesDir(null) ?: context.filesDir)

    init { rootDir.mkdirs() }

    // ── Name sanitization ────────────────────────────────────────────────────
    /** Replaces filesystem-unsafe characters with underscores and trims whitespace. */
    fun sanitizeName(name: String): String =
        name.replace(Regex("""[/\\:*?"<>|]"""), "_").trim()

    // ── Groups — only user-created groups; folder is "{sanitized-name}_{first8ofId}" ──
    fun getGroupsDir(): File = File(rootDir, "groups").also { it.mkdirs() }
    fun groupFolderName(name: String, id: String): String = "${sanitizeName(name)}_${id.take(8)}"
    fun getGroupDir(name: String, id: String): File = File(getGroupsDir(), groupFolderName(name, id)).also { it.mkdirs() }
    fun getGroupFile(name: String, id: String): File = File(getGroupDir(name, id), "group.json")

    /**
     * Finds a user-group directory by its full UUID.
     * Scans the (small) groups dir by name suffix, then verifies via group.json to avoid collisions.
     */
    fun findGroupDirById(groupId: String): File? {
        val suffix = "_${groupId.take(8)}"
        val candidate = getGroupsDir().listFiles()?.firstOrNull {
            it.isDirectory && it.name.endsWith(suffix)
        } ?: return null
        return try {
            val json = org.json.JSONObject(File(candidate, "group.json").readText())
            if (json.optString("id") == groupId) candidate else null
        } catch (_: Exception) { null }
    }

    fun deleteGroupDir(name: String, id: String): Boolean =
        File(getGroupsDir(), groupFolderName(name, id)).deleteRecursively()

    fun renameGroupDir(oldName: String, newName: String, id: String) {
        val oldDir = File(getGroupsDir(), groupFolderName(oldName, id))
        val newDir = File(getGroupsDir(), groupFolderName(newName, id))
        if (oldDir.exists() && oldDir.canonicalPath != newDir.canonicalPath) {
            if (oldDir.renameTo(newDir)) {
                if (oldDir.exists()) oldDir.deleteRecursively()
            }
        }
    }

    // ── POIs — folder is "{sanitized-name}_{first8ofId}" nested inside the group dir ──
    fun getPoisDir(): File = File(rootDir, "pois").also { it.mkdirs() }
    fun poiFolderName(name: String, id: String): String = "${sanitizeName(name)}_${id.take(8)}"

    /**
     * Returns the directory for a user POI, nested inside its group folder.
     * If [groupId] is null the POI is orphaned and falls back to pois/orphans/.
     */
    fun getPoiDir(name: String, id: String, groupId: String?): File {
        val base = if (groupId != null) {
            findGroupDirById(groupId) ?: File(getGroupsDir(), "unknown_${groupId.take(8)}")
        } else {
            File(getPoisDir(), "orphans").also { it.mkdirs() }
        }
        return File(base, poiFolderName(name, id)).also { it.mkdirs() }
    }

    fun getPoiFile(name: String, id: String, groupId: String?): File = File(getPoiDir(name, id, groupId), "poi.json")

    /**
     * Directory for a POI's media files (images, etc.).
     * Stored in app-private external storage to avoid Android's MediaProvider
     * FUSE restriction on writing image file types in public external storage.
     */
    fun getPoiMediaDir(name: String, id: String): File =
        File(mediaRootDir, "pois/${poiFolderName(name, id)}").also { it.mkdirs() }

    /**
     * Stable app-private location for a zip whose images back an imported bulk group.
     * The zip is copied here during import so its path is guaranteed to remain valid.
     */
    fun getImportZipFile(groupName: String, groupId: String): File =
        File(mediaRootDir, "import_zips/${sanitizeName(groupName)}_${groupId.take(8)}.zip")
            .also { it.parentFile?.mkdirs() }

    // ── Bulk imported POIs — one folder per group in pois/ ─────────────────────────
    /** The JSONL file holding all POIs for a bulk-imported group (one JSON object per line). */
    fun getBulkPoisFile(name: String, id: String): File = File(getPoiDir(name, id), "bulk_pois.jsonl")

    /** Manifest file for a bulk-imported group — stores group metadata alongside its JSONL. */
    fun getBulkManifestFile(name: String, id: String): File = File(getPoiDir(name, id), "manifest.json")

    /** Returns the raw bulk-import POI dir (under pois/, not groups/). */
    private fun getPoiDir(name: String, id: String): File = File(getPoisDir(), poiFolderName(name, id)).also { it.mkdirs() }

    fun deletePoiFolder(name: String, id: String): Boolean {
        File(getPoisDir(), poiFolderName(name, id)).deleteRecursively()
        File(mediaRootDir, "pois/${poiFolderName(name, id)}").deleteRecursively()
        File(mediaRootDir, "import_zips/${sanitizeName(name)}_${id.take(8)}.zip").delete()
        return true
    }

    fun deletePoiFolder(name: String, id: String, groupId: String?) {
        getPoiDir(name, id, groupId).deleteRecursively()
        File(mediaRootDir, "pois/${poiFolderName(name, id)}").deleteRecursively()
    }

    /**
     * Moves a POI folder when its name or group changes.
     * Handles all cases: rename-only, group-move-only, or both simultaneously.
     * Media dir (in mediaRootDir) is renamed by POI name/id only (not group-scoped).
     */
    fun movePoiFolder(
        oldName: String, newName: String,
        poiId: String,
        oldGroupId: String?, newGroupId: String?,
    ) {
        val oldBase = if (oldGroupId != null) {
            findGroupDirById(oldGroupId) ?: File(getGroupsDir(), "unknown_${oldGroupId.take(8)}")
        } else {
            File(getPoisDir(), "orphans")
        }
        val newBase = if (newGroupId != null) {
            findGroupDirById(newGroupId) ?: File(getGroupsDir(), "unknown_${newGroupId.take(8)}")
        } else {
            File(getPoisDir(), "orphans").also { it.mkdirs() }
        }
        val oldDir = File(oldBase, poiFolderName(oldName, poiId))
        val newDir = File(newBase, poiFolderName(newName, poiId))
        if (oldDir.exists() && oldDir.canonicalPath != newDir.canonicalPath) {
            newDir.parentFile?.mkdirs()
            if (!oldDir.renameTo(newDir)) {
                oldDir.copyRecursively(newDir, overwrite = true)
                oldDir.deleteRecursively()
            }
        }
        // Rename media dir if POI name changed
        if (oldName != newName) {
            val oldMedia = File(mediaRootDir, "pois/${poiFolderName(oldName, poiId)}")
            val newMedia = File(mediaRootDir, "pois/${poiFolderName(newName, poiId)}")
            if (oldMedia.exists() && oldMedia.canonicalPath != newMedia.canonicalPath) {
                if (!oldMedia.renameTo(newMedia)) {
                    oldMedia.copyRecursively(newMedia, overwrite = true)
                    oldMedia.deleteRecursively()
                }
            }
        }
    }

    // ── Recordings — folder is "{sanitized-name}_{first8ofId}" ───────────────
    fun getRecordingsDir(): File = File(rootDir, "recordings").also { it.mkdirs() }
    fun recordingFolderName(name: String, id: String): String = "${sanitizeName(name)}_${id.take(8)}"
    fun getRecordingDir(name: String, id: String): File = File(getRecordingsDir(), recordingFolderName(name, id)).also { it.mkdirs() }
    fun getRecordingFile(name: String, id: String): File = File(getRecordingDir(name, id), "recording.json")
    fun getRecordingPointsFile(name: String, id: String): File = File(getRecordingDir(name, id), "points.jsonl")
    fun deleteRecordingFolder(name: String, id: String): Boolean =
        File(getRecordingsDir(), recordingFolderName(name, id)).deleteRecursively()
    fun renameRecordingFolder(oldName: String, newName: String, id: String) {
        val oldDir = File(getRecordingsDir(), recordingFolderName(oldName, id))
        val newDir = File(getRecordingsDir(), recordingFolderName(newName, id))
        if (oldDir.exists() && oldDir.canonicalPath != newDir.canonicalPath) {
            if (oldDir.renameTo(newDir)) {
                if (oldDir.exists()) oldDir.deleteRecursively()
            }
        }
    }

    /** Temporary staging directory for media files being attached to a POI. */
    fun getTempDir(): File = File(mediaRootDir, "temp").also { it.mkdirs() }

    // ── Plans — filename is "{sanitized-name}_{first8ofId}.json" ─────────────
    fun getPlansDir(): File = File(rootDir, "plans").also { it.mkdirs() }
    fun getPlanFile(name: String, id: String): File = File(getPlansDir(), "${sanitizeName(name)}_${id.take(8)}.json")

    // ── MBTiles — stored in app-private external storage (no MANAGE_EXTERNAL_STORAGE needed) ──
    private val mbtilesRootDir: File
        get() = (context.getExternalFilesDir(null) ?: context.filesDir)

    fun getMbtilesDir(): File = File(mbtilesRootDir, "mbtiles").also { it.mkdirs() }

    fun getMbtilesFile(name: String, id: String): File =
        File(getMbtilesDir(), "${sanitizeName(name)}_${id.take(8)}.mbtiles")

    fun getMbtilesTempFile(): File = File(getMbtilesDir(), "import_tmp_${System.currentTimeMillis()}.mbtiles")

    fun getExportsDir(): File = File(rootDir, "exports").also { it.mkdirs() }

    fun resolvePath(relativePath: String): File = File(rootDir, relativePath)
    fun toRelativePath(file: File): String =
        file.absolutePath.removePrefix(rootDir.absolutePath + File.separator)
}
