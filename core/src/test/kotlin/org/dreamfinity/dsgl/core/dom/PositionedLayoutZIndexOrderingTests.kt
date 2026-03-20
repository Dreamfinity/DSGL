package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.dispatchClick
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.PositionMode
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PositionedLayoutZIndexOrderingTests {
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
    fun `positioned siblings paint by z-index and hit-testing is reverse paint`() {
        val root = ContainerNode(key = "z-order-root", stackLayout = true)
        val low = button("low", zIndex = -3, position = PositionMode.Relative).applyParent(root)
        val mid = button("mid", zIndex = 1, position = PositionMode.Absolute).applyParent(root)
        val high = button("high", zIndex = 5, position = PositionMode.Fixed).applyParent(root)

        renderTree(root, width = 220, height = 140)

        assertEquals(listOf(low, mid, high), root.orderedChildrenForPaintTraversal())
        assertEquals(listOf(high, mid, low), root.orderedChildrenForHitTestingTraversal())
    }

    @Test
    fun `topmost positioned overlap wins click by z-index`() {
        val root = ContainerNode(key = "z-hit-root", stackLayout = true)
        var lowClicks = 0
        var highClicks = 0

        button("low", zIndex = 0, position = PositionMode.Relative).apply {
            onClick { lowClicks += 1 }
        }.applyParent(root)
        button("high", zIndex = 6, position = PositionMode.Relative).apply {
            onClick { highClicks += 1 }
        }.applyParent(root)

        renderTree(root, width = 220, height = 140)

        assertTrue(dispatchClick(root, MouseClickEvent(4, 4, MouseButton.LEFT)))
        assertEquals(0, lowClicks)
        assertEquals(1, highClicks)
    }

    @Test
    fun `equal z-index uses DOM order tie-break for hit-testing`() {
        val root = ContainerNode(key = "z-tie-root", stackLayout = true)
        var firstClicks = 0
        var secondClicks = 0

        val first = button("first", zIndex = 2, position = PositionMode.Relative).apply {
            onClick { firstClicks += 1 }
        }.applyParent(root)
        val second = button("second", zIndex = 2, position = PositionMode.Relative).apply {
            onClick { secondClicks += 1 }
        }.applyParent(root)

        renderTree(root, width = 220, height = 140)

        assertEquals(listOf(first, second), root.orderedChildrenForPaintTraversal())
        assertTrue(dispatchClick(root, MouseClickEvent(4, 4, MouseButton.LEFT)))
        assertEquals(0, firstClicks)
        assertEquals(1, secondClicks)
    }

    @Test
    fun `mixed static and positioned overlap follows explicit DSGL stage rule`() {
        val root = ContainerNode(key = "z-mixed-root", stackLayout = true)
        var staticClicks = 0
        var positionedClicks = 0

        val staticNode = button("static", zIndex = 999, position = PositionMode.Static).apply {
            onClick { staticClicks += 1 }
        }.applyParent(root)
        val positionedNode = button("positioned", zIndex = -100, position = PositionMode.Relative).apply {
            onClick { positionedClicks += 1 }
        }.applyParent(root)

        renderTree(root, width = 220, height = 140)

        // Stage rule: positioned bucket paints above static bucket regardless of static z-index.
        assertEquals(listOf(staticNode, positionedNode), root.orderedChildrenForPaintTraversal())
        assertTrue(dispatchClick(root, MouseClickEvent(4, 4, MouseButton.LEFT)))
        assertEquals(0, staticClicks)
        assertEquals(1, positionedClicks)
    }

    @Test
    fun `z-index scope remains same-root only`() {
        val rootA = ContainerNode(key = "z-scope-a", stackLayout = true)
        val rootB = ContainerNode(key = "z-scope-b", stackLayout = true)

        val aLow = button("a-low", zIndex = -1, position = PositionMode.Relative).applyParent(rootA)
        val aHigh = button("a-high", zIndex = 2, position = PositionMode.Relative).applyParent(rootA)
        val bOnly = button("b-only", zIndex = 999, position = PositionMode.Relative).applyParent(rootB)

        renderTree(rootA, width = 220, height = 140)
        renderTree(rootB, width = 220, height = 140)

        assertEquals(listOf(aLow, aHigh), rootA.orderedChildrenForPaintTraversal())
        assertEquals(listOf(bOnly), rootB.orderedChildrenForPaintTraversal())
        assertFalse(aHigh.sharesRootStackingScopeForPositioning(bOnly))
    }

    @Test
    fun `inspector z-index override reorders positioned overlap consistently`() {
        val root = ContainerNode(key = "z-inspector-root", stackLayout = true)
        var lowerClicks = 0
        var upperClicks = 0

        val lower = button("lower", zIndex = 1, position = PositionMode.Relative).apply {
            onClick { lowerClicks += 1 }
        }.applyParent(root)
        val upper = button("upper", zIndex = 3, position = PositionMode.Relative).apply {
            onClick { upperClicks += 1 }
        }.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 220, 140)

        assertTrue(dispatchClick(root, MouseClickEvent(4, 4, MouseButton.LEFT)))
        assertEquals(0, lowerClicks)
        assertEquals(1, upperClicks)

        StyleEngine.setInspectorOverrideLiteral(upper, StyleProperty.Z_INDEX, "-5").getOrThrow()
        tree.render(ctx, 220, 140)
        assertEquals(listOf(upper, lower), root.orderedChildrenForPaintTraversal())

        assertTrue(dispatchClick(root, MouseClickEvent(4, 4, MouseButton.LEFT)))
        assertEquals(1, lowerClicks)
        assertEquals(1, upperClicks)
    }

    private fun renderTree(root: ContainerNode, width: Int, height: Int) {
        DomTree(root).render(ctx, width, height)
    }

    private fun button(key: String, zIndex: Int, position: PositionMode): ButtonNode {
        return ButtonNode(key, key = key).apply {
            width = 80
            height = 24
            this.zIndex = zIndex
            this.position = position
        }
    }
}
