package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerRuntime
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.InspectorEditorKind
import org.dreamfinity.dsgl.core.inspector.InspectorStyleEditorRowSnapshot
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.select.SelectRuntime
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InspectorPointerAlignmentTests {
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
        ColorPickerRuntime.engine.closeAll()
        SelectRuntime.host.closeAll()
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `hover aligns with visible style control under inspector scroll`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = true)
        setViewport(fixture, 420, 280)
        scrollInspectorBodyDown(fixture, steps = 6)
        assertTrue(fixture.inspector.panelScrollOffsetY > 0)

        val row = findOrScrollToVisibleSelectRow(fixture)
        val visibleRect = visibleControlRect(fixture, row)
        val visibleX = visibleRect.x + (visibleRect.width / 2).coerceAtLeast(1)
        val visibleY = visibleRect.y + (visibleRect.height / 2).coerceAtLeast(1)
        syncAndRender(fixture, visibleX, visibleY)
        assertTrue(findRowByProperty(fixture, row.property).controlHovered)

        val rawX = row.controlRect.x + (row.controlRect.width / 2).coerceAtLeast(1)
        val rawY = row.controlRect.y + (row.controlRect.height / 2).coerceAtLeast(1)
        syncAndRender(fixture, rawX, rawY)
        assertFalse(findRowByProperty(fixture, row.property).controlHovered)
    }

    @Test
    fun `inspector body scroll keeps hover hit-testing in sync`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = true)
        setViewport(fixture, 420, 280)
        scrollInspectorBodyDown(fixture, steps = 6)
        assertTrue(fixture.inspector.panelScrollOffsetY > 0)

        val row = findOrScrollToVisibleSelectRow(fixture)
        val rect = visibleControlRect(fixture, row)
        val cursorX = rect.x + (rect.width / 2).coerceAtLeast(1)
        val cursorY = rect.y + (rect.height / 2).coerceAtLeast(1)
        syncAndRender(fixture, cursorX, cursorY)
        assertTrue(findRowByProperty(fixture, row.property).controlHovered)

        fixture.host.handleMouseWheel(cursorX, cursorY, -120)
        syncAndRender(fixture, cursorX, cursorY)
        assertFalse(findRowByProperty(fixture, row.property).controlHovered)
    }

    @Test
    fun `inspector panel move keeps hover hit-testing aligned`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = true)
        setViewport(fixture, 420, 280)
        scrollInspectorBodyDown(fixture, steps = 6)
        assertTrue(fixture.inspector.panelScrollOffsetY > 0)

        val row = findOrScrollToVisibleSelectRow(fixture)
        val property = row.property
        val rect = visibleControlRect(fixture, row)
        val beforeX = rect.x + (rect.width / 2).coerceAtLeast(1)
        val beforeY = rect.y + (rect.height / 2).coerceAtLeast(1)
        syncAndRender(fixture, beforeX, beforeY)
        assertTrue(findRowByProperty(fixture, property).controlHovered)

        dragInspectorPanel(fixture, deltaX = -140, deltaY = 26)

        syncAndRender(fixture, beforeX, beforeY)
        assertFalse(findRowByProperty(fixture, property).controlHovered)

        val movedRect = visibleControlRect(fixture, findRowByProperty(fixture, property))
        val movedX = movedRect.x + (movedRect.width / 2).coerceAtLeast(1)
        val movedY = movedRect.y + (movedRect.height / 2).coerceAtLeast(1)
        syncAndRender(fixture, movedX, movedY)
        assertTrue(findRowByProperty(fixture, property).controlHovered)
    }

    @Test
    fun `select trigger click area matches visible trigger position after scroll`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = true)
        setViewport(fixture, 420, 280)
        scrollInspectorBodyDown(fixture, steps = 8)
        assertTrue(fixture.inspector.panelScrollOffsetY > 0)

        val row = findOrScrollToVisibleSelectRow(fixture)
        val rowIndex =
            fixture.inspector
                .overlayStyleEditorRows()
                .indexOfFirst { it.property == row.property }
                .takeIf { it >= 0 } ?: error("expected style row index for ${row.property.key}")
        val ownerKey = "dsgl-system-inspector-editor-select-$rowIndex"
        val property = row.property
        val triggerRect = openDropdownFromVisibleSelectRow(fixture, row)
        assertTrue(SelectRuntime.systemEngine.isOpenFor(ownerKey))

        fixture.host.handleMouseDown(
            triggerRect.x + 2,
            triggerRect.y + (triggerRect.height / 2).coerceAtLeast(1),
            MouseButton.LEFT,
        )
        fixture.host.handleMouseUp(
            triggerRect.x + 2,
            triggerRect.y + (triggerRect.height / 2).coerceAtLeast(1),
            MouseButton.LEFT,
        )
        syncAndRender(fixture, triggerRect.x + 2, triggerRect.y + 2)
        waitForSystemSelectClosed(fixture, ownerKey, triggerRect.x + 2, triggerRect.y + 2)

        val rawX = row.controlRect.x + 2
        val rawY = row.controlRect.y + (row.controlRect.height / 2).coerceAtLeast(1)
        fixture.host.handleMouseDown(rawX, rawY, MouseButton.LEFT)
        fixture.host.handleMouseUp(rawX, rawY, MouseButton.LEFT)
        syncAndRender(fixture, rawX, rawY)

        waitForSystemSelectClosed(fixture, ownerKey, rawX, rawY)
    }

    @Test
    fun `dropdown corrective behaviors are preserved after pointer-alignment fix`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = false)
        setViewport(fixture, 420, 280)

        val row = findOrScrollToVisibleSelectRow(fixture)
        val rowIndex =
            fixture.inspector
                .overlayStyleEditorRows()
                .indexOfFirst { it.property == row.property }
                .takeIf { it >= 0 } ?: error("expected style row index for ${row.property.key}")
        val ownerKey = "dsgl-system-inspector-editor-select-$rowIndex"
        val triggerRect = openDropdownFromVisibleSelectRow(fixture, row)
        val dropdown = selectPanelRect(ownerKey, fixture)
        settleFrames(fixture, steps = 1)
        val wheelX = dropdown.x + (dropdown.width / 2).coerceAtLeast(1)
        val wheelY = dropdown.y + (dropdown.height / 2).coerceAtLeast(1)

        assertTrue(dispatchSystemMouseWheel(fixture, wheelX, wheelY, -120))
        syncAndRender(fixture, wheelX, wheelY)

        assertTrue(SelectRuntime.systemEngine.isOpenFor(ownerKey))

        val panelRect = fixture.inspector.overlayPanelRect() ?: error("expected panel rect")
        val outsideX = (panelRect.x - 12).coerceAtLeast(1)
        val outsideY = (panelRect.y - 12).coerceAtLeast(1)
        assertFalse(dropdown.contains(outsideX, outsideY))
        assertFalse(triggerRect.contains(outsideX, outsideY))

        assertTrue(dispatchSystemMouseDown(fixture, outsideX, outsideY))
        assertTrue(dispatchSystemMouseUp(fixture, outsideX, outsideY))
        syncAndRender(fixture, outsideX, outsideY)
    }

    private fun openInspectorAndSelectTarget(withManyChildren: Boolean): Fixture {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
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

    private fun dragInspectorPanel(fixture: Fixture, deltaX: Int, deltaY: Int) {
        val panelRect = fixture.inspector.overlayPanelRect() ?: error("expected panel rect")
        val downX = panelRect.x + 16
        val downY = panelRect.y + 12
        val moveX = downX + deltaX
        val moveY = downY + deltaY
        fixture.host.handleMouseDown(downX, downY, MouseButton.LEFT)
        syncAndRender(fixture, downX, downY)
        syncAndRender(fixture, moveX, moveY)
        fixture.host.handleMouseUp(moveX, moveY, MouseButton.LEFT)
        syncAndRender(fixture, moveX, moveY)
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

    private fun settleFrames(fixture: Fixture, steps: Int) {
        val contentRect = fixture.inspector.overlayContentRect()
        val cursorX = contentRect.x + 4
        val cursorY = contentRect.y + 10
        repeat(steps) {
            syncAndRender(fixture, cursorX, cursorY)
        }
    }

    private fun findOrScrollToVisibleSelectRow(fixture: Fixture): InspectorStyleEditorRowSnapshot {
        repeat(120) {
            val rows =
                fixture.inspector.overlayStyleEditorRows().filter { row ->
                    row.editorKind == InspectorEditorKind.EnumSelect || row.editorKind == InspectorEditorKind.FontSelect
                }
            val contentRect = fixture.inspector.overlayContentRect()
            val bodyScrollY = fixture.inspector.panelScrollOffsetY
            val visible =
                rows.firstOrNull { row ->
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
                }
            if (visible != null) return visible
            scrollInspectorBodyDown(fixture, steps = 1)
        }
        error("expected visible inspector select row")
    }

    private fun openDropdownFromVisibleSelectRow(fixture: Fixture, row: InspectorStyleEditorRowSnapshot): Rect {
        val triggerRect = visibleControlRect(fixture, row)
        val clickX = triggerRect.x + 2
        val clickY = triggerRect.y + (triggerRect.height / 2).coerceAtLeast(1)
        fixture.host.handleMouseDown(clickX, clickY, MouseButton.LEFT)
        fixture.host.handleMouseUp(clickX, clickY, MouseButton.LEFT)
        syncAndRender(fixture, clickX, clickY)
        return triggerRect
    }

    private fun selectPanelRect(ownerKey: String, fixture: Fixture): Rect {
        SelectRuntime.systemEngine.onFrame(ctx, fixture.viewportWidth, fixture.viewportHeight, 1f)
        return SelectRuntime.systemEngine.debugPanelRect(ownerKey)
            ?: error("expected system select popup for owner=$ownerKey")
    }

    private fun dispatchSystemMouseDown(fixture: Fixture, x: Int, y: Int): Boolean =
        SelectRuntime.systemEngine.handleMouseDown(x, y, MouseButton.LEFT) ||
            fixture.host.handleMouseDown(x, y, MouseButton.LEFT)

    private fun dispatchSystemMouseUp(fixture: Fixture, x: Int, y: Int): Boolean =
        SelectRuntime.systemEngine.handleMouseUp(x, y, MouseButton.LEFT) ||
            fixture.host.handleMouseUp(x, y, MouseButton.LEFT)

    private fun dispatchSystemMouseWheel(
        fixture: Fixture,
        x: Int,
        y: Int,
        delta: Int,
    ): Boolean =
        SelectRuntime.systemEngine.handleMouseWheel(x, y, delta) ||
            fixture.host.handleMouseWheel(x, y, delta)

    private fun waitForSystemSelectClosed(
        fixture: Fixture,
        ownerKey: String,
        cursorX: Int,
        cursorY: Int,
    ) {
        repeat(30) {
            if (!SelectRuntime.systemEngine.isOpenFor(ownerKey)) return
            Thread.sleep(5)
            syncAndRender(fixture, cursorX, cursorY)
            SelectRuntime.systemEngine.onFrame(ctx, fixture.viewportWidth, fixture.viewportHeight, 1f)
        }
        assertFalse(SelectRuntime.systemEngine.isOpenFor(ownerKey))
    }

    private fun findRowByProperty(fixture: Fixture, property: StyleProperty): InspectorStyleEditorRowSnapshot =
        fixture.inspector
            .overlayStyleEditorRows()
            .firstOrNull { it.property == property }
            ?: error("expected row for property ${property.key}")

    private fun visibleControlRect(fixture: Fixture, row: InspectorStyleEditorRowSnapshot): Rect {
        val bodyScrollY = fixture.inspector.panelScrollOffsetY
        return Rect(
            row.controlRect.x,
            row.controlRect.y - bodyScrollY,
            row.controlRect.width,
            row.controlRect.height,
        )
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
            repeat(24) { index ->
                ContainerNode(key = "child-$index")
                    .apply {
                        bounds = Rect(980, 170 + index * 14, 120, 10)
                    }.applyParent(target)
            }
        }
        StyleEngine.setInspectorOverrideLiteral(target, StyleProperty.BACKGROUND_COLOR, "#FF112233").getOrThrow()
        return root
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
