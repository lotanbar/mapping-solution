package com.mappingsolution.ui.poi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedPoiContractTest {
    @Test
    fun `fields have the required fixed order and placeholders`() {
        assertEquals(listOf("Image", "Title", "Source", "Description", "Group", "Actions"), PoiScreenText.FIELD_ORDER)
        assertEquals("No image", PoiScreenText.NO_IMAGE)
        assertEquals("No description", PoiScreenText.NO_DESCRIPTION)
        assertEquals("No group", PoiScreenText.NO_GROUP)
        assertEquals("Loading photos and description…", PoiScreenText.LOADING_CONTENT)
    }

    @Test
    fun `created permissions`() {
        assertEquals(
            PoiActionAvailability(navigate = true, star = false, remove = true, editOrSave = true, addToPlan = true),
            poiActionAvailability(PoiScreenKind.CREATED, isEditing = false),
        )
    }

    @Test
    fun `unstarred external permissions`() {
        listOf(PoiScreenKind.IMPORTED, PoiScreenKind.OSM).forEach { kind ->
            assertEquals(
                PoiActionAvailability(navigate = true, star = true, remove = false, editOrSave = false, addToPlan = true),
                poiActionAvailability(kind, isEditing = false),
            )
        }
    }

    @Test
    fun `starred external permissions`() {
        listOf(PoiScreenKind.STARRED_IMPORTED, PoiScreenKind.STARRED_OSM).forEach { kind ->
            assertEquals(
                PoiActionAvailability(navigate = true, star = true, remove = false, editOrSave = true, addToPlan = true),
                poiActionAvailability(kind, isEditing = false),
            )
        }
    }

    @Test
    fun `editing and creation only enable save`() {
        PoiScreenKind.entries.forEach { kind ->
            val actions = poiActionAvailability(kind, isEditing = true)
            assertFalse(actions.navigate)
            assertFalse(actions.star)
            assertFalse(actions.remove)
            assertTrue(actions.editOrSave)
            assertFalse(actions.addToPlan)
        }
    }

    @Test
    fun `description codec persists source and personal note`() {
        val encoded = PoiDescriptionCodec.encode("Source description", "My private note")
        assertEquals(
            PoiDescriptionCodec.Parts("Source description", "My private note"),
            PoiDescriptionCodec.decode(encoded),
        )
    }

    @Test
    fun `legacy starred description remains source text`() {
        assertEquals(
            PoiDescriptionCodec.Parts("Existing description", ""),
            PoiDescriptionCodec.decode("Existing description"),
        )
        assertNull(PoiDescriptionCodec.encode(null, null))
    }

    @Test
    fun `unstar confirms only when personal data would be lost`() {
        assertFalse(requiresUnstarConfirmation("", null, 0))
        assertTrue(requiresUnstarConfirmation("note", null, 0))
        assertTrue(requiresUnstarConfirmation("", "group", 0))
        assertTrue(requiresUnstarConfirmation("", null, 1))
    }
}
