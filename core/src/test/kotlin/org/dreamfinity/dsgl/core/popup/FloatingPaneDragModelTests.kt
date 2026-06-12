package org.dreamfinity.dsgl.core.popup

import org.dreamfinity.dsgl.core.dom.layout.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingPaneDragModelTests {
    @Test
    fun `drag keeps pointer offset and clamps into viewport`() {
        val drag = FloatingPaneDragModel()
        val start = Rect(100, 80, 200, 120)
        drag.begin(mouseX = 130, mouseY = 100, rect = start)

        val next =
            drag.update(
                mouseX = 20,
                mouseY = 10,
                viewportWidth = 500,
                viewportHeight = 400,
                clamp = ::clampRect,
            )

        assertEquals(2, next.x)
        assertEquals(2, next.y)
        assertTrue(drag.moved)
    }

    @Test
    fun `small movement keeps moved flag false until threshold`() {
        val drag = FloatingPaneDragModel()
        val start = Rect(40, 50, 120, 90)
        drag.begin(mouseX = 60, mouseY = 70, rect = start)

        val next =
            drag.update(
                mouseX = 61,
                mouseY = 71,
                viewportWidth = 400,
                viewportHeight = 300,
                clamp = ::clampRect,
            )

        assertEquals(41, next.x)
        assertEquals(51, next.y)
        assertFalse(drag.moved)
    }

    private fun clampRect(rect: Rect, viewportWidth: Int, viewportHeight: Int): Rect {
        val minX = 2
        val minY = 2
        val maxX = (viewportWidth - rect.width - 2).coerceAtLeast(2)
        val maxY = (viewportHeight - rect.height - 2).coerceAtLeast(2)
        return Rect(
            x = rect.x.coerceIn(minX, maxX),
            y = rect.y.coerceIn(minY, maxY),
            width = rect.width,
            height = rect.height,
        )
    }
}
