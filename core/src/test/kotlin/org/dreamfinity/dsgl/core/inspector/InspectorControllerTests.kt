package org.dreamfinity.dsgl.core.inspector

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerState
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerStyle
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.colorpicker.internal.InspectorColorPickerHost
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.input.ClipboardAccess
import org.dreamfinity.dsgl.core.input.ClipboardBridge
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty

class InspectorControllerTests {
    @AfterTest
    fun cleanup() {
        StyleEngine.clearAllInspectorOverrides()
    }

    @Test
    fun `selection rebinds by stable key across layout commits`() {
        val controller = InspectorController()
        controller.toggle()

        val root1 = container("root", 0, 0, 220, 140)
        container("child", 12, 10, 80, 24).applyParent(root1)
        controller.onLayoutCommitted(root1, 1L)
        controller.onCursorMoved(16, 16)
        controller.handleMouseDown(16, 16, MouseButton.LEFT)
        assertEquals("child", controller.selectedKey)

        val root2 = container("root", 0, 0, 220, 140)
        container("child", 22, 18, 80, 24).applyParent(root2)
        controller.onLayoutCommitted(root2, 2L)
        assertEquals("child", controller.selectedKey)
    }

    @Test
    fun `selection clears when keyed node is removed`() {
        val controller = InspectorController()
        controller.toggle()

        val root1 = container("root", 0, 0, 220, 140)
        container("child", 12, 10, 80, 24).applyParent(root1)
        controller.onLayoutCommitted(root1, 1L)
        controller.onCursorMoved(16, 16)
        controller.handleMouseDown(16, 16, MouseButton.LEFT)
        assertEquals("child", controller.selectedKey)

        val root2 = container("root", 0, 0, 220, 140)
        controller.onLayoutCommitted(root2, 2L)
        assertNull(controller.selectedKey)
    }

    @Test
    fun `minimized widget click restores expanded panel`() {
        val controller = InspectorController()
        controller.toggle()
        val root = container("root", 0, 0, 220, 140)
        controller.onLayoutCommitted(root, 1L)
        renderFrame(controller, 220, 140)
        controller.minimize()
        renderFrame(controller, 220, 140)

        assertEquals(InspectorPanelState.Minimized, controller.panelState)
        val (startX, startY) = controller.panelPosition
        val clickX = startX + 2
        val clickY = startY + 2
        assertTrue(controller.handleMouseDown(clickX, clickY, MouseButton.LEFT))
        assertTrue(controller.isDraggingPanel)
        assertTrue(controller.handleMouseUp(clickX, clickY, MouseButton.LEFT))
        assertFalse(controller.isDraggingPanel)
        assertEquals(InspectorPanelState.Expanded, controller.panelState)
    }

    @Test
    fun `minimized widget drag clamps to viewport`() {
        val controller = InspectorController()
        controller.toggle()
        val root = container("root", 0, 0, 220, 140)
        controller.onLayoutCommitted(root, 1L)
        renderFrame(controller, 220, 140)
        controller.minimize()
        renderFrame(controller, 220, 140)

        val (startX, startY) = controller.panelPosition
        assertTrue(controller.handleMouseDown(startX + 2, startY + 2, MouseButton.LEFT))
        controller.onCapturedPointerMove(999, 999, 220, 140)
        assertTrue(controller.handleMouseUp(999, 999, MouseButton.LEFT))

        val (x, y) = controller.panelPosition
        assertEquals(2, x)
        assertEquals(82, y)
        assertEquals(InspectorPanelState.Minimized, controller.panelState)
    }

    @Test
    fun `expanded panel starts resize drag from edge`() {
        val controller = InspectorController()
        controller.toggle()
        val root = container("root", 0, 0, 1400, 900)
        controller.onLayoutCommitted(root, 1L)
        renderFrame(controller, 1400, 900)

        assertTrue(controller.handleMouseDown(778, 120, MouseButton.LEFT))
        assertTrue(controller.isDraggingPanel)
        controller.onCapturedPointerMove(720, 120, 800, 600)
        assertTrue(controller.handleMouseUp(720, 120, MouseButton.LEFT))
        assertFalse(controller.isDraggingPanel)
    }

