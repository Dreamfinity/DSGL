package org.dreamfinity.dsgl.core.overlay.system

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerRuntime
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.TextInputNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.InspectorEditorKind
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleProperty

class InspectorDropdownCorrectiveTests {
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
    fun `outside click closes active inspector dropdown`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = true)
        setViewport(fixture, 420, 280)
        scrollInspectorBodyDown(fixture, steps = 10)

        val (trigger, dropdown) = openInspectorSelectDropdown(fixture, requireScrollable = false)
        val contentRect = fixture.inspector.debugContentRect()
        var outsideX = contentRect.x + 4
        var outsideY = contentRect.y + 4
        if (dropdown.popupRect.contains(outsideX, outsideY) || trigger.contains(outsideX, outsideY)) {
            val panelRect = fixture.inspector.debugPanelRect() ?: error("expected panel rect")
            outsideX = panelRect.x + 8
            outsideY = panelRect.y + 8
        }

        assertTrue(fixture.host.handleMouseDown(outsideX, outsideY, MouseButton.LEFT))
        fixture.host.handleMouseUp(outsideX, outsideY, MouseButton.LEFT)
        syncAndRender(fixture, outsideX, outsideY)

        assertTrue(fixture.inspector.debugStyleEditorDropdowns().isEmpty())
    }

    @Test
    fun `wheel is routed to active dropdown before inspector body`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = false)
        openVisibleInspectorSelectDropdownWithoutBodyScroll(fixture)
        settleFrames(fixture, steps = 1)
        val beforePanelScroll = fixture.inspector.panelScrollOffsetY

        val contentRect = fixture.inspector.debugContentRect()
        val wheelX = contentRect.x + 4
        val wheelY = contentRect.y + 10

        assertTrue(fixture.host.handleMouseWheel(wheelX, wheelY, -120))
        syncAndRender(fixture, wheelX, wheelY)

        assertTrue(fixture.inspector.debugStyleEditorDropdowns().isNotEmpty())
        assertEquals(beforePanelScroll, fixture.inspector.panelScrollOffsetY)
    }

    @Test
    fun `inspector scroll keeps dropdown anchored under visible trigger`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = true)
        setViewport(fixture, 420, 280)
        scrollInspectorBodyDown(fixture, steps = 12)

        val (trigger, _) = openInspectorSelectDropdown(fixture, requireScrollable = false)
        val popup = findDropdownPopupNode(fixture)

        val expectedY = (trigger.y + trigger.height + 2)
            .coerceIn(2, (fixture.viewportHeight - popup.bounds.height - 2).coerceAtLeast(2))
        assertEquals(expectedY, popup.bounds.y)
    }

    @Test
    fun `dropdown x alignment follows visible trigger after inspector scroll`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = true)
        setViewport(fixture, 420, 280)
        scrollInspectorBodyDown(fixture, steps = 12)

        val (trigger, _) = openInspectorSelectDropdown(fixture, requireScrollable = false)
        val popup = findDropdownPopupNode(fixture)

        val expectedX = trigger.x
            .coerceIn(2, (fixture.viewportWidth - popup.bounds.width - 2).coerceAtLeast(2))
        assertEquals(expectedX, popup.bounds.x)
    }

    @Test
    fun `text edit migration remains intact after dropdown corrective fix`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = false)
        val input = findVisibleInputNode(fixture, "width")
        val (focusX, focusY) = focusInputByClick(fixture, input)
        syncAndRender(fixture, focusX, focusY)

        var focusedInput = findVisibleInputNode(fixture, "width")
        val nearEndX = (focusedInput.bounds.x + focusedInput.bounds.width - 3).coerceAtLeast(focusedInput.bounds.x + 1)
        val centerY = focusedInput.bounds.y + (focusedInput.bounds.height / 2).coerceAtLeast(1)
        fixture.host.handleMouseDown(nearEndX, centerY, MouseButton.LEFT)
        fixture.host.handleMouseUp(nearEndX, centerY, MouseButton.LEFT)
        syncAndRender(fixture, nearEndX, centerY)

        focusedInput = findVisibleInputNode(fixture, "width")
        FocusManager.requestFocus(focusedInput)
        val before = focusedInput.text
        assertTrue(fixture.host.handleKeyDown(0, '7'))
        syncAndRender(fixture, nearEndX, centerY)

        val refreshed = findVisibleInputNode(fixture, "width")
        assertTrue(refreshed.text.contains('7') || refreshed.text != before)
    }

    @Test
    fun `color edit integration remains system owned`() {
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

    private fun openVisibleInspectorSelectDropdownWithoutBodyScroll(
        fixture: Fixture
    ): Pair<Rect, org.dreamfinity.dsgl.core.inspector.InspectorDropdownSnapshot> {
        val contentRect = fixture.inspector.debugContentRect()
        val bodyScrollY = fixture.inspector.panelScrollOffsetY
        val row = fixture.inspector.debugStyleEditorRows().firstOrNull { row ->
            if (row.editorKind != InspectorEditorKind.EnumSelect && row.editorKind != InspectorEditorKind.FontSelect) {
                return@firstOrNull false
            }
            val visibleRect = Rect(
                row.controlRect.x,
                row.controlRect.y - bodyScrollY,
                row.controlRect.width,
                row.controlRect.height
            )
            val centerX = visibleRect.x + (visibleRect.width / 2).coerceAtLeast(1)
            val centerY = visibleRect.y + (visibleRect.height / 2).coerceAtLeast(1)
            contentRect.contains(centerX, centerY)
        } ?: error("expected visible inspector select row without body scrolling")

        val triggerRect = Rect(
            row.controlRect.x,
            row.controlRect.y - bodyScrollY,
            row.controlRect.width,
            row.controlRect.height
        )
        val clickX = triggerRect.x + 2
        val clickY = triggerRect.y + (triggerRect.height / 2).coerceAtLeast(1)
        fixture.host.handleMouseDown(clickX, clickY, MouseButton.LEFT)
        fixture.host.handleMouseUp(clickX, clickY, MouseButton.LEFT)
        syncAndRender(fixture, clickX, clickY)

        val opened = fixture.inspector.debugStyleEditorDropdowns().firstOrNull()
            ?: error("expected inspector dropdown to open from visible select row")
        return triggerRect to opened
    }

    private fun openInspectorSelectDropdown(
        fixture: Fixture,
        requireScrollable: Boolean
    ): Pair<Rect, org.dreamfinity.dsgl.core.inspector.InspectorDropdownSnapshot> {
        repeat(120) {
            val contentRect = fixture.inspector.debugContentRect()
            val bodyScrollY = fixture.inspector.panelScrollOffsetY
            val visibleSelectRows = fixture.inspector.debugStyleEditorRows().filter { row ->
                if (row.editorKind != InspectorEditorKind.EnumSelect && row.editorKind != InspectorEditorKind.FontSelect) {
                    return@filter false
                }
                val visibleRect = Rect(
                    row.controlRect.x,
                    row.controlRect.y - bodyScrollY,
                    row.controlRect.width,
                    row.controlRect.height
                )
                val centerX = visibleRect.x + (visibleRect.width / 2).coerceAtLeast(1)
                val centerY = visibleRect.y + (visibleRect.height / 2).coerceAtLeast(1)
                contentRect.contains(centerX, centerY)
            }

            visibleSelectRows.forEach { row ->
                val triggerRect = Rect(
                    row.controlRect.x,
                    row.controlRect.y - bodyScrollY,
                    row.controlRect.width,
                    row.controlRect.height
                )
                val clickX = triggerRect.x + 2
                val clickY = triggerRect.y + (triggerRect.height / 2).coerceAtLeast(1)
                fixture.host.handleMouseDown(clickX, clickY, MouseButton.LEFT)
                fixture.host.handleMouseUp(clickX, clickY, MouseButton.LEFT)
                syncAndRender(fixture, clickX, clickY)

                val opened = fixture.inspector.debugStyleEditorDropdowns().firstOrNull()
                if (opened != null && (!requireScrollable || opened.footerText != null)) {
                    return triggerRect to opened
                }
                if (opened != null) {
                    fixture.host.handleMouseDown(clickX, clickY, MouseButton.LEFT)
                    fixture.host.handleMouseUp(clickX, clickY, MouseButton.LEFT)
                    syncAndRender(fixture, clickX, clickY)
                }
            }

            scrollInspectorBodyDown(fixture, steps = 1)
        }
        error("expected inspector select dropdown to open")
    }

    private fun findDropdownPopupNode(fixture: Fixture): DOMNode {
        val inspectorNode = fixture.host.debugEntryNode(SystemOverlayEntryId.Inspector)
            ?: error("inspector entry missing")
        return collectNodes(inspectorNode).firstOrNull { node ->
            val key = node.key?.toString() ?: return@firstOrNull false
            key.startsWith("dsgl-system-inspector-dropdown-") &&
                    !key.contains("-option-") &&
                    !key.endsWith("-footer")
        } ?: error("expected dropdown popup node")
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
        val inspectorNode = fixture.host.debugEntryNode(SystemOverlayEntryId.Inspector)
            ?: error("inspector entry missing")
        val contentRect = fixture.inspector.debugContentRect()
        val candidates = collectNodes(inspectorNode)
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
        var viewportHeight: Int
    )
}

