package org.dreamfinity.dsgl.core.overlay.system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand

class SystemOverlayPanelShellTests {
    @Test
    fun `shell renders header close and body content slot`() {
        val panelState = SystemOverlayPanelState().apply {
            updateFromRect(Rect(30, 40, 240, 180))
        }
        val dragSession = SystemOverlayDragSession()
        val shell = SystemOverlayPanelShell(
            entryId = SystemOverlayEntryId.TransientSession,
            panelState = panelState,
            dragSession = dragSession
        )
        shell.configure(title = "Demo", draggable = true)

        val commands = ArrayList<RenderCommand>()
        shell.appendCommands(
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
    fun `shell drag keeps persistent drag session and updates panel state`() {
        val panelState = SystemOverlayPanelState().apply {
            updateFromRect(Rect(60, 70, 260, 180))
        }
        val dragSession = SystemOverlayDragSession()
        val shell = SystemOverlayPanelShell(
            entryId = SystemOverlayEntryId.ColorPickerPopup,
            panelState = panelState,
            dragSession = dragSession
        )
        shell.configure(title = "Drag", draggable = true)

        val header = shell.headerRect() ?: error("header rect missing")
        val startX = header.x + 8
        val startY = header.y + 8
        assertTrue(shell.handleMouseDown(startX, startY, MouseButton.LEFT))
        assertTrue(dragSession.active)

        var lastRect: Rect? = null
        assertTrue(
            shell.handleMouseMove(
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
            shell.handleMouseUp(
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
        assertEquals(movedRect, panelState.currentRectOrNull())
    }

    @Test
    fun `shell close button invokes close callback`() {
        val panelState = SystemOverlayPanelState().apply {
            updateFromRect(Rect(12, 20, 220, 140))
        }
        val shell = SystemOverlayPanelShell(
            entryId = SystemOverlayEntryId.TransientSession,
            panelState = panelState,
            dragSession = SystemOverlayDragSession()
        )
        var closed = 0
        shell.configure(title = "Closable", draggable = true, onClose = { closed += 1 })
        val closeRect = shell.closeRect() ?: error("close rect missing")

        assertTrue(shell.handleMouseDown(closeRect.x + 1, closeRect.y + 1, MouseButton.LEFT))
        assertEquals(1, closed)
    }
}