    @Test
    fun `minimized chip wraps long label into bounded lines`() {
        val controller = InspectorController()
        controller.toggle()
        val root = container("root", 0, 0, 600, 300)
        val child = container("super-long-selected-node-key-to-force-chip-wrap-behavior", 20, 20, 120, 24)
        child.applyParent(root)
        controller.onLayoutCommitted(root, 1L)
        controller.onCursorMoved(22, 22)
        controller.handleMouseDown(22, 22, MouseButton.LEFT)
        controller.minimize()
        controller.onCursorMoved(-100, -100)

        val snapshot = renderFrame(controller, 600, 300)
        assertEquals(InspectorPanelState.Minimized, snapshot.panelState)
        val chipTextLines = snapshot.minimizedLines
        assertTrue(chipTextLines.isNotEmpty())
        assertTrue(chipTextLines.size <= 2)
        chipTextLines.forEach { line ->
            assertTrue(line.length <= 24)
        }
    }

    @Test
    fun `locked inspector consumes input only inside panel`() {
        val controller = InspectorController()
        controller.toggle()
        controller.toggleMode()
        val root = container("root", 0, 0, 1400, 900)
        controller.onLayoutCommitted(root, 1L)
        renderFrame(controller, 1400, 900)

        assertTrue(controller.shouldConsumePointer(30, 30))
        assertTrue(controller.shouldConsumeWheel(30, 30))
        assertTrue(controller.shouldConsumeKeyboard(30, 30))
        assertFalse(controller.shouldConsumePointer(795, 500))
        assertFalse(controller.shouldConsumeWheel(795, 500))
        assertFalse(controller.shouldConsumeKeyboard(795, 500))
    }

    @Test
    fun `pick mode consumes clicks outside inspector for selection`() {
        val controller = InspectorController()
        controller.toggle()
        val root = container("root", 0, 0, 1400, 900)
        controller.onLayoutCommitted(root, 1L)
        renderFrame(controller, 1400, 900)

        assertTrue(controller.shouldConsumePointer(700, 500))
        assertTrue(controller.shouldConsumeWheel(700, 500))
        assertTrue(controller.shouldConsumeKeyboard(700, 500))
    }

    @Test
    fun `hover pick is suppressed over inspector ui and resumes over app`() {
        val controller = InspectorController()
        controller.toggle()

        val root = container("root", 0, 0, 1200, 700)
        container("inside-panel", 36, 36, 80, 24).applyParent(root)
        container("outside-panel", 980, 140, 80, 24).applyParent(root)
        controller.onLayoutCommitted(root, 1L)
        renderFrame(controller, 1200, 700)

        controller.onCursorMoved(40, 40)
        assertNull(controller.hoveredKey)

        controller.onCursorMoved(984, 144)
        assertEquals("outside-panel", controller.hoveredKey)
    }

    @Test
    fun `hover tracking runs only while pick mode is enabled`() {
        val controller = InspectorController()
        controller.toggle()

        val root = container("root", 0, 0, 1200, 700)
        container("target", 980, 320, 80, 24).applyParent(root)
        controller.onLayoutCommitted(root, 1L)
        renderFrame(controller, 1200, 700)

        controller.setPickMode(false)
        controller.onCursorMoved(984, 324)
        assertNull(controller.hoveredKey)

        controller.setPickMode(true)
        controller.onCursorMoved(984, 324)
        assertEquals("target", controller.hoveredKey)
    }

