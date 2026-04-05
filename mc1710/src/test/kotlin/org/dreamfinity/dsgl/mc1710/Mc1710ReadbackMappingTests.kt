package org.dreamfinity.dsgl.mc1710

import org.dreamfinity.dsgl.core.host.Viewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Mc1710ReadbackMappingTests {
    @Test
    fun `logical point maps to framebuffer sample point at scale one`() {
        val viewport = Viewport(
            width = 320,
            height = 180,
            scale = 1f,
            framebufferWidth = 320,
            framebufferHeight = 180
        )

        val samplePoint = logicalPointToFramebufferSamplePoint(viewport, logicalX = 17, logicalY = 23)

        assertEquals(FramebufferSamplePoint(17, 23), samplePoint)
    }

    @Test
    fun `logical point maps to framebuffer sample point at scale two`() {
        val viewport = Viewport(
            width = 320,
            height = 180,
            scale = 2f,
            framebufferWidth = 640,
            framebufferHeight = 360
        )

        val samplePoint = logicalPointToFramebufferSamplePoint(viewport, logicalX = 17, logicalY = 23)

        assertEquals(FramebufferSamplePoint(34, 46), samplePoint)
    }

    @Test
    fun `readback plan keeps output offsets in caller logical pixels when clipped`() {
        val viewport = Viewport(
            width = 100,
            height = 80,
            scale = 2f,
            framebufferWidth = 200,
            framebufferHeight = 160
        )

        val plan = planLogicalFramebufferReadback(
            viewport = viewport,
            logicalX = -2,
            logicalY = 3,
            logicalWidth = 5,
            logicalHeight = 4
        )

        assertNotNull(plan)
        assertEquals(2, plan.outputOffsetX)
        assertEquals(0, plan.outputOffsetY)
        assertEquals(0, plan.sourceRegion.logicalX)
        assertEquals(3, plan.sourceRegion.logicalY)
        assertEquals(3, plan.sourceRegion.logicalWidth)
        assertEquals(4, plan.sourceRegion.logicalHeight)
        assertEquals(0, plan.sourceRegion.framebufferX)
        assertEquals(6, plan.sourceRegion.framebufferYTop)
        assertEquals(6, plan.sourceRegion.framebufferWidth)
        assertEquals(8, plan.sourceRegion.framebufferHeight)
    }

    @Test
    fun `capture source region expands logical rect to framebuffer edges at scale two`() {
        val viewport = Viewport(
            width = 960,
            height = 540,
            scale = 2f,
            framebufferWidth = 1920,
            framebufferHeight = 1080
        )

        val region = logicalRectToFramebufferRegion(
            viewport = viewport,
            logicalX = 10,
            logicalY = 20,
            logicalWidth = 3,
            logicalHeight = 2
        )

        assertNotNull(region)
        assertEquals(10, region.logicalX)
        assertEquals(20, region.logicalY)
        assertEquals(3, region.logicalWidth)
        assertEquals(2, region.logicalHeight)
        assertEquals(20, region.framebufferX)
        assertEquals(40, region.framebufferYTop)
        assertEquals(6, region.framebufferWidth)
        assertEquals(4, region.framebufferHeight)
    }

    @Test
    fun `framebuffer offset picks representative pixel inside each logical cell at scale two`() {
        assertEquals(
            0,
            framebufferOffsetForLogicalCoordinate(
                logicalCoordinate = 10,
                framebufferStart = 20,
                framebufferLimitExclusive = 26,
                scale = 2f
            )
        )
        assertEquals(
            2,
            framebufferOffsetForLogicalCoordinate(
                logicalCoordinate = 11,
                framebufferStart = 20,
                framebufferLimitExclusive = 26,
                scale = 2f
            )
        )
        assertEquals(
            4,
            framebufferOffsetForLogicalCoordinate(
                logicalCoordinate = 12,
                framebufferStart = 20,
                framebufferLimitExclusive = 26,
                scale = 2f
            )
        )
    }

    @Test
    fun `out of bounds logical rect produces no readback plan`() {
        val viewport = Viewport(
            width = 100,
            height = 80,
            scale = 2f,
            framebufferWidth = 200,
            framebufferHeight = 160
        )

        val plan = planLogicalFramebufferReadback(
            viewport = viewport,
            logicalX = 100,
            logicalY = 80,
            logicalWidth = 5,
            logicalHeight = 4
        )

        assertNull(plan)
    }
}
