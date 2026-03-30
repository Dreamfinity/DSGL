package org.dreamfinity.dsgl.core.overlay.system

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
import org.dreamfinity.dsgl.core.input.ClipboardAccess
import org.dreamfinity.dsgl.core.input.ClipboardBridge
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleProperty

class InspectorTextEditingDomMigrationTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    private val clipboard = RecordingClipboardAccess()

    @AfterTest
    fun cleanup() {
        FocusManager.clearFocus()
        KeyModifiers.sync(shift = false, control = false, meta = false)
        ClipboardBridge.install(null)
        ColorPickerRuntime.engine.closeAll()
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `inspector live text editing is dom-first and controller edit session stays inactive`() {
        val fixture = openInspectorAndSelectTarget()
        val stringInput = findVisibleInputNode(
            host = fixture.host,
            inspector = fixture.inspector,
            keyPrefix = "dsgl-system-inspector-editor-input-"
        )

        val focused = focusInputByClick(fixture.host, stringInput)
        val nearEndX = (stringInput.bounds.x + stringInput.bounds.width - 3).coerceAtLeast(stringInput.bounds.x + 1)
        fixture.host.handleMouseDown(nearEndX, focused.second, MouseButton.LEFT)
        fixture.host.handleMouseUp(nearEndX, focused.second, MouseButton.LEFT)
        val beforeType = stringInput.text
        assertTrue(fixture.host.handleKeyDown(KeyCodes.BACKSPACE, 0.toChar()))
        assertTrue(stringInput.text.length < beforeType.length)
        assertNull(fixture.inspector.debugActiveEditBuffer())
    }

    @Test
    fun `inspector caret placement drag selection and shift arrows are dom-first`() {
        ClipboardBridge.install(clipboard)

        val fixture = openInspectorAndSelectTarget()
        val input = findVisibleInputNode(
            host = fixture.host,
            inspector = fixture.inspector,
            keyPrefix = "dsgl-system-inspector-editor-input-"
        )

        val focused = focusInputByClick(fixture.host, input)
        val y = focused.second
        val dragStartX = (focused.first - 24).coerceAtLeast(input.bounds.x + 2)
        val dragEndX = input.bounds.x + input.bounds.width - 3

        fixture.host.handleMouseDown(dragStartX, y, MouseButton.LEFT)
        fixture.host.handleMouseMove(dragEndX, y)
        fixture.host.handleMouseUp(dragEndX, y, MouseButton.LEFT)

        KeyModifiers.sync(shift = false, control = true, meta = false)
        val beforeCut = input.text
        assertTrue(fixture.host.handleKeyDown(KeyCodes.X, 'x'))
        assertTrue(input.text.length <= beforeCut.length)
        assertTrue(fixture.host.handleKeyDown(KeyCodes.V, 'v'))

        val nearEndX = input.bounds.x + input.bounds.width - 4
        fixture.host.handleMouseDown(nearEndX, y, MouseButton.LEFT)
        fixture.host.handleMouseUp(nearEndX, y, MouseButton.LEFT)

        KeyModifiers.sync(shift = true, control = false, meta = false)
        assertTrue(fixture.host.handleKeyDown(KeyCodes.LEFT, 0.toChar()))
        KeyModifiers.sync(shift = false, control = true, meta = false)
        assertTrue(fixture.host.handleKeyDown(KeyCodes.C, 'c'))
        assertEquals(1, clipboard.value.length)

        KeyModifiers.sync(shift = false, control = false, meta = false)
        assertNull(fixture.inspector.debugActiveEditBuffer())
    }

    @Test
    fun `inspector keyboard editing shortcuts run through dom input nodes`() {
        ClipboardBridge.install(clipboard)

        val fixture = openInspectorAndSelectTarget()
        val numericInput = findVisibleInputNode(
            host = fixture.host,
            inspector = fixture.inspector,
            keyPrefix = "dsgl-system-inspector-editor-numeric-input-width"
        )

        focusInputByClick(fixture.host, numericInput)
        assertEquals(numericInput.key, FocusManager.focusedNode()?.key)

        KeyModifiers.sync(shift = false, control = true, meta = false)
        assertTrue(fixture.host.handleKeyDown(KeyCodes.A, 'a'))
        assertTrue(fixture.host.handleKeyDown(KeyCodes.C, 'c'))
        val copied = clipboard.value
        assertTrue(copied.isNotEmpty())

        assertTrue(fixture.host.handleKeyDown(KeyCodes.X, 'x'))
        assertEquals("", numericInput.text)

        assertTrue(fixture.host.handleKeyDown(KeyCodes.V, 'v'))
        assertEquals(copied, numericInput.text)

        KeyModifiers.sync(shift = false, control = false, meta = false)
        assertTrue(fixture.host.handleKeyDown(KeyCodes.BACKSPACE, 0.toChar()))
        assertTrue(fixture.host.handleKeyDown(0, '7'))
        assertTrue(numericInput.text.contains('7'))
        assertTrue(fixture.host.handleKeyDown(KeyCodes.DELETE, 0.toChar()))
        assertNull(fixture.inspector.debugActiveEditBuffer())
    }

    @Test
    fun `inspector active text editing remains coherent across rebuilds`() {
        val fixture = openInspectorAndSelectTarget()
        var revision = fixture.nextRevision

        val firstInput = findVisibleInputNode(
            host = fixture.host,
            inspector = fixture.inspector,
            keyPrefix = "dsgl-system-inspector-editor-numeric-input-width"
        )
        val inputKey = firstInput.key ?: error("expected keyed inspector input")
        val focused = focusInputByClick(fixture.host, firstInput)
        val focusX = focused.first
        val focusY = focused.second

        KeyModifiers.sync(shift = false, control = true, meta = false)
        assertTrue(fixture.host.handleKeyDown(KeyCodes.A, 'a'))
        KeyModifiers.sync(shift = false, control = false, meta = false)
        assertTrue(fixture.host.handleKeyDown(0, '4'))
        assertTrue(fixture.host.handleKeyDown(0, '2'))

        repeat(3) {
            renderInspectorFrame(fixture, revision++, focusX, focusY)
            assertEquals(inputKey, FocusManager.focusedNode()?.key)
        }

        val refreshed = findVisibleInputNode(
            host = fixture.host,
            inspector = fixture.inspector,
            keyPrefix = "dsgl-system-inspector-editor-numeric-input-width"
        )
        assertEquals(inputKey, refreshed.key)
        assertEquals("42", refreshed.text)

        KeyModifiers.sync(shift = false, control = true, meta = false)
        assertTrue(fixture.host.handleKeyDown(KeyCodes.A, 'a'))
        KeyModifiers.sync(shift = false, control = false, meta = false)
        assertTrue(fixture.host.handleKeyDown(0, '3'))
        assertTrue(fixture.host.handleKeyDown(0, '7'))

        renderInspectorFrame(fixture, revision++, focusX, focusY)
        val refreshedAgain = findVisibleInputNode(
            host = fixture.host,
            inspector = fixture.inspector,
            keyPrefix = "dsgl-system-inspector-editor-numeric-input-width"
        )
        assertEquals("37", refreshedAgain.text)
        assertNull(fixture.inspector.debugActiveEditBuffer())
    }

    @Test
    fun `inspector color edit integration remains system owned after text migration`() {
        val fixture = openInspectorAndSelectTarget()
        val anchor = Rect(80, 80, 20, 18)

        assertTrue(fixture.inspector.debugOpenColorPickerForSelection(StyleProperty.BACKGROUND_COLOR, anchor))
        renderInspectorFrame(fixture, fixture.nextRevision, anchor.x + 1, anchor.y + 1)

        assertTrue(fixture.host.isSystemColorPickerOpen())
        assertEquals(OverlayOwnerScope.System, fixture.host.debugSystemColorPickerPopupOwnerScope())
        assertNotNull(fixture.host.debugEntryNode(SystemOverlayEntryId.ColorPickerPopup))
    }

    private fun openInspectorAndSelectTarget(): Fixture {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        inspector.installColorPickerHost(host.systemInspectorColorPickerPopupHost())
        val (root, target) = inspectedRoot()

        inspector.toggle()
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)
        host.handleMouseDown(984, 144, MouseButton.LEFT)
        host.handleMouseUp(984, 144, MouseButton.LEFT)
        inspector.setPickMode(false)

        host.syncFrame(root, inspectedLayoutRevision = 2L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)

        return Fixture(inspector, host, root, target, nextRevision = 3L)
    }

    private fun renderInspectorFrame(fixture: Fixture, revision: Long, cursorX: Int, cursorY: Int) {
        fixture.host.syncFrame(
            inspectedRoot = fixture.root,
            inspectedLayoutRevision = revision,
            cursorX = cursorX,
            cursorY = cursorY,
            inspectorPointerCaptured = fixture.inspector.isPointerCaptured
        )
        fixture.host.render(ctx, 1280, 720)
    }

    private fun findVisibleInputNode(host: SystemOverlayHost, inspector: InspectorController, keyPrefix: String): TextInputNode {
        val inspectorNode = host.debugEntryNode(SystemOverlayEntryId.Inspector) ?: error("inspector entry missing")
        val contentRect = inspector.overlayContentRect()
        val candidates = collectNodes(inspectorNode)
            .filterIsInstance<TextInputNode>()
            .filter { (it.key?.toString() ?: "").startsWith(keyPrefix) }

        val visible = candidates.firstOrNull { node ->
            val probeX = node.bounds.x + 2
            val probeY = node.bounds.y + (node.bounds.height / 2).coerceAtLeast(1)
            contentRect.contains(probeX, probeY)
        }
        return visible ?: candidates.firstOrNull() ?: error("expected inspector input for prefix '$keyPrefix'")
    }

    private fun focusInputByClick(host: SystemOverlayHost, input: TextInputNode): Pair<Int, Int> {
        val y = input.bounds.y + (input.bounds.height / 2).coerceAtLeast(1)
        val left = (input.bounds.x + 2).coerceAtMost(input.bounds.x + input.bounds.width - 2)
        val center = input.bounds.x + (input.bounds.width / 2).coerceAtLeast(1)
        val right = (input.bounds.x + input.bounds.width - 3).coerceAtLeast(input.bounds.x + 1)
        val points = listOf(left, center, right)
        points.forEach { x ->
            host.handleMouseDown(x, y, MouseButton.LEFT)
            host.handleMouseUp(x, y, MouseButton.LEFT)
            if (FocusManager.focusedNode()?.key == input.key) {
                return x to y
            }
        }
        FocusManager.requestFocus(input)
        return center to y
    }

    private fun inspectedRoot(): Pair<ContainerNode, ContainerNode> {
        val root = ContainerNode(key = "root")
        root.bounds = Rect(0, 0, 1280, 720)
        val target = ContainerNode(key = "target").apply {
            bounds = Rect(980, 140, 120, 30)
        }
        target.applyParent(root)
        StyleEngine.setInspectorOverrideLiteral(target, StyleProperty.BACKGROUND_COLOR, "#FF112233").getOrThrow()
        return root to target
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
        val target: ContainerNode,
        val nextRevision: Long
    )

    private class RecordingClipboardAccess : ClipboardAccess {
        var value: String = ""

        override fun readText(): String = value

        override fun writeText(value: String) {
            this.value = value
        }
    }
}


