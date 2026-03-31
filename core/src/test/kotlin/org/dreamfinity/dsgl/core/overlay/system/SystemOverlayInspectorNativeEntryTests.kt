package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.colorpicker.*
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.InspectorMode
import org.dreamfinity.dsgl.core.inspector.InspectorPanelState
import org.dreamfinity.dsgl.core.inspector.internal.SystemInspectorOverlayNode
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleProperty
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class SystemOverlayInspectorNativeEntryTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @AfterTest
    fun cleanup() {
        KeyModifiers.sync(shift = false, control = false, meta = false)
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
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 1280, 720)

        assertTrue(host.debugMountedEntryIds().contains(SystemOverlayEntryId.Inspector))
        val node = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("inspector entry missing")
        val styleTypes = collectStyleTypes(node)
        assertTrue(styleTypes.contains("dsgl-system-inspector"))
        assertFalse(styleTypes.contains("dsgl-system-raw-render-command"))
        assertFalse(styleTypes.contains("dsgl-system-inspector-command-bridge"))
    }

    @Test
    fun `expanded inspector paints occluder above full highlight geometry`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val initialRoot = inspectedRoot()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(
            initialRoot,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(984, 144, MouseButton.LEFT))

        val movedRoot = inspectedRootMovedUnderPanel()
        host.syncFrame(
            movedRoot,
            inspectedLayoutRevision = 2L,
            cursorX = 84,
            cursorY = 96,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 1280, 720)

        val panelRect = inspector.overlayPanelRect() ?: error("panel rect missing")
        val highlight = inspector.overlaySelectedHighlight() ?: error("selected highlight missing")
        assertTrue(intersects(highlight.contentRect, panelRect))

        val inspectorNode = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("inspector node missing")
        val directChildren = inspectorNode.children.toList()

        val occluder = directChildren.firstOrNull { it.key == "dsgl-system-inspector-panel-occluder" }
            ?: error("occluder node missing")
        val selectedContentFill = directChildren.firstOrNull { it.key == "dsgl-system-inspector-selected-content-fill" }
            ?: error("selected content fill node missing")

        assertEquals(panelRect, occluder.bounds)
        assertEquals(highlight.contentRect, selectedContentFill.bounds)
        assertTrue(directChildren.indexOf(occluder) > directChildren.indexOf(selectedContentFill))
        assertTrue(directChildren.none { (it.key?.toString() ?: "").contains("outside-occlusion") })
    }

    @Test
    fun `inspector runtime interaction path supports selection controls and system-owned color edit`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRoot()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 1280, 720)

        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertEquals("target", inspector.selectedKey)

        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 80, cursorY = 52, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)

        val pickToggle = inspector.overlayPickToggleBounds() ?: error("pick toggle missing")
        assertTrue(host.handleMouseDown(pickToggle.x + 1, pickToggle.y + 1, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(pickToggle.x + 1, pickToggle.y + 1, MouseButton.LEFT))
        assertEquals(InspectorMode.Pick, inspector.mode)

        val colorAction = inspector.overlayColorPickerActionBounds(StyleProperty.BACKGROUND_COLOR)
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

        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = colorAnchor.x + 1,
            cursorY = colorAnchor.y + 1,
            inspectorPointerCaptured = false
        )
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
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false
        )

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
        val minimizeRect = inspector.overlayMinimizeBounds() ?: error("minimize bounds missing")
        assertTrue(host.handleMouseDown(minimizeRect.x + 1, minimizeRect.y + 1, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(minimizeRect.x + 1, minimizeRect.y + 1, MouseButton.LEFT))
        assertEquals(InspectorPanelState.Minimized, inspector.panelState)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 40, cursorY = 30, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)

        val (chipX, chipY) = inspector.panelPosition
        assertTrue(host.handleMouseDown(chipX + 2, chipY + 2, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(chipX + 2, chipY + 2, MouseButton.LEFT))
        assertEquals(InspectorPanelState.Expanded, inspector.panelState)
        assertFalse(inspector.isPointerCaptured)

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
    fun `minimized inspector hides expanded panel host and keeps chip visible`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        val root = inspectedRoot()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 40, cursorY = 30, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)

        val minimizeRect = inspector.overlayMinimizeBounds() ?: error("minimize bounds missing")
        assertTrue(host.handleMouseDown(minimizeRect.x + 2, minimizeRect.y + 2, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(minimizeRect.x + 2, minimizeRect.y + 2, MouseButton.LEFT))
        assertEquals(InspectorPanelState.Minimized, inspector.panelState)

        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 48, cursorY = 36, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)

        val inspectorNode = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("inspector node missing")
        val panelHostNode = collectNodes(inspectorNode).firstOrNull { node ->
            (node.key?.toString() ?: "").startsWith("dsgl-overlay-panel-")
        } ?: error("panel host node missing")
        val minimizedChipNode = collectNodes(inspectorNode).firstOrNull { it.key == "dsgl-system-inspector-chip" }
            ?: error("minimized chip node missing")

        assertEquals(0, panelHostNode.bounds.width)
        assertEquals(0, panelHostNode.bounds.height)
        assertTrue(minimizedChipNode.bounds.width > 0)
        assertTrue(minimizedChipNode.bounds.height > 0)
    }

    @Test
    fun `declarative shell preserves migrated control and chip keys`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        val root = inspectedRoot()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)

        val expandedNode = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("inspector node missing")
        val expandedKeys = collectNodes(expandedNode).mapNotNull { it.key?.toString() }.toSet()
        assertTrue(expandedKeys.contains("dsgl-system-inspector-pick-toggle"))
        assertTrue(expandedKeys.contains("dsgl-system-inspector-minimize"))

        val minimizeRect = inspector.overlayMinimizeBounds() ?: error("minimize bounds missing")
        assertTrue(host.handleMouseDown(minimizeRect.x + 2, minimizeRect.y + 2, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(minimizeRect.x + 2, minimizeRect.y + 2, MouseButton.LEFT))
        assertEquals(InspectorPanelState.Minimized, inspector.panelState)

        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 48, cursorY = 36, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)

        val minimizedNode = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("inspector node missing")
        val minimizedKeys = collectNodes(minimizedNode).mapNotNull { it.key?.toString() }.toSet()
        assertTrue(minimizedKeys.contains("dsgl-system-inspector-chip"))
        assertTrue(minimizedKeys.any { it.startsWith("dsgl-system-inspector-chip-line-") })
    }

    @Test
    fun `minimized drag moves chip and releases pointer capture on mouse up`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        val root = inspectedRoot()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 40, cursorY = 30, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)

        val minimizeRect = inspector.overlayMinimizeBounds() ?: error("minimize bounds missing")
        assertTrue(host.handleMouseDown(minimizeRect.x + 2, minimizeRect.y + 2, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(minimizeRect.x + 2, minimizeRect.y + 2, MouseButton.LEFT))
        assertEquals(InspectorPanelState.Minimized, inspector.panelState)

        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 40, cursorY = 30, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)

        val (startX, startY) = inspector.panelPosition
        val downX = startX + 6
        val downY = startY + 6
        val dragX = downX + 40
        val dragY = downY + 20

        assertTrue(host.handleMouseDown(downX, downY, MouseButton.LEFT))
        assertTrue(host.handleMouseMove(dragX, dragY))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = dragX,
            cursorY = dragY,
            inspectorPointerCaptured = inspector.isPointerCaptured
        )
        host.render(ctx, 1280, 720)

        assertEquals(InspectorPanelState.Minimized, inspector.panelState)
        val (movedX, movedY) = inspector.panelPosition
        assertTrue(movedX != startX || movedY != startY)

        assertTrue(host.handleMouseUp(dragX, dragY, MouseButton.LEFT))
        assertFalse(inspector.isPointerCaptured)
        assertEquals(InspectorPanelState.Minimized, inspector.panelState)
    }

    @Test
    fun `inspector native path preserves scroll and scrollbar drag behavior`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRootWithManyChildren()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)

        val contentRect = inspector.overlayContentRect()
        val wheelX = contentRect.x + 4
        val wheelY = contentRect.y + 12
        assertTrue(host.handleMouseWheel(wheelX, wheelY, -120))

        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = wheelX,
            cursorY = wheelY,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 420, 280)
        host.paint(ctx)
        val afterWheel = inspector.panelScrollOffsetY
        assertTrue(afterWheel > 0, "expected wheel scroll > 0, actual=$afterWheel")

        val thumb = inspector.overlayScrollbarThumbRect()
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
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertEquals("target", inspector.selectedKey)

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)

        val thumb = inspector.overlayScrollbarThumbRect()
        assertTrue(thumb.width > 0 && thumb.height > 0)
        val pickToggle = inspector.overlayPickToggleBounds() ?: error("pick toggle missing")
        val modeBeforeRelease = inspector.mode

        val thumbX = thumb.x + thumb.width / 2
        val thumbY = thumb.y + thumb.height / 2
        assertTrue(host.handleMouseDown(thumbX, thumbY, MouseButton.LEFT))

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
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertEquals("target", inspector.selectedKey)

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)

        val thumb = inspector.overlayScrollbarThumbRect()
        assertTrue(thumb.width > 0 && thumb.height > 0)
        val panelRect = inspector.overlayPanelRect() ?: error("panel rect missing")
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
            host.syncFrame(
                root,
                inspectedLayoutRevision = 1L,
                cursorX = 984,
                cursorY = 144,
                inspectorPointerCaptured = false
            )
            host.render(ctx, 1280, 720)
            assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))

            host.syncFrame(
                root,
                inspectedLayoutRevision = 2L,
                cursorX = 80,
                cursorY = 52,
                inspectorPointerCaptured = false
            )
            host.render(ctx, 1280, 720)
            val colorAction = inspector.overlayColorPickerActionBounds(StyleProperty.BACKGROUND_COLOR)
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
            host.syncFrame(
                root,
                inspectedLayoutRevision = 3L,
                cursorX = colorAnchor.x + 1,
                cursorY = colorAnchor.y + 1,
                inspectorPointerCaptured = false
            )

            assertTrue(host.isSystemColorPickerOpen())
            assertEquals(OverlayOwnerScope.System, host.debugSystemColorPickerPopupOwnerScope())
            assertTrue(ColorPickerRuntime.engine.isOpenFor(appOwner))

            host.systemInspectorColorPickerPopupHost().close()
            host.syncFrame(
                root,
                inspectedLayoutRevision = 4L,
                cursorX = colorAnchor.x + 1,
                cursorY = colorAnchor.y + 1,
                inspectorPointerCaptured = false
            )
            assertFalse(host.isSystemColorPickerOpen())
            assertTrue(ColorPickerRuntime.engine.isOpenFor(appOwner))
        } finally {
            host.systemInspectorColorPickerPopupHost().close()
            ColorPickerRuntime.engine.close(appOwner)
        }
    }

    @Test
    fun `inspector-opened system color picker top controls expose hover feedback`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRoot()

        fun sync(revision: Long, cursorX: Int, cursorY: Int) {
            host.syncFrame(
                root,
                inspectedLayoutRevision = revision,
                cursorX = cursorX,
                cursorY = cursorY,
                inspectorPointerCaptured = false
            )
            host.render(ctx, 1280, 720)
        }

        inspector.toggle()
        host.onInputFrame(1280, 720)
        sync(revision = 1L, cursorX = 984, cursorY = 144)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertEquals("target", inspector.selectedKey)

        sync(revision = 2L, cursorX = 80, cursorY = 52)
        val colorAction = inspector.overlayColorPickerActionBounds(StyleProperty.BACKGROUND_COLOR)
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
        sync(revision = 3L, cursorX = colorAnchor.x + 1, cursorY = colorAnchor.y + 1)
        assertTrue(host.isSystemColorPickerOpen())
        assertEquals(OverlayOwnerScope.System, host.debugSystemColorPickerPopupOwnerScope())

        val layout = host.debugSystemColorPickerBodyLayout() ?: error("color picker body layout missing")
        val style = ColorPickerStyle()
        val hoverTargets = listOf(
            "dsgl-system-color-picker-mode-select" to layout.modeSelectRect,
            "dsgl-system-color-picker-order-argb" to (layout.argbOrderRect ?: error("argb order rect missing")),
            "dsgl-system-color-picker-button-copy" to layout.copyRect,
            "dsgl-system-color-picker-button-paste" to layout.pasteRect,
            "dsgl-system-color-picker-button-pipette" to layout.pipetteRect
        )

        var revision = 4L
        hoverTargets.forEach { (key, rect) ->
            val hoverX = rect.x + rect.width / 2
            val hoverY = rect.y + rect.height / 2
            assertTrue(host.handleMouseMove(hoverX, hoverY), "expected hover move to be consumed for $key")
            sync(revision = revision++, cursorX = hoverX, cursorY = hoverY)

            val pickerNode = host.debugEntryNode(SystemOverlayEntryId.ColorPickerPopup)
                ?: error("color picker entry missing")
            val buttonNode = collectNodes(pickerNode)
                .firstOrNull { it.key?.toString() == key } as? ButtonNode
                ?: error("button node missing for $key")
            assertEquals(style.buttonHoverColor, buttonNode.backgroundColor, "expected hover color for $key")
        }
    }

    @Test
    fun `inspector-opened system color picker mode dropdown options hover and click reliably`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRoot()

        fun sync(revision: Long, cursorX: Int, cursorY: Int) {
            host.syncFrame(
                root,
                inspectedLayoutRevision = revision,
                cursorX = cursorX,
                cursorY = cursorY,
                inspectorPointerCaptured = false
            )
            host.render(ctx, 1280, 720)
        }

        inspector.toggle()
        host.onInputFrame(1280, 720)
        sync(revision = 1L, cursorX = 984, cursorY = 144)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertEquals("target", inspector.selectedKey)
        val modeBeforeDropdown = inspector.mode

        sync(revision = 2L, cursorX = 80, cursorY = 52)
        val colorAction = inspector.overlayColorPickerActionBounds(StyleProperty.BACKGROUND_COLOR)
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
        sync(revision = 3L, cursorX = colorAnchor.x + 1, cursorY = colorAnchor.y + 1)
        assertTrue(host.isSystemColorPickerOpen())
        assertEquals(OverlayOwnerScope.System, host.debugSystemColorPickerPopupOwnerScope())

        val initialLayout = host.debugSystemColorPickerBodyLayout() ?: error("color picker body layout missing")
        assertTrue(host.handleMouseDown(initialLayout.modeSelectRect.x + 2, initialLayout.modeSelectRect.y + 2, MouseButton.LEFT))
        sync(
            revision = 4L,
            cursorX = initialLayout.modeSelectRect.x + 2,
            cursorY = initialLayout.modeSelectRect.y + 2
        )
        assertTrue(host.debugMountedEntryIds().contains(SystemOverlayEntryId.ColorPickerTransient))

        val expandedLayout = host.debugSystemColorPickerBodyLayout() ?: error("expanded color picker layout missing")
        val hslOption = expandedLayout.modeOptions.firstOrNull { it.mode == ColorFormatMode.HSL }
            ?: error("HSL mode option missing")
        val optionHoverX = hslOption.rect.x + hslOption.rect.width / 2
        val optionHoverY = hslOption.rect.y + hslOption.rect.height / 2
        assertTrue(host.handleMouseMove(optionHoverX, optionHoverY))
        sync(revision = 5L, cursorX = optionHoverX, cursorY = optionHoverY)

        val style = ColorPickerStyle()
        val transientNode = host.debugEntryNode(SystemOverlayEntryId.ColorPickerTransient)
            ?: error("transient entry missing")
        val optionNode = collectNodes(transientNode)
            .firstOrNull { it.key?.toString() == "dsgl-system-color-picker-mode-option-hsl" } as? ButtonNode
            ?: error("HSL option node missing")
        assertEquals(style.buttonHoverColor, optionNode.backgroundColor)

        assertTrue(host.handleMouseDown(optionHoverX, optionHoverY, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(optionHoverX, optionHoverY, MouseButton.LEFT))
        sync(revision = 6L, cursorX = optionHoverX, cursorY = optionHoverY)

        assertEquals(ColorFormatMode.HSL, host.debugSystemColorPickerState()?.mode)
        assertFalse(host.debugMountedEntryIds().contains(SystemOverlayEntryId.ColorPickerTransient))
        assertTrue(host.isSystemColorPickerOpen())
        assertEquals("target", inspector.selectedKey)
        assertEquals(modeBeforeDropdown, inspector.mode)
    }

    @Test
    fun `inspector native body content remains clipped in narrow viewport`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRootWithManyChildren()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))

        host.onInputFrame(320, 220)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 320, 220)

        val bodyRect = inspector.overlayContentRect()
        val inspectorNode = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("inspector node missing")
        val bodyNode = collectNodes(inspectorNode)
            .firstOrNull { it.key?.toString() == "dsgl-system-inspector-body" }
            ?: error("inspector body node missing")
        assertEquals(org.dreamfinity.dsgl.core.style.Overflow.Hidden, bodyNode.overflowX)
        assertEquals(org.dreamfinity.dsgl.core.style.Overflow.Auto, bodyNode.overflowY)
        val bodyViewport = bodyNode.overflowViewportRect() ?: bodyRect

        val initialCommands = host.paint(ctx)
        assertTrue(initialCommands.any { command ->
            command is RenderCommand.PushClip &&
                    command.x == bodyViewport.x &&
                    command.y == bodyViewport.y &&
                    command.width == bodyViewport.width &&
                    command.height == bodyViewport.height
        })

        assertTrue(host.handleMouseWheel(bodyRect.x + 4, bodyRect.y + 12, -120))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = bodyRect.x + 4,
            cursorY = bodyRect.y + 12,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 320, 220)

        val bodyLines = collectNodes(inspectorNode).filter { node ->
            if (node.display == Display.None) return@filter false
            val key = node.key?.toString() ?: return@filter false
            key.startsWith("dsgl-system-inspector-info-line-") ||
                    key.startsWith("dsgl-system-inspector-style-line-")
        }

        assertTrue(bodyLines.isNotEmpty())
        assertTrue(bodyLines.any { node ->
            node.bounds.y < bodyRect.y || node.bounds.y + node.bounds.height > bodyRect.y + bodyRect.height
        })

        val edgeIntersecting = bodyLines.filter { node ->
            intersects(node.bounds, bodyRect) && !containsFully(bodyRect, node.bounds)
        }
        assertTrue(edgeIntersecting.isNotEmpty())
        assertTrue(edgeIntersecting.all { it.bounds.height >= 24 })

        val scrolledCommands = host.paint(ctx)
        assertTrue(scrolledCommands.any { command ->
            command is RenderCommand.PushClip &&
                    command.x == bodyViewport.x &&
                    command.y == bodyViewport.y &&
                    command.width == bodyViewport.width &&
                    command.height == bodyViewport.height
        })
    }

    @Test
    fun `inspector clipped body blocks hidden row input and accepts visible portion`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRootWithManyChildren()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertEquals("target", inspector.selectedKey)

        host.onInputFrame(320, 213)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 320, 213)

        val bodyRect = inspector.overlayContentRect()
        val wheelX = bodyRect.x + 4
        val wheelY = bodyRect.y + 12

        var revision = 3L
        var edgeNode: DOMNode? = null
        var hiddenNode: DOMNode? = null
        var visibleNode: DOMNode? = null
        var latestInteractiveNodes: List<DOMNode> = emptyList()
        repeat(24) {
            val inspectorNode = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("inspector node missing")
            val interactiveNodes = collectNodes(inspectorNode).filter { node ->
                if (node.display == Display.None) return@filter false
                val key = node.key?.toString() ?: return@filter false
                isInteractiveInspectorControlKey(key)
            }
            latestInteractiveNodes = interactiveNodes
            edgeNode = interactiveNodes.firstOrNull { node ->
                intersects(node.bounds, bodyRect) && !containsFully(
                    bodyRect,
                    node.bounds
                )
            }
            hiddenNode = interactiveNodes.firstOrNull { node -> !intersects(node.bounds, bodyRect) }
            visibleNode = interactiveNodes.firstOrNull { node -> containsFully(bodyRect, node.bounds) }
            if (edgeNode != null && visibleNode != null) return@repeat
            assertTrue(host.handleMouseWheel(wheelX, wheelY, -120))
            host.syncFrame(
                root,
                inspectedLayoutRevision = revision,
                cursorX = wheelX,
                cursorY = wheelY,
                inspectorPointerCaptured = false
            )
            host.render(ctx, 320, 213)
            revision += 1L
        }

        val hiddenTarget =
            edgeNode ?: hiddenNode ?: latestInteractiveNodes.firstOrNull { node -> !intersects(node.bounds, bodyRect) }
            ?: error("failed to find hidden interactive inspector control")
        val visibleTarget =
            visibleNode ?: latestInteractiveNodes.firstOrNull { node -> intersects(node.bounds, bodyRect) } ?: edgeNode

        val hiddenX = if (edgeNode != null) {
            maxOf(hiddenTarget.bounds.x, bodyRect.x) + 2
        } else {
            hiddenTarget.bounds.x + (hiddenTarget.bounds.width / 2).coerceAtLeast(1)
        }
        val hiddenY = if (edgeNode != null) {
            if (hiddenTarget.bounds.y < bodyRect.y) {
                hiddenTarget.bounds.y + 1
            } else {
                hiddenTarget.bounds.y + hiddenTarget.bounds.height - 1
            }
        } else {
            hiddenTarget.bounds.y + (hiddenTarget.bounds.height / 2).coerceAtLeast(1)
        }

        assertFalse(bodyRect.contains(hiddenX, hiddenY))
        assertTrue(hiddenTarget.bounds.contains(hiddenX, hiddenY))

        assertFalse(host.handleMouseDown(hiddenX, hiddenY, MouseButton.LEFT))
        host.handleMouseUp(hiddenX, hiddenY, MouseButton.LEFT)
        assertEquals("target", inspector.selectedKey)

        if (visibleTarget == null) return

        val visibleX = maxOf(visibleTarget.bounds.x, bodyRect.x) + 2
        val visibleY = maxOf(visibleTarget.bounds.y, bodyRect.y) + 1
        assertTrue(bodyRect.contains(visibleX, visibleY))
        assertTrue(visibleTarget.bounds.contains(visibleX, visibleY))

        assertTrue(host.handleMouseDown(visibleX, visibleY, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(visibleX, visibleY, MouseButton.LEFT))
    }

    @Test
    fun `inspector body consumes generic scroll viewport and content state`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRootWithManyChildren()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)

        val inspectorNode = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("inspector node missing")
        val bodyNode = collectNodes(inspectorNode).firstOrNull { it.key == "dsgl-system-inspector-body" }
            ?: error("inspector body node missing")
        val scrollState = bodyNode.scrollContainerState()

        assertTrue(scrollState.axisY.scrollContainer)
        assertTrue(scrollState.axisY.clipsToViewport)
        assertTrue(scrollState.viewportRect.width > 0 && scrollState.viewportRect.height > 0)
        assertTrue(scrollState.contentExtent.height >= scrollState.viewportRect.height)
        assertTrue(!scrollState.axisX.scrollbarPresent)
        assertEquals(0, scrollState.horizontalScrollbarGutter)
        if (scrollState.axisY.scrollbarPresent) {
            assertTrue(scrollState.verticalScrollbarGutter > 0)
            assertTrue(scrollState.viewportRect.width < scrollState.baseViewportRect.width)
        } else {
            assertEquals(0, scrollState.verticalScrollbarGutter)
            assertEquals(scrollState.baseViewportRect.width, scrollState.viewportRect.width)
        }
        assertEquals(scrollState.baseViewportRect.height, scrollState.viewportRect.height)
        assertEquals(scrollState.viewportRect, bodyNode.overflowViewportRect())
    }

    @Test
    fun `inspector wheel scrolling works when hovering interactive input`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRootWithManyChildren()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertEquals("target", inspector.selectedKey)

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)

        val inspectorNode = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("inspector node missing")
        val bodyRect = inspector.overlayContentRect()
        val allNodes = collectNodes(inspectorNode)
        val interactiveNode = allNodes.firstOrNull { node ->
            val key = node.key?.toString() ?: return@firstOrNull false
            val interactiveControl = key.startsWith("dsgl-system-inspector-editor-input-") ||
                    key.startsWith("dsgl-system-inspector-editor-numeric-input-") ||
                    key.startsWith("dsgl-system-inspector-editor-select-") ||
                    key.startsWith("dsgl-system-inspector-editor-color-preview-")
            if (!interactiveControl) return@firstOrNull false
            val probeX = node.bounds.x + 2
            val probeY = node.bounds.y + (node.bounds.height / 2).coerceAtLeast(1)
            bodyRect.contains(probeX, probeY)
        }
        val wheelNode = interactiveNode ?: allNodes.firstOrNull { node ->
            val probeX = node.bounds.x + 2
            val probeY = node.bounds.y + (node.bounds.height / 2).coerceAtLeast(1)
            bodyRect.contains(probeX, probeY)
        } ?: error("visible inspector body content node missing")

        val wheelX = wheelNode.bounds.x + 2
        val wheelY = wheelNode.bounds.y + (wheelNode.bounds.height / 2).coerceAtLeast(1)

        val before = inspector.panelScrollOffsetY
        assertTrue(host.handleMouseWheel(wheelX, wheelY, -120))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = wheelX,
            cursorY = wheelY,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 420, 280)
        host.paint(ctx)
        assertTrue(inspector.panelScrollOffsetY > before)
    }

    @Test
    fun `inspector shift wheel does not consume vertical wheel path`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRootWithManyChildren()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)

        val bodyRect = inspector.overlayContentRect()
        val wheelX = bodyRect.x + 4
        val wheelY = bodyRect.y + 12
        val before = inspector.panelScrollOffsetY

        KeyModifiers.sync(shift = true, control = false, meta = false)
        host.handleMouseWheel(wheelX, wheelY, -120)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = wheelX,
            cursorY = wheelY,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 420, 280)
        host.paint(ctx)
        assertEquals(before, inspector.panelScrollOffsetY)
        KeyModifiers.sync(shift = false, control = false, meta = false)
    }

    @Test
    fun `inspector wheel scrolling remains symmetric across rebuilds`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRootWithManyChildren()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 1280, 720)
        host.paint(ctx)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)
        host.paint(ctx)

        val contentRect = inspector.overlayContentRect()
        val wheelX = contentRect.x + 4
        val wheelY = contentRect.y + 12

        repeat(4) { step ->
            assertTrue(host.handleMouseWheel(wheelX, wheelY, -120))
            host.syncFrame(
                root,
                inspectedLayoutRevision = 3L + step,
                cursorX = wheelX,
                cursorY = wheelY,
                inspectorPointerCaptured = false
            )
            host.render(ctx, 420, 280)
            host.paint(ctx)
        }
        repeat(16) { settle ->
            host.syncFrame(
                root,
                inspectedLayoutRevision = 20L + settle,
                cursorX = wheelX,
                cursorY = wheelY,
                inspectorPointerCaptured = false
            )
            host.render(ctx, 420, 280)
            host.paint(ctx)
        }
        val scrolledDown = inspector.panelScrollOffsetY
        assertTrue(scrolledDown > 0, "expected downward wheel to increase scroll: down=$scrolledDown")

        var consumedUpWheel = false
        repeat(8) { step ->
            val consumed = host.handleMouseWheel(wheelX, wheelY, 120)
            consumedUpWheel = consumedUpWheel || consumed
            host.syncFrame(
                root,
                inspectedLayoutRevision = 40L + step,
                cursorX = wheelX,
                cursorY = wheelY,
                inspectorPointerCaptured = false
            )
            host.render(ctx, 420, 280)
            host.paint(ctx)
        }
        assertTrue(consumedUpWheel, "expected at least one upward wheel step to be consumed")
        var scrolledUp = inspector.panelScrollOffsetY
        repeat(24) { settle ->
            if (scrolledUp < scrolledDown) return@repeat
            host.syncFrame(
                root,
                inspectedLayoutRevision = 60L + settle,
                cursorX = wheelX,
                cursorY = wheelY,
                inspectorPointerCaptured = false
            )
            host.render(ctx, 420, 280)
            host.paint(ctx)
            scrolledUp = inspector.panelScrollOffsetY
        }
        assertTrue(
            scrolledUp < scrolledDown,
            "expected upward wheel to reduce scroll: down=$scrolledDown up=$scrolledUp"
        )
    }

    @Test
    fun `inspector thumb drag remains active across rebuild without controller pointer capture`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRootWithManyChildren()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 1280, 720)
        host.paint(ctx)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)
        host.paint(ctx)

        val thumb = inspector.overlayScrollbarThumbRect()
        assertTrue(thumb.width > 0 && thumb.height > 0)
        val dragX = thumb.x + thumb.width / 2
        val dragStartY = thumb.y + thumb.height / 2

        assertTrue(host.handleMouseDown(dragX, dragStartY, MouseButton.LEFT))
        assertFalse(inspector.isPointerCaptured)
        val beforeDrag = inspector.panelScrollOffsetY

        assertTrue(host.handleMouseMove(dragX, dragStartY + 18))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = dragX,
            cursorY = dragStartY + 18,
            inspectorPointerCaptured = inspector.isPointerCaptured
        )
        host.render(ctx, 420, 280)
        host.paint(ctx)
        val afterFirstMove = inspector.panelScrollOffsetY
        assertTrue(afterFirstMove > beforeDrag)

        assertTrue(host.handleMouseMove(dragX, dragStartY + 42))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 4L,
            cursorX = dragX,
            cursorY = dragStartY + 42,
            inspectorPointerCaptured = inspector.isPointerCaptured
        )
        host.render(ctx, 420, 280)
        host.paint(ctx)
        val afterSecondMove = inspector.panelScrollOffsetY
        assertTrue(afterSecondMove > afterFirstMove)

        assertTrue(host.handleMouseUp(dragX, dragStartY + 42, MouseButton.LEFT))
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
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false
        )
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
        StyleEngine.setInspectorOverrideLiteral(root.children.first(), StyleProperty.BACKGROUND_COLOR, "#FF112233")
            .getOrThrow()
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

    private fun inspectedRootMovedUnderPanel(): ContainerNode {
        val root = ContainerNode(key = "root")
        root.bounds = Rect(0, 0, 1280, 720)
        val selected = ContainerNode(key = "target").apply {
            bounds = Rect(72, 84, 180, 80)
        }.applyParent(root)
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

    private fun intersects(a: Rect, b: Rect): Boolean {
        return a.x < b.x + b.width &&
                a.x + a.width > b.x &&
                a.y < b.y + b.height &&
                a.y + a.height > b.y
    }

    private fun containsFully(outer: Rect, inner: Rect): Boolean {
        return inner.x >= outer.x &&
                inner.y >= outer.y &&
                inner.x + inner.width <= outer.x + outer.width &&
                inner.y + inner.height <= outer.y + outer.height
    }

    private fun isInteractiveInspectorControlKey(key: String): Boolean {
        return key == "dsgl-system-inspector-parent-row" ||
                key.startsWith("dsgl-system-inspector-child-row-") ||
                key.startsWith("dsgl-system-inspector-editor-reset-") ||
                key.startsWith("dsgl-system-inspector-editor-select-") ||
                key.startsWith("dsgl-system-inspector-editor-dec-") ||
                key.startsWith("dsgl-system-inspector-editor-inc-") ||
                key.startsWith("dsgl-system-inspector-editor-unit-") ||
                key.startsWith("dsgl-system-inspector-editor-color-preview-") ||
                key == "dsgl-system-inspector-reset-node" ||
                key == "dsgl-system-inspector-clear-all"
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

    @Test
    fun `inspector consumer scroll reacts on frame update without viewport resize`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRootWithManyChildren()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 1280, 720)
        host.paint(ctx)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)
        host.paint(ctx)

        val contentRect = inspector.overlayContentRect()
        val wheelX = contentRect.x + 4
        val wheelY = contentRect.y + 14
        val before = inspector.panelScrollOffsetY

        assertTrue(host.handleMouseWheel(wheelX, wheelY, -120))
        host.syncFrame(
            root,
            inspectedLayoutRevision = 3L,
            cursorX = wheelX,
            cursorY = wheelY,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 420, 280)
        host.paint(ctx)

        assertTrue(inspector.panelScrollOffsetY > before)
    }

    @Test
    fun `inspector consumer thumb drag remains smooth and stable on release`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRootWithManyChildren()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 1280, 720)
        host.paint(ctx)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)
        host.paint(ctx)

        val thumb = inspector.overlayScrollbarThumbRect()
        assertTrue(thumb.width > 0 && thumb.height > 0)
        val dragX = thumb.x + thumb.width / 2
        val startY = thumb.y + thumb.height / 2

        assertTrue(host.handleMouseDown(dragX, startY, MouseButton.LEFT))
        var previousScroll = inspector.panelScrollOffsetY
        var previousThumbY = inspector.overlayScrollbarThumbRect().y

        repeat(6) { step ->
            val nextY = startY + (step + 1) * 9
            assertTrue(host.handleMouseMove(dragX, nextY))
            host.syncFrame(
                root,
                inspectedLayoutRevision = 3L + step,
                cursorX = dragX,
                cursorY = nextY,
                inspectorPointerCaptured = inspector.isPointerCaptured
            )
            host.render(ctx, 420, 280)
            host.paint(ctx)
            val currentScroll = inspector.panelScrollOffsetY
            val currentThumbY = inspector.overlayScrollbarThumbRect().y
            assertTrue(
                currentScroll >= previousScroll,
                "scroll regressed: prev=$previousScroll current=$currentScroll step=$step"
            )
            assertTrue(
                currentThumbY >= previousThumbY,
                "thumb regressed: prev=$previousThumbY current=$currentThumbY step=$step"
            )
            previousScroll = currentScroll
            previousThumbY = currentThumbY
        }

        assertTrue(host.handleMouseUp(dragX, startY + 6 * 9, MouseButton.LEFT))
        val settledScroll = inspector.panelScrollOffsetY
        val settledThumbY = inspector.overlayScrollbarThumbRect().y

        repeat(6) { idx ->
            host.syncFrame(
                root,
                inspectedLayoutRevision = 20L + idx,
                cursorX = dragX,
                cursorY = startY,
                inspectorPointerCaptured = inspector.isPointerCaptured
            )
            host.render(ctx, 420, 280)
            host.paint(ctx)
            assertEquals(settledScroll, inspector.panelScrollOffsetY)
            assertEquals(settledThumbY, inspector.overlayScrollbarThumbRect().y)
        }
    }

    @Test
    fun `inspector consumer fast thumb drag to boundary stays stable`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRootWithManyChildren()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false
        )
        host.render(ctx, 1280, 720)
        host.paint(ctx)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)
        host.paint(ctx)

        val thumb = inspector.overlayScrollbarThumbRect()
        assertTrue(thumb.width > 0 && thumb.height > 0)
        val dragX = thumb.x + thumb.width / 2
        val startY = thumb.y + thumb.height / 2

        assertTrue(host.handleMouseDown(dragX, startY, MouseButton.LEFT))
        var previousScroll = inspector.panelScrollOffsetY
        var previousThumbY = inspector.overlayScrollbarThumbRect().y
        repeat(7) { step ->
            val nextY = startY + (step + 1) * 120
            assertTrue(host.handleMouseMove(dragX, nextY))
            host.syncFrame(
                root,
                inspectedLayoutRevision = 20L + step,
                cursorX = dragX,
                cursorY = nextY,
                inspectorPointerCaptured = inspector.isPointerCaptured
            )
            host.render(ctx, 420, 280)
            host.paint(ctx)
            val currentScroll = inspector.panelScrollOffsetY
            val currentThumbY = inspector.overlayScrollbarThumbRect().y
            assertTrue(
                currentScroll >= previousScroll,
                "scroll regressed: prev=$previousScroll current=$currentScroll step=$step"
            )
            assertTrue(
                currentThumbY >= previousThumbY,
                "thumb regressed: prev=$previousThumbY current=$currentThumbY step=$step"
            )
            previousScroll = currentScroll
            previousThumbY = currentThumbY
        }

        val settledScroll = inspector.panelScrollOffsetY
        val settledThumbY = inspector.overlayScrollbarThumbRect().y
        repeat(8) { idx ->
            val boundaryY = startY + 2000
            assertTrue(host.handleMouseMove(dragX, boundaryY))
            host.syncFrame(
                root,
                inspectedLayoutRevision = 40L + idx,
                cursorX = dragX,
                cursorY = boundaryY,
                inspectorPointerCaptured = inspector.isPointerCaptured
            )
            host.render(ctx, 420, 280)
            host.paint(ctx)
            assertEquals(settledScroll, inspector.panelScrollOffsetY)
            assertEquals(settledThumbY, inspector.overlayScrollbarThumbRect().y)
        }

        assertTrue(host.handleMouseUp(dragX, startY + 2000, MouseButton.LEFT))
    }
}




