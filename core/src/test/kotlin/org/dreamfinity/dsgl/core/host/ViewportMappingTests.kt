package org.dreamfinity.dsgl.core.host

import kotlin.test.Test
import kotlin.test.assertEquals

class ViewportMappingTests {
    @Test
    fun `raw mouse converts to top-left DSGL coordinates`() {
        val viewport = Viewport(
            width = 1920,
            height = 1080,
            scale = 1f,
            framebufferWidth = 1920,
            framebufferHeight = 1080,
            x = 0,
            y = 0
        )

        assertEquals(ViewportPoint(0, 1079), viewport.rawMouseToDsgl(0, 0))
        assertEquals(ViewportPoint(101, 876), viewport.rawMouseToDsgl(101, 203))
        assertEquals(ViewportPoint(1919, 0), viewport.rawMouseToDsgl(1919, 1079))
    }

    @Test
    fun `raw mouse conversion respects viewport origin`() {
        val viewport = Viewport(
            width = 800,
            height = 600,
            scale = 1f,
            framebufferWidth = 800,
            framebufferHeight = 600,
            x = 40,
            y = 30
        )

        assertEquals(ViewportPoint(200, 419), viewport.rawMouseToDsgl(240, 210))
    }

    @Test
    fun `raw mouse converts framebuffer pixels to logical DSGL coordinates at scale two`() {
        val viewport = Viewport(
            width = 960,
            height = 540,
            scale = 2f,
            framebufferWidth = 1920,
            framebufferHeight = 1080,
            x = 0,
            y = 0
        )

        assertEquals(ViewportPoint(0, 539), viewport.rawMouseToDsgl(0, 0))
        assertEquals(ViewportPoint(50, 439), viewport.rawMouseToDsgl(100, 200))
        assertEquals(ViewportPoint(959, 0), viewport.rawMouseToDsgl(1919, 1079))
    }

    @Test
    fun `DSGL clip rect converts to GL scissor using viewport height and origin`() {
        val viewport = Viewport(
            width = 800,
            height = 600,
            scale = 1f,
            framebufferWidth = 800,
            framebufferHeight = 600,
            x = 40,
            y = 30
        )

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
    fun `DSGL clip rect converts to GL scissor in framebuffer pixels at scale two`() {
        val viewport = Viewport(
            width = 400,
            height = 300,
            scale = 2f,
            framebufferWidth = 800,
            framebufferHeight = 600,
            x = 40,
            y = 30
        )

        val scissor = viewport.dsglRectToGlScissor(
            dsglX = 100,
            dsglY = 120,
            dsglWidth = 240,
            dsglHeight = 80
        )

        assertEquals(240, scissor.x)
        assertEquals(230, scissor.y)
        assertEquals(480, scissor.width)
        assertEquals(160, scissor.height)
    }

    @Test
    fun `scissor conversion clamps negative width and height to zero`() {
        val viewport = Viewport(
            width = 640,
            height = 360,
            scale = 1f,
            framebufferWidth = 640,
            framebufferHeight = 360,
            x = 0,
            y = 0
        )

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

    @Test
    fun `scale-aware constructor defaults backing size from logical size and scale`() {
        val viewport = Viewport(width = 320, height = 180, scale = 2f)

        assertEquals(320, viewport.logicalWidth)
        assertEquals(180, viewport.logicalHeight)
        assertEquals(640, viewport.framebufferWidth)
        assertEquals(360, viewport.framebufferHeight)
    }
}
