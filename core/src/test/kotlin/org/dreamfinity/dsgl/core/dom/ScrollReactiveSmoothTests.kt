package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Insets
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.overlay.input.LayerDomInputRouter
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.StyleEngine
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScrollReactiveSmoothTests {
    private val ctx =
        object : UiMeasureContext {
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
    fun `changing scroll offset updates content translation on next paint without resize`() {
        val fixture = createFixture()
        val initialY = fixture.button.bounds.y

        fixture.viewport.setScrollOffsets(0, 60)
        assertEquals(initialY, fixture.button.bounds.y)

        fixture.tree.paint(ctx)

        assertEquals(initialY - 60, fixture.button.bounds.y)
    }

    @Test
    fun `hit-testing follows updated displayed scroll translation`() {
        val fixture = createFixture()
        val clickX = fixture.button.bounds.x + 6
        val initialClickY = fixture.button.bounds.y + fixture.button.bounds.height / 2

        assertTrue(dispatchClick(fixture.tree, clickX, initialClickY))
        assertEquals(1, fixture.clickCount.value)

        fixture.viewport.setScrollOffsets(0, 40)
        fixture.tree.paint(ctx)

        assertFalse(dispatchClick(fixture.tree, clickX, initialClickY))
        assertTrue(dispatchClick(fixture.tree, clickX, initialClickY - 40))
        assertEquals(2, fixture.clickCount.value)
    }

    @Test
    fun `thumb and content stay synchronized during smooth wheel scrolling`() {
        val fixture = createFixture()
        val wheelX = fixture.viewport.bounds.x + 4
        val wheelY = fixture.viewport.bounds.y + 4

        assertTrue(fixture.router.handleMouseWheel(wheelX, wheelY, -240))

        var previousScroll =
            fixture.viewport
                .scrollContainerState()
                .scrollY
        var previousThumb =
            fixture.viewport
                .debugScrollbarVisualState()
                .vertical
                ?.thumbRect
                ?.y
                ?: error("Expected vertical scrollbar")
        var previousButtonY = fixture.button.bounds.y

        repeat(14) {
            fixture.tree.paint(ctx)
            val state = fixture.viewport.scrollContainerState()
            val thumbY =
                fixture.viewport
                    .debugScrollbarVisualState()
                    .vertical
                    ?.thumbRect
                    ?.y
                    ?: error("Expected vertical scrollbar")
            assertTrue(state.scrollY >= previousScroll)
            assertTrue(thumbY >= previousThumb)
            assertTrue(fixture.button.bounds.y <= previousButtonY)
            val expectedButtonY = state.viewportRect.y - state.scrollY + fixture.button.margin.top
            assertTrue(
                kotlin.math.abs(expectedButtonY - fixture.button.bounds.y) <= 1,
                "expectedButtonY=$expectedButtonY actualButtonY=${fixture.button.bounds.y} " +
                    "scrollY=${state.scrollY} viewportY=${state.viewportRect.y}",
            )
            previousScroll = state.scrollY
            previousThumb = thumbY
            previousButtonY = fixture.button.bounds.y
        }

        assertTrue(previousScroll > 0)
    }

    @Test
    fun `wheel updates target while displayed scroll converges toward it`() {
        val fixture = createFixture()
        val wheelX = fixture.viewport.bounds.x + 4
        val wheelY = fixture.viewport.bounds.y + 4

        assertTrue(fixture.router.handleMouseWheel(wheelX, wheelY, -120))
        val beforeFrame = fixture.viewport.debugScrollAnimationState()
        assertTrue(beforeFrame.targetY > beforeFrame.resolvedY)

        fixture.tree.paint(ctx)
        val afterFirstFrame = fixture.viewport.debugScrollAnimationState()
        assertTrue(afterFirstFrame.resolvedY > beforeFrame.resolvedY)
        assertTrue(afterFirstFrame.resolvedY <= afterFirstFrame.targetY)

        repeat(90) {
            fixture.tree.paint(ctx)
        }
        val settled = fixture.viewport.debugScrollAnimationState()
        assertEquals(settled.targetY, settled.resolvedY)
    }

    @Test
    fun `smooth scrolling converges without oscillation or jitter`() {
        val fixture = createFixture()
        val wheelX = fixture.viewport.bounds.x + 4
        val wheelY = fixture.viewport.bounds.y + 4

        assertTrue(fixture.router.handleMouseWheel(wheelX, wheelY, -360))

        val history = ArrayList<Int>(160)
        repeat(160) {
            fixture.tree.paint(ctx)
            history +=
                fixture.viewport
                    .debugScrollAnimationState()
                    .resolvedY
        }
        val finalState = fixture.viewport.debugScrollAnimationState()
        assertEquals(finalState.targetY, finalState.resolvedY)

        history.zipWithNext().forEach { (prev, next) ->
            assertTrue(next >= prev)
        }

        repeat(10) {
            fixture.tree.paint(ctx)
            assertEquals(
                finalState.targetY,
                fixture.viewport
                    .debugScrollAnimationState()
                    .resolvedY,
            )
        }
    }

    @Test
    fun `thumb drag updates content and thumb continuously in sync`() {
        val fixture = createFixture()
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
        var previousThumbY =
            fixture.viewport
                .debugScrollbarVisualState()
                .vertical
                ?.thumbRect
                ?.y
                ?: error("Expected vertical scrollbar")
        var previousButtonY = fixture.button.bounds.y
        repeat(8) { step ->
            val nextY = startY + (step + 1) * 6
            assertTrue(fixture.router.handleMouseMove(dragX, nextY))
            fixture.tree.paint(ctx)
            val state = fixture.viewport.scrollContainerState()
            val debug = fixture.viewport.debugScrollAnimationState()
            val currentThumbY =
                fixture.viewport
                    .debugScrollbarVisualState()
                    .vertical
                    ?.thumbRect
                    ?.y
                    ?: error("Expected vertical scrollbar")
            assertTrue(state.scrollY >= previousScroll)
            assertTrue(currentThumbY >= previousThumbY)
            assertTrue(fixture.button.bounds.y <= previousButtonY)
            val expectedButtonY = state.viewportRect.y - state.scrollY + fixture.button.margin.top
            assertTrue(
                kotlin.math.abs(expectedButtonY - fixture.button.bounds.y) <= 1,
                "expectedButtonY=$expectedButtonY actualButtonY=${fixture.button.bounds.y} " +
                    "scrollY=${state.scrollY} viewportY=${state.viewportRect.y}",
            )
            assertEquals(state.scrollY, debug.resolvedY)
            assertTrue(kotlin.math.abs(debug.displayedY - debug.resolvedY.toDouble()) <= 1.0)
            previousScroll = state.scrollY
            previousThumbY = currentThumbY
            previousButtonY = fixture.button.bounds.y
        }
    }

    @Test
    fun `drag keeps target and displayed state coherent`() {
        val fixture = createFixture()
        val visual =
            fixture.viewport
                .debugScrollbarVisualState()
                .vertical ?: error("Expected vertical scrollbar")
        val dragX = visual.thumbRect.x + visual.thumbRect.width / 2
        val startY = visual.thumbRect.y + visual.thumbRect.height / 2

        assertTrue(fixture.router.handleMouseDown(dragX, startY, MouseButton.LEFT))
        repeat(7) { step ->
            assertTrue(fixture.router.handleMouseMove(dragX, startY + (step + 1) * 5))
            fixture.tree.paint(ctx)
            val debug = fixture.viewport.debugScrollAnimationState()
            assertEquals(debug.targetY, debug.resolvedY)
            assertEquals(debug.displayedY, debug.resolvedY.toDouble())
        }
    }

    @Test
    fun `fast thumb drag to max remains stable and coherent at boundary`() {
        val fixture = createFixture()
        val visual =
            fixture.viewport
                .debugScrollbarVisualState()
                .vertical ?: error("Expected vertical scrollbar")
        val dragX = visual.thumbRect.x + visual.thumbRect.width / 2
        val startY = visual.thumbRect.y + visual.thumbRect.height / 2

        fixture.viewport.beginPointerCapture(dragX, startY, MouseButton.LEFT)
        repeat(10) { step ->
            val nextY = startY + (step + 1) * 120
            fixture.viewport.continuePointerCapture(dragX, nextY, 0, 120, MouseButton.LEFT)
            fixture.tree.paint(ctx)
            val state = fixture.viewport.scrollContainerState()
            val debug = fixture.viewport.debugScrollAnimationState()
            assertEquals(state.scrollY, debug.resolvedY)
            assertEquals(debug.displayedY, debug.resolvedY.toDouble())
            assertTrue(state.scrollY in 0..state.maxScrollY)
        }
        val beforeSettle = fixture.viewport.scrollContainerState()
        var previousSettled = beforeSettle.scrollY
        repeat(10) {
            fixture.tree.paint(ctx)
            val state = fixture.viewport.scrollContainerState()
            val debug = fixture.viewport.debugScrollAnimationState()
            assertTrue(state.scrollY >= previousSettled)
            assertEquals(debug.displayedY, debug.resolvedY.toDouble())
            previousSettled = state.scrollY
        }
        fixture.viewport.endPointerCapture(dragX, startY + 1200, MouseButton.LEFT)
    }

    @Test
    fun `fast thumb drag to min remains stable and coherent at boundary`() {
        val fixture = createFixture()
        val visual =
            fixture.viewport
                .debugScrollbarVisualState()
                .vertical ?: error("Expected vertical scrollbar")
        val dragX = visual.thumbRect.x + visual.thumbRect.width / 2
        val startY = visual.thumbRect.y + visual.thumbRect.height / 2

        fixture.viewport.beginPointerCapture(dragX, startY, MouseButton.LEFT)
        fixture.viewport.continuePointerCapture(dragX, startY + 1200, 0, 1200, MouseButton.LEFT)
        fixture.tree.paint(ctx)
        fixture.viewport.continuePointerCapture(dragX, startY - 1200, 0, -2400, MouseButton.LEFT)

        repeat(10) {
            fixture.tree.paint(ctx)
            val state = fixture.viewport.scrollContainerState()
            val debug = fixture.viewport.debugScrollAnimationState()
            assertEquals(0, state.scrollY)
            assertEquals(debug.targetY, debug.resolvedY)
            assertEquals(debug.displayedY, debug.resolvedY.toDouble())
        }
        fixture.viewport.endPointerCapture(dragX, startY - 1200, MouseButton.LEFT)
    }

    @Test
    fun `drag session baseline stays frozen when live scrollbar geometry changes`() {
        val fixture = createFixture()
        val initialVisual =
            fixture.viewport
                .debugScrollbarVisualState()
                .vertical ?: error("Expected vertical scrollbar")
        val dragX = initialVisual.thumbRect.x + initialVisual.thumbRect.width / 2
        val startY = initialVisual.thumbRect.y + initialVisual.thumbRect.height / 2

        assertTrue(fixture.router.handleMouseDown(dragX, startY, MouseButton.LEFT))
        val baseline = fixture.viewport.debugScrollbarDragSession() ?: error("Expected active drag session")
        assertTrue(baseline.verticalAxis)
        assertEquals(initialVisual.trackRect.y, baseline.trackStartPx)
        assertEquals(initialVisual.trackRect.height, baseline.trackLengthPx)
        assertEquals(initialVisual.thumbRect.height, baseline.thumbLengthPx)

        fixture.filler.height = (fixture.filler.height ?: 0) + 260
        fixture.tree.render(ctx, 420, 260)
        fixture.tree.paint(ctx)

        val liveAfterResize =
            fixture.viewport
                .debugScrollbarVisualState()
                .vertical ?: error("Expected vertical scrollbar")
        assertTrue(liveAfterResize.maxScroll >= baseline.maxScroll)

        val baselineAfterResize = fixture.viewport.debugScrollbarDragSession() ?: error("Expected active drag session")
        assertEquals(baseline.trackStartPx, baselineAfterResize.trackStartPx)
        assertEquals(baseline.trackLengthPx, baselineAfterResize.trackLengthPx)
        assertEquals(baseline.thumbLengthPx, baselineAfterResize.thumbLengthPx)
        assertEquals(baseline.maxThumbTravelPx, baselineAfterResize.maxThumbTravelPx)
        assertEquals(baseline.maxScroll, baselineAfterResize.maxScroll)
        assertEquals(baseline.grabOffsetPx, baselineAfterResize.grabOffsetPx)

        val moveY = startY + 44
        assertTrue(fixture.router.handleMouseMove(dragX, moveY))
        fixture.tree.paint(ctx)

        val expectedScroll = expectedScrollFromSession(baseline, moveY)
        val state = fixture.viewport.scrollContainerState()
        val debug = fixture.viewport.debugScrollAnimationState()
        assertTrue(
            kotlin.math.abs(expectedScroll - state.scrollY) <= 1,
            "expectedScroll=$expectedScroll actualScroll=${state.scrollY} " +
                "moveY=$moveY baseline=$baseline stateMax=${state.maxScrollY}",
        )
        assertEquals(state.scrollY, debug.resolvedY)
        assertEquals(debug.displayedY, debug.resolvedY.toDouble())
        assertEquals(debug.targetY, debug.resolvedY)

        assertTrue(fixture.router.handleMouseUp(dragX, moveY, MouseButton.LEFT))
    }

    @Test
    fun `drag pointer capture continues when pointer leaves container bounds`() {
        val fixture = createFixture()
        val visual =
            fixture.viewport
                .debugScrollbarVisualState()
                .vertical ?: error("Expected vertical scrollbar")
        val dragX = visual.thumbRect.x + visual.thumbRect.width / 2
        val startY = visual.thumbRect.y + visual.thumbRect.height / 2

        assertTrue(fixture.router.handleMouseDown(dragX, startY, MouseButton.LEFT))
        val before =
            fixture.viewport
                .scrollContainerState()
                .scrollY
        val outsideX = fixture.viewport.bounds.x + fixture.viewport.bounds.width + 1200
        val outsideY = fixture.viewport.bounds.y + fixture.viewport.bounds.height + 1200

        assertTrue(fixture.router.handleMouseMove(outsideX, outsideY))
        fixture.tree.paint(ctx)

        val after =
            fixture.viewport
                .scrollContainerState()
                .scrollY
        assertTrue(after >= before)
        assertTrue(fixture.router.handleMouseUp(outsideX, outsideY, MouseButton.LEFT))
    }

    @Test
    fun `drag release is stable without snap back`() {
        val fixture = createFixture()
        val visual =
            fixture.viewport
                .debugScrollbarVisualState()
                .vertical ?: error("Expected vertical scrollbar")
        val dragX = visual.thumbRect.x + visual.thumbRect.width / 2
        val startY = visual.thumbRect.y + visual.thumbRect.height / 2

        assertTrue(fixture.router.handleMouseDown(dragX, startY, MouseButton.LEFT))
        assertTrue(fixture.router.handleMouseMove(dragX, startY + 42))
        fixture.tree.paint(ctx)
        assertTrue(fixture.router.handleMouseUp(dragX, startY + 42, MouseButton.LEFT))

        var previousState = fixture.viewport.scrollContainerState()
        repeat(10) {
            fixture.tree.paint(ctx)
            val nextState = fixture.viewport.scrollContainerState()
            val nextDebug = fixture.viewport.debugScrollAnimationState()
            assertTrue(nextState.scrollY >= previousState.scrollY)
            assertEquals(nextState.scrollY, nextDebug.resolvedY)
            assertTrue(kotlin.math.abs(nextDebug.displayedY - nextDebug.resolvedY.toDouble()) <= 1.0)
            previousState = nextState
        }
    }

    @Test
    fun `wheel smoothness remains after thumb drag interaction`() {
        val fixture = createFixture()
        val visual =
            fixture.viewport
                .debugScrollbarVisualState()
                .vertical ?: error("Expected vertical scrollbar")
        val dragX = visual.thumbRect.x + visual.thumbRect.width / 2
        val startY = visual.thumbRect.y + visual.thumbRect.height / 2

        assertTrue(fixture.router.handleMouseDown(dragX, startY, MouseButton.LEFT))
        assertTrue(fixture.router.handleMouseMove(dragX, startY + 24))
        fixture.tree.paint(ctx)
        assertTrue(fixture.router.handleMouseUp(dragX, startY + 24, MouseButton.LEFT))

        val wheelX = fixture.viewport.bounds.x + 4
        val wheelY = fixture.viewport.bounds.y + 4
        assertTrue(fixture.router.handleMouseWheel(wheelX, wheelY, -120))

        val initial = fixture.viewport.debugScrollAnimationState()
        assertTrue(initial.targetY > initial.resolvedY)
        fixture.tree.paint(ctx)
        val progressed = fixture.viewport.debugScrollAnimationState()
        assertTrue(progressed.resolvedY > initial.resolvedY)
        repeat(90) {
            fixture.tree.paint(ctx)
        }
        val settled = fixture.viewport.debugScrollAnimationState()
        assertEquals(settled.targetY, settled.resolvedY)
    }

    @Test
    fun `scroll updates do not depend on unrelated viewport dimension changes`() {
        val fixture = createFixture()
        val initialY = fixture.button.bounds.y

        fixture.viewport.setScrollOffsets(0, 80)
        fixture.tree.paint(ctx)
        val afterScrollPaint = fixture.button.bounds.y
        assertEquals(initialY - 80, afterScrollPaint)

        repeat(4) {
            fixture.tree.paint(ctx)
        }
        assertEquals(afterScrollPaint, fixture.button.bounds.y)
    }

    private fun dispatchClick(tree: DomTree, x: Int, y: Int): Boolean = tree.dispatchClick(MouseClickEvent(x, y, MouseButton.LEFT))

    private fun createFixture(): Fixture {
        val clickCount = IntBox()
        val root = ContainerNode(key = "root")
        val viewport =
            ContainerNode(key = "viewport")
                .apply {
                    width = 160
                    height = 90
                    overflowX = Overflow.Hidden
                    overflowY = Overflow.Auto
                }.applyParent(root)
        val button =
            ButtonNode("row", key = "row-button")
                .apply {
                    width = 120
                    height = 24
                    margin = Insets(top = 40, right = 0, bottom = 0, left = 0)
                    onClick {
                        clickCount.value += 1
                    }
                }.applyParent(viewport)
        val filler =
            ContainerNode(key = "filler")
                .apply {
                    width = 120
                    height = 320
                }.applyParent(viewport)

        val tree = DomTree(root)
        tree.render(ctx, 420, 260)
        tree.paint(ctx)
        val router = LayerDomInputRouter { root }
        return Fixture(tree, root, viewport, button, filler, router, clickCount)
    }

    private data class Fixture(
        val tree: DomTree,
        val root: ContainerNode,
        val viewport: ContainerNode,
        val button: ButtonNode,
        val filler: ContainerNode,
        val router: LayerDomInputRouter,
        val clickCount: IntBox,
    )

    private fun expectedScrollFromSession(session: ScrollbarDragSessionDebugState, pointerAxisPx: Int): Int {
        if (session.maxScroll <= 0 || session.maxThumbTravelPx <= 0) return 0
        val desiredThumbStart =
            (pointerAxisPx - session.trackStartPx - session.grabOffsetPx)
                .coerceIn(0, session.maxThumbTravelPx)
        val ratio = desiredThumbStart.toDouble() / session.maxThumbTravelPx.toDouble()
        return (ratio * session.maxScroll.toDouble()).roundToInt().coerceIn(0, session.maxScroll)
    }

    private data class IntBox(
        var value: Int = 0,
    )
}
