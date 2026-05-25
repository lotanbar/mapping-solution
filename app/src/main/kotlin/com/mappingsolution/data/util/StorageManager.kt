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

    // ── Groups — filename is the group name (unique by dedup) ────────────────
    fun getGroupsDir(): File = File(rootDir, "groups").also { it.mkdirs() }
    fun getGroupFile(groupName: String): File = File(getGroupsDir(), "${sanitizeName(groupName)}.json")

    // ── POIs — folder is "{sanitized-name}_{first8ofId}", media flat inside ──
    fun getPoisDir(): File = File(rootDir, "pois").also { it.mkdirs() }
    fun poiFolderName(name: String, id: String): String = "${sanitizeName(name)}_${id.take(8)}"
    fun getPoiDir(name: String, id: String): File = File(getPoisDir(), poiFolderName(name, id)).also { it.mkdirs() }
    fun getPoiFile(name: String, id: String): File = File(getPoiDir(name, id), "poi.json")

    /**
     * Directory for a POI's media files (images, etc.).
     * Stored in app-private external storage to avoid Android's MediaProvider
     * FUSE restriction on writing image file types in public external storage.
     */
    fun getPoiMediaDir(name: String, id: String): File =
        File(mediaRootDir, "pois/${poiFolderName(name, id)}").also { it.mkdirs() }

    // ── Bulk imported POIs — one folder per group ─────────────────────────
    /** The JSONL file holding all POIs for a bulk-imported group (one JSON object per line). */
    fun getBulkPoisFile(name: String, id: String): File = File(getPoiDir(name, id), "bulk_pois.jsonl")

    fun deletePoiFolder(name: String, id: String): Boolean {
        File(getPoisDir(), poiFolderName(name, id)).deleteRecursively()
        // Also remove the media dir in private storage
        File(mediaRootDir, "pois/${poiFolderName(name, id)}").deleteRecursively()
        return true
    }
    fun renamePoiFolder(oldName: String, newName: String, id: String) {
        val oldDir = File(getPoisDir(), poiFolderName(oldName, id))
        val newDir = File(getPoisDir(), poiFolderName(newName, id))
        if (oldDir.exists() && oldDir.canonicalPath != newDir.canonicalPath) {
            if (oldDir.renameTo(newDir)) {
                if (oldDir.exists()) oldDir.deleteRecursively()
            }
        }
        // Also rename the media dir in private storage
        val oldMedia = File(mediaRootDir, "pois/${poiFolderName(oldName, id)}")
        val newMedia = File(mediaRootDir, "pois/${poiFolderName(newName, id)}")
        if (oldMedia.exists() && oldMedia.canonicalPath != newMedia.canonicalPath) {
            if (oldMedia.renameTo(newMedia)) {
                if (oldMedia.exists()) oldMedia.deleteRecursively()
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
