package org.dreamfinity.dsgl.core.colorpicker

import org.dreamfinity.dsgl.core.dom.layout.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorPickerPopupGeometryTests {
    @Test
    fun `buildFrame derives header body and close rectangles from panel`() {
        val frame = ColorPickerPopupGeometry.buildFrame(
            panelRect = Rect(100, 80, 320, 260),
            headerHeight = 26,
            panelPadding = 6
        )

        assertEquals(Rect(100, 80, 320, 260), frame.panelRect)
        assertEquals(Rect(100, 80, 320, 26), frame.headerRect)
        assertEquals(Rect(106, 112, 308, 222), frame.bodyRect)
        assertEquals(Rect(400, 84, 16, 16), frame.closeRect)
    }

    @Test
    fun `clampPanel keeps popup inside viewport with margins`() {
        val clamped = ColorPickerPopupGeometry.clampPanel(
            rect = Rect(-50, 999, 300, 220),
            viewportWidth = 640,
            viewportHeight = 360
        )

        assertEquals(2, clamped.x)
        assertEquals(138, clamped.y)
        assertEquals(300, clamped.width)
        assertEquals(220, clamped.height)
    }

    @Test
    fun `resolvePanelRect uses remembered position when available`() {
        val owner = "owner"
        val store = ColorPickerPopupPositionStore().apply {
            remember(owner, Rect(140, 120, 300, 200))
        }
        val resolved = ColorPickerPopupGeometry.resolvePanelRect(
            owner = owner,
            anchorRect = Rect(20, 20, 10, 10),
            width = 320,
            height = 260,
            viewportWidth = 800,
            viewportHeight = 600,
            keepPosition = false,
            currentRect = null,
            store = store
        )

        assertEquals(140, resolved.x)
        assertEquals(120, resolved.y)
        assertEquals(320, resolved.width)
        assertEquals(260, resolved.height)
    }

    @Test
    fun `resolvePanelRect honors keepPosition using current rect`() {
        val resolved = ColorPickerPopupGeometry.resolvePanelRect(
            owner = "owner",
            anchorRect = Rect(20, 20, 10, 10),
            width = 300,
            height = 240,
            viewportWidth = 800,
            viewportHeight = 600,
            keepPosition = true,
            currentRect = Rect(200, 150, 120, 80),
            store = ColorPickerPopupPositionStore()
        )

        assertEquals(Rect(200, 150, 300, 240), resolved)
        assertTrue(resolved.x >= 2 && resolved.y >= 2)
    }
}
