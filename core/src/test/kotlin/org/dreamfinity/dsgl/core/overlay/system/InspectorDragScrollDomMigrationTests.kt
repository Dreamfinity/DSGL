package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPortalServices
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.TextInputNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.InspectorEditorKind
import org.dreamfinity.dsgl.core.inspector.InspectorStyleEditorRowSnapshot
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.select.SelectPortalServices
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.test.*

class InspectorDragScrollDomMigrationTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanup() {
        FocusManager.clearFocus()
        KeyModifiers.sync(shift = false, control = false, meta = false)
        ColorPickerPortalServices.engine.closeAll()
        SelectPortalServices.closeAll()
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `inspector panel drag is dom-first and controller drag authority stays demoted`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = true)

        val before = fixture.inspector.overlayPanelRect() ?: error("expected panel rect")
        val downX = before.x + 18
        val downY = before.y + 14
        val moveX = downX + 90
        val moveY = downY + 32

        assertTrue(fixture.host.handleMouseDown(downX, downY, MouseButton.LEFT))
        syncAndRender(fixture, downX, downY)

        assertFalse(fixture.inspector.isDraggingPanel)
        assertFalse(fixture.inspector.isPointerCaptured)
        assertTrue(
            fixture.host
                .debugEntryState(SystemOverlayEntryId.Inspector)
                ?.dragSession
                ?.active == true,
        )

        assertTrue(fixture.host.handleMouseMove(moveX, moveY))
        syncAndRender(fixture, moveX, moveY)
        val moved = fixture.inspector.overlayPanelRect() ?: error("expected moved panel rect")
        assertTrue(moved.x != before.x || moved.y != before.y)

        assertTrue(fixture.host.handleMouseUp(moveX, moveY, MouseButton.LEFT))
        syncAndRender(fixture, moveX, moveY)

        assertFalse(fixture.inspector.isDraggingPanel)
        assertFalse(fixture.inspector.isPointerCaptured)
        assertFalse(
            fixture.host
                .debugEntryState(SystemOverlayEntryId.Inspector)
                ?.dragSession
                ?.active == true,
        )
    }

    @Test
    fun `inspector panel drag stays monotonic when sync cursor lags behind dom drag updates`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = true)

        val before = fixture.inspector.overlayPanelRect() ?: error("expected panel rect")
        val downX = before.x + 18
        val downY = before.y + 14
        val dragX = downX + 92
        val dragY = downY + 34

        assertTrue(fixture.host.handleMouseDown(downX, downY, MouseButton.LEFT))
        syncAndRender(fixture, downX, downY)

        assertTrue(fixture.host.handleMouseMove(dragX, dragY))
        val afterDomDrag = fixture.inspector.overlayPanelRect() ?: error("expected moved panel rect")
        assertTrue(
            afterDomDrag.x > before.x || afterDomDrag.y > before.y,
            "expected drag move to advance panel: before=$before afterDomDrag=$afterDomDrag",
        )

        syncAndRender(fixture, downX, downY)
        val afterStaleSync = fixture.inspector.overlayPanelRect() ?: error("expected panel rect after stale sync")

        if (afterDomDrag.x > before.x) {
            assertTrue(
                afterStaleSync.x >= afterDomDrag.x,
                "stale sync cursor regressed panel x: before=$before dom=$afterDomDrag stale=$afterStaleSync",
            )
        }
        if (afterDomDrag.y > before.y) {
            assertTrue(
                afterStaleSync.y >= afterDomDrag.y,
                "stale sync cursor regressed panel y: before=$before dom=$afterDomDrag stale=$afterStaleSync",
            )
        }

        assertTrue(fixture.host.handleMouseMove(dragX + 28, dragY + 16))
        syncAndRender(fixture, dragX + 28, dragY + 16)
        val afterNextMove = fixture.inspector.overlayPanelRect() ?: error("expected panel rect after next drag move")
        assertTrue(
            afterNextMove.x >= afterStaleSync.x && afterNextMove.y >= afterStaleSync.y,
            "expected monotonic drag progression across sync/render cycle: stale=$afterStaleSync next=$afterNextMove",
        )

        assertTrue(fixture.host.handleMouseUp(dragX + 28, dragY + 16, MouseButton.LEFT))
        syncAndRender(fixture, dragX + 28, dragY + 16)
        assertFalse(
            fixture.host
                .debugEntryState(SystemOverlayEntryId.Inspector)
                ?.dragSession
                ?.active == true,
        )
    }

    @Test
    fun `inspector wheel body scroll is dom-first and not controller-authoritative`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = true)
        setViewport(fixture, 420, 280)

        val contentRect = fixture.inspector.overlayContentRect()
        val wheelX = contentRect.x + 4
        val wheelY = contentRect.y + 10
        val before = fixture.inspector.panelScrollOffsetY

        assertTrue(fixture.host.handleMouseWheel(wheelX, wheelY, -120))
        syncAndRender(fixture, wheelX, wheelY)

        val after = fixture.inspector.panelScrollOffsetY
        assertTrue(after > before)
        assertFalse(fixture.inspector.isPointerCaptured)
        assertFalse(fixture.inspector.isDraggingPanel)
        assertFalse(
            fixture.host
                .debugEntryState(SystemOverlayEntryId.Inspector)
                ?.dragSession
                ?.active == true,
        )
    }

    @Test
    fun `inspector scrollbar thumb drag is dom-first and not controller-authoritative`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = true)
        setViewport(fixture, 420, 280)
        scrollInspectorBodyDown(fixture, steps = 2)

        val thumb = fixture.inspector.overlayScrollbarThumbRect()
        assertTrue(thumb.width > 0 && thumb.height > 0)

        val dragX = thumb.x + thumb.width / 2
        val startY = thumb.y + thumb.height / 2
        val before = fixture.inspector.panelScrollOffsetY

        assertTrue(fixture.host.handleMouseDown(dragX, startY, MouseButton.LEFT))
        syncAndRender(fixture, dragX, startY)

        assertFalse(fixture.inspector.isPointerCaptured)
        assertFalse(fixture.inspector.isDraggingPanel)
        assertFalse(
            fixture.host
                .debugEntryState(SystemOverlayEntryId.Inspector)
                ?.dragSession
                ?.active == true,
        )

        assertTrue(fixture.host.handleMouseMove(dragX, startY + 40))
        syncAndRender(fixture, dragX, startY + 40)
        val after = fixture.inspector.panelScrollOffsetY
        assertTrue(after >= before)

        assertTrue(fixture.host.handleMouseUp(dragX, startY + 40, MouseButton.LEFT))
        syncAndRender(fixture, dragX, startY + 40)
        assertFalse(
            fixture.host
                .debugEntryState(SystemOverlayEntryId.Inspector)
                ?.dragSession
                ?.active == true,
        )
    }

    @Test
    fun `inspector drag and scroll continuity survives rebuilds`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = true)
        setViewport(fixture, 420, 280)
        scrollInspectorBodyDown(fixture, steps = 3)

        val thumb = fixture.inspector.overlayScrollbarThumbRect()
        assertTrue(thumb.width > 0 && thumb.height > 0)

        val dragX = thumb.x + thumb.width / 2
        val startY = thumb.y + thumb.height / 2
        assertTrue(fixture.host.handleMouseDown(dragX, startY, MouseButton.LEFT))
        syncAndRender(fixture, dragX, startY)
        assertFalse(
            fixture.host
                .debugEntryState(SystemOverlayEntryId.Inspector)
                ?.dragSession
                ?.active == true,
        )

        assertTrue(fixture.host.handleMouseMove(dragX, startY + 22))
        syncAndRender(fixture, dragX, startY + 22)
        val afterFirstMove = fixture.inspector.panelScrollOffsetY

        syncAndRender(fixture, dragX, startY + 22)
        assertFalse(
            fixture.host
                .debugEntryState(SystemOverlayEntryId.Inspector)
                ?.dragSession
                ?.active == true,
        )

        assertTrue(fixture.host.handleMouseMove(dragX, startY + 54))
        syncAndRender(fixture, dragX, startY + 54)
        val afterSecondMove = fixture.inspector.panelScrollOffsetY
        assertTrue(afterSecondMove >= afterFirstMove)

        assertTrue(fixture.host.handleMouseUp(dragX, startY + 54, MouseButton.LEFT))
        syncAndRender(fixture, dragX, startY + 54)
        assertFalse(
            fixture.host
                .debugEntryState(SystemOverlayEntryId.Inspector)
                ?.dragSession
                ?.active == true,
        )
    }

    @Test
    fun `controller dragMode remains inactive during dom drag and scroll interactions`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = true)
        setViewport(fixture, 420, 280)

        val panelRect = fixture.inspector.overlayPanelRect() ?: error("expected panel rect")
        val downX = panelRect.x + 14
        val downY = panelRect.y + 12
        assertTrue(fixture.host.handleMouseDown(downX, downY, MouseButton.LEFT))
        syncAndRender(fixture, downX, downY)
        assertFalse(fixture.inspector.isDraggingPanel)
        assertFalse(fixture.inspector.isPointerCaptured)
        assertTrue(
            fixture.host
                .debugEntryState(SystemOverlayEntryId.Inspector)
                ?.dragSession
                ?.active == true,
        )

        assertTrue(fixture.host.handleMouseUp(downX, downY, MouseButton.LEFT))
        syncAndRender(fixture, downX, downY)
        assertFalse(fixture.inspector.isDraggingPanel)
        assertFalse(fixture.inspector.isPointerCaptured)
    }

    @Test
    fun `text-edit migration remains intact after drag-scroll migration`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = false)
        val input = findVisibleInputNode(fixture, "width")
        val (focusX, focusY) = focusInputByClick(fixture, input)
        syncAndRender(fixture, focusX, focusY)

        FocusManager.requestFocus(input)
        fixture.host.handleKeyDown(0, '7')
        syncAndRender(fixture, focusX, focusY)

        val refreshed = findVisibleInputNode(fixture, "width")
        assertEquals(refreshed.key, FocusManager.focusedNode()?.key)
    }

    @Test
    fun `dropdown migration remains intact after drag-scroll migration`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = false)
        val row = findVisibleSelectRow(fixture)
        val rowIndex =
            fixture.inspector
                .overlayStyleEditorRows()
                .indexOfFirst { it.property == row.property }
                .takeIf { it >= 0 } ?: error("expected style row index for ${row.property.key}")
        val ownerKey = "dsgl-system-inspector-editor-select-$rowIndex"
        val trigger = visibleControlRect(fixture, row)
        val clickX = trigger.x + 2
        val clickY = trigger.y + (trigger.height / 2).coerceAtLeast(1)

        fixture.host.handleMouseDown(clickX, clickY, MouseButton.LEFT)
        fixture.host.handleMouseUp(clickX, clickY, MouseButton.LEFT)
        syncAndRender(fixture, clickX, clickY)

        assertTrue(SelectPortalServices.systemEngine.isOpenFor(ownerKey))

        fixture.host.handleMouseDown(clickX, clickY, MouseButton.LEFT)
        fixture.host.handleMouseUp(clickX, clickY, MouseButton.LEFT)
        syncAndRender(fixture, clickX, clickY)
        assertFalse(SelectPortalServices.systemEngine.isOpenFor(ownerKey))
    }

    @Test
    fun `inspector color edit integration remains system-owned after drag-scroll migration`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = false)
        val anchor = Rect(80, 80, 20, 18)

        assertTrue(fixture.inspector.debugOpenColorPickerForSelection(StyleProperty.BACKGROUND_COLOR, anchor))
        syncAndRender(fixture, anchor.x + 1, anchor.y + 1)

        assertTrue(fixture.host.isSystemColorPickerOpen())
        assertEquals(OverlayOwnerScope.System, fixture.host.debugSystemColorPickerPopupOwnerScope())
        assertNotNull(fixture.host.debugEntryNode(SystemOverlayEntryId.ColorPickerPopup))
    }

    private fun openInspectorAndSelectTarget(withManyChildren: Boolean): Fixture {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPortalService())
        val root = inspectedRoot(withManyChildren)

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false,
        )
        host.render(ctx, 1280, 720)
        host.paint(ctx)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(984, 144, MouseButton.LEFT))
        inspector.setPickMode(false)

        val fixture =
            Fixture(
                inspector = inspector,
                host = host,
                root = root,
                revision = 2L,
                viewportWidth = 1280,
                viewportHeight = 720,
            )
        syncAndRender(fixture, 984, 144)
        return fixture
    }

    private fun setViewport(fixture: Fixture, width: Int, height: Int) {
        fixture.viewportWidth = width
        fixture.viewportHeight = height
        syncAndRender(fixture, 90, 90)
    }

    private fun syncAndRender(fixture: Fixture, cursorX: Int, cursorY: Int) {
        fixture.host.onInputFrame(fixture.viewportWidth, fixture.viewportHeight)
        fixture.host.syncFrame(
            inspectedRoot = fixture.root,
            inspectedLayoutRevision = fixture.revision++,
            cursorX = cursorX,
            cursorY = cursorY,
            inspectorPointerCaptured = fixture.inspector.isPointerCaptured,
        )
        fixture.host.render(ctx, fixture.viewportWidth, fixture.viewportHeight)
        fixture.host.paint(ctx)
    }

    private fun scrollInspectorBodyDown(fixture: Fixture, steps: Int) {
        val contentRect = fixture.inspector.overlayContentRect()
        val wheelX = contentRect.x + 4
        val wheelY = contentRect.y + 10
        repeat(steps) {
            fixture.host.handleMouseWheel(wheelX, wheelY, -120)
            syncAndRender(fixture, wheelX, wheelY)
        }
    }

    private fun findVisibleSelectRow(fixture: Fixture): InspectorStyleEditorRowSnapshot {
        repeat(120) {
            val rows =
                fixture.inspector.overlayStyleEditorRows().filter { row ->
                    row.editorKind == InspectorEditorKind.EnumSelect || row.editorKind == InspectorEditorKind.FontSelect
                }
            val contentRect = fixture.inspector.overlayContentRect()
            val bodyScrollY = fixture.inspector.panelScrollOffsetY
            rows
                .firstOrNull { row ->
                    val rect =
                        Rect(
                            row.controlRect.x,
                            row.controlRect.y - bodyScrollY,
                            row.controlRect.width,
                            row.controlRect.height,
                        )
                    val centerX = rect.x + (rect.width / 2).coerceAtLeast(1)
                    val centerY = rect.y + (rect.height / 2).coerceAtLeast(1)
                    contentRect.contains(centerX, centerY)
                }?.let { return it }

            scrollInspectorBodyDown(fixture, steps = 1)
        }
        error("expected visible select row")
    }

    private fun visibleControlRect(fixture: Fixture, row: InspectorStyleEditorRowSnapshot): Rect {
        val bodyScrollY = fixture.inspector.panelScrollOffsetY
        return Rect(
            row.controlRect.x,
            row.controlRect.y - bodyScrollY,
            row.controlRect.width,
            row.controlRect.height,
        )
    }

    private fun focusInputByClick(fixture: Fixture, input: TextInputNode): Pair<Int, Int> {
        val y = input.bounds.y + (input.bounds.height / 2).coerceAtLeast(1)
        val left = (input.bounds.x + 2).coerceAtMost(input.bounds.x + input.bounds.width - 2)
        val center = input.bounds.x + (input.bounds.width / 2).coerceAtLeast(1)
        val right = (input.bounds.x + input.bounds.width - 3).coerceAtLeast(input.bounds.x + 1)
        val points = listOf(left, center, right)
        points.forEach { x ->
            fixture.host.handleMouseDown(x, y, MouseButton.LEFT)
            fixture.host.handleMouseUp(x, y, MouseButton.LEFT)
            syncAndRender(fixture, x, y)
            if (FocusManager.focusedNode()?.key == input.key) {
                return x to y
            }
        }
        FocusManager.requestFocus(input)
        return center to y
    }

    private fun findVisibleInputNode(fixture: Fixture, propertyKey: String): TextInputNode {
        val inspectorNode =
            fixture.host.debugEntryNode(SystemOverlayEntryId.Inspector)
                ?: error("inspector entry missing")
        val contentRect = fixture.inspector.overlayContentRect()
        val candidates =
            collectNodes(inspectorNode)
                .filterIsInstance<TextInputNode>()
                .filter { (it.key?.toString() ?: "") == "dsgl-system-inspector-editor-numeric-input-$propertyKey" }

        return candidates.firstOrNull { node ->
            val probeX = node.bounds.x + 2
            val probeY = node.bounds.y + (node.bounds.height / 2).coerceAtLeast(1)
            contentRect.contains(probeX, probeY)
        } ?: candidates.firstOrNull() ?: error("expected inspector input for property '$propertyKey'")
    }

    private fun inspectedRoot(withManyChildren: Boolean): ContainerNode {
        val root = ContainerNode(key = "root")
        root.bounds = Rect(0, 0, 1280, 720)
        val target =
            ContainerNode(key = "target").apply {
                bounds = Rect(980, 140, 120, 30)
            }
        target.applyParent(root)
        if (withManyChildren) {
            repeat(60) { index ->
                ContainerNode(key = "child-$index")
                    .apply {
                        bounds = Rect(980, 180 + index * 12, 180, 10)
                    }.applyParent(target)
            }
        }
        StyleEngine.setInspectorOverrideLiteral(target, StyleProperty.BACKGROUND_COLOR, "#FF112233").getOrThrow()
        return root
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

    private data class Fixture(
        val inspector: InspectorController,
        val host: SystemOverlayHost,
        val root: ContainerNode,
        var revision: Long,
        var viewportWidth: Int,
        var viewportHeight: Int,
    )
}
