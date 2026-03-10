package org.dreamfinity.dsgl.core.colorpicker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorPickerInteractionSessionTests {
    @Test
    fun `drag target lifecycle is explicit and resettable`() {
        val session = ColorPickerInteractionSession()
        assertFalse(session.hasActiveDragTarget())

        session.dragTarget = ColorPickerDragTarget.Field
        assertTrue(session.hasActiveDragTarget())
        assertEquals(ColorPickerDragTarget.Field, session.dragTarget)

        session.clearDragTarget()
        assertFalse(session.hasActiveDragTarget())
        assertEquals(ColorPickerDragTarget.None, session.dragTarget)
    }

    @Test
    fun `text input session tracks key and editable buffer`() {
        val session = ColorPickerInteractionSession()
        session.textInput.begin("hex", "#FFAABB")
        assertEquals("hex", session.textInput.activeKey)
        assertEquals("#FFAABB", session.textInput.buffer)

        session.textInput.backspace()
        session.textInput.append('0')
        assertEquals("#FFAAB0", session.textInput.buffer)

        session.textInput.clear()
        assertNull(session.textInput.activeKey)
        assertEquals("", session.textInput.buffer)
    }

    @Test
    fun `resetAll clears hover drag dropdown and text input state`() {
        val session = ColorPickerInteractionSession()
        session.setHover(10, 20)
        session.dragTarget = ColorPickerDragTarget.Alpha
        session.modeDropdownOpen = true
        session.textInput.begin("a", "100")

        session.resetAll()

        assertEquals(Int.MIN_VALUE, session.hoverX)
        assertEquals(Int.MIN_VALUE, session.hoverY)
        assertEquals(ColorPickerDragTarget.None, session.dragTarget)
        assertFalse(session.modeDropdownOpen)
        assertNull(session.textInput.activeKey)
        assertEquals("", session.textInput.buffer)
    }
}
