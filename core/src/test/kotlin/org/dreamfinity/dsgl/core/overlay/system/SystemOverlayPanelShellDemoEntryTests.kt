package org.dreamfinity.dsgl.core.overlay.system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.render.RenderCommand

class SystemOverlayPanelShellDemoEntryTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @Test
    fun `panel shell demo entry toggles mounts and keeps stable identity while open`() {
        val host = SystemOverlayHost(InspectorController())
        val root = inspectedRoot()

        host.onInputFrame(1280, 720)
        host.togglePanelShellDemo(anchorX = 160, anchorY = 120)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 162, cursorY = 122, inspectorPointerCaptured = false)
        val firstNode = host.debugEntryNode(SystemOverlayEntryId.PanelShellDemo) ?: error("node missing")
        val firstState = host.debugEntryState(SystemOverlayEntryId.PanelShellDemo) ?: error("state missing")
        assertTrue(firstState.active)
        assertTrue(host.debugMountedEntryIds().contains(SystemOverlayEntryId.PanelShellDemo))

        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 170, cursorY = 134, inspectorPointerCaptured = false)
        val secondNode = host.debugEntryNode(SystemOverlayEntryId.PanelShellDemo) ?: error("node missing")
        val secondState = host.debugEntryState(SystemOverlayEntryId.PanelShellDemo) ?: error("state missing")
        assertSame(firstNode, secondNode)
        assertSame(firstState, secondState)

        host.togglePanelShellDemo(anchorX = 160, anchorY = 120)
        host.syncFrame(root, inspectedLayoutRevision = 3L, cursorX = 170, cursorY = 134, inspectorPointerCaptured = false)
        assertFalse(host.debugMountedEntryIds().contains(SystemOverlayEntryId.PanelShellDemo))
    }

    @Test
    fun `panel shell demo supports drag and body button click`() {
        val host = SystemOverlayHost(InspectorController())
        val root = inspectedRoot()

        host.onInputFrame(1280, 720)
        host.togglePanelShellDemo(anchorX = 220, anchorY = 160)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 224, cursorY = 166, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        val state = host.debugEntryState(SystemOverlayEntryId.PanelShellDemo) ?: error("state missing")
        val before = state.panelState.currentRectOrNull() ?: error("panel missing")
        val node = host.debugEntryNode(SystemOverlayEntryId.PanelShellDemo) as? SystemOverlayPanelShellDemoNode
            ?: error("demo node missing")
        val buttonRect = node.buttonRect() ?: error("button rect missing")

        val headerStartX = before.x + 10
        val headerStartY = before.y + 10
        assertTrue(host.handleMouseDown(headerStartX, headerStartY, MouseButton.LEFT))
        assertTrue(host.handleMouseMove(headerStartX + 60, headerStartY + 30))
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = headerStartX + 60, cursorY = headerStartY + 30, inspectorPointerCaptured = false)
        val moved = state.panelState.currentRectOrNull() ?: error("panel missing")
        assertTrue(moved.x > before.x)
        assertTrue(host.handleMouseUp(headerStartX + 60, headerStartY + 30, MouseButton.LEFT))

        host.syncFrame(root, inspectedLayoutRevision = 3L, cursorX = moved.x + 8, cursorY = moved.y + 8, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        val updatedNode = host.debugEntryNode(SystemOverlayEntryId.PanelShellDemo) as? SystemOverlayPanelShellDemoNode
            ?: error("demo node missing")
        val movedButtonRect = updatedNode.buttonRect() ?: error("button rect missing")
        assertTrue(host.handleMouseDown(movedButtonRect.x + 1, movedButtonRect.y + 1, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 4L,
            cursorX = movedButtonRect.x + 1,
            cursorY = movedButtonRect.y + 1,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 1280, 720)
        assertEquals(1, updatedNode.currentButtonClicks())
        assertNotNull(updatedNode.buttonRect())
    }

    @Test
    fun `panel shell demo uses render viewport before first mouse input`() {
        val host = SystemOverlayHost(InspectorController())
        val root = inspectedRoot()

        host.render(ctx, 1280, 720)
        host.togglePanelShellDemo(anchorX = 460, anchorY = 320)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 460, cursorY = 320, inspectorPointerCaptured = false)

        val state = host.debugEntryState(SystemOverlayEntryId.PanelShellDemo) ?: error("state missing")
        val rect = state.panelState.currentRectOrNull() ?: error("panel missing")
        assertEquals(460, rect.x)
        assertEquals(320, rect.y)
    }

    private fun inspectedRoot(): ContainerNode {
        val root = ContainerNode(key = "root")
        root.bounds = Rect(0, 0, 1280, 720)
        ContainerNode(key = "child").apply {
            bounds = Rect(20, 20, 120, 32)
        }.applyParent(root)
        return root
    }
}
