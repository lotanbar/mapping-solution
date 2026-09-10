package com.mappingsolution.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDirectionUtilsTest {
    @Test
    fun `english paragraph containing a Hebrew name stays LTR`() {
        assertFalse("Givat Nili (Hebrew: גבעת ניל״י) is a moshav.".isRtlParagraph())
    }

    @Test
    fun `Hebrew paragraph containing English stays RTL`() {
        assertTrue("גבעת ניל״י היא מושב בשם Givat Nili.".isRtlParagraph())
    }
}
