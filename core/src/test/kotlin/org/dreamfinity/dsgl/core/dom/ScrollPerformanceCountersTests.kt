package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.debug.ScrollPerformanceCounters
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.KeyModifiers
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
import kotlin.test.assertTrue

class ScrollPerformanceCountersTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanup() {
        KeyModifiers.sync(shift = false, control = false, meta = false)
        ScrollPerformanceCounters.resetForTests()
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `ordinary wheel scroll keeps using guarded fast path in common case`() {
        val fixture = createScrollStickyFixture()
        ScrollPerformanceCounters.resetForTests()

        val wheelX = fixture.viewport.bounds.x + 4
        val wheelY = fixture.viewport.bounds.y + 4
        assertTrue(fixture.router.handleMouseWheel(wheelX, wheelY, -120))
        fixture.tree.paint(ctx)

        val counters = ScrollPerformanceCounters.snapshot()
        println("ScrollPerformanceCounters ordinary-wheel frame: $counters")
        assertEquals(1L, counters.guardedScrollVisualFastPathRuns)
        assertEquals(0L, counters.fullRerenderLayoutRuns)
        assertEquals(0L, counters.measureChildForLayoutCalls)
        assertEquals(1L, counters.stickyResolutionCalls)
        assertEquals(1L, counters.stickyVisualRefreshResolvedNodes)
        assertTrue(counters.scrollContainerStateCalls > 0L)
        assertTrue(counters.chunkTraversalCalls > 0L)
        assertTrue(counters.chunkRebuildCalls > 0L)
    }

    @Test
    fun `ordinary scroll mutation uses guarded visual fast path without layout measurement`() {
        val fixture = createScrollStickyFixture()
        ScrollPerformanceCounters.resetForTests()

        fixture.viewport.setScrollOffsets(0, 64)
        fixture.tree.paint(ctx)

        val counters = ScrollPerformanceCounters.snapshot()
        println("ScrollPerformanceCounters ordinary scroll frame: $counters")
        assertTrue(counters.paintCalls >= 1)
        assertEquals(1L, counters.guardedScrollVisualFastPathRuns)
        assertEquals(0L, counters.fullRerenderLayoutRuns)
        assertEquals(0L, counters.measureChildForLayoutCalls)
        assertEquals(1L, counters.scrollVisualGeometryRefreshRuns)
        assertEquals(1L, counters.stickyVisualRefreshRuns)
        assertEquals(1L, counters.stickyVisualRefreshResolvedNodes)
        assertTrue(counters.stickyVisualInvalidateRuns > 0L)
        assertEquals(1L, counters.stickyVisualInvalidatedNodes)
        assertTrue(counters.scrollVisualGeometryTranslatedNodes > 0L)
        assertTrue(counters.scrollContainerStateCalls > 0)
        assertEquals(1L, counters.stickyResolutionCalls)
        assertEquals(1L, counters.stickyHorizontalResolutionCalls)
        assertEquals(1L, counters.stickyVerticalResolutionCalls)
        assertTrue(counters.scrollContainerStateCalls <= 18L)
        assertTrue(counters.chunkTraversalCalls > 0)
        assertTrue(counters.chunkRebuildCalls > 0)
        assertTrue(counters.chunkRebuildNanos > 0L)
        assertTrue(counters.paintTotalNanos > 0L)
    }

    @Test
    fun `sticky effectiveTransform reads do not repeatedly resolve sticky offsets after refresh`() {
        val fixture = createScrollStickyFixture()
        ScrollPerformanceCounters.resetForTests()

        fixture.viewport.setScrollOffsets(0, 64)
        fixture.tree.paint(ctx)
        val afterPaint = ScrollPerformanceCounters.snapshot()
        assertEquals(1L, afterPaint.stickyResolutionCalls)
        assertEquals(1L, afterPaint.stickyHorizontalResolutionCalls)
        assertEquals(1L, afterPaint.stickyVerticalResolutionCalls)

        repeat(24) {
            fixture.sticky.effectiveTransform()
        }
        val afterRepeatedReads = ScrollPerformanceCounters.snapshot()
        assertEquals(afterPaint.stickyResolutionCalls, afterRepeatedReads.stickyResolutionCalls)
        assertEquals(afterPaint.stickyHorizontalResolutionCalls, afterRepeatedReads.stickyHorizontalResolutionCalls)
        assertEquals(afterPaint.stickyVerticalResolutionCalls, afterRepeatedReads.stickyVerticalResolutionCalls)
        assertEquals(afterPaint.scrollContainerStateCalls, afterRepeatedReads.scrollContainerStateCalls)
    }

