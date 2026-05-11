package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.contextmenu.ContextMenuModel
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuPortalService
import org.dreamfinity.dsgl.core.contextmenu.contextMenu
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseDownEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContextMenuEventsTests {
    private class RecordingPortalService : ContextMenuPortalService {
        var openedModel: ContextMenuModel? = null
        var openedAtCursor: Pair<Int, Int>? = null
        var openedAnchored: Rect? = null

        override fun openAtCursor(model: ContextMenuModel, x: Int, y: Int) {
            openedModel = model
            openedAtCursor = x to y
        }

        override fun openAnchored(model: ContextMenuModel, anchorRect: Rect) {
            openedModel = model
            openedAnchored = anchorRect
        }

        override fun closeAll() = Unit

        override fun isOpen(): Boolean = openedAtCursor != null || openedAnchored != null
    }

    @Test
    fun `openMenu in context handler uses mouse root coordinates`() {
        val host = RecordingPortalService()
        val node = ContainerNode()
        node.bounds = Rect(10, 15, 120, 30)
        val model =
            contextMenu(id = "events.cursor") {
                item("Open")
            }
        node.onContextMenu(portalService = host) {
            openMenu(model)
        }

        val event = MouseDownEvent(74, 91, MouseButton.RIGHT)
        event.target = node
        node.onMouseDown?.invoke(event)

        assertTrue(event.cancelled)
        assertEquals(74 to 91, host.openedAtCursor)
    }

    @Test
    fun `openMenuAnchored in context handler uses target bounds`() {
        val host = RecordingPortalService()
        val node = ContainerNode()
        node.bounds = Rect(22, 41, 86, 19)
        val model =
            contextMenu(id = "events.anchor") {
                item("Open")
            }
        node.onContextMenu(portalService = host) {
            openMenuAnchored(model)
        }

        val event = MouseDownEvent(31, 49, MouseButton.RIGHT)
        event.target = node
        node.onMouseDown?.invoke(event)

        assertTrue(event.cancelled)
        val anchor = host.openedAnchored
        assertNotNull(anchor)
        assertEquals(node.bounds, anchor)
    }

    @Test
    fun `context menu inherits font settings from triggering node when model omits them`() {
        val host = RecordingPortalService()
        val node = ContainerNode()
        node.bounds = Rect(22, 41, 86, 19)
        node.fontId = "test-font"
        node.fontSize = 24
        val model =
            contextMenu(id = "events.font") {
                item("Open")
            }
        node.onContextMenu(portalService = host) {
            openMenu(model)
        }

        val event = MouseDownEvent(31, 49, MouseButton.RIGHT)
        event.target = node
        node.onMouseDown?.invoke(event)

        val openedModel = host.openedModel
        assertNotNull(openedModel)
        assertEquals("test-font", openedModel.fontId)
        assertEquals(24, openedModel.fontSize)
    }

    @Test
    fun `context menu inherits font from clicked target on repeated opens`() {
        val host = RecordingPortalService()
        val parent = ContainerNode()
        val child = ContainerNode()
        child.parent = parent
        parent.children += child
        parent.bounds = Rect(0, 0, 100, 40)
        child.bounds = Rect(10, 10, 60, 16)
        child.fontId = "child-font"
        child.fontSize = 28
        val model =
            contextMenu(id = "events.target.font") {
                item("Open")
            }
        parent.onContextMenu(portalService = host) {
            openMenu(model)
        }

        repeat(2) {
            val event = MouseDownEvent(31, 19, MouseButton.RIGHT)
            event.target = child
            parent.onMouseDown?.invoke(event)
            val openedModel = host.openedModel
            assertNotNull(openedModel)
            assertEquals("child-font", openedModel.fontId)
            assertEquals(28, openedModel.fontSize)
        }
    }
}