    @Test
    fun `expanded inspector panel scrolls body content with mouse wheel`() {
        val controller = InspectorController()
        controller.toggle()

        val root = container("root", 0, 0, 1200, 800)
        val selected = container("selected-target", 760, 120, 180, 120)
        selected.applyParent(root)
        repeat(40) { index ->
            container("child-$index", 760, 150 + index * 12, 120, 10).applyParent(selected)
        }
        controller.onLayoutCommitted(root, 1L)
        renderFrame(controller, 900, 640)
        controller.onCursorMoved(768, 126)
        controller.handleMouseDown(768, 126, MouseButton.LEFT)

        renderFrame(controller, 320, 220)
        assertEquals(0, controller.panelScrollOffsetY)
        assertTrue(controller.handleMouseWheel(46, 90, -120))
        assertTrue(controller.panelScrollOffsetY > 0)

        repeat(100) { controller.handleMouseWheel(46, 90, -120) }
        val scrolled = controller.panelScrollOffsetY
        assertTrue(scrolled > 0)

        repeat(100) { controller.handleMouseWheel(46, 90, 120) }
        assertEquals(0, controller.panelScrollOffsetY)
    }

    @Test
    fun `expanded inspector panel scrolls by dragging scrollbar thumb`() {
        val controller = InspectorController()
        controller.toggle()

        val root = container("root", 0, 0, 1600, 1200)
        val selected = container("selected-scroll-target", 980, 220, 260, 180)
        selected.applyParent(root)
        repeat(60) { index ->
            container("scroll-child-$index", 980, 240 + index * 14, 180, 10).applyParent(selected)
        }
        controller.onLayoutCommitted(root, 1L)
        renderFrame(controller, 1400, 900)
        controller.onCursorMoved(990, 230)
        controller.handleMouseDown(990, 230, MouseButton.LEFT)

        renderFrame(controller, 420, 280)
        val thumb = controller.debugScrollbarThumbRect()
        if (thumb.width <= 0 || thumb.height <= 0) {
            fail("Expected inspector scrollbar thumb to be available.")
        }

        val thumbCenterX = thumb.x + 1
        val thumbCenterY = thumb.y + (thumb.height / 2)
        assertTrue(controller.handleMouseDown(thumbCenterX, thumbCenterY, MouseButton.LEFT))
        controller.onCapturedPointerMove(thumbCenterX, thumbCenterY + 80, 420, 280)
        controller.handleMouseUp(thumbCenterX, thumbCenterY + 80, MouseButton.LEFT)
        assertTrue(controller.panelScrollOffsetY > 0)
    }

    @Test
    fun `header click is captured and starts panel drag`() {
        val controller = InspectorController()
        controller.toggle()
        val root = container("root", 0, 0, 900, 700)
        controller.onLayoutCommitted(root, 1L)
        renderFrame(controller, 900, 700)

        assertTrue(controller.handleMouseDown(38, 30, MouseButton.LEFT))
        assertTrue(controller.isDraggingPanel)
        controller.onCapturedPointerMove(96, 60, 900, 700)
        assertTrue(controller.handleMouseUp(96, 60, MouseButton.LEFT))
        assertFalse(controller.isDraggingPanel)
    }

