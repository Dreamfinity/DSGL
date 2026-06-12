package org.dreamfinity.dsgl.core.portal

import org.dreamfinity.dsgl.core.dnd.DndRuntime
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseDownEvent
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplicationDndGhostPortalTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanup() {
        DndRuntime.engine.cancelActiveDrag()
    }

    @Test
    fun `application drag ghost paints through application portal entry`() {
        val host = ApplicationPortalHost()
        val root = draggableRoot()
        val draggable = root.children.first()

        startDrag(root, draggable)

        val commands = ArrayList<RenderCommand>()
        host.appendDndGhostPortalCommands(
            root = root,
            measureContext = ctx,
            viewportWidth = 300,
            viewportHeight = 120,
            out = commands,
        )

        val state = host.debugDndGhostPortalState()
        assertTrue(state.active)
        assertEquals(ScreenDomainSurfaces.ApplicationPortal, state.surface)
        assertEquals("application.dnd-ghost", state.id.value)
        assertTrue(commands.any { command -> command is RenderCommand.DrawText && command.text == "drag" })
    }

    @Test
    fun `application drag ghost portal deactivates after drag cancel`() {
        val host = ApplicationPortalHost()
        val root = draggableRoot()
        val draggable = root.children.first()

        startDrag(root, draggable)
        host.appendDndGhostPortalCommands(root, ctx, 300, 120, ArrayList())
        assertTrue(host.debugDndGhostPortalState().active)

        DndRuntime.engine.cancelActiveDrag()
        val commands = ArrayList<RenderCommand>()
        host.appendDndGhostPortalCommands(root, ctx, 300, 120, commands)

        assertFalse(host.debugDndGhostPortalState().active)
        assertTrue(commands.isEmpty())
    }

    private fun draggableRoot(): ContainerNode {
        val root = ContainerNode(key = "dnd-portal-root")
        ContainerNode(key = "dnd-portal-source")
            .apply {
                draggable = true
                width = 80
                height = 20
            }.applyParent(root)
        root.render(ctx, 0, 0, 300, 120)
        return root
    }

    private fun startDrag(root: ContainerNode, draggable: DOMNode) {
        DndRuntime.engine.cancelActiveDrag()
        val down =
            MouseDownEvent(10, 10, MouseButton.LEFT).also { event ->
                event.target = draggable
            }
        DndRuntime.engine.onMouseDown(root, draggable, down)
        DndRuntime.engine.onMouseMove(root, 80, 10)
        assertTrue(DndRuntime.engine.isDragging)
    }
}
