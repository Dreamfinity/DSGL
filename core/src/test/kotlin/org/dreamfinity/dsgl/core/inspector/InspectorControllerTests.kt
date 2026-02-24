package org.dreamfinity.dsgl.core.inspector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand

class InspectorControllerTests {
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
        controller.minimize()
        controller.appendOverlayCommands(220, 140, mutableListOf())

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
        controller.minimize()
        controller.appendOverlayCommands(220, 140, mutableListOf())

        val (startX, startY) = controller.panelPosition
        assertTrue(controller.handleMouseDown(startX + 2, startY + 2, MouseButton.LEFT))
        controller.onCapturedPointerMove(999, 999, 220, 140)
        assertTrue(controller.handleMouseUp(999, 999, MouseButton.LEFT))

        val (x, y) = controller.panelPosition
        assertEquals(58, x)
        assertEquals(112, y)
        assertEquals(InspectorPanelState.Minimized, controller.panelState)
    }

    @Test
    fun `expanded panel starts resize drag from edge`() {
        val controller = InspectorController()
        controller.toggle()
        val root = container("root", 0, 0, 800, 600)
        controller.onLayoutCommitted(root, 1L)
        controller.appendOverlayCommands(800, 600, mutableListOf())

        assertTrue(controller.handleMouseDown(381, 120, MouseButton.LEFT))
        assertTrue(controller.isDraggingPanel)
        controller.onCapturedPointerMove(500, 120, 800, 600)
        assertTrue(controller.handleMouseUp(500, 120, MouseButton.LEFT))
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

        val commands = mutableListOf<RenderCommand>()
        controller.appendOverlayCommands(600, 300, commands)
        val (chipX, chipY) = controller.panelPosition
        val chipTextLines = commands
            .filterIsInstance<RenderCommand.DrawText>()
            .filter { it.x >= chipX && it.y in chipY..(chipY + 25) }

        assertTrue(chipTextLines.isNotEmpty())
        assertTrue(chipTextLines.size <= 2)
        chipTextLines.forEach { line ->
            assertTrue(line.text.length <= 24)
        }
    }

    @Test
    fun `locked inspector consumes input only inside panel`() {
        val controller = InspectorController()
        controller.toggle()
        controller.toggleMode() // locked
        val root = container("root", 0, 0, 800, 600)
        controller.onLayoutCommitted(root, 1L)
        controller.appendOverlayCommands(800, 600, mutableListOf())

        assertTrue(controller.shouldConsumePointer(30, 30))
        assertTrue(controller.shouldConsumeWheel(30, 30))
        assertTrue(controller.shouldConsumeKeyboard(30, 30))
        assertFalse(controller.shouldConsumePointer(700, 500))
        assertFalse(controller.shouldConsumeWheel(700, 500))
        assertFalse(controller.shouldConsumeKeyboard(700, 500))
    }

    @Test
    fun `pick mode consumes clicks outside inspector for selection`() {
        val controller = InspectorController()
        controller.toggle() // pick
        val root = container("root", 0, 0, 800, 600)
        controller.onLayoutCommitted(root, 1L)
        controller.appendOverlayCommands(800, 600, mutableListOf())

        assertTrue(controller.shouldConsumePointer(700, 500))
        assertTrue(controller.shouldConsumeWheel(700, 500))
        assertTrue(controller.shouldConsumeKeyboard(700, 500))
    }

    @Test
    fun `hover pick is suppressed over inspector ui and resumes over app`() {
        val controller = InspectorController()
        controller.toggle()

        val root = container("root", 0, 0, 800, 600)
        container("inside-panel", 36, 36, 80, 24).applyParent(root)
        container("outside-panel", 560, 140, 80, 24).applyParent(root)
        controller.onLayoutCommitted(root, 1L)
        controller.appendOverlayCommands(800, 600, mutableListOf())

        controller.onCursorMoved(40, 40)
        assertNull(controller.hoveredKey)

        val overPanelCommands = mutableListOf<RenderCommand>()
        controller.appendOverlayCommands(800, 600, overPanelCommands)
        val tooltipTextsOverPanel = overPanelCommands
            .filterIsInstance<RenderCommand.DrawText>()
            .filter { it.text.contains("div[") && it.text.contains("x") && it.text.contains("@") }
        assertTrue(tooltipTextsOverPanel.isEmpty())

        controller.onCursorMoved(564, 144)
        assertEquals("outside-panel", controller.hoveredKey)
    }

    @Test
    fun `hover tracking runs only while pick mode is enabled`() {
        val controller = InspectorController()
        controller.toggle()

        val root = container("root", 0, 0, 800, 600)
        container("target", 560, 320, 80, 24).applyParent(root)
        controller.onLayoutCommitted(root, 1L)
        controller.appendOverlayCommands(800, 600, mutableListOf())

        controller.setPickMode(false)
        controller.onCursorMoved(564, 324)
        assertNull(controller.hoveredKey)

        controller.setPickMode(true)
        controller.onCursorMoved(564, 324)
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
        controller.appendOverlayCommands(900, 640, mutableListOf())
        controller.onCursorMoved(768, 126)
        controller.handleMouseDown(768, 126, MouseButton.LEFT)

        controller.appendOverlayCommands(320, 220, mutableListOf())
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
    fun `path breadcrumb wraps to multiple lines inside narrow panel`() {
        val controller = InspectorController()
        controller.toggle()

        val root = container("root-with-an-extremely-long-token-for-wrapping", 0, 0, 1200, 800)
        val middle = container("middle-node-with-very-long-name-segment", 600, 100, 140, 90)
        val leaf = container("leaf-node-with-even-longer-name-segment-for-wrapping", 620, 120, 120, 40)
        middle.applyParent(root)
        leaf.applyParent(middle)

        controller.onLayoutCommitted(root, 1L)
        controller.appendOverlayCommands(900, 640, mutableListOf())
        controller.onCursorMoved(626, 126)
        controller.handleMouseDown(626, 126, MouseButton.LEFT)

        val commands = mutableListOf<RenderCommand>()
        controller.appendOverlayCommands(260, 220, commands)
        val pathLines = commands
            .filterIsInstance<RenderCommand.DrawText>()
            .map { it.text }
            .filter { it.startsWith("Path:") || it.startsWith("  >") || it.startsWith("  root") || it.startsWith("  middle") || it.startsWith("  leaf") }

        assertTrue(pathLines.size >= 2)
        pathLines.forEach { line ->
            assertTrue(line.length <= 45)
        }
    }

    private fun container(key: Any, x: Int, y: Int, width: Int, height: Int): ContainerNode {
        return ContainerNode(key = key).apply {
            bounds = Rect(x, y, width, height)
        }
    }
}