    @Test
    fun `path breadcrumb wraps to multiple lines inside narrow panel`() {
        val controller = InspectorController()
        controller.toggle()

        val root = container("root-with-an-extremely-long-token-for-wrapping", 0, 0, 1400, 900)
        val middle = container("middle-node-with-very-long-name-segment", 980, 100, 140, 90)
        val leaf = container("leaf-node-with-even-longer-name-segment-for-wrapping", 1000, 120, 120, 40)
        middle.applyParent(root)
        leaf.applyParent(middle)

        controller.onLayoutCommitted(root, 1L)
        renderFrame(controller, 1200, 700)
        controller.onCursorMoved(1006, 126)
        controller.handleMouseDown(1006, 126, MouseButton.LEFT)

        val snapshot = renderFrame(controller, 520, 340)
        val pathLines = snapshot.infoLines
            .filter {
                it.startsWith("Path:") || it.startsWith("  >") || it.startsWith("  root") ||
                        it.startsWith("  middle") || it.startsWith("  leaf")
            }

        assertTrue(pathLines.size >= 2)
        pathLines.forEach { line ->
            assertTrue(line.length <= 45)
        }
    }
    @Test
    fun `expanded snapshot includes full computed style property list`() {
        val controller = InspectorController()
        controller.toggle()

        val root = container("root", 0, 0, 1400, 900)
        val selected = container("selected-target", 980, 120, 180, 120)
        selected.applyParent(root)
        controller.onLayoutCommitted(root, 1L)
        renderFrame(controller, 1200, 700)
        controller.onCursorMoved(988, 126)
        controller.handleMouseDown(988, 126, MouseButton.LEFT)

        val snapshot = renderFrame(controller, 700, 420)
        assertTrue(snapshot.styleLines.any { it.startsWith("background-image:") })
        assertTrue(snapshot.styleLines.any { it.startsWith("border-radius:") })
        assertTrue(snapshot.styleLines.any { it.startsWith("align:") })
        assertTrue(snapshot.styleLines.any { it.startsWith("justify-items:") })
        assertTrue(snapshot.styleLines.any { it.startsWith("flex-grow:") })
        assertTrue(snapshot.styleLines.any { it.startsWith("flex-shrink:") })
        assertTrue(snapshot.styleLines.any { it.startsWith("flex-basis:") })
        assertTrue(snapshot.styleLines.any { it.startsWith("grid-auto-flow:") })
    }

    @Test
    fun `child labels are ellipsized for narrow inspector widths`() {
        val controller = InspectorController()
        controller.toggle()

        val root = container("root", 0, 0, 1200, 800)
        val selected = container("selected-target", 980, 120, 180, 120)
        selected.applyParent(root)
        container("child-with-an-extremely-long-name-segment-for-ellipsis-validation", 992, 160, 120, 20)
            .applyParent(selected)
        controller.onLayoutCommitted(root, 1L)
        renderFrame(controller, 1200, 700)
        controller.onCursorMoved(988, 126)
        controller.handleMouseDown(988, 126, MouseButton.LEFT)

        val snapshot = renderFrame(controller, 320, 220)
        assertTrue(snapshot.childLabels.isNotEmpty())
        assertTrue(snapshot.childLabels.any { it.endsWith("...") })
    }
    @Test
    fun `inspector opens picker for color property and stays interactive after preview commit`() {
        val pickerHost = RecordingInspectorColorPickerHost()
        val controller = InspectorController(colorPickerManager = pickerHost)
        controller.toggle()

        val root = container("root", 0, 0, 1200, 800)
        val selected = container("selected-target", 980, 120, 180, 120)
        selected.applyParent(root)
        StyleEngine.setInspectorOverrideLiteral(selected, StyleProperty.BACKGROUND_COLOR, "#FF112233").getOrThrow()

        controller.onLayoutCommitted(root, 1L)
        renderFrame(controller, 900, 640)
        controller.onCursorMoved(988, 126)
        controller.handleMouseDown(988, 126, MouseButton.LEFT)

        assertTrue(
            controller.debugOpenColorPickerForSelection(
                StyleProperty.BACKGROUND_COLOR,
                Rect(120, 80, 16, 16)
            )
        )

        val opened = pickerHost.lastOpen
        assertNotNull(opened)
        assertTrue(pickerHost.isOpen())

        opened.onPreview?.invoke(RgbaColor(0.25f, 0.5f, 0.75f, 1f))
        val previewLiteral = (StyleEngine.inspectorOverrideFor(selected, StyleProperty.BACKGROUND_COLOR) as? StyleExpression.Literal)?.value
        assertEquals("#FF4080BF", previewLiteral)

        opened.onCommit?.invoke(RgbaColor(1f, 0f, 0f, 1f))
        val committedLiteral = (StyleEngine.inspectorOverrideFor(selected, StyleProperty.BACKGROUND_COLOR) as? StyleExpression.Literal)?.value
        assertEquals("#FFFF0000", committedLiteral)

        pickerHost.close()
        assertFalse(pickerHost.isOpen())
        assertTrue(controller.handleMouseDown(38, 30, MouseButton.LEFT))
        assertTrue(controller.isDraggingPanel)
        assertTrue(controller.handleMouseUp(38, 30, MouseButton.LEFT))
        assertFalse(controller.isDraggingPanel)
    }