    @Test
    fun `idle paint keeps full rerender counter at zero`() {
        val fixture = createScrollStickyFixture()
        ScrollPerformanceCounters.resetForTests()

        fixture.tree.paint(ctx)

        val counters = ScrollPerformanceCounters.snapshot()
        assertEquals(0L, counters.guardedScrollVisualFastPathRuns)
        assertEquals(0L, counters.scrollVisualGeometryRefreshRuns)
        assertEquals(0L, counters.scrollVisualGeometryTranslatedNodes)
        assertEquals(0L, counters.fullRerenderLayoutRuns)
        assertEquals(0L, counters.measureChildForLayoutCalls)
        assertTrue(counters.chunkTraversalCalls > 0)
    }

    @Test
    fun `guarded scroll visual fast path skips full rerender for visual-only scroll invalidation`() {
        val fixture = createScrollStickyFixture()
        val baseline = fixture.viewport.captureScrollSessionSnapshot()
        val visualOnlySnapshot =
            baseline.copy(
                displayedY = baseline.displayedY + 0.4,
                resolvedY = baseline.resolvedY,
            )
        fixture.viewport.restoreScrollSessionSnapshot(visualOnlySnapshot)

        ScrollPerformanceCounters.resetForTests()
        fixture.tree.paint(ctx)

        val counters = ScrollPerformanceCounters.snapshot()
        println("ScrollPerformanceCounters visual-only fast-path frame: $counters")
        assertTrue(counters.paintCalls >= 1)
        assertEquals(1L, counters.guardedScrollVisualFastPathRuns)
        assertEquals(1L, counters.scrollVisualGeometryRefreshRuns)
        assertEquals(1L, counters.stickyVisualRefreshRuns)
        assertEquals(1L, counters.stickyVisualRefreshResolvedNodes)
        assertTrue(counters.stickyVisualInvalidateRuns > 0L)
        assertTrue(counters.stickyVisualInvalidatedNodes in 0L..1L)
        assertEquals(0L, counters.fullRerenderLayoutRuns)
        assertEquals(0L, counters.measureChildForLayoutCalls)
        assertEquals(1L, counters.stickyResolutionCalls)
        assertTrue(counters.chunkTraversalCalls > 0)
        assertTrue(counters.chunkRebuildCalls > 0)
    }

    @Test
    fun `thumb-drag frame keeps sticky correct and remains visual-path only`() {
        val fixture = createScrollStickyFixture()
        val visual =
            fixture.viewport
                .debugScrollbarVisualState()
                .vertical
                ?: error("Expected vertical scrollbar")
        val dragX = visual.thumbRect.x + visual.thumbRect.width / 2
        val startY = visual.thumbRect.y + visual.thumbRect.height / 2
        val dragY = startY + 26

        assertTrue(fixture.router.handleMouseDown(dragX, startY, MouseButton.LEFT))
        assertTrue(fixture.router.handleMouseMove(dragX, dragY))

        ScrollPerformanceCounters.resetForTests()
        fixture.tree.paint(ctx)

        val counters = ScrollPerformanceCounters.snapshot()
        println("ScrollPerformanceCounters thumb-drag frame: $counters")
        assertEquals(1L, counters.guardedScrollVisualFastPathRuns)
        assertEquals(0L, counters.fullRerenderLayoutRuns)
        assertEquals(0L, counters.measureChildForLayoutCalls)
        assertEquals(1L, counters.stickyResolutionCalls)
        assertEquals(1L, counters.stickyVisualRefreshResolvedNodes)
        assertTrue(counters.scrollContainerStateCalls > 0L)
        assertTrue(counters.chunkTraversalCalls > 0L)
        assertTrue(counters.chunkRebuildCalls > 0L)

        val visibleStickyRect =
            UsedInteractionGeometryResolver.resolveNodeGeometry(fixture.sticky).visibleBorderRect
                ?: UsedInteractionGeometryResolver.resolveNodeGeometry(fixture.sticky).usedBorderRect
        assertEquals(0, visibleStickyRect.y)
        assertTrue(fixture.router.handleMouseUp(dragX, dragY, MouseButton.LEFT))
    }

