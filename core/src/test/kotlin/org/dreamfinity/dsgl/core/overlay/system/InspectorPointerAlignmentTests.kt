package org.dreamfinity.dsgl.core.overlay.system

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerRuntime
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.InspectorDropdownSnapshot
import org.dreamfinity.dsgl.core.inspector.InspectorEditorKind
import org.dreamfinity.dsgl.core.inspector.InspectorStyleEditorRowSnapshot
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleProperty

class InspectorPointerAlignmentTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @AfterTest
    fun cleanup() {
        FocusManager.clearFocus()
        KeyModifiers.sync(shift = false, control = false, meta = false)
        ColorPickerRuntime.engine.closeAll()
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
        val property = row.property
        val (triggerRect, openedOnVisible) = openDropdownFromVisibleSelectRow(fixture, row)
        assertEquals(property, openedOnVisible.property)

        fixture.host.handleMouseDown(triggerRect.x + 2, triggerRect.y + (triggerRect.height / 2).coerceAtLeast(1), MouseButton.LEFT)
        fixture.host.handleMouseUp(triggerRect.x + 2, triggerRect.y + (triggerRect.height / 2).coerceAtLeast(1), MouseButton.LEFT)
        syncAndRender(fixture, triggerRect.x + 2, triggerRect.y + 2)
        assertTrue(fixture.inspector.debugStyleEditorDropdowns().isEmpty())

        val rawX = row.controlRect.x + 2
        val rawY = row.controlRect.y + (row.controlRect.height / 2).coerceAtLeast(1)
        fixture.host.handleMouseDown(rawX, rawY, MouseButton.LEFT)
        fixture.host.handleMouseUp(rawX, rawY, MouseButton.LEFT)
        syncAndRender(fixture, rawX, rawY)

        val openedOnRaw = fixture.inspector.debugStyleEditorDropdowns().firstOrNull()
        assertTrue(openedOnRaw == null || openedOnRaw.property != property)
    }

    @Test
    fun `dropdown corrective behaviors are preserved after pointer-alignment fix`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = false)
        setViewport(fixture, 420, 280)

        val row = findOrScrollToVisibleSelectRow(fixture)
        val (triggerRect, dropdown) = openDropdownFromVisibleSelectRow(fixture, row)
        settleFrames(fixture, steps = 1)
        val contentRect = fixture.inspector.debugContentRect()
        val wheelX = contentRect.x + 4
        val wheelY = contentRect.y + 10

        assertTrue(fixture.host.handleMouseWheel(wheelX, wheelY, -120))
        syncAndRender(fixture, wheelX, wheelY)

        assertTrue(fixture.inspector.debugStyleEditorDropdowns().isNotEmpty())

        var outsideX = contentRect.x + 4
        var outsideY = contentRect.y + 4
        if (dropdown.popupRect.contains(outsideX, outsideY) || triggerRect.contains(outsideX, outsideY)) {
            val panelRect = fixture.inspector.debugPanelRect() ?: error("expected panel rect")
            outsideX = panelRect.x + 8
            outsideY = panelRect.y + 8
        }

        fixture.host.handleMouseDown(outsideX, outsideY, MouseButton.LEFT)
        fixture.host.handleMouseUp(outsideX, outsideY, MouseButton.LEFT)
        syncAndRender(fixture, outsideX, outsideY)

        assertTrue(fixture.inspector.debugStyleEditorDropdowns().isEmpty())
    }

    private fun openInspectorAndSelectTarget(withManyChildren: Boolean): Fixture {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val root = inspectedRoot(withManyChildren)

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        host.paint(ctx)
        assertTrue(host.handleMouseDown(984, 144, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(984, 144, MouseButton.LEFT))
        inspector.setPickMode(false)

        val fixture = Fixture(
            inspector = inspector,
            host = host,
            root = root,
            revision = 2L,
            viewportWidth = 1280,
            viewportHeight = 720
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
            inspectorPointerCaptured = fixture.inspector.isPointerCaptured
        )
        fixture.host.render(ctx, fixture.viewportWidth, fixture.viewportHeight)
        fixture.host.paint(ctx)
    }

    private fun dragInspectorPanel(fixture: Fixture, deltaX: Int, deltaY: Int) {
        val panelRect = fixture.inspector.debugPanelRect() ?: error("expected panel rect")
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
        val contentRect = fixture.inspector.debugContentRect()
        val wheelX = contentRect.x + 4
        val wheelY = contentRect.y + 10
        repeat(steps) {
            fixture.host.handleMouseWheel(wheelX, wheelY, -120)
            syncAndRender(fixture, wheelX, wheelY)
        }
    }

    private fun settleFrames(fixture: Fixture, steps: Int) {
        val contentRect = fixture.inspector.debugContentRect()
        val cursorX = contentRect.x + 4
        val cursorY = contentRect.y + 10
        repeat(steps) {
            syncAndRender(fixture, cursorX, cursorY)
        }
    }

    private fun findVisibleSelectRowWithoutScrolling(fixture: Fixture): InspectorStyleEditorRowSnapshot {
        val rows = fixture.inspector.debugStyleEditorRows().filter { row ->
            row.editorKind == InspectorEditorKind.EnumSelect || row.editorKind == InspectorEditorKind.FontSelect
        }
        val contentRect = fixture.inspector.debugContentRect()
        val bodyScrollY = fixture.inspector.panelScrollOffsetY
        return rows.firstOrNull { row ->
            val rect = Rect(
                row.controlRect.x,
                row.controlRect.y - bodyScrollY,
                row.controlRect.width,
                row.controlRect.height
            )
            val centerX = rect.x + (rect.width / 2).coerceAtLeast(1)
            val centerY = rect.y + (rect.height / 2).coerceAtLeast(1)
            contentRect.contains(centerX, centerY)
        } ?: error("expected visible inspector select row without scrolling")
    }

    private fun findOrScrollToVisibleSelectRow(fixture: Fixture): InspectorStyleEditorRowSnapshot {
        repeat(120) {
            val rows = fixture.inspector.debugStyleEditorRows().filter { row ->
                row.editorKind == InspectorEditorKind.EnumSelect || row.editorKind == InspectorEditorKind.FontSelect
            }
            val contentRect = fixture.inspector.debugContentRect()
            val bodyScrollY = fixture.inspector.panelScrollOffsetY
            val visible = rows.firstOrNull { row ->
                val rect = Rect(
                    row.controlRect.x,
                    row.controlRect.y - bodyScrollY,
                    row.controlRect.width,
                    row.controlRect.height
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

    private fun openDropdownFromVisibleSelectRow(
        fixture: Fixture,
        row: InspectorStyleEditorRowSnapshot
    ): Pair<Rect, InspectorDropdownSnapshot> {
        val triggerRect = visibleControlRect(fixture, row)
        val clickX = triggerRect.x + 2
        val clickY = triggerRect.y + (triggerRect.height / 2).coerceAtLeast(1)
        fixture.host.handleMouseDown(clickX, clickY, MouseButton.LEFT)
        fixture.host.handleMouseUp(clickX, clickY, MouseButton.LEFT)
        syncAndRender(fixture, clickX, clickY)
        val opened = fixture.inspector.debugStyleEditorDropdowns().firstOrNull()
        assertNotNull(opened)
        return triggerRect to opened
    }

    private fun findRowByProperty(fixture: Fixture, property: StyleProperty): InspectorStyleEditorRowSnapshot {
        return fixture.inspector.debugStyleEditorRows().firstOrNull { it.property == property }
            ?: error("expected row for property ${property.key}")
    }

    private fun visibleControlRect(fixture: Fixture, row: InspectorStyleEditorRowSnapshot): Rect {
        val bodyScrollY = fixture.inspector.panelScrollOffsetY
        return Rect(
            row.controlRect.x,
            row.controlRect.y - bodyScrollY,
            row.controlRect.width,
            row.controlRect.height
        )
    }

    private fun inspectedRoot(withManyChildren: Boolean): ContainerNode {
        val root = ContainerNode(key = "root")
        root.bounds = Rect(0, 0, 1280, 720)
        val target = ContainerNode(key = "target").apply {
            bounds = Rect(980, 140, 120, 30)
        }
        target.applyParent(root)
        if (withManyChildren) {
            repeat(24) { index ->
                ContainerNode(key = "child-$index").apply {
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
        var viewportHeight: Int
    )
}

