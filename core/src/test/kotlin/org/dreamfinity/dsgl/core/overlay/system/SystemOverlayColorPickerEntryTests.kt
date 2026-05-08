package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.colorpicker.ColorFormatMode
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupRequest
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerRuntime
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerState
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerStyle
import org.dreamfinity.dsgl.core.colorpicker.RgbChannelOrder
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SystemOverlayColorPickerEntryTests {
    private val ctx =
        object : UiMeasureContext {
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
        assertFalse(ColorPickerRuntime.engine.isOpen())

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
    fun `system picker entry path stays independent from application runtime popup path`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()
        val appOwner = Any()

        try {
            ColorPickerRuntime.engine.open(
                ColorPickerPopupRequest(
                    owner = appOwner,
                    ownerScope = OverlayOwnerScope.Application,
                    anchorRect = Rect(240, 210, 20, 18),
                    title = "App Popup",
                    state = popupState(),
                ),
            )
            assertTrue(ColorPickerRuntime.engine.isOpenFor(appOwner))

            pickerHost.open(anchorRect = Rect(40, 42, 20, 18), title = "Popup", state = popupState())
            host.onInputFrame(960, 720)
            host.syncFrame(
                root,
                inspectedLayoutRevision = 1L,
                cursorX = 44,
                cursorY = 48,
                inspectorPointerCaptured = false,
            )

            val node = host.debugEntryNode(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry node missing")
            val styleTypes = collectStyleTypes(node)
            assertTrue(styleTypes.contains("dsgl-overlay-panel"))
            assertTrue(styleTypes.contains("dsgl-system-color-picker-native-body"))
            assertFalse(styleTypes.contains("dsgl-system-raw-render-command"))
            assertEquals(OverlayOwnerScope.System, host.debugSystemColorPickerPopupOwnerScope())
            assertTrue(host.isSystemColorPickerOpen())
            assertTrue(ColorPickerRuntime.engine.isOpenFor(appOwner))

            pickerHost.close()
            host.syncFrame(
                root,
                inspectedLayoutRevision = 2L,
                cursorX = 44,
                cursorY = 48,
                inspectorPointerCaptured = false,
            )
            assertFalse(host.isSystemColorPickerOpen())
            assertTrue(ColorPickerRuntime.engine.isOpenFor(appOwner))
        } finally {
            pickerHost.close()
            ColorPickerRuntime.engine.close(appOwner)
        }
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
        assertTrue(host.handleMouseDown(startX, startY, MouseButton.LEFT))
        assertTrue(stateBefore.dragSession.active)

        host.handleMouseMove(startX + 50, startY + 30)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 2L,
            cursorX = startX + 50,
            cursorY = startY + 30,
            inspectorPointerCaptured = false,
        )
        val midState = host.debugEntryState(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry state missing")
        val panelMid = midState.panelState.currentRectOrNull() ?: error("panel missing")
        assertNotEquals(panelBefore.x, panelMid.x)

        host.handleMouseMove(startX + 90, startY + 60)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = startX + 90,
            cursorY = startY + 60,
            inspectorPointerCaptured = false,
        )
        val movingNode = host.debugEntryNode(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry node missing")
        val movingState = host.debugEntryState(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry state missing")
        val panelAfter = movingState.panelState.currentRectOrNull() ?: error("panel missing")
        assertSame(stableNode, movingNode)
        assertSame(stateBefore, movingState)
        assertTrue(movingState.dragSession.active)
        assertNotEquals(panelMid.x, panelAfter.x)

        assertTrue(host.handleMouseUp(startX + 90, startY + 60, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 4L,
            cursorX = startX + 90,
            cursorY = startY + 60,
            inspectorPointerCaptured = false,
        )
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
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 126,
            cursorY = 108,
            inspectorPointerCaptured = false,
        )

        val initialNode = host.debugEntryNode(SystemOverlayEntryId.ColorPickerPopup) ?: error("entry node missing")
        val header = host.debugSystemColorPickerHeaderRect() ?: error("header missing")
        val startX = header.x + 6
        val startY = header.y + 6
        assertTrue(host.handleMouseDown(startX, startY, MouseButton.LEFT))

        repeat(5) { step ->
            val mx = startX + 20 + step * 10
            val my = startY + 15 + step * 7
            host.handleMouseMove(mx, my)
            host.syncFrame(
                inspectedRoot = root,
                inspectedLayoutRevision = 2L + step,
                cursorX = mx,
                cursorY = my,
                inspectorPointerCaptured = false,
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
        assertTrue(host.handleMouseDown(closeRect.x + 1, closeRect.y + 1, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 2L,
            cursorX = closeRect.x + 1,
            cursorY = closeRect.y + 1,
            inspectorPointerCaptured = false,
        )
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
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 364,
            cursorY = 226,
            inspectorPointerCaptured = false,
        )
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
        assertTrue(styleTypes.contains("dsgl-overlay-panel"))
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
            onPreview = { previews += it },
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

        assertTrue(host.handleMouseDown(startX, startY, MouseButton.LEFT))
        host.handleMouseMove(midX, midY)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 2L,
            cursorX = midX,
            cursorY = midY,
            inspectorPointerCaptured = false,
        )
        host.handleMouseMove(endX, endY)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = endX,
            cursorY = endY,
            inspectorPointerCaptured = false,
        )
        assertTrue(host.handleMouseUp(endX, endY, MouseButton.LEFT))

        val state = host.debugSystemColorPickerState() ?: error("state missing")
        assertTrue(previews.size >= 2)
        assertNotEquals(0.3f, state.color.r)
    }

    @Test
    fun `system picker current swatch click commits once without double apply`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()
        var commits = 0

        pickerHost.open(
            anchorRect = Rect(80, 90, 20, 18),
            title = "Popup",
            state =
                ColorPickerState(
                    color = RgbaColor(0.3f, 0.5f, 0.7f, 1f),
                    previous = RgbaColor(0.1f, 0.2f, 0.3f, 1f),
                    mode = ColorFormatMode.RGB,
                    alphaEnabled = true,
                    closeOnSelect = false,
                ),
            onCommit = { commits += 1 },
        )
        host.onInputFrame(1200, 800)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 88, cursorY = 98, inspectorPointerCaptured = false)

        val currentSwatch = host.debugSystemColorPickerBodyLayout()?.currentSwatchRect ?: error("swatch rect missing")
        assertTrue(host.handleMouseDown(currentSwatch.x + 2, currentSwatch.y + 2, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(currentSwatch.x + 2, currentSwatch.y + 2, MouseButton.LEFT))
        assertEquals(1, commits)
    }

    @Test
    fun `system picker recent swatch click previews once without double apply`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()
        val initial = popupState()
        val previews = ArrayList<RgbaColor>()

        pickerHost.open(
            anchorRect = Rect(80, 90, 20, 18),
            title = "Popup",
            state = initial,
            onPreview = { previews += it },
        )
        host.onInputFrame(1200, 800)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 88, cursorY = 98, inspectorPointerCaptured = false)

        val layout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val field = layout.colorFieldRect
        val startX = field.x + 2
        val startY = field.y + 2
        val endX = field.x + field.width - 2
        val endY = field.y + field.height - 2

        assertTrue(host.handleMouseDown(startX, startY, MouseButton.LEFT))
        host.handleMouseMove(endX, endY)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 2L,
            cursorX = endX,
            cursorY = endY,
            inspectorPointerCaptured = false,
        )
        assertTrue(host.handleMouseUp(endX, endY, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = endX,
            cursorY = endY,
            inspectorPointerCaptured = false,
        )

        val stateAfterDrag = host.debugSystemColorPickerState() ?: error("state missing")
        assertNotEquals(initial.color.toArgbInt(), stateAfterDrag.color.toArgbInt())
        val previewCountBeforeRecentClick = previews.size

        host.render(ctx, 1200, 800)
        val recentRect =
            host
                .debugSystemColorPickerBodyLayout()
                ?.recentRects
                ?.getOrNull(1)
                ?: error("recent swatch rect missing")
        assertTrue(host.handleMouseDown(recentRect.x + 1, recentRect.y + 1, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(recentRect.x + 1, recentRect.y + 1, MouseButton.LEFT))
        assertEquals(previewCountBeforeRecentClick + 1, previews.size)
        assertEquals(
            initial.color.toArgbInt(),
            host
                .debugSystemColorPickerState()
                ?.color
                ?.toArgbInt(),
        )
    }

    @Test
    fun `system picker sync state updates current swatch without drag nudge`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()
        val initial = popupState()
        val updated =
            initial.copy(
                color = RgbaColor(0.92f, 0.16f, 0.24f, 1f),
                previous = initial.color,
            )

        pickerHost.open(anchorRect = Rect(80, 90, 20, 18), title = "Popup", state = initial)
        host.onInputFrame(1200, 800)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 88, cursorY = 98, inspectorPointerCaptured = false)

        val swatchRect = host.debugSystemColorPickerBodyLayout()?.currentSwatchRect ?: error("swatch rect missing")
        host.render(ctx, 1200, 800)
        val beforeColor = resolveRectFillColor(host.paint(ctx), swatchRect) ?: error("before swatch fill missing")
        assertEquals(initial.color.toArgbInt(), beforeColor)

        pickerHost.open(anchorRect = Rect(80, 90, 20, 18), title = "Popup", state = updated)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 88, cursorY = 98, inspectorPointerCaptured = false)

        host.render(ctx, 1200, 800)
        val afterColor = resolveRectFillColor(host.paint(ctx), swatchRect) ?: error("after swatch fill missing")
        assertEquals(updated.color.toArgbInt(), afterColor)
        assertNotEquals(beforeColor, afterColor)
    }

    @Test
    fun `system picker sync state updates rgb order button selected visuals without drag nudge`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()
        val style = ColorPickerStyle()
        val initial = popupState().copy(mode = ColorFormatMode.RGB, rgbOrder = RgbChannelOrder.RGBA)
        val updated = initial.copy(rgbOrder = RgbChannelOrder.ARGB)

        pickerHost.open(anchorRect = Rect(80, 90, 20, 18), title = "Popup", state = initial)
        host.onInputFrame(1200, 800)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 2, cursorY = 2, inspectorPointerCaptured = false)

        val initialLayout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val rgbaRect = initialLayout.rgbaOrderRect ?: error("rgba rect missing")
        val argbRect = initialLayout.argbOrderRect ?: error("argb rect missing")
        host.render(ctx, 1200, 800)
        val rgbaBefore = resolveRectFillColor(host.paint(ctx), rgbaRect) ?: error("rgba fill missing")
        val argbBefore = resolveRectFillColor(host.paint(ctx), argbRect) ?: error("argb fill missing")
        assertEquals(style.buttonActiveColor, rgbaBefore)
        assertEquals(style.buttonBackgroundColor, argbBefore)

        pickerHost.open(anchorRect = Rect(80, 90, 20, 18), title = "Popup", state = updated)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 2, cursorY = 2, inspectorPointerCaptured = false)

        host.render(ctx, 1200, 800)
        val rgbaAfter = resolveRectFillColor(host.paint(ctx), rgbaRect) ?: error("rgba fill missing after sync")
        val argbAfter = resolveRectFillColor(host.paint(ctx), argbRect) ?: error("argb fill missing after sync")
        assertEquals(style.buttonBackgroundColor, rgbaAfter)
        assertEquals(style.buttonActiveColor, argbAfter)
        assertNotEquals(rgbaBefore, rgbaAfter)
        assertNotEquals(argbBefore, argbAfter)
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
        assertTrue(host.handleMouseDown(hueStartX, hueY, MouseButton.LEFT))
        host.handleMouseMove(hueEndX, hueY)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 2L,
            cursorX = hueEndX,
            cursorY = hueY,
            inspectorPointerCaptured = false,
        )
        assertTrue(host.handleMouseUp(hueEndX, hueY, MouseButton.LEFT))

        val alphaStartX = alpha.x + alpha.width / 2
        val alphaEndX = alpha.x + 2
        val alphaY = alpha.y + alpha.height / 2
        assertTrue(host.handleMouseDown(alphaStartX, alphaY, MouseButton.LEFT))
        host.handleMouseMove(alphaEndX, alphaY)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = alphaEndX,
            cursorY = alphaY,
            inspectorPointerCaptured = false,
        )
        assertTrue(host.handleMouseUp(alphaEndX, alphaY, MouseButton.LEFT))

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
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 128,
            cursorY = 128,
            inspectorPointerCaptured = false,
        )

        val initialLayout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val modeSelect = initialLayout.modeSelectRect
        assertTrue(host.handleMouseDown(modeSelect.x + 2, modeSelect.y + 2, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 2L,
            cursorX = modeSelect.x + 2,
            cursorY = modeSelect.y + 2,
            inspectorPointerCaptured = false,
        )
        val expandedLayout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val hslOption =
            expandedLayout.modeOptions.firstOrNull { it.mode == ColorFormatMode.HSL } ?: error("HSL option missing")
        assertTrue(host.handleMouseDown(hslOption.rect.x + 2, hslOption.rect.y + 2, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = hslOption.rect.x + 2,
            cursorY =
                hslOption.rect.y + 2,
            inspectorPointerCaptured = false,
        )

        val modeChanged = host.debugSystemColorPickerState() ?: error("state missing")
        assertEquals(ColorFormatMode.HSL, modeChanged.mode)

        val hslLayout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val saturationInput = hslLayout.inputSlots.firstOrNull { it.key == "s" } ?: error("s input missing")
        assertTrue(
            host.handleMouseDown(saturationInput.inputRect.x + 2, saturationInput.inputRect.y + 2, MouseButton.LEFT),
        )
        assertTrue(host.handleKeyDown(KeyCodes.HOME, 0.toChar()))
        repeat(4) {
            assertTrue(host.handleKeyDown(KeyCodes.DELETE, 0.toChar()))
        }
        assertTrue(host.handleKeyDown(0, '0'))
        assertTrue(host.handleKeyDown(KeyCodes.ENTER, '\n'))

        val updated = host.debugSystemColorPickerState() ?: error("state missing")
        assertEquals(ColorFormatMode.HSL, updated.mode)
        val updatedLayout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        assertEquals(listOf("h", "s", "l", "a"), updatedLayout.inputSlots.map { it.key })
    }

    @Test
    fun `system picker input focus retargets by semantic key across rgb order switch`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()

        pickerHost.open(anchorRect = Rect(120, 120, 20, 18), title = "Popup", state = popupState())
        host.onInputFrame(1200, 800)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 128,
            cursorY = 128,
            inspectorPointerCaptured = false,
        )

        val initialLayout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val redInput = initialLayout.inputSlots.firstOrNull { it.key == "r" } ?: error("r input missing")
        assertTrue(host.handleMouseDown(redInput.inputRect.x + 2, redInput.inputRect.y + 2, MouseButton.LEFT))

        val argbButton = initialLayout.argbOrderRect ?: error("argb button missing")
        assertTrue(host.handleMouseDown(argbButton.x + 2, argbButton.y + 2, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 2L,
            cursorX = argbButton.x + 2,
            cursorY = argbButton.y + 2,
            inspectorPointerCaptured = false,
        )
        assertEquals("dsgl-system-color-picker-input-value-1", FocusManager.focusedNode()?.key)

        assertTrue(host.handleKeyDown(KeyCodes.HOME, 0.toChar()))
        repeat(4) {
            assertTrue(host.handleKeyDown(KeyCodes.DELETE, 0.toChar()))
        }
        assertTrue(host.handleKeyDown(0, '0'))
        assertTrue(host.handleKeyDown(KeyCodes.ENTER, '\n'))

        val updated = host.debugSystemColorPickerState() ?: error("state missing")
        assertEquals(RgbChannelOrder.ARGB, updated.rgbOrder)
        assertEquals(1f, updated.color.a)
        assertEquals("dsgl-system-color-picker-input-value-1", FocusManager.focusedNode()?.key)
    }

    @Test
    fun `system picker rgb order buttons use dom semantic actions without double apply`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()
        var previews = 0
        var commits = 0

        pickerHost.open(
            anchorRect = Rect(120, 120, 20, 18),
            title = "Popup",
            state = popupState().copy(mode = ColorFormatMode.RGB, rgbOrder = RgbChannelOrder.RGBA),
            onPreview = { previews += 1 },
            onCommit = { commits += 1 },
        )
        host.onInputFrame(1200, 800)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 128,
            cursorY = 128,
            inspectorPointerCaptured = false,
        )

        val initialLayout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val redInput = initialLayout.inputSlots.firstOrNull { it.key == "r" } ?: error("r input missing")
        assertTrue(host.handleMouseDown(redInput.inputRect.x + 2, redInput.inputRect.y + 2, MouseButton.LEFT))
        host.render(ctx, 1200, 800)

        val argbButton = initialLayout.argbOrderRect ?: error("argb button missing")
        assertTrue(host.handleMouseDown(argbButton.x + 2, argbButton.y + 2, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(argbButton.x + 2, argbButton.y + 2, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 2L,
            cursorX = argbButton.x + 2,
            cursorY = argbButton.y + 2,
            inspectorPointerCaptured = false,
        )

        val updated = host.debugSystemColorPickerState() ?: error("state missing")
        assertEquals(RgbChannelOrder.ARGB, updated.rgbOrder)
        assertEquals(ColorFormatMode.RGB, updated.mode)
        assertEquals(0, previews)
        assertEquals(0, commits)
        assertFalse(host.debugMountedEntryIds().contains(SystemOverlayEntryId.ColorPickerTransient))
    }

    @Test
    fun `system picker mode trigger toggles dropdown through dom path without double apply`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()

        pickerHost.open(anchorRect = Rect(120, 120, 20, 18), title = "Popup", state = popupState())
        host.onInputFrame(1200, 800)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 128,
            cursorY = 128,
            inspectorPointerCaptured = false,
        )

        val initialLayout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val modeSelect = initialLayout.modeSelectRect
        assertFalse(host.debugMountedEntryIds().contains(SystemOverlayEntryId.ColorPickerTransient))

        assertTrue(host.handleMouseDown(modeSelect.x + 2, modeSelect.y + 2, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(modeSelect.x + 2, modeSelect.y + 2, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 2L,
            cursorX = modeSelect.x + 2,
            cursorY = modeSelect.y + 2,
            inspectorPointerCaptured = false,
        )

        assertTrue(host.debugMountedEntryIds().contains(SystemOverlayEntryId.ColorPickerTransient))
        assertNotNull(host.debugSystemColorPickerBodyLayout()?.modeOptionsRect)

        assertTrue(host.handleMouseDown(modeSelect.x + 2, modeSelect.y + 2, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(modeSelect.x + 2, modeSelect.y + 2, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = modeSelect.x + 2,
            cursorY = modeSelect.y + 2,
            inspectorPointerCaptured = false,
        )

        assertFalse(host.debugMountedEntryIds().contains(SystemOverlayEntryId.ColorPickerTransient))
        assertTrue(host.debugSystemColorPickerBodyLayout()?.modeOptionsRect == null)
    }

    @Test
    fun `system picker mode option click changes mode and closes dropdown via dom path`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()
        var previews = 0
        var commits = 0

        pickerHost.open(
            anchorRect = Rect(120, 120, 20, 18),
            title = "Popup",
            state = popupState(),
            onPreview = { previews += 1 },
            onCommit = { commits += 1 },
        )
        host.onInputFrame(1200, 800)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 128,
            cursorY = 128,
            inspectorPointerCaptured = false,
        )

        val initialLayout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val modeSelect = initialLayout.modeSelectRect
        assertTrue(host.handleMouseDown(modeSelect.x + 2, modeSelect.y + 2, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(modeSelect.x + 2, modeSelect.y + 2, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 2L,
            cursorX = modeSelect.x + 2,
            cursorY = modeSelect.y + 2,
            inspectorPointerCaptured = false,
        )

        val expandedLayout = host.debugSystemColorPickerBodyLayout() ?: error("expanded layout missing")
        val hslOption =
            expandedLayout.modeOptions.firstOrNull { it.mode == ColorFormatMode.HSL } ?: error("HSL option missing")
        assertTrue(host.handleMouseDown(hslOption.rect.x + 2, hslOption.rect.y + 2, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(hslOption.rect.x + 2, hslOption.rect.y + 2, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = hslOption.rect.x + 2,
            cursorY = hslOption.rect.y + 2,
            inspectorPointerCaptured = false,
        )

        assertEquals(ColorFormatMode.HSL, host.debugSystemColorPickerState()?.mode)
        assertFalse(host.debugMountedEntryIds().contains(SystemOverlayEntryId.ColorPickerTransient))
        assertEquals(0, previews)
        assertEquals(0, commits)
    }

    @Test
    fun `system picker mode dropdown is mounted in transient lane and stays interactive`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()

        pickerHost.open(anchorRect = Rect(120, 120, 20, 18), title = "Popup", state = popupState())
        host.onInputFrame(1200, 800)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 128,
            cursorY = 128,
            inspectorPointerCaptured = false,
        )

        assertFalse(host.debugMountedEntryIds().contains(SystemOverlayEntryId.ColorPickerTransient))

        val initialLayout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val modeSelect = initialLayout.modeSelectRect
        assertTrue(host.handleMouseDown(modeSelect.x + 2, modeSelect.y + 2, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 2L,
            cursorX = modeSelect.x + 2,
            cursorY = modeSelect.y + 2,
            inspectorPointerCaptured = false,
        )

        assertTrue(host.debugMountedEntryIds().contains(SystemOverlayEntryId.ColorPickerTransient))
        val transientNode =
            host.debugEntryNode(SystemOverlayEntryId.ColorPickerTransient) ?: error("transient node missing")
        val transientStyleTypes = collectStyleTypes(transientNode)
        assertTrue(transientStyleTypes.contains("dsgl-system-color-picker-native-mode-dropdown-overlay"))

        val expandedLayout = host.debugSystemColorPickerBodyLayout() ?: error("expanded layout missing")
        val hslOption =
            expandedLayout.modeOptions.firstOrNull { it.mode == ColorFormatMode.HSL } ?: error("HSL option missing")
        assertTrue(host.handleMouseDown(hslOption.rect.x + 2, hslOption.rect.y + 2, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = hslOption.rect.x + 2,
            cursorY =
                hslOption.rect.y + 2,
            inspectorPointerCaptured = false,
        )

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
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 146,
            cursorY = 146,
            inspectorPointerCaptured = false,
        )

        val layout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val pipette = layout.pipetteRect
        assertTrue(host.handleMouseDown(pipette.x + 2, pipette.y + 2, MouseButton.LEFT))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 2L,
            cursorX = pipette.x + 2,
            cursorY = pipette.y + 2,
            inspectorPointerCaptured = false,
        )

        val mounted = host.debugMountedEntryIds()
        assertTrue(mounted.contains(SystemOverlayEntryId.ColorPickerPopup))
        assertTrue(mounted.contains(SystemOverlayEntryId.ColorPickerTransient))
        assertTrue(host.isSystemColorPickerOpen())
        assertTrue(host.debugEntryState(SystemOverlayEntryId.ColorPickerPopup)?.active == true)
        assertEquals(OverlayOwnerScope.System, host.debugSystemColorPickerPopupOwnerScope())

        val moveConsumed = host.handleMouseMove(pipette.x + 40, pipette.y + 40)
        assertTrue(moveConsumed)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = pipette.x + 40,
            cursorY = pipette.y + 40,
            inspectorPointerCaptured = false,
        )

        assertTrue(host.debugMountedEntryIds().contains(SystemOverlayEntryId.ColorPickerPopup))
    }

    @Test
    fun `system picker pipette transient entry emits visible tooltip commands`() {
        val host = SystemOverlayHost(InspectorController())
        val pickerHost = host.systemInspectorColorPickerPopupHost()
        val root = inspectedRoot()
        val gridColor = 0x7F4C93FF
        val checkerLight = 0x7F0AA0A0
        val checkerDark = 0x7F104040

        pickerHost.open(
            anchorRect = Rect(140, 140, 20, 18),
            title = "Popup",
            state = popupState(),
            style =
                ColorPickerStyle(
                    eyedropperGridSize = 5,
                    eyedropperCellSize = 3,
                    eyedropperGridOverlayEnabled = true,
                    eyedropperGridOverlayColor = gridColor,
                    checkerLightColor = checkerLight,
                    checkerDarkColor = checkerDark,
                ),
        )
        host.onInputFrame(1200, 800)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 146,
            cursorY = 146,
            inspectorPointerCaptured = false,
        )

        val layout = host.debugSystemColorPickerBodyLayout() ?: error("layout missing")
        val pipette = layout.pipetteRect
        assertTrue(host.handleMouseDown(pipette.x + 2, pipette.y + 2, MouseButton.LEFT))
        host.handleMouseMove(pipette.x + 32, pipette.y + 28)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 2L,
            cursorX = pipette.x + 32,
            cursorY = pipette.y + 28,
            inspectorPointerCaptured = false,
        )

        host.render(ctx, 1200, 800)
        val commands = host.paint(ctx)
        assertTrue(commands.any { it is RenderCommand.CaptureScreenRegion })
        assertTrue(commands.any { it is RenderCommand.DrawCapturedScreenRegion })
        assertTrue(
            commands.none { command ->
                command is RenderCommand.DrawRect && command.width == 3 && command.height == 3
            },
        )
        assertTrue(commands.any { it is RenderCommand.DrawCheckerboard })
        assertTrue(
            commands.none { command ->
                command is RenderCommand.DrawRect && (command.color == checkerLight || command.color == checkerDark)
            },
        )
        val capturedRegion = commands.filterIsInstance<RenderCommand.DrawCapturedScreenRegion>().single()
        val gridOverlay = capturedRegion.gridOverlay ?: error("grid overlay missing")
        assertEquals(5, gridOverlay.columns)
        assertEquals(5, gridOverlay.rows)
        assertEquals(3, gridOverlay.magnification)
        assertEquals(gridColor, gridOverlay.color)
        assertTrue(
            commands.none { command ->
                command is RenderCommand.DrawRect && command.color == gridColor
            },
        )
        assertTrue(
            commands.any { command ->
                command is RenderCommand.DrawText && command.text.startsWith("Mode:")
            },
        )
    }

    private fun collectStyleTypes(root: DOMNode): Set<String> {
        val out = LinkedHashSet<String>()

        fun walk(node: DOMNode) {
            out += node.styleType
            node.children.forEach(::walk)
        }
        walk(root)
        return out
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
        root.bounds = Rect(0, 0, 1200, 800)
        ContainerNode(key = "child")
            .apply {
                bounds = Rect(16, 18, 120, 30)
            }.applyParent(root)
        return root
    }

    private fun resolveRectFillColor(commands: List<RenderCommand>, rect: Rect): Int? =
        commands
            .asReversed()
            .asSequence()
            .filterIsInstance<RenderCommand.DrawRect>()
            .firstOrNull { command ->
                command.x == rect.x &&
                    command.y == rect.y &&
                    command.width == rect.width &&
                    command.height == rect.height
            }?.color
}