    @Test
    fun `animated scroll settling keeps sticky and fast-path behavior`() {
        val fixture = createScrollStickyFixture()
        val wheelX = fixture.viewport.bounds.x + 4
        val wheelY = fixture.viewport.bounds.y + 4
        assertTrue(fixture.router.handleMouseWheel(wheelX, wheelY, -240))

        fixture.tree.paint(ctx)
        val afterFirst = fixture.viewport.debugScrollAnimationState()
        assertTrue(afterFirst.targetY >= afterFirst.resolvedY)

        ScrollPerformanceCounters.resetForTests()
        fixture.tree.paint(ctx)
        val counters = ScrollPerformanceCounters.snapshot()
        println("ScrollPerformanceCounters animated-settling frame: $counters")

        val state = fixture.viewport.scrollContainerState()
        val expectedBaseY = fixture.stickyBaseTopY - state.scrollY
        val expectedVisibleY = maxOf(expectedBaseY, 0)
        val visibleStickyRect =
            UsedInteractionGeometryResolver.resolveNodeGeometry(fixture.sticky).visibleBorderRect
                ?: UsedInteractionGeometryResolver.resolveNodeGeometry(fixture.sticky).usedBorderRect

        assertEquals(1L, counters.guardedScrollVisualFastPathRuns)
        assertEquals(0L, counters.fullRerenderLayoutRuns)
        assertEquals(0L, counters.measureChildForLayoutCalls)
        assertEquals(1L, counters.stickyResolutionCalls)
        assertEquals(1L, counters.stickyVisualRefreshResolvedNodes)
        assertTrue(counters.scrollContainerStateCalls > 0L)
        assertTrue(counters.chunkTraversalCalls > 0L)
        assertTrue(counters.chunkRebuildCalls > 0L)
        assertEquals(expectedVisibleY, visibleStickyRect.y)
    }

    @Test
    fun `local scroll invalidation refreshes only sticky nodes in affected subtree`() {
        val root = ContainerNode(key = "perf-narrow-root")
        val leftViewport =
            ContainerNode(key = "perf-narrow-left")
                .apply {
                    width = 160
                    height = 90
                    overflowY = Overflow.Auto
                }.applyParent(root)
        val rightViewport =
            ContainerNode(key = "perf-narrow-right")
                .apply {
                    width = 160
                    height = 90
                    overflowY = Overflow.Auto
                }.applyParent(root)

        ContainerNode(key = "perf-narrow-left-sticky")
            .apply {
                width = 140
                height = 20
                inlineStyleDeclarations =
                    styleDeclarations(
                        StyleProperty.POSITION to "sticky",
                        StyleProperty.TOP to "0px",
                    )
            }.applyParent(leftViewport)
        ContainerNode(key = "perf-narrow-left-filler")
            .apply {
                width = 140
                height = 320
            }.applyParent(leftViewport)

        ContainerNode(key = "perf-narrow-right-sticky")
            .apply {
                width = 140
                height = 20
                inlineStyleDeclarations =
                    styleDeclarations(
                        StyleProperty.POSITION to "sticky",
                        StyleProperty.TOP to "0px",
                    )
            }.applyParent(rightViewport)
        ContainerNode(key = "perf-narrow-right-filler")
            .apply {
                width = 140
                height = 320
            }.applyParent(rightViewport)

        val tree = DomTree(root)
        tree.render(ctx, 420, 220)
        tree.paint(ctx)

        ScrollPerformanceCounters.resetForTests()
        leftViewport.setScrollOffsets(0, 64)
        tree.paint(ctx)

        val counters = ScrollPerformanceCounters.snapshot()
        println("ScrollPerformanceCounters local-subtree frame: $counters")
        assertEquals(1L, counters.guardedScrollVisualFastPathRuns)
        assertEquals(1L, counters.stickyVisualRefreshRuns)
        assertEquals(1L, counters.stickyVisualRefreshResolvedNodes)
        assertEquals(1L, counters.stickyVisualInvalidatedNodes)
        assertEquals(1L, counters.stickyResolutionCalls)
        assertEquals(1L, counters.stickyVerticalResolutionCalls)
        assertTrue(counters.scrollContainerStateCalls < 25L)
        assertEquals(0L, counters.measureChildForLayoutCalls)
        assertTrue(counters.chunkTraversalCalls > 0L)
        assertTrue(counters.chunkRebuildCalls in 1L..6L)
    }

