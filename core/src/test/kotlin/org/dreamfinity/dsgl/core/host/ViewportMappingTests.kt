package org.dreamfinity.dsgl.core.host

import kotlin.test.Test
import kotlin.test.assertEquals

class ViewportMappingTests {
    @Test
    fun `raw mouse converts to top-left DSGL coordinates`() {
        val viewport = Viewport(width = 1920, height = 1080, scale = 1f, x = 0, y = 0)

        assertEquals(ViewportPoint(0, 1079), viewport.rawMouseToDsgl(0, 0))
        assertEquals(ViewportPoint(101, 876), viewport.rawMouseToDsgl(101, 203))
        assertEquals(ViewportPoint(1919, 0), viewport.rawMouseToDsgl(1919, 1079))
    }

    @Test
    fun `raw mouse conversion respects viewport origin`() {
        val viewport = Viewport(width = 800, height = 600, scale = 1f, x = 40, y = 30)

        assertEquals(ViewportPoint(200, 419), viewport.rawMouseToDsgl(240, 210))
    }

    @Test
    fun `DSGL clip rect converts to GL scissor using viewport height and origin`() {
        val viewport = Viewport(width = 800, height = 600, scale = 1f, x = 40, y = 30)

        val scissor = viewport.dsglRectToGlScissor(
            dsglX = 100,
            dsglY = 120,
            dsglWidth = 240,
            dsglHeight = 80
        )

        assertEquals(140, scissor.x)
        assertEquals(430, scissor.y)
        assertEquals(240, scissor.width)
        assertEquals(80, scissor.height)
    }

    @Test
    fun `scissor conversion clamps negative width and height to zero`() {
        val viewport = Viewport(width = 640, height = 360, scale = 1f, x = 0, y = 0)

        val scissor = viewport.dsglRectToGlScissor(
            dsglX = 10,
            dsglY = 20,
            dsglWidth = -2,
            dsglHeight = -3
        )

        assertEquals(10, scissor.x)
        assertEquals(340, scissor.y)
        assertEquals(0, scissor.width)
        assertEquals(0, scissor.height)
    }
}
