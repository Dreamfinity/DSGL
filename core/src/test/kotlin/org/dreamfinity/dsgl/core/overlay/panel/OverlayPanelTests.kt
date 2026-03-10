package org.dreamfinity.dsgl.core.overlay.panel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand

class OverlayPanelTests {
    @Test
    fun `panel renders header close and body content slot`() {
        val panelState = OverlayPanelState().apply {
            updateFromRect(Rect(30, 40, 240, 180))
        }
        val dragSession = OverlayPanelDragSession()
        val panel = OverlayPanel(
            ownerId = "demo-owner",
            panelState = panelState,
            dragSession = dragSession
        )
        panel.configure(title = "Demo", draggable = true)

        val commands = ArrayList<RenderCommand>()
        panel.appendCommands(
            viewportWidth = 800,
            viewportHeight = 600,
            out = commands,
            appendBody = { bodyRect, out ->
                out += RenderCommand.DrawText("Body text", bodyRect.x + 4, bodyRect.y + 4, 0xFFFFFFFF.toInt())
                out += RenderCommand.DrawImage("minecraft:textures/gui/options_background.png", bodyRect.x + 4, bodyRect.y + 20, 24, 24)
            }
        )

        assertTrue(commands.any { it is RenderCommand.DrawText && it.text == "Demo" })
        assertTrue(commands.any { it is RenderCommand.DrawText && it.text == "Body text" })
        assertTrue(commands.any { it is RenderCommand.DrawImage })
    }

    @Test
    fun `panel drag keeps persistent drag session and updates panel state`() {
        val panelState = OverlayPanelState().apply {
            updateFromRect(Rect(60, 70, 260, 180))
        }
        val dragSession = OverlayPanelDragSession()
        val panel = OverlayPanel(
            ownerId = "drag-owner",
            panelState = panelState,
            dragSession = dragSession
        )
        panel.configure(title = "Drag", draggable = true)

        val header = panel.headerRect() ?: error("header rect missing")
        val startX = header.x + 8
        val startY = header.y + 8
        assertTrue(panel.handleMouseDown(startX, startY, MouseButton.LEFT))
        assertTrue(dragSession.active)
        assertEquals("drag-owner", dragSession.ownerId)

        var lastRect: Rect? = null
        assertTrue(
            panel.handleMouseMove(
                mouseX = startX + 42,
                mouseY = startY + 26,
                viewportWidth = 1200,
                viewportHeight = 800
            ) { rect ->
                lastRect = rect
            }
        )
        val movedRect = panelState.currentRectOrNull() ?: error("panel rect missing")
        assertNotNull(lastRect)
        assertEquals(lastRect, movedRect)
        assertTrue(movedRect.x > 60)

        assertTrue(
            panel.handleMouseUp(
                mouseX = startX + 42,
                mouseY = startY + 26,
                button = MouseButton.LEFT,
                viewportWidth = 1200,
                viewportHeight = 800
            ) { rect ->
                lastRect = rect
            }
        )
        assertFalse(dragSession.active)
        assertEquals(null, dragSession.ownerId)
        assertEquals(movedRect, panelState.currentRectOrNull())
    }

    @Test
    fun `panel close button invokes close callback`() {
        val panelState = OverlayPanelState().apply {
            updateFromRect(Rect(12, 20, 220, 140))
        }
        val panel = OverlayPanel(
            ownerId = Any(),
            panelState = panelState,
            dragSession = OverlayPanelDragSession()
        )
        var closed = 0
        panel.configure(title = "Closable", draggable = true, onClose = { closed += 1 })
        val closeRect = panel.closeRect() ?: error("close rect missing")

        assertTrue(panel.handleMouseDown(closeRect.x + 1, closeRect.y + 1, MouseButton.LEFT))
        assertEquals(1, closed)
    }
}