    @Test
    fun `layout-dirty scroll invalidation still falls back to full rerender`() {
        val fixture = createScrollStickyFixture()
        val baseline = fixture.viewport.captureScrollSessionSnapshot()
        val layoutDirtySnapshot =
            baseline.copy(
                targetY = baseline.targetY + 48,
                displayedY = baseline.displayedY + 48.0,
                resolvedY = baseline.resolvedY + 48,
            )
        fixture.viewport.restoreScrollSessionSnapshot(layoutDirtySnapshot)

        ScrollPerformanceCounters.resetForTests()
        fixture.tree.paint(ctx)

        val counters = ScrollPerformanceCounters.snapshot()
        println("ScrollPerformanceCounters layout-dirty fallback frame: $counters")
        assertTrue(counters.paintCalls >= 1)
        assertEquals(0L, counters.guardedScrollVisualFastPathRuns)
        assertTrue(counters.fullRerenderLayoutRuns >= 1)
        assertTrue(counters.measureChildForLayoutCalls > 0)
    }

    private fun createScrollStickyFixture(): ScrollStickyFixture {
        val root = ContainerNode(key = "perf-root")
        val viewport =
            ContainerNode(key = "perf-scroll-viewport")
                .apply {
                    width = 180
                    height = 100
                    overflowY = Overflow.Auto
                }.applyParent(root)
        val topSpacer =
            ContainerNode(key = "perf-top-spacer")
                .apply {
                    width = 160
                    height = 32
                }.applyParent(viewport)

        val sticky =
            ContainerNode(key = "perf-sticky")
                .apply {
                    width = 160
                    height = 24
                    inlineStyleDeclarations =
                        styleDeclarations(
                            StyleProperty.POSITION to "sticky",
                            StyleProperty.TOP to "0px",
                        )
                }.applyParent(viewport)

        ContainerNode(key = "perf-filler")
            .apply {
                width = 160
                height = 420
            }.applyParent(viewport)

        val tree = DomTree(root)
        tree.render(ctx, 320, 220)
        tree.paint(ctx)
        val router = SurfaceDomInputRouter { root }
        return ScrollStickyFixture(
            tree = tree,
            root = root,
            viewport = viewport,
            sticky = sticky,
            router = router,
            stickyBaseTopY = topSpacer.height ?: 0,
        )
    }

    private fun styleDeclarations(vararg entries: Pair<StyleProperty, String>): StyleDeclarations =
        StyleDeclarations().apply {
            entries.forEach { (property, literal) ->
                set(property, StyleExpression.Literal(literal))
            }
        }

    private data class ScrollStickyFixture(
        val tree: DomTree,
        val root: ContainerNode,
        val viewport: ContainerNode,
        val sticky: ContainerNode,
        val router: SurfaceDomInputRouter,
        val stickyBaseTopY: Int,
    )
}
