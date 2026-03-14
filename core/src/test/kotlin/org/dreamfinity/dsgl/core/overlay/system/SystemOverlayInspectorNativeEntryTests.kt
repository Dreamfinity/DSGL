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
import org.dreamfinity.dsgl.core.event.KeyModifiers
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
        val bodyNode = collectNodes(inspectorNode)
            .firstOrNull { it.key?.toString() == "dsgl-system-inspector-body" }
            ?: error("inspector body node missing")
        assertEquals(org.dreamfinity.dsgl.core.style.Overflow.Hidden, bodyNode.overflow)

        val initialCommands = host.paint(ctx)
        assertTrue(initialCommands.any { command ->
            command is RenderCommand.PushClip &&
                command.x == bodyRect.x &&
                command.y == bodyRect.y &&
                command.width == bodyRect.width &&
                command.height == bodyRect.height
        })

        assertTrue(host.handleMouseWheel(bodyRect.x + 4, bodyRect.y + 12, -120))
        host.syncFrame(root, inspectedLayoutRevision = 3L, cursorX = bodyRect.x + 4, cursorY = bodyRect.y + 12, inspectorPointerCaptured = false)
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
                command.x == bodyRect.x &&
                command.y == bodyRect.y &&
                command.width == bodyRect.width &&
                command.height == bodyRect.height
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
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertEquals("target", inspector.selectedKey)

        host.onInputFrame(320, 213)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 320, 213)

        val bodyRect = inspector.debugContentRect()
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
            edgeNode = interactiveNodes.firstOrNull { node -> intersects(node.bounds, bodyRect) && !containsFully(bodyRect, node.bounds) }
            hiddenNode = interactiveNodes.firstOrNull { node -> !intersects(node.bounds, bodyRect) }
            visibleNode = interactiveNodes.firstOrNull { node -> containsFully(bodyRect, node.bounds) }
            if (edgeNode != null && visibleNode != null) return@repeat
            assertTrue(host.handleMouseWheel(wheelX, wheelY, -120))
            host.syncFrame(root, inspectedLayoutRevision = revision, cursorX = wheelX, cursorY = wheelY, inspectorPointerCaptured = false)
            host.render(ctx, 320, 213)
            revision += 1L
        }

        val hiddenTarget = edgeNode ?: hiddenNode ?: latestInteractiveNodes.firstOrNull { node -> !intersects(node.bounds, bodyRect) } ?: error("failed to find hidden interactive inspector control")
        val visibleTarget = visibleNode ?: latestInteractiveNodes.firstOrNull { node -> intersects(node.bounds, bodyRect) } ?: edgeNode

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
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
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
        assertTrue(!scrollState.axisY.scrollbarPresent)
        assertEquals(0, scrollState.horizontalScrollbarGutter)
        assertEquals(0, scrollState.verticalScrollbarGutter)
        assertEquals(scrollState.baseViewportRect, scrollState.viewportRect)
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
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertEquals("target", inspector.selectedKey)

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)

        val inspectorNode = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("inspector node missing")
        val bodyRect = inspector.debugContentRect()
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
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)

        val bodyRect = inspector.debugContentRect()
        val wheelX = bodyRect.x + 4
        val wheelY = bodyRect.y + 12
        val before = inspector.panelScrollOffsetY

        KeyModifiers.sync(shift = true, control = false, meta = false)
        assertFalse(host.handleMouseWheel(wheelX, wheelY, -120))
        assertEquals(before, inspector.panelScrollOffsetY)
        KeyModifiers.sync(shift = false, control = false, meta = false)
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
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        host.paint(ctx)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)
        host.paint(ctx)

        val contentRect = inspector.debugContentRect()
        val wheelX = contentRect.x + 4
        val wheelY = contentRect.y + 14
        val before = inspector.panelScrollOffsetY

        assertTrue(host.handleMouseWheel(wheelX, wheelY, -120))
        host.syncFrame(root, inspectedLayoutRevision = 3L, cursorX = wheelX, cursorY = wheelY, inspectorPointerCaptured = false)
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
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        host.paint(ctx)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))

        host.onInputFrame(420, 280)
        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 90, cursorY = 90, inspectorPointerCaptured = false)
        host.render(ctx, 420, 280)
        host.paint(ctx)

        val thumb = inspector.debugScrollbarThumbRect()
        assertTrue(thumb.width > 0 && thumb.height > 0)
        val dragX = thumb.x + thumb.width / 2
        val startY = thumb.y + thumb.height / 2

        assertTrue(host.handleMouseDown(dragX, startY, MouseButton.LEFT))
        var previousScroll = inspector.panelScrollOffsetY
        var previousThumbY = inspector.debugScrollbarThumbRect().y

        repeat(6) { step ->
            val nextY = startY + (step + 1) * 9
            assertTrue(host.handleMouseMove(dragX, nextY))
            host.syncFrame(root, inspectedLayoutRevision = 3L + step, cursorX = dragX, cursorY = nextY, inspectorPointerCaptured = inspector.isPointerCaptured)
            host.render(ctx, 420, 280)
            host.paint(ctx)
            val currentScroll = inspector.panelScrollOffsetY
            val currentThumbY = inspector.debugScrollbarThumbRect().y
            assertTrue(currentScroll >= previousScroll)
            assertTrue(currentThumbY >= previousThumbY)
            previousScroll = currentScroll
            previousThumbY = currentThumbY
        }

        assertTrue(host.handleMouseUp(dragX, startY + 6 * 9, MouseButton.LEFT))
        val settledScroll = inspector.panelScrollOffsetY
        val settledThumbY = inspector.debugScrollbarThumbRect().y

        repeat(6) { idx ->
            host.syncFrame(root, inspectedLayoutRevision = 20L + idx, cursorX = dragX, cursorY = startY, inspectorPointerCaptured = inspector.isPointerCaptured)
            host.render(ctx, 420, 280)
            host.paint(ctx)
            assertEquals(settledScroll, inspector.panelScrollOffsetY)
            assertEquals(settledThumbY, inspector.debugScrollbarThumbRect().y)
        }
    }
}

