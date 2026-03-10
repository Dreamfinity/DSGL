package org.dreamfinity.dsgl.core.overlay.system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.colorpicker.ColorFormatMode
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerRuntime
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerState
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope

class SystemOverlayColorPickerEntryTests {
    @Test
    fun `system picker popup lifecycle is entry owned and stable`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()

        assertFalse(host.isSystemColorPickerOpen())
        assertFalse(ColorPickerRuntime.engine.isOpen())

        pickerHost.open(anchorRect = Rect(40, 42, 20, 18), title = "Popup", state = popupState())
        host.onInputFrame(960, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 44, cursorY = 48, inspectorPointerCaptured = false)
        val firstNode = host.debugEntryNode(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry node missing")
        val firstState = host.debugEntryState(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry state missing")
        assertEquals(OverlayOwnerScope.System, host.debugSystemColorPickerPopupOwnerScope())
        assertTrue(firstState.active)
        assertNotNull(firstState.panelState.currentRectOrNull())

        host.onInputFrame(960, 720)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 50, cursorY = 56, inspectorPointerCaptured = false)
        val secondNode = host.debugEntryNode(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry node missing")
        val secondState = host.debugEntryState(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry state missing")
        assertSame(firstNode, secondNode)
        assertSame(firstState, secondState)

        pickerHost.close()
        host.syncFrame(root, inspectedLayoutRevision = 3L, cursorX = 50, cursorY = 56, inspectorPointerCaptured = false)
        assertFalse(host.isSystemColorPickerOpen())
        assertFalse(host.debugMountedEntryIds().contains(SystemOverlayEntryId.ColorPickerPopup))

        pickerHost.open(anchorRect = Rect(40, 42, 20, 18), title = "Popup", state = popupState())
        host.onInputFrame(960, 720)
        host.syncFrame(root, inspectedLayoutRevision = 4L, cursorX = 52, cursorY = 58, inspectorPointerCaptured = false)
        val reopenedNode = host.debugEntryNode(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry node missing")
        val reopenedState = host.debugEntryState(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry state missing")
        assertSame(firstNode, reopenedNode)
        assertSame(firstState, reopenedState)
        assertTrue(reopenedState.active)
    }

    @Test
    fun `system picker popup drag uses persistent entry drag session and keeps node stable`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()

        pickerHost.open(anchorRect = Rect(80, 90, 20, 18), title = "Popup", state = popupState())
        host.onInputFrame(1200, 800)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 88, cursorY = 98, inspectorPointerCaptured = false)

        val stableNode = host.debugEntryNode(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry node missing")
        val stateBefore = host.debugEntryState(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry state missing")
        val panelBefore = stateBefore.panelState.currentRectOrNull() ?: error("panel missing")
        val header = host.debugSystemColorPickerHeaderRect() ?: error("header missing")
        val startX = header.x + 8
        val startY = header.y + 8
        assertTrue(host.handleMouseDown(startX, startY, org.dreamfinity.dsgl.core.event.MouseButton.LEFT))
        assertTrue(stateBefore.dragSession.active)

        host.handleMouseMove(startX + 50, startY + 30)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = startX + 50, cursorY = startY + 30, inspectorPointerCaptured = false)
        val midState = host.debugEntryState(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry state missing")
        val panelMid = midState.panelState.currentRectOrNull() ?: error("panel missing")
        assertNotEquals(panelBefore.x, panelMid.x)

        host.handleMouseMove(startX + 90, startY + 60)
        host.syncFrame(root, inspectedLayoutRevision = 3L, cursorX = startX + 90, cursorY = startY + 60, inspectorPointerCaptured = false)
        val movingNode = host.debugEntryNode(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry node missing")
        val movingState = host.debugEntryState(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry state missing")
        val panelAfter = movingState.panelState.currentRectOrNull() ?: error("panel missing")
        assertSame(stableNode, movingNode)
        assertSame(stateBefore, movingState)
        assertTrue(movingState.dragSession.active)
        assertNotEquals(panelMid.x, panelAfter.x)

        assertTrue(host.handleMouseUp(startX + 90, startY + 60, org.dreamfinity.dsgl.core.event.MouseButton.LEFT))
        host.syncFrame(root, inspectedLayoutRevision = 4L, cursorX = startX + 90, cursorY = startY + 60, inspectorPointerCaptured = false)
        val finalState = host.debugEntryState(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry state missing")
        val panelFinal = finalState.panelState.currentRectOrNull() ?: error("panel missing")
        assertFalse(finalState.dragSession.active)
        assertEquals(panelAfter.x, panelFinal.x)
        assertEquals(panelAfter.y, panelFinal.y)
    }

    @Test
    fun `system picker popup survives routine sync updates without remount during drag`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()

        pickerHost.open(anchorRect = Rect(120, 100, 20, 18), title = "Popup", state = popupState())
        host.onInputFrame(1200, 800)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 126, cursorY = 108, inspectorPointerCaptured = false)

        val initialNode = host.debugEntryNode(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry node missing")
        val header = host.debugSystemColorPickerHeaderRect() ?: error("header missing")
        val startX = header.x + 6
        val startY = header.y + 6
        assertTrue(host.handleMouseDown(startX, startY, org.dreamfinity.dsgl.core.event.MouseButton.LEFT))

        repeat(5) { step ->
            val mx = startX + 20 + step * 10
            val my = startY + 15 + step * 7
            host.handleMouseMove(mx, my)
            host.syncFrame(
                inspectedRoot = root,
                inspectedLayoutRevision = 2L + step,
                cursorX = mx,
                cursorY = my,
                inspectorPointerCaptured = false
            )
            val node = host.debugEntryNode(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry node missing")
            val state = host.debugEntryState(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry state missing")
            assertSame(initialNode, node)
            assertTrue(state.dragSession.active)
        }
    }

    @Test
    fun `system picker popup close button closes entry through panel shell`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()

        pickerHost.open(anchorRect = Rect(80, 86, 20, 18), title = "Popup", state = popupState())
        host.onInputFrame(1200, 800)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 84, cursorY = 92, inspectorPointerCaptured = false)
        val closeRect = host.debugSystemColorPickerCloseRect() ?: error("close rect missing")
        assertTrue(host.handleMouseDown(closeRect.x + 1, closeRect.y + 1, org.dreamfinity.dsgl.core.event.MouseButton.LEFT))
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = closeRect.x + 1, cursorY = closeRect.y + 1, inspectorPointerCaptured = false)
        assertFalse(host.isSystemColorPickerOpen())
        assertFalse(host.debugMountedEntryIds().contains(SystemOverlayEntryId.ColorPickerPopup))
    }

    @Test
    fun `system picker keyboard-open path uses valid viewport after input frame sync`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()
        val anchor = Rect(360, 220, 1, 1)

        pickerHost.open(anchorRect = anchor, title = "Popup", state = popupState())
        host.onInputFrame(1200, 800)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 364, cursorY = 226, inspectorPointerCaptured = false)
        val state = host.debugEntryState(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry state missing")
        val panel = state.panelState.currentRectOrNull() ?: error("panel missing")

        assertNotEquals(2, panel.x)
        assertNotEquals(2, panel.y)
        assertTrue(panel.x >= 8)
        assertTrue(panel.y >= 8)
    }

    private fun popupState(): ColorPickerState {
        return ColorPickerState(
            color = RgbaColor(0.3f, 0.5f, 0.7f, 1f),
            previous = RgbaColor(0.3f, 0.5f, 0.7f, 1f),
            mode = ColorFormatMode.RGB,
            alphaEnabled = true,
            closeOnSelect = false
        )
    }

    private fun inspectedRoot(): ContainerNode {
        val root = ContainerNode(key = "root")
        root.bounds = Rect(0, 0, 1200, 800)
        ContainerNode(key = "child").apply {
            bounds = Rect(16, 18, 120, 30)
        }.applyParent(root)
        return root
    }
}
