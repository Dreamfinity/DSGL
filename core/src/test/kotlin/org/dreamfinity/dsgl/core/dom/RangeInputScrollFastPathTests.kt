package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.debug.ScrollPerformanceCounters
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.RangeInputNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.portal.input.SurfaceDomInputRouter
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.StyleDeclarations
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RangeInputScrollFastPathTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanup() {
        RangeInputNode.clearActiveDrag()
        StyleEngine.setStylesDirectory(null)
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
        ScrollPerformanceCounters.resetForTests()
    }

    @Test
    fun `range track follows bounds during wheel scroll fast path`() {
        val fixture = createFixture(includeSticky = false)
        ScrollPerformanceCounters.resetForTests()

        val wheelX = fixture.viewport.bounds.x + 4
        val wheelY = fixture.viewport.bounds.y + 4
        assertTrue(fixture.router.handleMouseWheel(wheelX, wheelY, -240))
        val commands = fixture.tree.paint(ctx)

        val state = fixture.viewport.scrollContainerState()
        assertTrue(state.scrollY > 0)
        assertEquals(fixture.baseRangeY - state.scrollY, fixture.range.bounds.y)
        assertTrackMatchesBounds(commands, fixture.range)

        val counters = ScrollPerformanceCounters.snapshot()
        assertTrue(counters.guardedScrollVisualFastPathRuns >= 1L)
        assertEquals(0L, counters.fullRerenderLayoutRuns)
        assertEquals(0L, counters.measureChildForLayoutCalls)
    }

    @Test
    fun `range track follows bounds during scrollbar thumb drag fast path`() {
        val fixture = createFixture(includeSticky = false)
        val visual =
            fixture.viewport
                .debugScrollbarVisualState()
                .vertical ?: error("Expected vertical scrollbar")
        val dragX = visual.thumbRect.x + visual.thumbRect.width / 2
        val startY = visual.thumbRect.y + visual.thumbRect.height / 2

        ScrollPerformanceCounters.resetForTests()
        assertTrue(fixture.router.handleMouseDown(dragX, startY, MouseButton.LEFT))
        assertTrue(fixture.router.handleMouseMove(dragX, startY + 28))
        val commands = fixture.tree.paint(ctx)

        val state = fixture.viewport.scrollContainerState()
        assertTrue(state.scrollY > 0)
        assertEquals(fixture.baseRangeY - state.scrollY, fixture.range.bounds.y)
        assertTrackMatchesBounds(commands, fixture.range)

        val counters = ScrollPerformanceCounters.snapshot()
        assertTrue(counters.guardedScrollVisualFastPathRuns >= 1L)
        assertEquals(0L, counters.fullRerenderLayoutRuns)
        assertEquals(0L, counters.measureChildForLayoutCalls)

        assertTrue(fixture.router.handleMouseUp(dragX, startY + 28, MouseButton.LEFT))
    }

    @Test
    fun `range interaction remains aligned after scroll only fast path updates`() {
        val fixture = createFixture(includeSticky = false)
        val wheelX = fixture.viewport.bounds.x + 4
        val wheelY = fixture.viewport.bounds.y + 4
        assertTrue(fixture.router.handleMouseWheel(wheelX, wheelY, -240))
        fixture.tree.paint(ctx)

        val visible = visibleRect(fixture.range)
        val y = visible.y + visible.height / 2
        val leftX = visible.x + 1
        val rightX = visible.x + visible.width - 1

        assertTrue(fixture.router.handleMouseDown(leftX, y, MouseButton.LEFT))
        assertTrue(fixture.router.handleMouseUp(leftX, y, MouseButton.LEFT))
        val low = fixture.range.value

        assertTrue(fixture.router.handleMouseDown(rightX, y, MouseButton.LEFT))
        assertTrue(fixture.router.handleMouseUp(rightX, y, MouseButton.LEFT))
        val high = fixture.range.value

        assertTrue(high > low)
        assertTrue(high >= 80L)
    }

    @Test
    fun `nested scroll containers keep range visual and interaction geometry aligned`() {
        val fixture = createNestedFixture()
        val innerWheelX = fixture.inner.bounds.x + 4
        val innerWheelY = fixture.inner.bounds.y + 4
        assertTrue(fixture.router.handleMouseWheel(innerWheelX, innerWheelY, -240))
        val commands = fixture.tree.paint(ctx)

        val innerState = fixture.inner.scrollContainerState()
        assertTrue(innerState.scrollY > 0)
        assertEquals(fixture.baseRangeY - innerState.scrollY, fixture.range.bounds.y)
        assertTrackMatchesBounds(commands, fixture.range)

        val visible = visibleRect(fixture.range)
        val y = visible.y + visible.height / 2
        val rightX = visible.x + visible.width - 1
        assertTrue(fixture.router.handleMouseDown(rightX, y, MouseButton.LEFT))
        assertTrue(fixture.router.handleMouseUp(rightX, y, MouseButton.LEFT))
        assertTrue(fixture.range.value >= 80L)
    }

    @Test
    fun `sticky remains correct while thumb dragging with range input present`() {
        val fixture = createFixture(includeSticky = true)
        val sticky = assertNotNull(fixture.sticky)
        val visual =
            fixture.viewport
                .debugScrollbarVisualState()
                .vertical ?: error("Expected vertical scrollbar")
        val dragX = visual.thumbRect.x + visual.thumbRect.width / 2
        val startY = visual.thumbRect.y + visual.thumbRect.height / 2

        assertTrue(fixture.router.handleMouseDown(dragX, startY, MouseButton.LEFT))
        repeat(6) { step ->
            assertTrue(fixture.router.handleMouseMove(dragX, startY + (step + 1) * 6))
            val commands = fixture.tree.paint(ctx)
            val state = fixture.viewport.scrollContainerState()
            val expectedStickyBaseY = fixture.baseStickyY - state.scrollY
            val expectedStickyVisibleY = maxOf(expectedStickyBaseY, fixture.viewport.bounds.y)
            assertEquals(expectedStickyBaseY, sticky.bounds.y)
            assertEquals(expectedStickyVisibleY, visibleRect(sticky).y)
            assertTrackMatchesBounds(commands, fixture.range)
        }
        assertTrue(fixture.router.handleMouseUp(dragX, startY + 36, MouseButton.LEFT))
    }

    @Test
    fun `high frequency tiny thumb drag deltas keep range track aligned`() {
        val fixture = createFixture(includeSticky = false)
        val visual =
            fixture.viewport
                .debugScrollbarVisualState()
                .vertical ?: error("Expected vertical scrollbar")
        val dragX = visual.thumbRect.x + visual.thumbRect.width / 2
        val startY = visual.thumbRect.y + visual.thumbRect.height / 2

        assertTrue(fixture.router.handleMouseDown(dragX, startY, MouseButton.LEFT))
        var previousScroll =
            fixture.viewport
                .scrollContainerState()
                .scrollY
        var previousTrackY = expectedTrackRect(fixture.range).y

        repeat(32) { step ->
            assertTrue(fixture.router.handleMouseMove(dragX, startY + step + 1))
            val commands = fixture.tree.paint(ctx)
            val scrollY =
                fixture.viewport
                    .scrollContainerState()
                    .scrollY
            val trackY = expectedTrackRect(fixture.range).y
            assertTrue(scrollY >= previousScroll)
            assertTrue(trackY <= previousTrackY)
            assertTrackMatchesBounds(commands, fixture.range)
            previousScroll = scrollY
            previousTrackY = trackY
        }

        assertTrue(fixture.router.handleMouseUp(dragX, startY + 32, MouseButton.LEFT))
    }

    @Test
    fun `ordinary range dragging value changes remains unchanged`() {
        val fixture = createFixture(includeSticky = false)
        val visible = visibleRect(fixture.range)
        val y = visible.y + visible.height / 2
        val leftX = visible.x + 2
        val rightX = visible.x + visible.width - 2

        assertTrue(fixture.router.handleMouseDown(leftX, y, MouseButton.LEFT))
        assertTrue(fixture.router.handleMouseMove(rightX, y))
        assertTrue(fixture.router.handleMouseUp(rightX, y, MouseButton.LEFT))
        assertTrue(fixture.range.value >= 80L)
    }

    private fun createFixture(includeSticky: Boolean): Fixture {
        val root = ContainerNode(key = "range-fast-root")
        val viewport =
            ContainerNode(key = "range-fast-viewport")
                .apply {
                    width = 170
                    height = 90
                    overflowX = Overflow.Hidden
                    overflowY = Overflow.Auto
                }.applyParent(root)

        val sticky =
            if (includeSticky) {
                ContainerNode(key = "range-fast-sticky")
                    .apply {
                        width = 170
                        height = 18
                        inlineStyleDeclarations =
                            styleDeclarations(
                                StyleProperty.POSITION to "sticky",
                                StyleProperty.TOP to "0px",
                            )
                    }.applyParent(viewport)
            } else {
                null
            }

        val controls =
            ContainerNode(key = "range-fast-controls")
                .apply {
                    width = 170
                }.applyParent(viewport)
        ContainerNode(key = "range-fast-spacer")
            .apply {
                width = 170
                height = 42
            }.applyParent(controls)
        val range =
            RangeInputNode(value = 0L, min = 0L, max = 100L, key = "range-fast-input")
                .apply {
                    width = 150
                    height = 16
                }.applyParent(controls)
        ContainerNode(key = "range-fast-filler")
            .apply {
                width = 170
                height = 360
            }.applyParent(controls)

        val tree = DomTree(root)
        tree.render(ctx, 420, 260)
        tree.paint(ctx)
        val router = SurfaceDomInputRouter { root }
        return Fixture(
            tree = tree,
            root = root,
            viewport = viewport,
            range = range,
            sticky = sticky,
            router = router,
            baseRangeY = range.bounds.y,
            baseStickyY = sticky?.bounds?.y ?: 0,
        )
    }

    private fun createNestedFixture(): NestedFixture {
        val root = ContainerNode(key = "range-nested-root")
        val outer =
            ContainerNode(key = "range-nested-outer")
                .apply {
                    width = 200
                    height = 120
                    overflowY = Overflow.Auto
                }.applyParent(root)
        ContainerNode(key = "range-nested-outer-spacer")
            .apply {
                width = 200
                height = 26
            }.applyParent(outer)
        val inner =
            ContainerNode(key = "range-nested-inner")
                .apply {
                    width = 180
                    height = 80
                    overflowY = Overflow.Auto
                }.applyParent(outer)
        ContainerNode(key = "range-nested-inner-spacer")
            .apply {
                width = 180
                height = 20
            }.applyParent(inner)
        val range =
            RangeInputNode(value = 0L, min = 0L, max = 100L, key = "range-nested-input")
                .apply {
                    width = 150
                    height = 16
                }.applyParent(inner)
        ContainerNode(key = "range-nested-inner-filler")
            .apply {
                width = 180
                height = 280
            }.applyParent(inner)
        ContainerNode(key = "range-nested-outer-filler")
            .apply {
                width = 200
                height = 260
            }.applyParent(outer)

        val tree = DomTree(root)
        tree.render(ctx, 520, 320)
        tree.paint(ctx)
        val router = SurfaceDomInputRouter { root }
        return NestedFixture(
            tree = tree,
            root = root,
            outer = outer,
            inner = inner,
            range = range,
            router = router,
            baseRangeY = range.bounds.y,
        )
    }

    private fun assertTrackMatchesBounds(commands: List<RenderCommand>, range: RangeInputNode) {
        val expected = expectedTrackRect(range)
        val match =
            commands
                .filterIsInstance<RenderCommand.DrawRect>()
                .firstOrNull { command ->
                    command.x == expected.x &&
                        command.y == expected.y &&
                        command.width == expected.width &&
                        command.height == expected.height &&
                        command.color == range.trackColor
                }
        assertNotNull(
            match,
            "Range track command did not match expected geometry: expected=$expected bounds=${range.bounds}",
        )
    }

    private fun expectedTrackRect(range: RangeInputNode): Rect {
        val trackHeight = maxOf(2, range.bounds.height / 3)
        val trackY = range.bounds.y + (range.bounds.height - trackHeight) / 2
        return Rect(
            x = range.bounds.x,
            y = trackY,
            width = range.bounds.width,
            height = trackHeight,
        )
    }

    private fun visibleRect(node: DOMNode): Rect {
        val geometry = UsedInteractionGeometryResolver.resolveNodeGeometry(node)
        return geometry.visibleBorderRect ?: geometry.usedBorderRect
    }

    private fun styleDeclarations(vararg entries: Pair<StyleProperty, String>): StyleDeclarations =
        StyleDeclarations().apply {
            entries.forEach { (property, literal) ->
                set(property, StyleExpression.Literal(literal))
            }
        }

    private data class Fixture(
        val tree: DomTree,
        val root: ContainerNode,
        val viewport: ContainerNode,
        val range: RangeInputNode,
        val sticky: ContainerNode?,
        val router: SurfaceDomInputRouter,
        val baseRangeY: Int,
        val baseStickyY: Int,
    )

    private data class NestedFixture(
        val tree: DomTree,
        val root: ContainerNode,
        val outer: ContainerNode,
        val inner: ContainerNode,
        val range: RangeInputNode,
        val router: SurfaceDomInputRouter,
        val baseRangeY: Int,
    )
}
