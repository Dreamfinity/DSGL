package org.dreamfinity.dsgl.core.inspector

import org.dreamfinity.dsgl.core.style.CssUnit
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InspectorEditSessionTests {
    @Test
    fun `begin seeds active edit state`() {
        val session = InspectorEditSession()
        session.begin(
            property = StyleProperty.WIDTH,
            initialBuffer = "42",
            initialUnit = CssUnit.Px,
            isNumeric = true,
        )

        assertEquals(StyleProperty.WIDTH, session.activeProperty)
        assertEquals("42", session.activeBuffer)
        assertEquals(CssUnit.Px, session.activeUnit)
        assertTrue(session.activeIsNumeric)
    }

    @Test
    fun `closeAllDropdowns clears dropdown ownership and scroll state`() {
        val session =
            InspectorEditSession().apply {
                openValueProperty = StyleProperty.DISPLAY
                openValueScrollIndex = 4
                openUnitProperty = StyleProperty.WIDTH
                openUnitScrollIndex = 3
            }

        session.closeAllDropdowns()

        assertNull(session.openValueProperty)
        assertEquals(0, session.openValueScrollIndex)
        assertNull(session.openUnitProperty)
        assertEquals(0, session.openUnitScrollIndex)
    }

    @Test
    fun `resetAll clears both edit and dropdown state`() {
        val session =
            InspectorEditSession().apply {
                begin(
                    property = StyleProperty.HEIGHT,
                    initialBuffer = "11",
                    initialUnit = CssUnit.Em,
                    isNumeric = false,
                )
                openValueProperty = StyleProperty.DISPLAY
                openValueScrollIndex = 1
            }

        session.resetAll()

        assertNull(session.activeProperty)
        assertEquals("", session.activeBuffer)
        assertNull(session.activeUnit)
        assertFalse(session.activeIsNumeric)
        assertNull(session.openValueProperty)
        assertEquals(0, session.openValueScrollIndex)
        assertNull(session.openUnitProperty)
        assertEquals(0, session.openUnitScrollIndex)
    }
}
