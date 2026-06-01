package org.dreamfinity.dsgl.core.dnd.internal

import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.RangeInputNode
import org.dreamfinity.dsgl.core.dom.elements.TextInputNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseDownEvent
import org.dreamfinity.dsgl.core.event.MouseDragEvent
import org.dreamfinity.dsgl.core.event.collectHoverChain
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DefaultDndEngineTests {
    @AfterTest
    fun cleanup() {
        DefaultDndEngine.cancelActiveDrag()
    }

    @Test
    fun `drop target selection prefers deepest candidate over previous ancestor`() {
        val list = ContainerNode(key = "list")
        val folder = ContainerNode(key = "folder")

        val selected =
            DefaultDndEngine.selectDropTargetCandidate(
                candidates = listOf(list, folder),
                previousTarget = list,
            )

        assertSame(folder, selected)
    }

    @Test
    fun `drop target selection keeps deepest candidate when already selected`() {
        val folder = ContainerNode(key = "folder")

        val selected =
            DefaultDndEngine.selectDropTargetCandidate(
                candidates = listOf(folder),
                previousTarget = folder,
            )

        assertSame(folder, selected)
    }

    @Test
    fun `shiftCommand translates checkerboard command without changing checker offsets`() {
        val shift =
            DefaultDndEngine::class.java.getDeclaredMethod(
                "shiftCommand",
                RenderCommand::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
        shift.isAccessible = true
        val command =
            RenderCommand.DrawCheckerboard(
                x = 10,
                y = 15,
                width = 20,
                height = 25,
                cellSize = 4,
                lightColor = 0xFFCCDDEE.toInt(),
                darkColor = 0xFF445566.toInt(),
                offsetX = 3,
                offsetY = 7,
            )

        val shifted = shift.invoke(DefaultDndEngine, command, 8, -5) as RenderCommand.DrawCheckerboard

        assertEquals(18, shifted.x)
        assertEquals(10, shifted.y)
        assertEquals(command.offsetX, shifted.offsetX)
        assertEquals(command.offsetY, shifted.offsetY)
    }

    @Test
    fun `drop target selection returns null for empty candidates`() {
        val selected =
            DefaultDndEngine.selectDropTargetCandidate(
                candidates = emptyList(),
                previousTarget = null,
            )

        assertNull(selected)
    }

    @Test
    fun `cancelled mouse down does not arm dnd`() {
        val root = ContainerNode(key = "dnd-cancel-root")
        val draggable =
            ContainerNode(key = "dnd-cancel-source")
                .apply {
                    draggable = true
                    width = 80
                    height = 20
                }.applyParent(root)
        root.render(testMeasureContext(), 0, 0, 200, 120)
        val down =
            MouseDownEvent(10, 10, MouseButton.LEFT).also { event ->
                event.target = draggable
                event.cancelled = true
            }

        DefaultDndEngine.onMouseDown(root, draggable, down)
        DefaultDndEngine.onMouseMove(root, 80, 10)

        assertFalse(DefaultDndEngine.isDragging)
    }

    @Test
    fun `range input consumes pointer sequence before draggable ancestor can arm dnd`() {
        val root = ContainerNode(key = "dnd-range-root")
        val draggableParent =
            ContainerNode(key = "dnd-range-parent")
                .apply {
                    draggable = true
                    width = 160
                    height = 40
                }.applyParent(root)
        val range =
            RangeInputNode(value = 0L, min = 0L, max = 100L, key = "dnd-range")
                .applyParent(draggableParent)
        root.render(testMeasureContext(), 0, 0, 200, 120)
        range.render(testMeasureContext(), 10, 10, 120, 12)
        val down =
            MouseDownEvent(10, 16, MouseButton.LEFT).also { event ->
                event.target = range
            }
        val drag =
            MouseDragEvent(10, 16, 110, 0, MouseButton.LEFT).also { event ->
                event.target = range
            }

        EventBus.post(down)
        DefaultDndEngine.onMouseDown(root, range, down)
        EventBus.post(drag)
        DefaultDndEngine.onMouseMove(root, 120, 16)

        assertTrue(range.value > 0L)
        assertTrue(drag.cancelled)
        assertFalse(DefaultDndEngine.isDragging)
    }

    @Test
    fun `text input selection drag consumes before draggable ancestor can arm dnd`() {
        val root = ContainerNode(key = "dnd-text-root")
        val draggableParent =
            ContainerNode(key = "dnd-text-parent")
                .apply {
                    draggable = true
                    width = 180
                    height = 40
                }.applyParent(root)
        val input =
            TextInputNode(text = "abcdef", key = "dnd-text-input")
                .applyParent(draggableParent)
        root.render(testMeasureContext(), 0, 0, 240, 120)
        input.render(testMeasureContext(), 10, 10, 120, 20)
        val down =
            MouseDownEvent(12, 16, MouseButton.LEFT).also { event ->
                event.target = input
            }
        val drag =
            MouseDragEvent(12, 16, 90, 0, MouseButton.LEFT).also { event ->
                event.target = input
            }

        EventBus.post(down)
        DefaultDndEngine.onMouseDown(root, input, down)
        EventBus.post(drag)
        DefaultDndEngine.onMouseMove(root, 102, 16)

        assertFalse(DefaultDndEngine.isDragging)
        assertTrue(drag.cancelled)
    }

    @Test
    fun `hover chain remains coherent for dnd candidate selection after core hover migration`() {
        val root = ContainerNode(key = "dnd-root")
        val parent =
            ContainerNode(key = "dnd-parent")
                .apply {
                    width = 120
                    height = 60
                    droppable = true
                }.applyParent(root)
        val child =
            ContainerNode(key = "dnd-child")
                .apply {
                    width = 34
                    height = 16
                    droppable = true
                }.applyParent(parent)

        root.render(
            ctx =
                object : UiMeasureContext {
                    override val fontHeight: Int = 9

                    override fun measureText(text: String): Int = text.length * 6

                    override fun paint(commands: List<RenderCommand>) = Unit
                },
            x = 0,
            y = 0,
            width = 260,
            height = 140,
        )

        val chain = collectHoverChain(root, 10, 10)
        val candidates = chain.filter { it.droppable }
        val selected = DefaultDndEngine.selectDropTargetCandidate(candidates, previousTarget = null)

        assertSame(child, selected)
        assertEquals(listOf(root, parent, child), chain)
    }

    private fun testMeasureContext(): UiMeasureContext =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun measureText(text: String, fontId: String?, fontSize: Int?): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }
}
