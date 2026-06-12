package org.dreamfinity.dsgl.core.contextmenu

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PopupPlacementTests {
    @Test
    fun `submenu flips left when overflowing right edge`() {
        val result =
            PopupPlacement.resolve(
                PopupPlacementRequest(
                    preferredRect = Rect(290, 40, 120, 100),
                    popupSize = Size(120, 100),
                    viewport = Rect(0, 0, 320, 180),
                    padding = 6,
                    horizontalFlipX = 150,
                ),
            )

        assertTrue(result.flippedHorizontally)
        assertEquals(150, result.rect.x)
    }

    @Test
    fun `placement clamps vertically into viewport`() {
        val result =
            PopupPlacement.resolve(
                PopupPlacementRequest(
                    preferredRect = Rect(40, 170, 120, 80),
                    popupSize = Size(120, 80),
                    viewport = Rect(0, 0, 320, 180),
                    padding = 6,
                ),
            )

        assertTrue(result.clampedVertically)
        assertEquals(94, result.rect.y)
    }

    @Test
    fun `oversized popup is constrained to viewport bounds`() {
        val result =
            PopupPlacement.resolve(
                PopupPlacementRequest(
                    preferredRect = Rect(0, 0, 600, 500),
                    popupSize = Size(600, 500),
                    viewport = Rect(0, 0, 320, 180),
                    padding = 8,
                ),
            )

        assertEquals(304, result.rect.width)
        assertEquals(164, result.rect.height)
        assertEquals(8, result.rect.x)
        assertEquals(8, result.rect.y)
    }
}
