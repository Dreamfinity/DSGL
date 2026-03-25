package org.dreamfinity.dsgl.core.dom

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.debug.ScrollPerformanceCounters
import org.dreamfinity.dsgl.core.debug.ScrollPerformanceSnapshot
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.StyleDeclarations
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty

class ScrollPerformanceCountersTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @AfterTest
    fun cleanup() {
        ScrollPerformanceCounters.resetForTests()
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `scroll-only paint exposes current expensive path counters`() {
        val fixture = createScrollStickyFixture()
        ScrollPerformanceCounters.resetForTests()

        fixture.viewport.setScrollOffsets(0, 64)
        fixture.tree.paint(ctx)

        val counters = ScrollPerformanceCounters.snapshot()
        println("ScrollPerformanceCounters baseline (scroll-only frame): $counters")
        assertTrue(counters.paintCalls >= 1)
        assertEquals(0L, counters.guardedScrollVisualFastPathRuns)
        assertTrue(counters.fullRerenderLayoutRuns >= 1)
        assertTrue(counters.measureChildForLayoutCalls > 0)
        assertTrue(counters.scrollContainerStateCalls > 0)
        assertTrue(counters.stickyResolutionCalls > 0)
        assertTrue(counters.stickyVerticalResolutionCalls > 0)
        assertTrue(counters.chunkTraversalCalls > 0)
        assertTrue(counters.chunkRebuildCalls > 0)
        assertTrue(counters.fullRerenderLayoutNanos > 0L)
        assertTrue(counters.chunkRebuildNanos > 0L)
        assertTrue(counters.paintTotalNanos > 0L)
    }

    @Test
    fun `idle paint keeps full rerender counter at zero`() {
        val fixture = createScrollStickyFixture()
        ScrollPerformanceCounters.resetForTests()

        fixture.tree.paint(ctx)

        val counters = ScrollPerformanceCounters.snapshot()
        assertEquals(0L, counters.guardedScrollVisualFastPathRuns)
        assertEquals(0L, counters.fullRerenderLayoutRuns)
        assertEquals(0L, counters.measureChildForLayoutCalls)
        assertTrue(counters.chunkTraversalCalls > 0)
    }

    @Test
    fun `guarded scroll visual fast path skips full rerender for visual-only scroll invalidation`() {
        val fixture = createScrollStickyFixture()
        val baseline = fixture.viewport.captureScrollSessionSnapshot()
        val visualOnlySnapshot = baseline.copy(
            displayedY = baseline.displayedY + 0.4,
            resolvedY = baseline.resolvedY
        )
        fixture.viewport.restoreScrollSessionSnapshot(visualOnlySnapshot)

        ScrollPerformanceCounters.resetForTests()
        fixture.tree.paint(ctx)

        val counters = ScrollPerformanceCounters.snapshot()
        println("ScrollPerformanceCounters visual-only fast-path frame: $counters")
        assertTrue(counters.paintCalls >= 1)
        assertEquals(1L, counters.guardedScrollVisualFastPathRuns)
        assertEquals(0L, counters.fullRerenderLayoutRuns)
        assertEquals(0L, counters.measureChildForLayoutCalls)
        assertTrue(counters.chunkTraversalCalls > 0)
        assertTrue(counters.chunkRebuildCalls > 0)
    }

    private fun createScrollStickyFixture(): ScrollStickyFixture {
        val root = ContainerNode(key = "perf-root")
        val viewport = ContainerNode(key = "perf-scroll-viewport").apply {
            width = 180
            height = 100
            overflowY = Overflow.Auto
        }.applyParent(root)

        ContainerNode(key = "perf-sticky").apply {
            width = 160
            height = 24
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "sticky",
                StyleProperty.TOP to "0px"
            )
        }.applyParent(viewport)

        ContainerNode(key = "perf-filler").apply {
            width = 160
            height = 420
        }.applyParent(viewport)

        val tree = DomTree(root)
        tree.render(ctx, 320, 220)
        tree.paint(ctx)
        return ScrollStickyFixture(tree = tree, viewport = viewport)
    }

    private fun styleDeclarations(vararg entries: Pair<StyleProperty, String>): StyleDeclarations {
        return StyleDeclarations().apply {
            entries.forEach { (property, literal) ->
                set(property, StyleExpression.Literal(literal))
            }
        }
    }

    private data class ScrollStickyFixture(
        val tree: DomTree,
        val viewport: ContainerNode
    )
}
