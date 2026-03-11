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
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerStyle
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerState
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope

class SystemOverlayColorPickerEntryTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }
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
    fun `system picker popup close button closes entry through panel panel`() {
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


    @Test
    fun `system picker entry mounts native body subtree without command bridge`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()

        pickerHost.open(anchorRect = Rect(60, 70, 20, 18), title = "Popup", state = popupState())
        host.onInputFrame(1200, 800)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 64, cursorY = 74, inspectorPointerCaptured = false)

        val node = host.debugEntryNode(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry node missing")
        val styleTypes = collectStyleTypes(node)
        assertTrue(styleTypes.contains("dsgl-system-color-picker-native-body"))
        assertFalse(styleTypes.contains("dsgl-system-color-picker-command-bridge"))
        assertFalse(styleTypes.contains("dsgl-system-raw-render-command"))
    }

    @Test
    fun `system picker color field drag updates color continuously`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()
        val previews = ArrayList<RgbaColor>()

        pickerHost.open(
            anchorRect = Rect(80, 90, 20, 18),
            title = "Popup",
            state = popupState(),
            onPreview = { previews += it }
        )
        host.onInputFrame(1200, 800)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 88, cursorY = 98, inspectorPointerCaptured = false)

        val layout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val field = layout.colorFieldRect
        val startX = field.x + 2
        val startY = field.y + 2
        val midX = field.x + field.width / 2
        val midY = field.y + field.height / 2
        val endX = field.x + field.width - 2
        val endY = field.y + field.height - 2

        assertTrue(host.handleMouseDown(startX, startY, org.dreamfinity.dsgl.core.event.MouseButton.LEFT))
        host.handleMouseMove(midX, midY)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = midX, cursorY = midY, inspectorPointerCaptured = false)
        host.handleMouseMove(endX, endY)
        host.syncFrame(root, inspectedLayoutRevision = 3L, cursorX = endX, cursorY = endY, inspectorPointerCaptured = false)
        assertTrue(host.handleMouseUp(endX, endY, org.dreamfinity.dsgl.core.event.MouseButton.LEFT))

        val state = host.debugSystemColorPickerState() ?: error("state missing")
        assertTrue(previews.size >= 2)
        assertNotEquals(0.3f, state.color.r)
    }

    @Test
    fun `system picker hue and alpha drag update state`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()

        pickerHost.open(anchorRect = Rect(80, 90, 20, 18), title = "Popup", state = popupState())
        host.onInputFrame(1200, 800)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 88, cursorY = 98, inspectorPointerCaptured = false)

        val layout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val hue = layout.hueRect
        val alpha = layout.alphaRect ?: error("alpha rect missing")
        val initial = host.debugSystemColorPickerState() ?: error("state missing")

        val hueStartX = hue.x + 2
        val hueEndX = hue.x + hue.width - 2
        val hueY = hue.y + hue.height / 2
        assertTrue(host.handleMouseDown(hueStartX, hueY, org.dreamfinity.dsgl.core.event.MouseButton.LEFT))
        host.handleMouseMove(hueEndX, hueY)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = hueEndX, cursorY = hueY, inspectorPointerCaptured = false)
        assertTrue(host.handleMouseUp(hueEndX, hueY, org.dreamfinity.dsgl.core.event.MouseButton.LEFT))

        val alphaStartX = alpha.x + alpha.width / 2
        val alphaEndX = alpha.x + 2
        val alphaY = alpha.y + alpha.height / 2
        assertTrue(host.handleMouseDown(alphaStartX, alphaY, org.dreamfinity.dsgl.core.event.MouseButton.LEFT))
        host.handleMouseMove(alphaEndX, alphaY)
        host.syncFrame(root, inspectedLayoutRevision = 3L, cursorX = alphaEndX, cursorY = alphaY, inspectorPointerCaptured = false)
        assertTrue(host.handleMouseUp(alphaEndX, alphaY, org.dreamfinity.dsgl.core.event.MouseButton.LEFT))

        val changed = host.debugSystemColorPickerState() ?: error("state missing")
        assertNotEquals(initial.color.toArgbInt(), changed.color.toArgbInt())
        assertTrue(changed.color.a < initial.color.a)
    }

    @Test
    fun `system picker text input and mode controls stay synchronized`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()

        pickerHost.open(anchorRect = Rect(120, 120, 20, 18), title = "Popup", state = popupState())
        host.onInputFrame(1200, 800)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 128, cursorY = 128, inspectorPointerCaptured = false)

        val initialLayout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val modeSelect = initialLayout.modeSelectRect
        assertTrue(host.handleMouseDown(modeSelect.x + 2, modeSelect.y + 2, org.dreamfinity.dsgl.core.event.MouseButton.LEFT))
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = modeSelect.x + 2, cursorY = modeSelect.y + 2, inspectorPointerCaptured = false)
        val expandedLayout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val hslOption = expandedLayout.modeOptions.firstOrNull { it.mode == ColorFormatMode.HSL } ?: error("HSL option missing")
        assertTrue(host.handleMouseDown(hslOption.rect.x + 2, hslOption.rect.y + 2, org.dreamfinity.dsgl.core.event.MouseButton.LEFT))
        host.syncFrame(root, inspectedLayoutRevision = 3L, cursorX = hslOption.rect.x + 2, cursorY = hslOption.rect.y + 2, inspectorPointerCaptured = false)

        val modeChanged = host.debugSystemColorPickerState() ?: error("state missing")
        assertEquals(ColorFormatMode.HSL, modeChanged.mode)

        val hslLayout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val hueInput = hslLayout.inputSlots.firstOrNull { it.key == "h" } ?: error("h input missing")
        assertTrue(host.handleMouseDown(hueInput.inputRect.x + 2, hueInput.inputRect.y + 2, org.dreamfinity.dsgl.core.event.MouseButton.LEFT))
        assertTrue(host.handleKeyDown(org.dreamfinity.dsgl.core.event.KeyCodes.DELETE, 0.toChar()))
        assertTrue(host.handleKeyDown(0, '1'))
        assertTrue(host.handleKeyDown(0, '8'))
        assertTrue(host.handleKeyDown(0, '0'))
        assertTrue(host.handleKeyDown(org.dreamfinity.dsgl.core.event.KeyCodes.ENTER, '\n'))

        val updated = host.debugSystemColorPickerState() ?: error("state missing")
        assertEquals(ColorFormatMode.HSL, updated.mode)
        assertTrue(updated.color.toArgbInt() != modeChanged.color.toArgbInt())
    }


    @Test
    fun `system picker mode dropdown is mounted in transient lane and stays interactive`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()

        pickerHost.open(anchorRect = Rect(120, 120, 20, 18), title = "Popup", state = popupState())
        host.onInputFrame(1200, 800)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 128, cursorY = 128, inspectorPointerCaptured = false)

        assertFalse(host.debugMountedEntryIds().contains(SystemOverlayEntryId.ColorPickerTransient))

        val initialLayout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val modeSelect = initialLayout.modeSelectRect
        assertTrue(host.handleMouseDown(modeSelect.x + 2, modeSelect.y + 2, org.dreamfinity.dsgl.core.event.MouseButton.LEFT))
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = modeSelect.x + 2, cursorY = modeSelect.y + 2, inspectorPointerCaptured = false)

        assertTrue(host.debugMountedEntryIds().contains(SystemOverlayEntryId.ColorPickerTransient))
        val transientNode = host.debugEntryNode(SystemOverlayEntryId.ColorPickerTransient) ?: error("transient node missing")
        val transientStyleTypes = collectStyleTypes(transientNode)
        assertTrue(transientStyleTypes.contains("dsgl-system-color-picker-native-mode-dropdown-overlay"))

        val expandedLayout = host.debugSystemColorPickerBodyLayout() ?: error("expanded layout missing")
        val hslOption = expandedLayout.modeOptions.firstOrNull { it.mode == ColorFormatMode.HSL } ?: error("HSL option missing")
        assertTrue(host.handleMouseDown(hslOption.rect.x + 2, hslOption.rect.y + 2, org.dreamfinity.dsgl.core.event.MouseButton.LEFT))
        host.syncFrame(root, inspectedLayoutRevision = 3L, cursorX = hslOption.rect.x + 2, cursorY = hslOption.rect.y + 2, inspectorPointerCaptured = false)

        assertEquals(ColorFormatMode.HSL, host.debugSystemColorPickerState()?.mode)
        assertFalse(host.debugMountedEntryIds().contains(SystemOverlayEntryId.ColorPickerTransient))
    }

    @Test
    fun `system picker pipette keeps system overlay visible and uses transient lane`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()

        pickerHost.open(anchorRect = Rect(140, 140, 20, 18), title = "Popup", state = popupState())
        host.onInputFrame(1200, 800)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 146, cursorY = 146, inspectorPointerCaptured = false)

        val layout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val pipette = layout.pipetteRect
        assertTrue(host.handleMouseDown(pipette.x + 2, pipette.y + 2, org.dreamfinity.dsgl.core.event.MouseButton.LEFT))
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = pipette.x + 2, cursorY = pipette.y + 2, inspectorPointerCaptured = false)

        val mounted = host.debugMountedEntryIds()
        assertTrue(mounted.contains(SystemOverlayEntryId.ColorPickerPopup))
        assertTrue(mounted.contains(SystemOverlayEntryId.ColorPickerTransient))
        assertTrue(host.isSystemColorPickerOpen())
        assertTrue(host.debugEntryState(SystemOverlayEntryId.ColorPickerPopup)?.active == true)
        assertEquals(OverlayOwnerScope.System, host.debugSystemColorPickerPopupOwnerScope())

        val moveConsumed = host.handleMouseMove(pipette.x + 40, pipette.y + 40)
        assertTrue(moveConsumed)
        host.syncFrame(root, inspectedLayoutRevision = 3L, cursorX = pipette.x + 40, cursorY = pipette.y + 40, inspectorPointerCaptured = false)

        assertTrue(host.debugMountedEntryIds().contains(SystemOverlayEntryId.ColorPickerPopup))
    }


    @Test
    fun `system picker pipette transient entry emits visible tooltip commands`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()

        pickerHost.open(
            anchorRect = Rect(140, 140, 20, 18),
            title = "Popup",
            state = popupState(),
            style = ColorPickerStyle(eyedropperGridSize = 5, eyedropperCellSize = 3)
        )
        host.onInputFrame(1200, 800)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 146, cursorY = 146, inspectorPointerCaptured = false)

        val layout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val pipette = layout.pipetteRect
        assertTrue(host.handleMouseDown(pipette.x + 2, pipette.y + 2, org.dreamfinity.dsgl.core.event.MouseButton.LEFT))
        host.handleMouseMove(pipette.x + 32, pipette.y + 28)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = pipette.x + 32, cursorY = pipette.y + 28, inspectorPointerCaptured = false)

        host.render(ctx, 1200, 800)
        val commands = host.paint(ctx)
        assertTrue(commands.any { it is RenderCommand.CaptureScreenRegion })
        assertTrue(commands.any { it is RenderCommand.DrawCapturedScreenRegion })
        assertTrue(commands.none { command ->
            command is RenderCommand.DrawRect && command.width == 3 && command.height == 3
        })
        assertTrue(commands.any { command ->
            command is RenderCommand.DrawText && command.text.startsWith("Mode:")
        })
    }
    private fun collectStyleTypes(root: org.dreamfinity.dsgl.core.dom.DOMNode): Set<String> {
        val out = LinkedHashSet<String>()
        fun walk(node: org.dreamfinity.dsgl.core.dom.DOMNode) {
            out += node.styleType
            node.children.forEach(::walk)
        }
        walk(root)
        return out
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