    @Test
    fun `native inspector rows no longer activate controller text edit session`() {
        val controller = InspectorController()
        controller.toggle()

        val root = container("root", 0, 0, 1400, 900)
        val selected = container("target", 980, 120, 180, 120)
        selected.applyParent(root)
        StyleEngine.setInspectorOverrideLiteral(selected, StyleProperty.BACKGROUND_COLOR, "#FF112233").getOrThrow()

        controller.onLayoutCommitted(root, 1L)
        renderFrame(controller, 1200, 700)
        controller.onCursorMoved(988, 126)
        assertTrue(controller.handleMouseDown(988, 126, MouseButton.LEFT))
        renderFrame(controller, 1200, 700)

        val row = controller.debugStyleEditorRows().firstOrNull {
            it.property == StyleProperty.BACKGROUND_COLOR && it.editorKind == InspectorEditorKind.StringInput
        } ?: error("Expected color string input row.")
        val inputRect = row.inputRect ?: row.controlRect
        val clickX = inputRect.x + 10
        val clickY = inputRect.y + inputRect.height / 2

        controller.onCursorMoved(clickX, clickY)
        controller.handleMouseDown(clickX, clickY, MouseButton.LEFT)
        controller.handleMouseUp(clickX, clickY, MouseButton.LEFT)

        assertNull(controller.debugActiveEditBuffer())
        assertFalse(controller.handleKeyDown(KeyCodes.A, 'a'))
    }

    @Test
    fun `style editor rows grow and keep spacing when labels wrap in narrow layout`() {
        val controller = InspectorController()
        controller.toggle()

        val root = container("root", 0, 0, 1400, 900)
        val selected = container("target", 980, 120, 180, 120)
        selected.applyParent(root)
        controller.onLayoutCommitted(root, 1L)

        renderFrame(controller, 1200, 700)
        controller.onCursorMoved(988, 126)
        controller.handleMouseDown(988, 126, MouseButton.LEFT)

        renderFrame(controller, 260, 240)
        val rows = controller.debugStyleEditorRows()
        assertTrue(rows.isNotEmpty())

        assertTrue(rows.any { it.rowRect.height > it.controlRect.height + 8 })
        rows.zipWithNext().forEach { (prev, next) ->
            assertTrue(next.rowRect.y >= prev.rowRect.y + prev.rowRect.height + 4)
        }
    }
    @Test
    fun `inspector dropdown option click is consumed and applied over underlying controls`() {
        val controller = InspectorController()
        controller.toggle()

        val root = container("root", 0, 0, 1400, 900)
        val selected = container("target", 980, 120, 180, 120)
        selected.applyParent(root)

        controller.onLayoutCommitted(root, 1L)
        renderFrame(controller, 1200, 700)
        controller.onCursorMoved(988, 126)
        controller.handleMouseDown(988, 126, MouseButton.LEFT)
        renderFrame(controller, 1200, 700)

        val row = controller.debugStyleEditorRows().firstOrNull { it.editorKind == InspectorEditorKind.EnumSelect }
            ?: error("Expected enum select row.")
        val openX = row.controlRect.x + 4
        val openY = row.controlRect.y + row.controlRect.height / 2
        controller.onCursorMoved(openX, openY)
        assertTrue(controller.handleMouseDown(openX, openY, MouseButton.LEFT))

        renderFrame(controller, 1200, 700)
        val dropdown = controller.debugStyleEditorDropdowns().firstOrNull() ?: error("Expected open dropdown.")
        val option = dropdown.options.firstOrNull { !it.text.equals(row.controlValue, ignoreCase = true) }
            ?: dropdown.options.firstOrNull()
            ?: error("Expected dropdown option.")
        val optionX = option.rect.x + 2
        val optionY = option.rect.y + option.rect.height / 2

        assertTrue(controller.shouldConsumePointer(optionX, optionY))
        controller.onCursorMoved(optionX, optionY)
        assertTrue(controller.handleMouseDown(optionX, optionY, MouseButton.LEFT))

        renderFrame(controller, 1200, 700)
        assertTrue(controller.debugStyleEditorDropdowns().isEmpty())
        val literal = (StyleEngine.inspectorOverrideFor(selected, row.property) as? StyleExpression.Literal)?.value
        assertNotNull(literal)
        assertTrue(literal.equals(option.text, ignoreCase = true))
    }


