package org.dreamfinity.dsgl.core.portal.panel

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FloatingPanelTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @Test
    fun `panel uses native node path and does not expose legacy append commands api`() {
        assertTrue(
            FloatingPanel::class.java.methods
                .none { it.name == "appendCommands" },
        )

        val panelState =
            FloatingPanelState().apply {
                updateFromRect(Rect(30, 40, 240, 180))
            }
        val panel =
            FloatingPanel(
                ownerId = "demo-owner",
                panelState = panelState,
                dragSession = FloatingPanelDragSession(),
            )
        panel.configure(title = "Demo", draggable = true)
        panel.setBodyContent(FillNode("body"))

        val node = panel.node()
        node.render(ctx, 0, 0, 800, 600)
        val commands = ArrayList<RenderCommand>()
        node.appendRenderCommands(ctx, commands)

        assertTrue(commands.isNotEmpty())
        assertTrue(node.children.any { it.styleType == "div" || it.styleType == "button" || it.styleType == "text" })
        assertTrue(node.children.none { it.styleType == "dsgl-system-raw-render-command" })
    }

    @Test
    fun `panel drag keeps persistent drag session and updates panel state`() {
        val panelState =
            FloatingPanelState().apply {
                updateFromRect(Rect(60, 70, 260, 180))
            }
        val dragSession = FloatingPanelDragSession()
        val panel =
            FloatingPanel(
                ownerId = "drag-owner",
                panelState = panelState,
                dragSession = dragSession,
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
                viewportHeight = 800,
            ) { rect ->
                lastRect = rect
            },
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
                viewportHeight = 800,
            ) { rect ->
                lastRect = rect
            },
        )
        assertFalse(dragSession.active)
        assertEquals(null, dragSession.ownerId)
        assertEquals(movedRect, panelState.currentRectOrNull())
    }

    @Test
    fun `panel close button invokes close callback`() {
        val panelState =
            FloatingPanelState().apply {
                updateFromRect(Rect(12, 20, 220, 140))
            }
        val panel =
            FloatingPanel(
                ownerId = Any(),
                panelState = panelState,
                dragSession = FloatingPanelDragSession(),
            )
        var closed = 0
        panel.configure(title = "Closable", draggable = true, onClose = { closed += 1 })
        val closeRect = panel.closeRect() ?: error("close rect missing")

        assertTrue(panel.handleMouseDown(closeRect.x + 1, closeRect.y + 1, MouseButton.LEFT))
        assertEquals(1, closed)
    }

    private class FillNode(
        key: Any?,
    ) : DOMNode(key) {
        override fun measure(ctx: UiMeasureContext): Size = Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))

        override fun render(
            ctx: UiMeasureContext,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
        ) {
            bounds = Rect(x, y, width, height)
        }
    }
}
