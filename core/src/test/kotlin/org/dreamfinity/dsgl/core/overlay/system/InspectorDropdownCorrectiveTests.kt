package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerRuntime
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
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.select.SelectRuntime
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.test.*

class InspectorDropdownCorrectiveTests {
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
    fun `outside click closes active inspector dropdown`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = true)
        setViewport(fixture, 420, 280)
        scrollInspectorBodyDown(fixture, steps = 10)

        val (trigger, ownerKey) = openInspectorSelectDropdown(fixture, requireScrollable = false)
        val dropdown = selectPanelRect(ownerKey, fixture)
        val panelRect = fixture.inspector.overlayPanelRect() ?: error("expected panel rect")
        val outsideX = (panelRect.x - 12).coerceAtLeast(1)
        val outsideY = (panelRect.y - 12).coerceAtLeast(1)
        assertFalse(dropdown.contains(outsideX, outsideY))
        assertFalse(trigger.contains(outsideX, outsideY))

        assertTrue(dispatchSystemMouseDown(fixture, outsideX, outsideY))
        dispatchSystemMouseUp(fixture, outsideX, outsideY)
        syncAndRender(fixture, outsideX, outsideY)

        waitForSystemSelectClosed(fixture, ownerKey, outsideX, outsideY)
    }

    @Test
    fun `wheel is routed to active dropdown before inspector body`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = false)
        val (_, ownerKey) = openVisibleInspectorSelectDropdownWithoutBodyScroll(fixture)
        settleFrames(fixture, steps = 1)
        val beforePanelScroll = fixture.inspector.panelScrollOffsetY

        val popup = selectPanelRect(ownerKey, fixture)
        val wheelX = popup.x + (popup.width / 2).coerceAtLeast(1)
        val wheelY = popup.y + (popup.height / 2).coerceAtLeast(1)

        assertTrue(dispatchSystemMouseWheel(fixture, wheelX, wheelY, -120))
        syncAndRender(fixture, wheelX, wheelY)

        assertTrue(SelectRuntime.systemEngine.isOpenFor(ownerKey))
        assertEquals(beforePanelScroll, fixture.inspector.panelScrollOffsetY)
    }

    @Test
    fun `inspector scroll keeps dropdown anchored under visible trigger`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = true)
        setViewport(fixture, 420, 280)
        scrollInspectorBodyDown(fixture, steps = 12)

        val (trigger, ownerKey) = openInspectorSelectDropdown(fixture, requireScrollable = false)
        val popup = selectPanelRect(ownerKey, fixture)
        assertTrue(popup.y >= 0)
        assertTrue(popup.y + popup.height <= fixture.viewportHeight)
    }

    @Test
    fun `dropdown x alignment follows visible trigger after inspector scroll`() {
        val fixture = openInspectorAndSelectTarget(withManyChildren = true)
        setViewport(fixture, 420, 280)
        scrollInspectorBodyDown(fixture, steps = 12)

        val (trigger, ownerKey) = openInspectorSelectDropdown(fixture, requireScrollable = false)
        val popup = selectPanelRect(ownerKey, fixture)

        val expectedX =
            trigger.x
                .coerceIn(2, (fixture.viewportWidth - popup.width - 2).coerceAtLeast(2))
        assertEquals(expectedX, popup.x)
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

    private fun settleFrames(fixture: Fixture, steps: Int) {
        val contentRect = fixture.inspector.overlayContentRect()
        val cursorX = contentRect.x + 4
        val cursorY = contentRect.y + 10
        repeat(steps) {
            syncAndRender(fixture, cursorX, cursorY)
        }
    }

    private fun openVisibleInspectorSelectDropdownWithoutBodyScroll(fixture: Fixture): Pair<Rect, String> {
        val contentRect = fixture.inspector.overlayContentRect()
        val bodyScrollY = fixture.inspector.panelScrollOffsetY
        val row =
            fixture.inspector.overlayStyleEditorRows().firstOrNull { row ->
                if (row.editorKind != InspectorEditorKind.EnumSelect &&
                    row.editorKind != InspectorEditorKind.FontSelect
                ) {
                    return@firstOrNull false
                }
                val visibleRect =
                    Rect(
                        row.controlRect.x,
                        row.controlRect.y - bodyScrollY,
                        row.controlRect.width,
                        row.controlRect.height,
                    )
                val centerX = visibleRect.x + (visibleRect.width / 2).coerceAtLeast(1)
                val centerY = visibleRect.y + (visibleRect.height / 2).coerceAtLeast(1)
                contentRect.contains(centerX, centerY)
            } ?: error("expected visible inspector select row without body scrolling")

        val triggerRect =
            Rect(
                row.controlRect.x,
                row.controlRect.y - bodyScrollY,
                row.controlRect.width,
                row.controlRect.height,
            )
        val rowIndex =
            fixture.inspector
                .overlayStyleEditorRows()
                .indexOfFirst { it.property == row.property }
                .takeIf { it >= 0 } ?: error("expected style row index for ${row.property.key}")
        val ownerKey = "dsgl-system-inspector-editor-select-$rowIndex"
        val clickX = triggerRect.x + 2
        val clickY = triggerRect.y + (triggerRect.height / 2).coerceAtLeast(1)
        dispatchSystemMouseDown(fixture, clickX, clickY)
        dispatchSystemMouseUp(fixture, clickX, clickY)
        syncAndRender(fixture, clickX, clickY)

        assertTrue(SelectRuntime.systemEngine.isOpenFor(ownerKey))
        return triggerRect to ownerKey
    }

    private fun openInspectorSelectDropdown(fixture: Fixture, requireScrollable: Boolean): Pair<Rect, String> {
        repeat(120) {
            val contentRect = fixture.inspector.overlayContentRect()
            val bodyScrollY = fixture.inspector.panelScrollOffsetY
            val visibleSelectRows =
                fixture.inspector.overlayStyleEditorRows().filter { row ->
                    if (row.editorKind != InspectorEditorKind.EnumSelect &&
                        row.editorKind != InspectorEditorKind.FontSelect
                    ) {
                        return@filter false
                    }
                    val visibleRect =
                        Rect(
                            row.controlRect.x,
                            row.controlRect.y - bodyScrollY,
                            row.controlRect.width,
                            row.controlRect.height,
                        )
                    val centerX = visibleRect.x + (visibleRect.width / 2).coerceAtLeast(1)
                    val centerY = visibleRect.y + (visibleRect.height / 2).coerceAtLeast(1)
                    contentRect.contains(centerX, centerY)
                }

            visibleSelectRows.forEach { row ->
                val triggerRect =
                    Rect(
                        row.controlRect.x,
                        row.controlRect.y - bodyScrollY,
                        row.controlRect.width,
                        row.controlRect.height,
                    )
                val rowIndex =
                    fixture.inspector
                        .overlayStyleEditorRows()
                        .indexOfFirst { it.property == row.property }
                        .takeIf { it >= 0 } ?: return@forEach
                val ownerKey = "dsgl-system-inspector-editor-select-$rowIndex"
                val clickX = triggerRect.x + 2
                val clickY = triggerRect.y + (triggerRect.height / 2).coerceAtLeast(1)
                dispatchSystemMouseDown(fixture, clickX, clickY)
                dispatchSystemMouseUp(fixture, clickX, clickY)
                syncAndRender(fixture, clickX, clickY)

                val opened = SelectRuntime.systemEngine.isOpenFor(ownerKey)
                if (opened) {
                    val popup = selectPanelRect(ownerKey, fixture)
                    if (!requireScrollable || popup.height > triggerRect.height + 24) {
                        return triggerRect to ownerKey
                    }
                }
                if (opened) {
                    dispatchSystemMouseDown(fixture, clickX, clickY)
                    dispatchSystemMouseUp(fixture, clickX, clickY)
                    syncAndRender(fixture, clickX, clickY)
                }
            }

            scrollInspectorBodyDown(fixture, steps = 1)
        }
        error("expected inspector select dropdown to open")
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
