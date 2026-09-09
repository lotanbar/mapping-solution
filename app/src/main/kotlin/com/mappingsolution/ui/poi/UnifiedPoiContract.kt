package com.mappingsolution.ui.poi

object PoiScreenText {
    val FIELD_ORDER = listOf("Image", "Title", "Source", "Description", "Group", "Actions")
    const val NO_IMAGE = "No image"
    const val NO_DESCRIPTION = "No description"
    const val NO_GROUP = "No group"
}

/** The mutually exclusive states supported by the unified POI screen. */
enum class PoiScreenKind {
    CREATION,
    CREATED,
    IMPORTED,
    OSM,
    STARRED_IMPORTED,
    STARRED_OSM,
}

data class PoiActionAvailability(
    val navigate: Boolean,
    val star: Boolean,
    val remove: Boolean,
    val editOrSave: Boolean,
    val addToPlan: Boolean,
)

fun poiActionAvailability(kind: PoiScreenKind, isEditing: Boolean): PoiActionAvailability {
    if (isEditing) {
        return PoiActionAvailability(
            navigate = false,
            star = false,
            remove = false,
            editOrSave = true,
            addToPlan = false,
        )
    }
    return when (kind) {
        PoiScreenKind.CREATION -> PoiActionAvailability(false, false, false, true, false)
        PoiScreenKind.CREATED -> PoiActionAvailability(true, false, true, true, true)
        PoiScreenKind.IMPORTED, PoiScreenKind.OSM ->
            PoiActionAvailability(true, true, false, false, true)
        PoiScreenKind.STARRED_IMPORTED, PoiScreenKind.STARRED_OSM ->
            PoiActionAvailability(true, true, false, true, true)
    }
}

fun requiresUnstarConfirmation(personalNote: String, groupId: String?, personalPhotoCount: Int): Boolean =
    personalNote.isNotBlank() || groupId != null || personalPhotoCount > 0

/**
 * Keeps source text and a private note in the existing Poi.description field.
 * The deliberately uncommon separator also makes old starred POIs read as source-only.
 */
object PoiDescriptionCodec {
    const val SEPARATOR = "\n\u001EPERSONAL_NOTE\u001E\n"

    data class Parts(val source: String, val personalNote: String)

    fun decode(value: String?): Parts {
        val raw = value.orEmpty()
        val separatorIndex = raw.indexOf(SEPARATOR)
        return if (separatorIndex < 0) {
            Parts(source = raw, personalNote = "")
        } else {
            Parts(
                source = raw.substring(0, separatorIndex),
                personalNote = raw.substring(separatorIndex + SEPARATOR.length),
            )
        }
    }

    fun encode(source: String?, personalNote: String?): String? {
        val sourceText = source.orEmpty().trim()
        val noteText = personalNote.orEmpty().trim()
        return when {
            noteText.isNotEmpty() -> sourceText + SEPARATOR + noteText
            sourceText.isNotEmpty() -> sourceText
            else -> null
        }
    }
}
