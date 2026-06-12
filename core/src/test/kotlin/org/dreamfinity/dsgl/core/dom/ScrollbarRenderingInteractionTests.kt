package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.portal.input.SurfaceDomInputRouter
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.StyleEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScrollbarRenderingInteractionTests {
    private val trackColor = 0x55303030
    private val thumbColor = 0xAA9AA5B1.toInt()
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanup() {
        KeyModifiers.sync(shift = false, control = false, meta = false)
        StyleEngine.setStylesDirectory(null)
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `generic scrollbar rendering emits track and thumb when present`() {
        val viewport =
            createFixture(
                overflowX = Overflow.Visible,
                overflowY = Overflow.Scroll,
                viewportWidth = 120,
                viewportHeight = 70,
                contentWidth = 90,
                contentHeight = 40,
            ).viewport
        val commands = selfCommands(viewport)

        val vertical = viewport.debugScrollbarVisualState().vertical
        assertNotNull(vertical)
        assertTrue(
            commands.any { command ->
                command is RenderCommand.DrawRect &&
                    command.color == trackColor &&
                    command.x == vertical.trackRect.x &&
                    command.y == vertical.trackRect.y &&
                    command.width == vertical.trackRect.width &&
                    command.height == vertical.trackRect.height
            },
        )
        assertTrue(
            commands.any { command ->
                command is RenderCommand.DrawRect &&
                    command.color == thumbColor &&
                    command.x == vertical.thumbRect.x &&
                    command.y == vertical.thumbRect.y &&
                    command.width == vertical.thumbRect.width &&
                    command.height == vertical.thumbRect.height
            },
        )
    }

    @Test
    fun `thumb geometry stays synchronized with scroll offsets`() {
        val viewport =
            createFixture(
                overflowX = Overflow.Visible,
                overflowY = Overflow.Auto,
                viewportWidth = 120,
                viewportHeight = 70,
                contentWidth = 90,
                contentHeight = 260,
            ).viewport

        val initialState = viewport.scrollContainerState()
        val initialThumb =
            viewport
                .debugScrollbarVisualState()
                .vertical
                ?.thumbRect
                ?: error("Expected vertical scrollbar thumb")

        viewport.setScrollOffsets(0, initialState.maxScrollY / 2)
        val middleState = viewport.scrollContainerState()
        val middleThumb =
            viewport
                .debugScrollbarVisualState()
                .vertical
                ?.thumbRect
                ?: error("Expected vertical scrollbar thumb")

        viewport.setScrollOffsets(0, middleState.maxScrollY)
        val endVisual =
            viewport.debugScrollbarVisualState().vertical
                ?: error("Expected vertical scrollbar thumb")
        val endThumb = endVisual.thumbRect

        assertTrue(initialThumb.height >= 8)
        assertTrue(initialThumb.height <= endVisual.trackRect.height)
        assertTrue(middleThumb.y > initialThumb.y)
        assertTrue(endThumb.y >= middleThumb.y)
        val trackEnd = endVisual.trackRect.y + endVisual.trackRect.height
        val thumbEnd = endThumb.y + endThumb.height
        assertTrue(thumbEnd in (trackEnd - 1)..trackEnd)
    }

    @Test
    fun `wheel scrolling updates generic container scroll state`() {
        val fixture =
            createFixture(
                overflowX = Overflow.Visible,
                overflowY = Overflow.Auto,
                viewportWidth = 120,
                viewportHeight = 70,
                contentWidth = 90,
                contentHeight = 260,
            )
        val root = fixture.root
        val viewport = fixture.viewport
        val wheelTarget = fixture.wheelTarget
        val router = fixture.router

        val wheelX = wheelTarget.bounds.x + 2
        val wheelY = wheelTarget.bounds.y + 2
        val before = viewport.scrollContainerState().scrollY

        assertTrue(root.bounds.contains(wheelX, wheelY))
        assertTrue(router.handleMouseWheel(wheelX, wheelY, -120))
        advanceScrollAnimation(viewport)

        val after = viewport.scrollContainerState().scrollY
        assertTrue(after > before)
    }

    @Test
    fun `thumb drag updates scroll offset through generic pointer capture`() {
        val fixture =
            createFixture(
                overflowX = Overflow.Visible,
                overflowY = Overflow.Auto,
                viewportWidth = 120,
                viewportHeight = 70,
                contentWidth = 90,
                contentHeight = 320,
            )
        val viewport = fixture.viewport
        val router = fixture.router

        val visual =
            viewport.debugScrollbarVisualState().vertical
                ?: error("Expected vertical scrollbar visual state")
        val startX = visual.thumbRect.x + (visual.thumbRect.width / 2).coerceAtLeast(1)
        val startY = visual.thumbRect.y + (visual.thumbRect.height / 2).coerceAtLeast(1)
        val dragY = (visual.trackRect.y + visual.trackRect.height - 2).coerceAtLeast(startY + 1)

        val before = viewport.scrollContainerState().scrollY
        assertTrue(router.handleMouseDown(startX, startY, MouseButton.LEFT))
        assertTrue(router.handleMouseMove(startX, dragY))
        assertTrue(router.handleMouseUp(startX, dragY, MouseButton.LEFT))

        val after = viewport.scrollContainerState().scrollY
        assertTrue(after > before)
    }

    @Test
    fun `overflow modes control generic scrollbar visuals`() {
        val modes =
            listOf(
                Overflow.Visible to false,
                Overflow.Hidden to false,
                Overflow.Scroll to true,
                Overflow.Auto to true,
            )
        modes.forEach { (mode, expectedVisible) ->
            val viewport =
                createFixture(
                    overflowX = Overflow.Visible,
                    overflowY = mode,
                    viewportWidth = 120,
                    viewportHeight = 70,
                    contentWidth = 90,
                    contentHeight = 240,
                ).viewport
            val commands = selfCommands(viewport)

            val vertical = viewport.debugScrollbarVisualState().vertical
            assertEquals(expectedVisible, vertical != null, "Mode=$mode")
            val hasTrack =
                commands.any { command ->
                    command is RenderCommand.DrawRect && command.color == trackColor
                }
            assertEquals(expectedVisible, hasTrack, "Mode=$mode")
        }
    }

    @Test
    fun `horizontal auto scrollbar appears when content width exceeds viewport`() {
        val viewport =
            createFixture(
                overflowX = Overflow.Auto,
                overflowY = Overflow.Hidden,
                viewportWidth = 794,
                viewportHeight = 634,
                contentWidth = 826,
                contentHeight = 620,
            ).viewport
        val commands = selfCommands(viewport)

        val horizontal = viewport.debugScrollbarVisualState().horizontal
        assertNotNull(horizontal)
        assertTrue(
            commands.any { command ->
                command is RenderCommand.DrawRect &&
                    command.color == trackColor &&
                    command.x == horizontal.trackRect.x &&
                    command.y == horizontal.trackRect.y &&
                    command.width == horizontal.trackRect.width &&
                    command.height == horizontal.trackRect.height
            },
        )
    }

    @Test
    fun `normal wheel scrolls only vertical axis`() {
        val fixture =
            createFixture(
                overflowX = Overflow.Auto,
                overflowY = Overflow.Auto,
                viewportWidth = 120,
                viewportHeight = 70,
                contentWidth = 280,
                contentHeight = 260,
            )
        val viewport = fixture.viewport
        val wheelTarget = fixture.wheelTarget
        val router = fixture.router

        KeyModifiers.sync(shift = false, control = false, meta = false)
        val wheelX = wheelTarget.bounds.x + 2
        val wheelY = wheelTarget.bounds.y + 2

        assertTrue(router.handleMouseWheel(wheelX, wheelY, -120))
        advanceScrollAnimation(viewport)
        val state = viewport.scrollContainerState()
        assertTrue(state.scrollY > 0)
        assertEquals(0, state.scrollX)
    }

    @Test
    fun `shift plus wheel scrolls only horizontal axis`() {
        val fixture =
            createFixture(
                overflowX = Overflow.Auto,
                overflowY = Overflow.Auto,
                viewportWidth = 120,
                viewportHeight = 70,
                contentWidth = 280,
                contentHeight = 260,
            )
        val viewport = fixture.viewport
        val wheelTarget = fixture.wheelTarget
        val router = fixture.router

        KeyModifiers.sync(shift = true, control = false, meta = false)
        val wheelX = wheelTarget.bounds.x + 2
        val wheelY = wheelTarget.bounds.y + 2

        assertTrue(router.handleMouseWheel(wheelX, wheelY, -120))
        advanceScrollAnimation(viewport)
        val state = viewport.scrollContainerState()
        assertTrue(state.scrollX > 0)
        assertEquals(0, state.scrollY)
    }

    @Test
    fun `wheel chains to ancestor when intended axis cannot scroll on hovered container`() {
        val root =
            ContainerNode(key = "root").apply {
                bounds = Rect(0, 0, 640, 360)
            }
        val parent =
            ContainerNode(key = "parent")
                .apply {
                    bounds = Rect(20, 20, 260, 120)
                    overflowX = Overflow.Hidden
                    overflowY = Overflow.Auto
                }.applyParent(root)
        ContainerNode(key = "parent-content")
            .apply {
                bounds = Rect(parent.bounds.x, parent.bounds.y, 250, 420)
            }.applyParent(parent)

        val child =
            ContainerNode(key = "child")
                .apply {
                    bounds = Rect(parent.bounds.x + 8, parent.bounds.y + 8, 140, 60)
                    overflowX = Overflow.Auto
                    overflowY = Overflow.Hidden
                }.applyParent(parent)
        ContainerNode(key = "child-content")
            .apply {
                bounds = Rect(child.bounds.x, child.bounds.y, 340, 50)
            }.applyParent(child)
        val childButton =
            ButtonNode("child", key = "child-button")
                .apply {
                    bounds = Rect(child.bounds.x + 6, child.bounds.y + 6, 80, 20)
                    onClick { }
                }.applyParent(child)

        val router = SurfaceDomInputRouter { root }
        KeyModifiers.sync(shift = false, control = false, meta = false)
        val wheelX = childButton.bounds.x + 1
        val wheelY = childButton.bounds.y + 1

        val childBefore = child.scrollContainerState()
        val parentBefore = parent.scrollContainerState()
        assertTrue(router.handleMouseWheel(wheelX, wheelY, -120))
        advanceScrollAnimation(parent)

        val childAfter = child.scrollContainerState()
        val parentAfter = parent.scrollContainerState()
        assertEquals(childBefore.scrollX, childAfter.scrollX)
        assertEquals(childBefore.scrollY, childAfter.scrollY)
        assertTrue(parentAfter.scrollY > parentBefore.scrollY)
    }

    @Test
    fun `wheel scroll keeps thumb position synchronized`() {
        val fixture =
            createFixture(
                overflowX = Overflow.Visible,
                overflowY = Overflow.Auto,
                viewportWidth = 120,
                viewportHeight = 70,
                contentWidth = 90,
                contentHeight = 320,
            )
        val viewport = fixture.viewport
        val wheelTarget = fixture.wheelTarget
        val router = fixture.router
        KeyModifiers.sync(shift = false, control = false, meta = false)

        val beforeVisual =
            viewport.debugScrollbarVisualState().vertical ?: error("Expected vertical scrollbar visual state")
        val wheelX = wheelTarget.bounds.x + 2
        val wheelY = wheelTarget.bounds.y + 2
        assertTrue(router.handleMouseWheel(wheelX, wheelY, -120))
        advanceScrollAnimation(viewport)

        val afterVisual =
            viewport.debugScrollbarVisualState().vertical ?: error("Expected vertical scrollbar visual state")
        assertTrue(afterVisual.thumbRect.y > beforeVisual.thumbRect.y)
        assertFalse(afterVisual.thumbRect == beforeVisual.thumbRect)
    }

    @Test
    fun `wheel chains to ancestor when inner container is at vertical limit`() {
        val root =
            ContainerNode(key = "root").apply {
                bounds = Rect(0, 0, 640, 360)
            }
        val outer =
            ContainerNode(key = "outer")
                .apply {
                    bounds = Rect(20, 20, 260, 150)
                    overflowX = Overflow.Hidden
                    overflowY = Overflow.Auto
                }.applyParent(root)
        ContainerNode(key = "outer-content")
            .apply {
                bounds = Rect(outer.bounds.x, outer.bounds.y, 240, 460)
            }.applyParent(outer)
        val inner =
            ContainerNode(key = "inner")
                .apply {
                    bounds = Rect(36, 36, 140, 72)
                    overflowX = Overflow.Hidden
                    overflowY = Overflow.Auto
                }.applyParent(outer)
        ContainerNode(key = "inner-content")
            .apply {
                bounds = Rect(inner.bounds.x, inner.bounds.y, 120, 320)
            }.applyParent(inner)

        val router = SurfaceDomInputRouter { root }
        KeyModifiers.sync(shift = false, control = false, meta = false)
        val innerMax = inner.scrollContainerState().maxScrollY
        inner.setScrollOffsets(0, innerMax)
        advanceScrollAnimation(root, frames = 8)

        val wheelX = inner.bounds.x + 6
        val wheelY = inner.bounds.y + 6
        val innerBefore = inner.scrollContainerState()
        val outerBefore = outer.scrollContainerState()

        assertTrue(router.handleMouseWheel(wheelX, wheelY, -120))
        advanceScrollAnimation(root, frames = 10)

        val innerAfter = inner.scrollContainerState()
        val outerAfter = outer.scrollContainerState()
        val outerDebug = outer.debugScrollAnimationState()
        assertEquals(innerBefore.scrollY, innerAfter.scrollY)
        assertTrue(outerAfter.scrollY > outerBefore.scrollY)
        assertEquals(outerAfter.scrollY, outerDebug.resolvedY)
        assertTrue(kotlin.math.abs(outerDebug.displayedY - outerDebug.resolvedY.toDouble()) <= 1.0)
    }

    @Test
    fun `dragging nested inner thumb does not affect outer scroll state`() {
        val root =
            ContainerNode(key = "root").apply {
                bounds = Rect(0, 0, 680, 420)
            }
        val outer =
            ContainerNode(key = "outer")
                .apply {
                    bounds = Rect(18, 18, 280, 180)
                    overflowX = Overflow.Hidden
                    overflowY = Overflow.Scroll
                }.applyParent(root)
        ContainerNode(key = "outer-content")
            .apply {
                bounds = Rect(outer.bounds.x, outer.bounds.y, 250, 520)
            }.applyParent(outer)

        val inner =
            ContainerNode(key = "inner")
                .apply {
                    bounds = Rect(40, 42, 160, 90)
                    overflowX = Overflow.Hidden
                    overflowY = Overflow.Scroll
                }.applyParent(outer)
        ContainerNode(key = "inner-content")
            .apply {
                bounds = Rect(inner.bounds.x, inner.bounds.y, 130, 360)
            }.applyParent(inner)

        val router = SurfaceDomInputRouter { root }
        val innerThumb =
            inner
                .debugScrollbarVisualState()
                .vertical
                ?.thumbRect ?: error("inner thumb missing")
        val dragX = innerThumb.x + innerThumb.width / 2
        val startY = innerThumb.y + innerThumb.height / 2
        val endY = startY + 32

        val outerBefore = outer.scrollContainerState()
        val innerBefore = inner.scrollContainerState()

        assertTrue(router.handleMouseDown(dragX, startY, MouseButton.LEFT))
        assertTrue(router.handleMouseMove(dragX, endY))
        assertTrue(router.handleMouseUp(dragX, endY, MouseButton.LEFT))
        advanceScrollAnimation(root, frames = 4)

        val outerAfter = outer.scrollContainerState()
        val innerAfter = inner.scrollContainerState()
        val innerDebug = inner.debugScrollAnimationState()

        assertEquals(outerBefore.scrollY, outerAfter.scrollY)
        assertTrue(innerAfter.scrollY > innerBefore.scrollY)
        assertEquals(innerAfter.scrollY, innerDebug.resolvedY)
        assertTrue(kotlin.math.abs(innerDebug.displayedY - innerDebug.resolvedY.toDouble()) <= 1.0)
    }

    private fun advanceScrollAnimation(node: DOMNode, frames: Int = 6) {
        repeat(frames) {
            node.advanceScrollAnimationsRecursively(1.0 / 60.0)
            node.consumeScrollLayoutDirtyRecursively()
        }
    }

    private fun selfCommands(node: DOMNode): List<RenderCommand> {
        val out = ArrayList<RenderCommand>()
        DOMNode.withChildrenRenderPass(enabled = false) {
            node.buildRenderCommands(ctx, out)
        }
        return out
    }

    private fun createFixture(
        overflowX: Overflow,
        overflowY: Overflow,
        viewportWidth: Int,
        viewportHeight: Int,
        contentWidth: Int,
        contentHeight: Int,
    ): Quad {
        val root =
            ContainerNode(key = "root").apply {
                bounds = Rect(0, 0, 2000, 1200)
            }
        val viewport =
            ContainerNode(key = "viewport")
                .apply {
                    bounds = Rect(40, 30, viewportWidth, viewportHeight)
                    this.overflowX = overflowX
                    this.overflowY = overflowY
                }.applyParent(root)
        ContainerNode(key = "content")
            .apply {
                bounds = Rect(viewport.bounds.x, viewport.bounds.y, contentWidth, contentHeight)
            }.applyParent(viewport)
        val wheelTarget =
            ButtonNode("wheel", key = "wheel-target").apply {
                bounds = Rect(viewport.bounds.x + 6, viewport.bounds.y + 6, 64, 20)
                onClick { }
            }
        wheelTarget.applyParent(viewport)

        val router = SurfaceDomInputRouter { root }
        return Quad(root, viewport, wheelTarget, router)
    }

    private data class Quad(
        val root: ContainerNode,
        val viewport: ContainerNode,
        val wheelTarget: ButtonNode,
        val router: SurfaceDomInputRouter,
    )
}
