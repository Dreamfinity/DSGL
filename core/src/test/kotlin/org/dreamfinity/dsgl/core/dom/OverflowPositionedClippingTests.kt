package org.dreamfinity.dsgl.core.dom

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.dispatchClick
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.PositionMode
import org.dreamfinity.dsgl.core.style.StyleDeclarations
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty

class OverflowPositionedClippingTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @AfterTest
    fun cleanup() {
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `overflow auto keeps clipping for ordinary in-flow child`() {
        val root = ContainerNode(key = "root")
        val clip = ContainerNode(key = "clip").apply {
            width = 80
            height = 40
            overflow = Overflow.Auto
        }.applyParent(root)
        ButtonNode("child", key = "child").apply {
            width = 120
            height = 16
        }.applyParent(clip)

        val tree = DomTree(root)
        tree.render(ctx, 220, 160)
        val state = clip.scrollContainerState()
        val commands = tree.paint(ctx, applyStyles = false)

        assertTrue(state.axisX.clipsToViewport)
        val clipPush = commands.filterIsInstance<RenderCommand.PushClip>().firstOrNull { push ->
            push.x == state.viewportRect.x &&
                push.y == state.viewportRect.y &&
                push.width == state.viewportRect.width &&
                push.height == state.viewportRect.height
        }
        assertTrue(clipPush != null)
    }

    @Test
    fun `relative child is clipped in mixed-axis overflow auto and scroll`() {
        verifyPositionedChildGutterClipping(position = PositionMode.Relative, overflowY = Overflow.Auto)
        verifyPositionedChildGutterClipping(position = PositionMode.Relative, overflowY = Overflow.Scroll)
    }

    @Test
    fun `absolute child is clipped in mixed-axis overflow auto and scroll`() {
        verifyPositionedChildGutterClipping(position = PositionMode.Absolute, overflowY = Overflow.Auto)
        verifyPositionedChildGutterClipping(position = PositionMode.Absolute, overflowY = Overflow.Scroll)
    }

    @Test
    fun `nested mixed-axis overflow still clips positioned descendant at outer gutter edge`() {
        val root = ContainerNode(key = "root")
        val outer = ContainerNode(key = "outer").apply {
            width = 90
            height = 50
            overflowX = Overflow.Visible
            overflowY = Overflow.Auto
        }.applyParent(root)
        val inner = ContainerNode(key = "inner").apply {
            width = 86
            height = 40
        }.applyParent(outer)
        ContainerNode(key = "filler").apply {
            width = 20
            height = 220
        }.applyParent(outer)
        val absolute = ButtonNode("abs", key = "nested-absolute").apply {
            width = 24
            height = 12
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "absolute",
                StyleProperty.LEFT to "74px",
                StyleProperty.TOP to "0px"
            )
        }.applyParent(inner)

        val tree = DomTree(root)
        tree.render(ctx, 240, 180)
        tree.paint(ctx, applyStyles = false)
        val outerState = outer.scrollContainerState()

        val visibleX = outerState.viewportRect.x + outerState.viewportRect.width - 1
        val gutterX = outerState.viewportRect.x + outerState.viewportRect.width + 1
        val probeY = outerState.viewportRect.y + 2

        assertTrue(absolute.containsGlobalPoint(visibleX, probeY))
        assertFalse(absolute.containsGlobalPoint(gutterX, probeY))
    }

    private fun verifyPositionedChildGutterClipping(position: PositionMode, overflowY: Overflow) {
        val root = ContainerNode(key = "root-$position-$overflowY")
        val scroll = ContainerNode(key = "scroll-$position-$overflowY").apply {
            width = 80
            height = 40
            overflowX = Overflow.Visible
            this.overflowY = overflowY
        }.applyParent(root)
        var clicks = 0
        val child = ButtonNode("child", key = "child-$position-$overflowY").apply {
            width = 20
            height = 12
            val modeLiteral = when (position) {
                PositionMode.Absolute -> "absolute"
                PositionMode.Relative -> "relative"
                PositionMode.Fixed -> "fixed"
                PositionMode.Static -> "static"
                PositionMode.Sticky -> "sticky"
            }
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to modeLiteral,
                StyleProperty.LEFT to "68px",
                StyleProperty.TOP to "0px"
            )
            onClick { clicks += 1 }
        }
        if (position == PositionMode.Relative) {
            child.applyParent(scroll)
            ContainerNode(key = "filler-$position-$overflowY").apply {
                width = 16
                height = 220
            }.applyParent(scroll)
        } else {
            ContainerNode(key = "filler-$position-$overflowY").apply {
                width = 16
                height = 220
            }.applyParent(scroll)
            child.applyParent(scroll)
        }

        val tree = DomTree(root)
        tree.render(ctx, 240, 180)
        val commands = tree.paint(ctx, applyStyles = false)
        val state = scroll.scrollContainerState()

        assertTrue(state.axisY.scrollbarPresent)
        assertTrue(state.verticalScrollbarGutter > 0)
        assertEquals(
            state.baseViewportRect.width - state.verticalScrollbarGutter,
            state.viewportRect.width
        )

        val visibleX = state.viewportRect.x + state.viewportRect.width - 1
        val gutterX = state.viewportRect.x + state.viewportRect.width + 1
        val probeY = state.viewportRect.y + 2

        assertTrue(child.containsGlobalPoint(visibleX, probeY))
        assertFalse(child.containsGlobalPoint(gutterX, probeY))
        assertTrue(dispatchClick(root, MouseClickEvent(visibleX, probeY, MouseButton.LEFT)))
        assertFalse(dispatchClick(root, MouseClickEvent(gutterX, probeY, MouseButton.LEFT)))
        assertEquals(1, clicks)

        val clipPush = commands.filterIsInstance<RenderCommand.PushClip>().firstOrNull { push ->
            push.x == state.viewportRect.x &&
                push.y == state.viewportRect.y &&
                push.width == state.viewportRect.width &&
                push.height == state.viewportRect.height
        }
        assertTrue(clipPush != null)
    }

    private fun styleDeclarations(vararg entries: Pair<StyleProperty, String>): StyleDeclarations {
        return StyleDeclarations().apply {
            entries.forEach { (property, literal) ->
                set(property, StyleExpression.Literal(literal))
            }
        }
    }
}
