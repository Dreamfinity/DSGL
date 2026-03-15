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
    fun `overflow scroll always marks axis scrollbar present and reserves gutter`() {
        val root = ContainerNode(key = "root")
        val container = ContainerNode(key = "scroll-axis").apply {
            width = 100
            height = 60
            overflowX = Overflow.Scroll
            overflowY = Overflow.Scroll
        }.applyParent(root)
        ContainerNode(key = "child").apply {
            width = 40
            height = 20
        }.applyParent(container)

        DomTree(root).render(ctx, 320, 180)
        val state = container.scrollContainerState()

        assertTrue(state.axisX.scrollbarPresent)
        assertTrue(state.axisY.scrollbarPresent)
        assertTrue(state.horizontalScrollbarGutter > 0)
        assertTrue(state.verticalScrollbarGutter > 0)
        assertEquals(state.horizontalScrollbarGutter, state.axisX.scrollbarGutter)
        assertEquals(state.verticalScrollbarGutter, state.axisY.scrollbarGutter)
        assertEquals(state.baseViewportRect.width - state.verticalScrollbarGutter, state.viewportRect.width)
        assertEquals(state.baseViewportRect.height - state.horizontalScrollbarGutter, state.viewportRect.height)
    }

    @Test
    fun `overflow auto marks scrollbar presence only when axis content exceeds viewport`() {
        val root = ContainerNode(key = "root").apply {
            bounds = Rect(0, 0, 320, 180)
        }
        val container = ContainerNode(key = "auto-axis").apply {
            bounds = Rect(10, 10, 100, 60)
            overflowX = Overflow.Auto
            overflowY = Overflow.Auto
        }.applyParent(root)
        val child = ContainerNode(key = "child").apply {
            bounds = Rect(10, 10, 90, 50)
        }
        child.applyParent(container)

        var state = container.scrollContainerState()
        assertTrue(!state.axisX.scrollbarPresent)
        assertTrue(!state.axisY.scrollbarPresent)

        child.bounds = Rect(10, 10, 120, 75)
        state = container.scrollContainerState()
        assertTrue(state.axisX.scrollbarPresent)
        assertTrue(state.axisY.scrollbarPresent)
        assertTrue(state.horizontalScrollbarGutter > 0)
        assertTrue(state.verticalScrollbarGutter > 0)
    }
    @Test
    fun `overflow x auto reserves horizontal gutter when measured content width exceeds viewport`() {
        val root = ContainerNode(key = "root")
        val container = ContainerNode(key = "auto-horizontal").apply {
            width = 794
            height = 634
            overflowX = Overflow.Auto
            overflowY = Overflow.Hidden
        }.applyParent(root)
        ContainerNode(key = "child").apply {
            width = 826
            height = 620
        }.applyParent(container)

        DomTree(root).render(ctx, 1400, 1000)
        val state = container.scrollContainerState()

        assertEquals(794, state.baseViewportRect.width)
        assertEquals(634, state.baseViewportRect.height)
        assertEquals(826, state.contentExtent.width)
        assertEquals(620, state.contentExtent.height)
        assertTrue(state.axisX.scrollbarPresent)
        assertTrue(!state.axisY.scrollbarPresent)
        assertTrue(state.horizontalScrollbarGutter > 0)
        assertEquals(0, state.verticalScrollbarGutter)
        assertEquals(state.baseViewportRect.height - state.horizontalScrollbarGutter, state.viewportRect.height)
    }
    @Test
    fun `visible and hidden overflow never expose visible scrollbars or gutters`() {
        val root = ContainerNode(key = "root")
        val container = ContainerNode(key = "hidden-visible-axis").apply {
            width = 100
            height = 60
            overflowX = Overflow.Visible
            overflowY = Overflow.Hidden
        }.applyParent(root)
        ContainerNode(key = "child").apply {
            width = 180
            height = 220
        }.applyParent(container)

        DomTree(root).render(ctx, 320, 220)
        val state = container.scrollContainerState()

        assertTrue(!state.axisX.scrollContainer)
        assertTrue(state.axisY.scrollContainer)
        assertTrue(!state.axisX.scrollbarPresent)
        assertTrue(!state.axisY.scrollbarPresent)
        assertEquals(0, state.horizontalScrollbarGutter)
        assertEquals(0, state.verticalScrollbarGutter)
        assertEquals(0, state.axisX.scrollbarGutter)
        assertEquals(0, state.axisY.scrollbarGutter)
    }


    @Test
    fun `mixed overflow axis combinations resolve deterministic state`() {
        val cases = listOf(
            Triple(Overflow.Hidden, Overflow.Auto, Pair(true, true)),
            Triple(Overflow.Visible, Overflow.Scroll, Pair(false, true)),
            Triple(Overflow.Scroll, Overflow.Visible, Pair(true, false))
        )

        cases.forEachIndexed { index, (overflowX, overflowY, expectedScrollContainer) ->
            val root = ContainerNode(key = "root-$index")
            val container = ContainerNode(key = "mixed-$index").apply {
                width = 110
                height = 66
                this.overflowX = overflowX
                this.overflowY = overflowY
            }.applyParent(root)
            ContainerNode(key = "child-$index").apply {
                width = 220
                height = 190
            }.applyParent(container)

            DomTree(root).render(ctx, 360, 220)
            val state = container.scrollContainerState()

            assertEquals(expectedScrollContainer.first, state.axisX.scrollContainer)
            assertEquals(expectedScrollContainer.second, state.axisY.scrollContainer)

            val expectedVisibleX = overflowX == Overflow.Scroll || (overflowX == Overflow.Auto && state.contentExtent.width > state.viewportRect.width)
            val expectedVisibleY = overflowY == Overflow.Scroll || (overflowY == Overflow.Auto && state.contentExtent.height > state.viewportRect.height)
            assertEquals(expectedVisibleX, state.axisX.scrollbarPresent)
            assertEquals(expectedVisibleY, state.axisY.scrollbarPresent)
            assertEquals(if (expectedVisibleX) state.horizontalScrollbarGutter else 0, state.axisX.scrollbarGutter)
            assertEquals(if (expectedVisibleY) state.verticalScrollbarGutter else 0, state.axisY.scrollbarGutter)
        }
    }
    @Test
    fun `cross-axis gutter forcing enables dependent auto scrollbar deterministically`() {
        val root = ContainerNode(key = "root").apply {
            bounds = Rect(0, 0, 320, 220)
        }
        val container = ContainerNode(key = "cross-axis").apply {
            bounds = Rect(10, 10, 100, 80)
            overflowX = Overflow.Auto
            overflowY = Overflow.Auto
        }.applyParent(root)
        ContainerNode(key = "child").apply {
            bounds = Rect(10, 10, 96, 220)
        }.applyParent(container)

        val state = container.scrollContainerState()

        assertTrue(state.contentExtent.width <= state.baseViewportRect.width)
        assertTrue(state.axisY.scrollbarPresent)
        assertTrue(state.axisX.scrollbarPresent)
        assertTrue(state.verticalScrollbarGutter > 0)
        assertTrue(state.horizontalScrollbarGutter > 0)
        assertTrue(state.contentExtent.width > state.viewportRect.width)
    }

    @Test
    fun `final viewport resolution remains stable after gutter resolution`() {
        val root = ContainerNode(key = "root")
        val container = ContainerNode(key = "stable-viewport").apply {
            width = 120
            height = 72
            overflowX = Overflow.Auto
            overflowY = Overflow.Scroll
        }.applyParent(root)
        ContainerNode(key = "child").apply {
            width = 118
            height = 190
        }.applyParent(container)

        DomTree(root).render(ctx, 320, 220)
        val first = container.scrollContainerState()
        val second = container.scrollContainerState()

        assertEquals(first.viewportRect, second.viewportRect)
        assertEquals(first.axisX.scrollbarPresent, second.axisX.scrollbarPresent)
        assertEquals(first.axisY.scrollbarPresent, second.axisY.scrollbarPresent)
        assertEquals(first.baseViewportRect.width - first.verticalScrollbarGutter, first.viewportRect.width)
        assertEquals(first.baseViewportRect.height - first.horizontalScrollbarGutter, first.viewportRect.height)
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
