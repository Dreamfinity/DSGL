package org.dreamfinity.dsgl.core.portal

import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ApplicationFloatingWindowPortalTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @Test
    fun `F10 floating window toggles through application portal and keeps stable identity while open`() {
        val host = ApplicationPortalHost()

        host.onInputFrame(1280, 720)
        host.toggleFloatingWindowDemo(anchorX = 160, anchorY = 120)
        host.render(ctx, 1280, 720)
        val firstNode = host.floatingWindowPortal.debugNode()
        val firstState = host.floatingWindowPortal.debugState()

        assertTrue(host.isFloatingWindowDemoOpen())
        assertTrue(firstState.active)
        assertEquals("application.f10-floating-window", firstState.id.value)
        assertTrue(firstNode.parent === host.rootNode)

        host.render(ctx, 1280, 720)
        assertSame(firstNode, host.floatingWindowPortal.debugNode())
        assertSame(firstState, host.floatingWindowPortal.debugState())

        host.toggleFloatingWindowDemo(anchorX = 160, anchorY = 120)
        host.render(ctx, 1280, 720)
        assertFalse(host.isFloatingWindowDemoOpen())
        assertFalse(firstState.active)
        assertTrue(firstNode.parent == null)
    }

    @Test
    fun `F10 floating window supports DOM button click and pointer-captured drag`() {
        val host = ApplicationPortalHost()

        host.onInputFrame(1280, 720)
        host.toggleFloatingWindowDemo(anchorX = 220, anchorY = 160)
        host.render(ctx, 1280, 720)
        val node = host.floatingWindowPortal.debugNode()
        val before = node.panelRect() ?: error("panel missing")
        val buttonRect = node.buttonRect() ?: error("button missing")

        assertTrue(host.handleMouseDown(buttonRect.x + 1, buttonRect.y + 1, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(buttonRect.x + 1, buttonRect.y + 1, MouseButton.LEFT))
        host.render(ctx, 1280, 720)
        assertEquals(1, node.currentButtonClicks())
        assertNotNull(node.buttonRect())

        val headerStartX = before.x + 10
        val headerStartY = before.y + 10
        assertTrue(host.handleMouseDown(headerStartX, headerStartY, MouseButton.LEFT))
        assertTrue(host.handleMouseMove(headerStartX + 60, headerStartY + 30))
        assertTrue(host.handleMouseMove(headerStartX + 180, headerStartY + 70))
        assertTrue(host.handleMouseUp(headerStartX + 180, headerStartY + 70, MouseButton.LEFT))
        host.render(ctx, 1280, 720)

        val moved = node.panelRect() ?: error("panel missing")
        assertTrue(moved.x > before.x)
        assertTrue(moved.y > before.y)
        assertSame(node, host.floatingWindowPortal.debugNode())
    }

    @Test
    fun `F10 floating window consumes body hover and pointer input without blocking child controls`() {
        val host = ApplicationPortalHost()

        host.onInputFrame(1280, 720)
        host.toggleFloatingWindowDemo(anchorX = 220, anchorY = 160)
        host.render(ctx, 1280, 720)
        val node = host.floatingWindowPortal.debugNode()
        val bodyRect = node.bodyRect() ?: error("body missing")
        val bodyX = bodyRect.x + bodyRect.width - 8
        val bodyY = bodyRect.y + bodyRect.height - 8

        assertTrue(host.handleMouseMove(bodyX, bodyY))
        assertTrue(host.handleMouseDown(bodyX, bodyY, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(bodyX, bodyY, MouseButton.LEFT))
        assertEquals(0, node.currentButtonClicks())

        val buttonRect = node.buttonRect() ?: error("button missing")
        assertTrue(host.handleMouseDown(buttonRect.x + 1, buttonRect.y + 1, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(buttonRect.x + 1, buttonRect.y + 1, MouseButton.LEFT))
        assertEquals(1, node.currentButtonClicks())
    }

    @Test
    fun `F10 floating window does not consume outside panel input`() {
        val host = ApplicationPortalHost()

        host.onInputFrame(1280, 720)
        host.toggleFloatingWindowDemo(anchorX = 220, anchorY = 160)
        host.render(ctx, 1280, 720)
        val panelRect =
            host.floatingWindowPortal
                .debugNode()
                .panelRect() ?: error("panel missing")
        val outsideX = panelRect.x + panelRect.width + 12
        val outsideY = panelRect.y + panelRect.height + 12

        assertFalse(host.handleMouseMove(outsideX, outsideY))
        assertFalse(host.handleMouseDown(outsideX, outsideY, MouseButton.LEFT))
    }

    @Test
    fun `F10 floating window follows frame cursor during active drag`() {
        val host = ApplicationPortalHost()

        host.onInputFrame(1280, 720)
        host.toggleFloatingWindowDemo(anchorX = 220, anchorY = 160)
        host.render(ctx, 1280, 720)
        val node = host.floatingWindowPortal.debugNode()
        val before = node.panelRect() ?: error("panel missing")
        val headerStartX = before.x + 10
        val headerStartY = before.y + 10

        assertTrue(host.handleMouseDown(headerStartX, headerStartY, MouseButton.LEFT))
        host.syncPortalFrame(
            measureContext = ctx,
            viewportWidth = 1280,
            viewportHeight = 720,
            viewportScale = 1f,
            mouseX = headerStartX + 90,
            mouseY = headerStartY + 40,
        )
        host.render(ctx, 1280, 720)

        val moved = node.panelRect() ?: error("panel missing")
        assertTrue(moved.x > before.x)
        assertTrue(moved.y > before.y)
    }

    @Test
    fun `F10 floating window close button closes and reopen restores interactions`() {
        val host = ApplicationPortalHost()

        host.onInputFrame(1280, 720)
        host.toggleFloatingWindowDemo(anchorX = 280, anchorY = 180)
        host.render(ctx, 1280, 720)
        val node = host.floatingWindowPortal.debugNode()
        val rect = node.panelRect() ?: error("panel missing")
        val closeX = rect.x + rect.width - 4 - 16 + 1
        val closeY = rect.y + 4 + 1

        assertTrue(host.handleMouseDown(closeX, closeY, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(closeX, closeY, MouseButton.LEFT))
        host.render(ctx, 1280, 720)
        assertFalse(host.isFloatingWindowDemoOpen())
        assertTrue(node.parent == null)

        host.toggleFloatingWindowDemo(anchorX = 280, anchorY = 180)
        host.render(ctx, 1280, 720)
        assertTrue(host.isFloatingWindowDemoOpen())
        assertSame(node, host.floatingWindowPortal.debugNode())
        assertTrue(node.parent === host.rootNode)
    }

    @Test
    fun `F10 floating window uses render viewport before first mouse input`() {
        val host = ApplicationPortalHost()

        host.render(ctx, 1280, 720)
        host.toggleFloatingWindowDemo(anchorX = 460, anchorY = 320)
        host.render(ctx, 1280, 720)

        val rect =
            host.floatingWindowPortal
                .debugNode()
                .panelRect() ?: error("panel missing")
        assertEquals(460, rect.x)
        assertEquals(320, rect.y)
    }
}
