package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.colorpicker.ColorFormatMode
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerState
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanelDragSession
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanelDragType
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanelState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SystemOverlayEntryInfrastructureTests {
    @Test
    fun `system overlay host exposes explicit persistent entries`() {
        val host = SystemOverlayHost(InspectorController())
        assertEquals(
            listOf(
                SystemOverlayEntryId.Inspector,
                SystemOverlayEntryId.ColorPickerPopup,
                SystemOverlayEntryId.ColorPickerTransient,
                SystemOverlayEntryId.PanelDemo,
            ),
            host.debugRegisteredEntryIds(),
        )
    }

    @Test
    fun `inspector entry keeps stable node and state while active`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        val root = inspectedRoot()

        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 10, cursorY = 10, inspectorPointerCaptured = false)
        assertFalse(host.debugMountedEntryIds().contains(SystemOverlayEntryId.Inspector))

        inspector.toggle()
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 14, cursorY = 14, inspectorPointerCaptured = false)
        val firstState = host.debugEntryState(SystemOverlayEntryId.Inspector) ?: error("state missing")
        val firstNode = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("node missing")
        assertTrue(host.debugMountedEntryIds().contains(SystemOverlayEntryId.Inspector))
        assertTrue(firstState.active)

        host.syncFrame(root, inspectedLayoutRevision = 3L, cursorX = 22, cursorY = 20, inspectorPointerCaptured = false)
        val secondState = host.debugEntryState(SystemOverlayEntryId.Inspector) ?: error("state missing")
        val secondNode = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("node missing")
        assertSame(firstState, secondState)
        assertSame(firstNode, secondNode)

        inspector.deactivate()
        host.syncFrame(root, inspectedLayoutRevision = 4L, cursorX = 22, cursorY = 20, inspectorPointerCaptured = false)
        assertFalse(host.debugMountedEntryIds().contains(SystemOverlayEntryId.Inspector))
    }

    @Test
    fun `color picker entry keeps panel state and identity stable across routine updates`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()
        pickerHost.open(anchorRect = Rect(36, 44, 20, 18), title = "Popup", state = popupState())
        try {
            host.syncFrame(
                root,
                inspectedLayoutRevision = 1L,
                cursorX = 40,
                cursorY = 42,
                inspectorPointerCaptured = false,
            )
            val firstState = host.debugEntryState(SystemOverlayEntryId.ColorPickerPopup) ?: error("state missing")
            val firstNode = host.debugEntryNode(SystemOverlayEntryId.ColorPickerPopup) ?: error("node missing")
            val firstRect = firstState.panelState.currentRectOrNull()
            assertNotNull(firstRect)
            assertTrue(firstState.active)

            host.syncFrame(
                root,
                inspectedLayoutRevision = 2L,
                cursorX = 60,
                cursorY = 65,
                inspectorPointerCaptured = false,
            )
            val secondState = host.debugEntryState(SystemOverlayEntryId.ColorPickerPopup) ?: error("state missing")
            val secondNode = host.debugEntryNode(SystemOverlayEntryId.ColorPickerPopup) ?: error("node missing")
            val secondRect = secondState.panelState.currentRectOrNull() ?: error("panel rect missing")

            assertSame(firstState, secondState)
            assertSame(firstNode, secondNode)
            assertEquals(firstRect.x, secondRect.x)
            assertEquals(firstRect.y, secondRect.y)
            assertTrue(host.debugMountedEntryIds().contains(SystemOverlayEntryId.ColorPickerPopup))
        } finally {
            pickerHost.close()
        }

        host.syncFrame(root, inspectedLayoutRevision = 3L, cursorX = 60, cursorY = 65, inspectorPointerCaptured = false)
        assertFalse(host.debugMountedEntryIds().contains(SystemOverlayEntryId.ColorPickerPopup))
    }

    @Test
    fun `entry ordering stays explicit when both system entries are active`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()
        inspector.toggle()
        pickerHost.open(anchorRect = Rect(36, 44, 20, 18), title = "Popup", state = popupState())
        try {
            host.syncFrame(
                root,
                inspectedLayoutRevision = 10L,
                cursorX = 20,
                cursorY = 18,
                inspectorPointerCaptured = false,
            )
            assertEquals(
                listOf(SystemOverlayEntryId.Inspector, SystemOverlayEntryId.ColorPickerPopup),
                host.debugMountedEntryIds(),
            )
        } finally {
            inspector.deactivate()
            pickerHost.close()
        }
    }

    @Test
    fun `drag session captures start anchor updates and ends deterministically`() {
        val panelState = OverlayPanelState()
        panelState.updateFromRect(Rect(20, 24, 300, 200))
        val session = OverlayPanelDragSession()

        session.begin(
            ownerId = SystemOverlayEntryId.ColorPickerPopup,
            type = OverlayPanelDragType.PanelMove,
            pointerX = 100,
            pointerY = 120,
            panelState = panelState,
        )
        assertTrue(session.active)
        assertEquals(SystemOverlayEntryId.ColorPickerPopup, session.ownerId)
        assertEquals(OverlayPanelDragType.PanelMove, session.type)
        assertEquals(100, session.startPointerX)
        assertEquals(120, session.startPointerY)
        assertEquals(20, session.startPanelX)
        assertEquals(24, session.startPanelY)
        assertEquals(300, session.startPanelWidth)
        assertEquals(200, session.startPanelHeight)

        session.update(pointerX = 132, pointerY = 168)
        assertEquals(132, session.currentPointerX)
        assertEquals(168, session.currentPointerY)

        session.end()
        assertFalse(session.active)
        assertEquals(null, session.ownerId)
        assertEquals(null, session.type)
    }

    @Test
    fun `transient system ownership is owner session based and not cursor based`() {
        val registry = SystemOverlayTransientOwnershipRegistry()
        val ownerA = Any()
        val ownerB = Any()

        val sessionA1 = registry.resolve(ownerA, cursorX = 12, cursorY = 18)
        val sessionA2 = registry.resolve(ownerA, cursorX = 900, cursorY = 640)
        val sessionB = registry.resolve(ownerB, cursorX = 12, cursorY = 18)

        assertSame(sessionA1, sessionA2)
        assertNotSame(sessionA1, sessionB)
        assertEquals(SystemOverlayEntryId.TransientSession, sessionA1.entryState.id)
    }

    @Test
    fun `host transient session api preserves owner based stable sessions`() {
        val host = SystemOverlayHost(InspectorController())
        val owner = Any()
        val first = host.resolveTransientSession(owner, cursorX = 10, cursorY = 10)
        val second = host.resolveTransientSession(owner, cursorX = 400, cursorY = 220)
        assertSame(first, second)
        assertEquals(1, host.debugTransientSessionCount())
        assertTrue(host.releaseTransientSession(owner))
        assertEquals(0, host.debugTransientSessionCount())
    }

    private fun popupState(): ColorPickerState =
        ColorPickerState(
            color = RgbaColor(0.3f, 0.5f, 0.7f, 1f),
            previous = RgbaColor(0.3f, 0.5f, 0.7f, 1f),
            mode = ColorFormatMode.RGB,
            alphaEnabled = true,
            closeOnSelect = false,
        )

    private fun inspectedRoot(): ContainerNode {
        val root = ContainerNode(key = "root")
        root.bounds = Rect(0, 0, 800, 600)
        ContainerNode(key = "child")
            .apply {
                bounds = Rect(16, 18, 120, 30)
            }.applyParent(root)
        return root
    }
}