    @Test
    fun `numeric override commit follows property grammar`() {
        val controller = InspectorController()
        controller.toggle()

        val root = container("root", 0, 0, 1400, 900)
        val selected = container("target", 980, 120, 180, 120)
        selected.applyParent(root)

        controller.onLayoutCommitted(root, 1L)
        renderFrame(controller, 1200, 700)
        controller.onCursorMoved(988, 126)
        controller.handleMouseDown(988, 126, MouseButton.LEFT)

        assertTrue(controller.debugApplyNumericOverride(StyleProperty.Z_INDEX, "5", "px"))
        val zIndexLiteral = (StyleEngine.inspectorOverrideFor(selected, StyleProperty.Z_INDEX) as? StyleExpression.Literal)?.value
        assertEquals("5", zIndexLiteral)

        assertTrue(controller.debugApplyNumericOverride(StyleProperty.WIDTH, "24", "em"))
        val widthLiteral = (StyleEngine.inspectorOverrideFor(selected, StyleProperty.WIDTH) as? StyleExpression.Literal)?.value
        assertEquals("24em", widthLiteral)
    }
    private fun renderFrame(controller: InspectorController, viewportWidth: Int, viewportHeight: Int): InspectorDomSnapshot {
        return controller.buildDomSnapshot(viewportWidth, viewportHeight)
            ?: error("Inspector snapshot must exist while active.")
    }

    private fun container(key: Any, x: Int, y: Int, width: Int, height: Int): ContainerNode {
        return ContainerNode(key = key).apply {
            bounds = Rect(x, y, width, height)
        }
    }


    private class RecordingClipboardAccess : ClipboardAccess {
        var contents: String = ""

        override fun readText(): String = contents

        override fun writeText(value: String) {
            contents = value
        }
    }
    private class RecordingInspectorColorPickerHost : InspectorColorPickerHost {
        var lastOpen: OpenCall? = null
        private var open: Boolean = false

        override fun open(
            anchorRect: Rect,
            title: String,
            state: ColorPickerState,
            style: ColorPickerStyle,
            width: Int,
            draggable: Boolean,
            closeOnOutsideClick: Boolean,
            onPreview: ((RgbaColor) -> Unit)?,
            onChange: ((RgbaColor) -> Unit)?,
            onCommit: ((RgbaColor) -> Unit)?,
            onClose: (() -> Unit)?
        ) {
            lastOpen = OpenCall(
                anchorRect = anchorRect,
                title = title,
                state = state,
                onPreview = onPreview,
                onChange = onChange,
                onCommit = onCommit,
                onClose = onClose
            )
            open = true
        }

        override fun close() {
            if (!open) return
            open = false
            lastOpen?.onClose?.invoke()
        }

        override fun isOpen(): Boolean = open
    }

    private data class OpenCall(
        val anchorRect: Rect,
        val title: String,
        val state: ColorPickerState,
        val onPreview: ((RgbaColor) -> Unit)?,
        val onChange: ((RgbaColor) -> Unit)?,
        val onCommit: ((RgbaColor) -> Unit)?,
        val onClose: (() -> Unit)?
    )
}

