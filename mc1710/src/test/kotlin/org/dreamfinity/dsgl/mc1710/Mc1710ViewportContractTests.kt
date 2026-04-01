package org.dreamfinity.dsgl.mc1710

import kotlin.test.Test
import kotlin.test.assertEquals

class Mc1710ViewportContractTests {
    @Test
    fun `mc1710 viewport keeps gui size logical and display size framebuffer`() {
        val viewport = buildMc1710Viewport(
            logicalWidth = 960,
            logicalHeight = 540,
            framebufferWidth = 1920,
            framebufferHeight = 1080
        )

        assertEquals(960, viewport.logicalWidth)
        assertEquals(540, viewport.logicalHeight)
        assertEquals(1920, viewport.framebufferWidth)
        assertEquals(1080, viewport.framebufferHeight)
        assertEquals(2f, viewport.scale)
    }

    @Test
    fun `mc1710 viewport keeps conservative uniform scale when axes differ slightly`() {
        val viewport = buildMc1710Viewport(
            logicalWidth = 853,
            logicalHeight = 480,
            framebufferWidth = 1707,
            framebufferHeight = 960
        )

        assertEquals(853, viewport.logicalWidth)
        assertEquals(480, viewport.logicalHeight)
        assertEquals(1707, viewport.framebufferWidth)
        assertEquals(960, viewport.framebufferHeight)
        assertEquals(2f, viewport.scale)
    }
}
