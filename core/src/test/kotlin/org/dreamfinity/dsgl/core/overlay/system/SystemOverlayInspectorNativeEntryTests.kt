package org.dreamfinity.dsgl.core.overlay.system

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.colorpicker.ColorFormatMode
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupRequest
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerRuntime
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerState
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.InspectorMode
import org.dreamfinity.dsgl.core.inspector.InspectorPanelState
import org.dreamfinity.dsgl.core.inspector.internal.SystemInspectorOverlayNode
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope

class SystemOverlayInspectorNativeEntryTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @AfterTest
    fun cleanup() {
        StyleEngine.setStylesDirectory(null)
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `inspector migration removes intermediate native overlay model classes`() {
        val loadResult = runCatching {
            Class.forName("org.dreamfinity.dsgl.core.inspector.InspectorNativeOverlayModel")
        }
        assertTrue(loadResult.isFailure)
    }

    @Test
    fun `inspector controller no longer exposes manual append overlay commands path`() {
        val methodNames = InspectorController::class.java.methods.map { it.name }
        assertFalse(methodNames.contains("appendOverlayCommands"))
    }

    @Test
    fun `inspector overlay rebuild does not leak event bus registrations`() {
        val controller = InspectorController()
        val overlay = SystemInspectorOverlayNode(controller)
        val root = inspectedRoot()

        controller.toggle()
        overlay.bindInspectedTree(root, layoutRevision = 1L)
        overlay.updateCursor(mouseX = 984, mouseY = 144, pointerCaptured = false)
        overlay.render(ctx, 0, 0, 1280, 720)
        val firstSnapshot = EventBus.debugListenerSnapshot()

        repeat(24) { frame ->
            overlay.bindInspectedTree(root, layoutRevision = 2L + frame)
            overlay.updateCursor(mouseX = 984, mouseY = 144, pointerCaptured = false)
            overlay.render(ctx, 0, 0, 1280, 720)
        }
        val repeatedSnapshot = EventBus.debugListenerSnapshot()

        assertTrue(repeatedSnapshot.registeredNodes <= firstSnapshot.registeredNodes + 4)
        assertTrue(repeatedSnapshot.registeredCallbacks <= firstSnapshot.registeredCallbacks + 24)

        controller.deactivate()
        overlay.render(ctx, 0, 0, 1280, 720)
        val deactivatedSnapshot = EventBus.debugListenerSnapshot()
        assertTrue(deactivatedSnapshot.registeredNodes <= firstSnapshot.registeredNodes)
        assertTrue(deactivatedSnapshot.registeredCallbacks <= firstSnapshot.registeredCallbacks)
    }

    @Test
    fun `live inspector path is native system-overlay entry and anti-legacy guarded`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRoot()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)

        assertTrue(host.debugMountedEntryIds().contains(SystemOverlayEntryId.Inspector))
        val node = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("inspector entry missing")
        val styleTypes = collectStyleTypes(node)
        assertTrue(styleTypes.contains("dsgl-system-inspector"))
        assertFalse(styleTypes.contains("dsgl-system-raw-render-command"))
        assertFalse(styleTypes.contains("dsgl-system-inspector-command-bridge"))
    }

    @Test
    fun `inspector runtime interaction path supports selection controls and system-owned color edit`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRoot()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)

        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertEquals("target", inspector.selectedKey)

        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 80, cursorY = 52, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)

        val pickToggle = inspector.debugPickToggleBounds() ?: error("pick toggle missing")
        assertTrue(host.handleMouseDown(pickToggle.x + 1, pickToggle.y + 1, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(pickToggle.x + 1, pickToggle.y + 1, MouseButton.LEFT))
        assertEquals(InspectorMode.Pick, inspector.mode)

        val colorAction = inspector.debugColorPickerActionBounds(StyleProperty.BACKGROUND_COLOR)
        val colorAnchor = colorAction ?: Rect(80, 80, 20, 18)
        val openedByClick = if (colorAction != null) {
            host.handleMouseDown(colorAction.x + 1, colorAction.y + 1, MouseButton.LEFT) &&
                host.handleMouseUp(colorAction.x + 1, colorAction.y + 1, MouseButton.LEFT)
        } else {
            false
        }
        if (!openedByClick) {
            assertTrue(inspector.debugOpenColorPickerForSelection(StyleProperty.BACKGROUND_COLOR, colorAnchor))
        }

        host.syncFrame(root, inspectedLayoutRevision = 3L, cursorX = colorAnchor.x + 1, cursorY = colorAnchor.y + 1, inspectorPointerCaptured = false)
        assertTrue(host.isSystemColorPickerOpen())
        assertEquals(OverlayOwnerScope.System, host.debugSystemColorPickerPopupOwnerScope())

        val pickerNode = host.debugEntryNode(SystemOverlayEntryId.ColorPickerPopup) ?: error("picker node missing")
        val pickerStyles = collectStyleTypes(pickerNode)
        assertTrue(pickerStyles.contains("dsgl-system-color-picker-native-body"))
        assertFalse(pickerStyles.contains("dsgl-system-raw-render-command"))
    }

    @Test
    fun `pick selection resolves from latest synced tree before render`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        val root = inspectedRoot()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)

        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertEquals("target", inspector.selectedKey)
    }

    @Test
    fun `inspector minimize restore and close reopen remain stable`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRoot()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 40, cursorY = 30, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)

        val initialNode = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("inspector node missing")
        val minimizeRect = inspector.debugMinimizeBounds() ?: error("minimize bounds missing")
        assertTrue(host.handleMouseDown(minimizeRect.x + 1, minimizeRect.y + 1, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(minimizeRect.x + 1, minimizeRect.y + 1, MouseButton.LEFT))
        assertEquals(InspectorPanelState.Minimized, inspector.panelState)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 40, cursorY = 30, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)

        val (chipX, chipY) = inspector.panelPosition
        assertTrue(host.handleMouseDown(chipX + 2, chipY + 2, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(chipX + 2, chipY + 2, MouseButton.LEFT))
        assertEquals(InspectorPanelState.Expanded, inspector.panelState)

        inspector.deactivate()
        host.syncFrame(root, inspectedLayoutRevision = 3L, cursorX = 40, cursorY = 30, inspectorPointerCaptured = false)
        assertFalse(host.debugMountedEntryIds().contains(SystemOverlayEntryId.Inspector))

        inspector.toggle()
        host.syncFrame(root, inspectedLayoutRevision = 4L, cursorX = 40, cursorY = 30, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        val reopenedNode = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("inspector node missing")
        assertSame(initialNode, reopenedNode)
    }

    @Test
    fun `inspector native path preserves scroll and scrollbar drag behavior`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRootWithManyChildren()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)

        val contentRect = inspector.debugContentRect()
        val wheelX = contentRect.x + 4
        val wheelY = contentRect.y + 12
        assertTrue(host.handleMouseWheel(wheelX, wheelY, -120))
        val afterWheel = inspector.panelScrollOffsetY
        assertTrue(afterWheel > 0)

        host.syncFrame(root, inspectedLayoutRevision = 3L, cursorX = wheelX, cursorY = wheelY, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)

        val thumb = inspector.debugScrollbarThumbRect()
        assertTrue(thumb.width > 0 && thumb.height > 0)
        val thumbX = thumb.x + 1
        val thumbY = thumb.y + thumb.height / 2
        assertTrue(host.handleMouseDown(thumbX, thumbY, MouseButton.LEFT))
        assertTrue(host.handleMouseMove(thumbX, thumbY + 70))
        assertTrue(host.handleMouseUp(thumbX, thumbY + 70, MouseButton.LEFT))
        assertTrue(inspector.panelScrollOffsetY >= afterWheel)
    }


    @Test
    fun `scrollbar drag release over control ends capture and does not trigger control click`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRootWithManyChildren()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertEquals("target", inspector.selectedKey)

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)

        val thumb = inspector.debugScrollbarThumbRect()
        assertTrue(thumb.width > 0 && thumb.height > 0)
        val pickToggle = inspector.debugPickToggleBounds() ?: error("pick toggle missing")
        val modeBeforeRelease = inspector.mode

        val thumbX = thumb.x + thumb.width / 2
        val thumbY = thumb.y + thumb.height / 2
        assertTrue(host.handleMouseDown(thumbX, thumbY, MouseButton.LEFT))
        assertTrue(inspector.isPointerCaptured)

        val releaseX = pickToggle.x + 2
        val releaseY = pickToggle.y + pickToggle.height / 2
        assertTrue(host.handleMouseMove(releaseX, releaseY))
        assertTrue(host.handleMouseUp(releaseX, releaseY, MouseButton.LEFT))

        assertFalse(inspector.isPointerCaptured)
        assertEquals(modeBeforeRelease, inspector.mode)

        val scrollAfterRelease = inspector.panelScrollOffsetY
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = releaseX,
            cursorY = releaseY + 48,
            inspectorPointerCaptured = inspector.isPointerCaptured
        )
        host.render(ctx, 420, 280)
        assertEquals(scrollAfterRelease, inspector.panelScrollOffsetY)
    }
    @Test
    fun `scrollbar drag release outside inspector consumes mouse up and stops capture`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRootWithManyChildren()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertEquals("target", inspector.selectedKey)

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)

        val thumb = inspector.debugScrollbarThumbRect()
        assertTrue(thumb.width > 0 && thumb.height > 0)
        val panelRect = inspector.debugPanelRect() ?: error("panel rect missing")
        val modeBeforeRelease = inspector.mode

        val candidatePoints = listOf(
            Pair((panelRect.x + panelRect.width + 4).coerceAtMost(419), panelRect.y + 6),
            Pair((panelRect.x - 4).coerceAtLeast(0), panelRect.y + 6),
            Pair(panelRect.x + 6, (panelRect.y - 4).coerceAtLeast(0)),
            Pair(panelRect.x + 6, (panelRect.y + panelRect.height + 4).coerceAtMost(279))
        )
        val outsidePoint = candidatePoints.firstOrNull { (x, y) -> !panelRect.contains(x, y) }
            ?: error("failed to find outside release point")
        val releaseX = outsidePoint.first
        val releaseY = outsidePoint.second

        val thumbX = thumb.x + thumb.width / 2
        val thumbY = thumb.y + thumb.height / 2
        assertTrue(host.handleMouseDown(thumbX, thumbY, MouseButton.LEFT))
        assertTrue(inspector.isPointerCaptured)

        assertTrue(host.handleMouseMove(releaseX, releaseY))
        assertTrue(host.handleMouseUp(releaseX, releaseY, MouseButton.LEFT))
        assertFalse(inspector.isPointerCaptured)
        assertEquals(modeBeforeRelease, inspector.mode)

        val scrollAfterRelease = inspector.panelScrollOffsetY
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = (releaseX + 32).coerceAtMost(419),
            cursorY = (releaseY + 32).coerceAtMost(279),
            inspectorPointerCaptured = inspector.isPointerCaptured
        )
        host.render(ctx, 420, 280)
        assertEquals(scrollAfterRelease, inspector.panelScrollOffsetY)
    }
    @Test
    fun `inspector color edit ownership stays system-owned and independent from app runtime popup`() {
        val appOwner = Any()
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRoot()

        try {
            ColorPickerRuntime.engine.open(
                ColorPickerPopupRequest(
                    owner = appOwner,
                    ownerScope = OverlayOwnerScope.Application,
                    anchorRect = Rect(240, 210, 20, 18),
                    title = "App Popup",
                    state = popupState()
                )
            )
            assertTrue(ColorPickerRuntime.engine.isOpenFor(appOwner))

            inspector.toggle()
            host.onInputFrame(1280, 720)
            host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
            host.render(ctx, 1280, 720)
            assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))

            host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 80, cursorY = 52, inspectorPointerCaptured = false)
            host.render(ctx, 1280, 720)
            val colorAction = inspector.debugColorPickerActionBounds(StyleProperty.BACKGROUND_COLOR)
            val colorAnchor = colorAction ?: Rect(80, 80, 20, 18)
            val openedByClick = if (colorAction != null) {
                host.handleMouseDown(colorAction.x + 1, colorAction.y + 1, MouseButton.LEFT) &&
                    host.handleMouseUp(colorAction.x + 1, colorAction.y + 1, MouseButton.LEFT)
            } else {
                false
            }
            if (!openedByClick) {
                assertTrue(inspector.debugOpenColorPickerForSelection(StyleProperty.BACKGROUND_COLOR, colorAnchor))
            }
            host.syncFrame(root, inspectedLayoutRevision = 3L, cursorX = colorAnchor.x + 1, cursorY = colorAnchor.y + 1, inspectorPointerCaptured = false)

            assertTrue(host.isSystemColorPickerOpen())
            assertEquals(OverlayOwnerScope.System, host.debugSystemColorPickerPopupOwnerScope())
            assertTrue(ColorPickerRuntime.engine.isOpenFor(appOwner))

            host.systemInspectorColorPickerPopupHost().close()
            host.syncFrame(root, inspectedLayoutRevision = 4L, cursorX = colorAnchor.x + 1, cursorY = colorAnchor.y + 1, inspectorPointerCaptured = false)
            assertFalse(host.isSystemColorPickerOpen())
            assertTrue(ColorPickerRuntime.engine.isOpenFor(appOwner))
        } finally {
            host.systemInspectorColorPickerPopupHost().close()
            ColorPickerRuntime.engine.close(appOwner)
        }
    }
    @Test
    fun `inspector native body content remains clipped in narrow viewport`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRootWithManyChildren()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))

        host.onInputFrame(320, 220)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 320, 220)

        val bodyRect = inspector.debugContentRect()
        val inspectorNode = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("inspector node missing")
        val clippedNodes = collectNodes(inspectorNode).filter { node ->
            if (node.display == Display.None) return@filter false
            val key = node.key?.toString() ?: return@filter false
            key.startsWith("dsgl-system-inspector-info-line-") ||
                    key.startsWith("dsgl-system-inspector-style-line-") ||
                    key == "dsgl-system-inspector-parent-row" ||
                    key.startsWith("dsgl-system-inspector-child-row-") ||
                    key == "dsgl-system-inspector-edit-color" ||
                    key == "dsgl-system-inspector-reset-node" ||
                    key == "dsgl-system-inspector-clear-all" ||
                    key == "dsgl-system-inspector-scrollbar-track" ||
                    key == "dsgl-system-inspector-scrollbar-thumb"
        }

        assertTrue(clippedNodes.isNotEmpty())
        clippedNodes.forEach { node ->
            val bounds = node.bounds
            assertTrue(bounds.x >= bodyRect.x)
            assertTrue(bounds.y >= bodyRect.y)
            assertTrue(bounds.x + bounds.width <= bodyRect.x + bodyRect.width)
            assertTrue(bounds.y + bounds.height <= bodyRect.y + bodyRect.height)
        }
    }
    @Test
    fun `inspector style boundary stays isolated from application stylesheet`() {
        val stylesDir = createTempStylesDir(
            """
            text { color: #FF00FF00; }
            div { background-color: #FFFF00FF; }
            """.trimIndent()
        )
        StyleEngine.setStylesDirectory(stylesDir)
        StyleEngine.forceReloadStylesheets()

        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRoot()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        val commands = host.paint(ctx)
        val headerTexts = commands
            .filterIsInstance<RenderCommand.DrawText>()
            .filter { it.text.startsWith("Inspector") }

        assertTrue(headerTexts.isNotEmpty())
        assertTrue(headerTexts.none { it.color == 0xFF00FF00.toInt() })
        assertTrue(headerTexts.any { it.color == 0xFFE6EDF6.toInt() })
    }

    private fun inspectedRoot(): ContainerNode {
        val root = ContainerNode(key = "root")
        root.bounds = Rect(0, 0, 1280, 720)
        ContainerNode(key = "target").apply {
            bounds = Rect(980, 140, 120, 30)
        }.applyParent(root)
        StyleEngine.setInspectorOverrideLiteral(root.children.first(), StyleProperty.BACKGROUND_COLOR, "#FF112233").getOrThrow()
        return root
    }

    private fun inspectedRootWithManyChildren(): ContainerNode {
        val root = ContainerNode(key = "root")
        root.bounds = Rect(0, 0, 1800, 1200)
        val selected = ContainerNode(key = "target").apply {
            bounds = Rect(980, 140, 260, 180)
        }.applyParent(root)
        repeat(60) { index ->
            ContainerNode(key = "child-$index").apply {
                bounds = Rect(980, 180 + index * 12, 180, 10)
            }.applyParent(selected)
        }
        StyleEngine.setInspectorOverrideLiteral(selected, StyleProperty.BACKGROUND_COLOR, "#FF112233").getOrThrow()
        return root
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

    private fun collectNodes(root: DOMNode): List<DOMNode> {
        val out = ArrayList<DOMNode>()
        fun walk(node: DOMNode) {
            out += node
            node.children.forEach(::walk)
        }
        walk(root)
        return out
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

    private fun createTempStylesDir(dss: String): File {
        val root = Files.createTempDirectory("dsgl-system-inspector-style-").toFile()
        root.resolve("test.dss").writeText(dss)
        return root
    }
}



