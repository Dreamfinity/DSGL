package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SystemOverlayPanelDemoEntryTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @Test
    fun `panel panel demo entry toggles mounts and keeps stable identity while open`() {
        val host = SystemOverlayHost(InspectorController())
        val root = inspectedRoot()

        host.onInputFrame(1280, 720)
        host.togglePanelDemo(anchorX = 160, anchorY = 120)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 162,
            cursorY = 122,
            inspectorPointerCaptured = false,
        )
        val firstNode = host.debugEntryNode(SystemOverlayEntryId.PanelDemo) ?: error("node missing")
        val firstState = host.debugEntryState(SystemOverlayEntryId.PanelDemo) ?: error("state missing")
        assertTrue(firstState.active)
        assertTrue(host.debugMountedEntryIds().contains(SystemOverlayEntryId.PanelDemo))

        host.syncFrame(
            root,
            inspectedLayoutRevision = 2L,
            cursorX = 170,
            cursorY = 134,
            inspectorPointerCaptured = false,
        )
        val secondNode = host.debugEntryNode(SystemOverlayEntryId.PanelDemo) ?: error("node missing")
        val secondState = host.debugEntryState(SystemOverlayEntryId.PanelDemo) ?: error("state missing")
        assertSame(firstNode, secondNode)
        assertSame(firstState, secondState)

        host.togglePanelDemo(anchorX = 160, anchorY = 120)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = 170,
            cursorY = 134,
            inspectorPointerCaptured = false,
        )
        assertFalse(host.debugMountedEntryIds().contains(SystemOverlayEntryId.PanelDemo))
    }

    @Test
    fun `panel panel demo supports drag and body button click`() {
        val host = SystemOverlayHost(InspectorController())
        val root = inspectedRoot()

        host.onInputFrame(1280, 720)
        host.togglePanelDemo(anchorX = 220, anchorY = 160)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 224,
            cursorY = 166,
            inspectorPointerCaptured = false,
        )
        host.render(ctx, 1280, 720)
        val state = host.debugEntryState(SystemOverlayEntryId.PanelDemo) ?: error("state missing")
        val before = state.panelState.currentRectOrNull() ?: error("panel missing")
        val node =
            host.debugEntryNode(SystemOverlayEntryId.PanelDemo) as? SystemOverlayPanelDemoNode
                ?: error("demo node missing")
        val buttonRect = node.buttonRect() ?: error("button rect missing")

        val headerStartX = before.x + 10
        val headerStartY = before.y + 10
        assertTrue(host.handleMouseDown(headerStartX, headerStartY, MouseButton.LEFT))
        assertTrue(host.handleMouseMove(headerStartX + 60, headerStartY + 30))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 2L,
            cursorX = headerStartX + 60,
            cursorY = headerStartY + 30,
            inspectorPointerCaptured = false,
        )
        val moved = state.panelState.currentRectOrNull() ?: error("panel missing")
        assertTrue(moved.x > before.x)
        assertTrue(host.handleMouseUp(headerStartX + 60, headerStartY + 30, MouseButton.LEFT))

        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = moved.x + 8,
            cursorY = moved.y + 8,
            inspectorPointerCaptured = false,
        )
        host.render(ctx, 1280, 720)
        val updatedNode =
            host.debugEntryNode(SystemOverlayEntryId.PanelDemo) as? SystemOverlayPanelDemoNode
                ?: error("demo node missing")
        val movedButtonRect = updatedNode.buttonRect() ?: error("button rect missing")
        assertTrue(host.handleMouseDown(movedButtonRect.x + 1, movedButtonRect.y + 1, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 4L,
            cursorX = movedButtonRect.x + 1,
            cursorY = movedButtonRect.y + 1,
            inspectorPointerCaptured = false,
        )
        host.render(ctx, 1280, 720)
        assertEquals(1, updatedNode.currentButtonClicks())
        assertNotNull(updatedNode.buttonRect())
    }

    @Test
    fun `panel panel demo close button closes and reopen restores interactions`() {
        val host = SystemOverlayHost(InspectorController())
        val root = inspectedRoot()

        host.onInputFrame(1280, 720)
        host.togglePanelDemo(anchorX = 280, anchorY = 180)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 282,
            cursorY = 182,
            inspectorPointerCaptured = false,
        )
        val state = host.debugEntryState(SystemOverlayEntryId.PanelDemo) ?: error("state missing")
        val rect = state.panelState.currentRectOrNull() ?: error("panel missing")
        val closeX = rect.x + rect.width - 4 - 16 + 1
        val closeY = rect.y + 4 + 1

        assertTrue(host.handleMouseDown(closeX, closeY, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 2L,
            cursorX = closeX,
            cursorY = closeY,
            inspectorPointerCaptured = false,
        )
        assertFalse(host.isOverlayPanelDemoOpen())
        assertFalse(host.debugMountedEntryIds().contains(SystemOverlayEntryId.PanelDemo))

        host.togglePanelDemo(anchorX = 280, anchorY = 180)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = 284,
            cursorY = 184,
            inspectorPointerCaptured = false,
        )
        assertTrue(host.isOverlayPanelDemoOpen())
        assertTrue(host.debugMountedEntryIds().contains(SystemOverlayEntryId.PanelDemo))
    }

    @Test
    fun `panel panel demo remains stable across open drag body click drag close reopen sequence`() {
        val host = SystemOverlayHost(InspectorController())
        val root = inspectedRoot()

        host.onInputFrame(1280, 720)
        host.togglePanelDemo(anchorX = 260, anchorY = 170)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 260,
            cursorY = 170,
            inspectorPointerCaptured = false,
        )
        host.render(ctx, 1280, 720)

        val initialNode =
            host.debugEntryNode(SystemOverlayEntryId.PanelDemo) as? SystemOverlayPanelDemoNode
                ?: error("demo node missing")
        val state = host.debugEntryState(SystemOverlayEntryId.PanelDemo) ?: error("state missing")
        val initialRect = state.panelState.currentRectOrNull() ?: error("panel missing")

        val firstDragStartX = initialRect.x + 10
        val firstDragStartY = initialRect.y + 10
        assertTrue(host.handleMouseDown(firstDragStartX, firstDragStartY, MouseButton.LEFT))
        assertTrue(host.handleMouseMove(firstDragStartX + 40, firstDragStartY + 20))
        assertTrue(host.handleMouseUp(firstDragStartX + 40, firstDragStartY + 20, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 2L,
            cursorX = firstDragStartX + 40,
            cursorY = firstDragStartY + 20,
            inspectorPointerCaptured = false,
        )
        host.render(ctx, 1280, 720)

        val movedRect = state.panelState.currentRectOrNull() ?: error("panel missing")
        assertTrue(movedRect.x > initialRect.x)
        val movedNode =
            host.debugEntryNode(SystemOverlayEntryId.PanelDemo) as? SystemOverlayPanelDemoNode
                ?: error("demo node missing")
        assertSame(initialNode, movedNode)

        val buttonRect = movedNode.buttonRect() ?: error("button missing")
        assertTrue(host.handleMouseDown(buttonRect.x + 1, buttonRect.y + 1, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = buttonRect.x + 1,
            cursorY = buttonRect.y + 1,
            inspectorPointerCaptured = false,
        )
        host.render(ctx, 1280, 720)
        assertEquals(1, movedNode.currentButtonClicks())

        val secondDragStartX = movedRect.x + 10
        val secondDragStartY = movedRect.y + 10
        assertTrue(host.handleMouseDown(secondDragStartX, secondDragStartY, MouseButton.LEFT))
        assertTrue(host.handleMouseMove(secondDragStartX + 30, secondDragStartY + 10))
        assertTrue(host.handleMouseUp(secondDragStartX + 30, secondDragStartY + 10, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 4L,
            cursorX = secondDragStartX + 30,
            cursorY = secondDragStartY + 10,
            inspectorPointerCaptured = false,
        )

        val secondMovedRect = state.panelState.currentRectOrNull() ?: error("panel missing")
        assertTrue(secondMovedRect.x > movedRect.x)

        val closeX = secondMovedRect.x + secondMovedRect.width - 4 - 16 + 1
        val closeY = secondMovedRect.y + 4 + 1
        assertTrue(host.handleMouseDown(closeX, closeY, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 5L,
            cursorX = closeX,
            cursorY = closeY,
            inspectorPointerCaptured = false,
        )
        assertFalse(host.isOverlayPanelDemoOpen())

        host.togglePanelDemo(anchorX = 260, anchorY = 170)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 6L,
            cursorX = 262,
            cursorY = 172,
            inspectorPointerCaptured = false,
        )
        val reopenedNode =
            host.debugEntryNode(SystemOverlayEntryId.PanelDemo) as? SystemOverlayPanelDemoNode
                ?: error("demo node missing")
        assertSame(initialNode, reopenedNode)
        assertTrue(host.isOverlayPanelDemoOpen())
    }

    @Test
    fun `panel panel demo uses render viewport before first mouse input`() {
        val host = SystemOverlayHost(InspectorController())
        val root = inspectedRoot()

        host.render(ctx, 1280, 720)
        host.togglePanelDemo(anchorX = 460, anchorY = 320)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 460,
            cursorY = 320,
            inspectorPointerCaptured = false,
        )

        val state = host.debugEntryState(SystemOverlayEntryId.PanelDemo) ?: error("state missing")
        val rect = state.panelState.currentRectOrNull() ?: error("panel missing")
        assertEquals(460, rect.x)
        assertEquals(320, rect.y)
    }

    @Test
    fun `panel panel demo uses native overlay panel node in live path`() {
        val host = SystemOverlayHost(InspectorController())
        val root = inspectedRoot()

        host.onInputFrame(1280, 720)
        host.togglePanelDemo(anchorX = 180, anchorY = 140)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 182,
            cursorY = 142,
            inspectorPointerCaptured = false,
        )
        host.render(ctx, 1280, 720)

        val node =
            host.debugEntryNode(SystemOverlayEntryId.PanelDemo) as? SystemOverlayPanelDemoNode
                ?: error("demo node missing")
        assertTrue(node.children.any { it.styleType == "dsgl-overlay-panel" })
        assertTrue(node.children.none { it.styleType == "dsgl-system-raw-render-command" })
    }

    private fun inspectedRoot(): ContainerNode {
        val root = ContainerNode(key = "root")
        root.bounds = Rect(0, 0, 1280, 720)
        ContainerNode(key = "child")
            .apply {
                bounds = Rect(20, 20, 120, 32)
            }.applyParent(root)
        return root
    }
}
