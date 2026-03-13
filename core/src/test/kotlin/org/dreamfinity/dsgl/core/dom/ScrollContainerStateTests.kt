package org.dreamfinity.dsgl.core.dom

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.overlay.input.LayerDomInputRouter
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.StyleEngine

class ScrollContainerStateTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @AfterTest
    fun cleanup() {
        StyleEngine.setStylesDirectory(null)
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `generic viewport rect is exposed for scroll-capable container`() {
        val root = ContainerNode(key = "root")
        val container = ContainerNode(padding = 4, key = "viewport").apply {
            width = 100
            height = 60
            overflow = Overflow.Hidden
        }.applyParent(root)
        ContainerNode(key = "child").apply {
            width = 140
            height = 24
        }.applyParent(container)

        DomTree(root).render(ctx, 320, 180)
        val state = container.scrollContainerState()

        assertEquals(container.bounds.x + container.border.left + container.padding.left, state.viewportRect.x)
        assertEquals(container.bounds.y + container.border.top + container.padding.top, state.viewportRect.y)
        assertEquals(100, state.viewportRect.width)
        assertEquals(60, state.viewportRect.height)
    }

    @Test
    fun `generic content extent is distinct from viewport when content exceeds bounds`() {
        val root = ContainerNode(key = "root")
        val container = ContainerNode(key = "extent").apply {
            width = 80
            height = 40
            overflow = Overflow.Hidden
        }.applyParent(root)
        ContainerNode(key = "child-a").apply {
            width = 70
            height = 30
        }.applyParent(container)
        ContainerNode(key = "child-b").apply {
            width = 70
            height = 30
        }.applyParent(container)

        DomTree(root).render(ctx, 320, 180)
        val state = container.scrollContainerState()

        assertTrue(state.viewportRect.height < state.contentExtent.height)
    }

    @Test
    fun `per-axis overflow modes resolve to expected scroll-container capability`() {
        val root = ContainerNode(key = "root")
        val subject = ContainerNode(key = "axis").apply {
            width = 80
            height = 40
        }.applyParent(root)

        DomTree(root).render(ctx, 320, 180)

        subject.overflowX = Overflow.Visible
        subject.overflowY = Overflow.Visible
        var state = subject.scrollContainerState()
        assertTrue(!state.axisX.scrollContainer && !state.axisY.scrollContainer)

        subject.overflowX = Overflow.Hidden
        subject.overflowY = Overflow.Visible
        state = subject.scrollContainerState()
        assertTrue(state.axisX.scrollContainer && !state.axisY.scrollContainer)

        subject.overflowX = Overflow.Scroll
        subject.overflowY = Overflow.Auto
        state = subject.scrollContainerState()
        assertTrue(state.axisX.scrollContainer && state.axisY.scrollContainer)
        assertEquals(Overflow.Scroll, state.axisX.overflow)
        assertEquals(Overflow.Auto, state.axisY.overflow)
    }

    @Test
    fun `generic scroll offsets are meaningful for scroll-capable axes`() {
        val root = ContainerNode(key = "root")
        val container = ContainerNode(key = "scroll-state").apply {
            width = 90
            height = 40
            overflowX = Overflow.Visible
            overflowY = Overflow.Auto
        }.applyParent(root)
        ContainerNode(key = "tall-child").apply {
            width = 70
            height = 180
        }.applyParent(container)

        container.setScrollOffsets(scrollX = 45, scrollY = 70)
        DomTree(root).render(ctx, 320, 220)
        DomTree(root).render(ctx, 320, 220)
        var state = container.scrollContainerState()
        assertEquals(0, state.scrollX)
        assertEquals(70, state.scrollY)
        assertTrue(state.maxScrollY >= 70)

        container.setScrollOffsets(scrollX = 10, scrollY = 999)
        DomTree(root).render(ctx, 320, 220)
        DomTree(root).render(ctx, 320, 220)
        state = container.scrollContainerState()
        assertEquals(0, state.scrollX)
        assertEquals(state.maxScrollY, state.scrollY)
    }

    @Test
    fun `paint and input clipping remain consistent with overflow auto state`() {
        val root = ContainerNode(key = "root").apply {
            bounds = Rect(0, 0, 320, 200)
        }
        val clippedViewport = ContainerNode(key = "clip").apply {
            bounds = Rect(20, 20, 100, 40)
            overflowX = Overflow.Visible
            overflowY = Overflow.Auto
        }.applyParent(root)
        val child = ButtonNode("child", key = "clip-child").apply {
            bounds = Rect(24, 46, 80, 22)
        }
        child.applyParent(clippedViewport)

        val commands = ArrayList<RenderCommand>()
        root.appendRenderCommands(ctx, commands)
        assertTrue(commands.any { command ->
            command is RenderCommand.PushClip &&
                command.y == clippedViewport.bounds.y &&
                command.height == clippedViewport.bounds.height
        })

        val router = LayerDomInputRouter { root }
        assertTrue(router.handleMouseDown(30, 58, MouseButton.LEFT))
        assertTrue(router.handleMouseUp(30, 58, MouseButton.LEFT))
        assertTrue(!router.handleMouseDown(30, 65, MouseButton.LEFT))
        assertTrue(!router.handleMouseUp(30, 65, MouseButton.LEFT))
    }
}
