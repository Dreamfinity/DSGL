package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.contextmenu.ContextMenuHost
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuModel
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
    private class RecordingHost : ContextMenuHost {
        var openedAtCursor: Pair<Int, Int>? = null
        var openedAnchored: Rect? = null

        override fun openAtCursor(model: ContextMenuModel, x: Int, y: Int) {
            openedAtCursor = x to y
        }

        override fun openAnchored(model: ContextMenuModel, anchorRect: Rect) {
            openedAnchored = anchorRect
        }

        override fun closeAll() = Unit

        override fun isOpen(): Boolean = openedAtCursor != null || openedAnchored != null
    }

    @Test
    fun `openMenu in context handler uses mouse root coordinates`() {
        val host = RecordingHost()
        val node = ContainerNode()
        node.bounds = Rect(10, 15, 120, 30)
        val model = contextMenu(id = "events.cursor") {
            item("Open")
        }
        node.onContextMenu(host = host) {
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
        val host = RecordingHost()
        val node = ContainerNode()
        node.bounds = Rect(22, 41, 86, 19)
        val model = contextMenu(id = "events.anchor") {
            item("Open")
        }
        node.onContextMenu(host = host) {
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
}
