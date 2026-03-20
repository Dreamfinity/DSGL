package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.dispatchClick
import org.dreamfinity.dsgl.core.style.PositionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PositionedLayoutModelTests {
    @Test
    fun `root stacking scope is isolated per dom root`() {
        val rootA = ContainerNode(key = "root-a")
        val rootB = ContainerNode(key = "root-b")
        val childA = ContainerNode(key = "child-a").applyParent(rootA)
        val childB = ContainerNode(key = "child-b").applyParent(rootB)

        assertSame(rootA, childA.rootStackingScopeForPositioning())
        assertSame(rootB, childB.rootStackingScopeForPositioning())
        assertFalse(childA.sharesRootStackingScopeForPositioning(childB))
    }

    @Test
    fun `paint and hit-test ordering use one shared model`() {
        val root = ContainerNode(key = "order-root")

        val staticChild = ContainerNode(key = "static")
        staticChild.applyParent(root)

        val positionedLow = ContainerNode(key = "positioned-low").apply {
            position = PositionMode.Relative
            zIndex = -1
        }
        positionedLow.applyParent(root)

        val positionedHigh = ContainerNode(key = "positioned-high").apply {
            position = PositionMode.Relative
            zIndex = 5
        }
        positionedHigh.applyParent(root)

        val paintOrder = root.orderedChildrenForPaintTraversal()
        val hitOrder = root.orderedChildrenForHitTestingTraversal()

        assertEquals(listOf(staticChild, positionedLow, positionedHigh), paintOrder)
        assertEquals(paintOrder.reversed(), hitOrder)
    }

    @Test
    fun `equal ordering priority uses dom order tie-breaker`() {
        val root = ContainerNode(key = "tie-root")
        val first = ContainerNode(key = "first").apply {
            position = PositionMode.Relative
        }
        val second = ContainerNode(key = "second").apply {
            position = PositionMode.Relative
        }
        val third = ContainerNode(key = "third").apply {
            position = PositionMode.Relative
        }

        first.applyParent(root)
        second.applyParent(root)
        third.applyParent(root)

        assertEquals(listOf(first, second, third), root.orderedChildrenForPaintTraversal())
        assertEquals(listOf(third, second, first), root.orderedChildrenForHitTestingTraversal())
    }

    @Test
    fun `absolute containing block primitive resolves nearest positioned ancestor or root`() {
        val root = ContainerNode(key = "cb-root")
        val staticAncestor = ContainerNode(key = "cb-static-ancestor").applyParent(root)
        val positionedAncestor = ContainerNode(key = "cb-positioned-ancestor").apply {
            position = PositionMode.Relative
        }.applyParent(staticAncestor)
        val nestedStatic = ContainerNode(key = "cb-nested-static").applyParent(positionedAncestor)
        val leaf = ContainerNode(key = "cb-leaf").applyParent(nestedStatic)

        val branchWithoutPositioned = ContainerNode(key = "cb-branch").applyParent(root)
        val leafWithoutPositioned = ContainerNode(key = "cb-leaf-root-fallback").applyParent(branchWithoutPositioned)

        assertSame(positionedAncestor, leaf.containingBlockForAbsolutePositioning())
        assertSame(root, leafWithoutPositioned.containingBlockForAbsolutePositioning())
    }

    @Test
    fun `fixed root viewport primitive resolves current dom root viewport`() {
        val rootA = ContainerNode(key = "fixed-root-a")
        val rootB = ContainerNode(key = "fixed-root-b")

        val nodeA = ContainerNode(key = "fixed-a-node").applyParent(rootA)
        val nodeB = ContainerNode(key = "fixed-b-node").applyParent(rootB)

        assertSame(rootA, nodeA.fixedViewportRootForPositioning())
        assertSame(rootB, nodeB.fixedViewportRootForPositioning())
        assertFalse(nodeA.fixedViewportRootForPositioning() === nodeB.fixedViewportRootForPositioning())
    }

    @Test
    fun `z-index ordering remains scoped to current root`() {
        val rootA = ContainerNode(key = "z-root-a")
        val rootB = ContainerNode(key = "z-root-b")

        val aLow = ContainerNode(key = "a-low").apply {
            position = PositionMode.Relative
            zIndex = -10
        }.applyParent(rootA)
        val aHigh = ContainerNode(key = "a-high").apply {
            position = PositionMode.Relative
            zIndex = 10
        }.applyParent(rootA)

        val bOnly = ContainerNode(key = "b-only").apply {
            position = PositionMode.Relative
            zIndex = 999
        }.applyParent(rootB)

        assertEquals(listOf(aLow, aHigh), rootA.orderedChildrenForPaintTraversal())
        assertEquals(listOf(bOnly), rootB.orderedChildrenForPaintTraversal())
        assertFalse(aHigh.sharesRootStackingScopeForPositioning(bOnly))
    }

    @Test
    fun `dispatch click follows reverse paint order`() {
        val root = ContainerNode(key = "click-root").apply {
            bounds = Rect(0, 0, 120, 80)
        }
        var underClicks = 0
        var overClicks = 0

        val under = ButtonNode("under", key = "under").apply {
            bounds = Rect(10, 10, 80, 24)
            onClick { underClicks += 1 }
        }
        under.applyParent(root)

        val over = ButtonNode("over", key = "over").apply {
            bounds = Rect(10, 10, 80, 24)
            position = PositionMode.Relative
            zIndex = 2
            onClick { overClicks += 1 }
        }
        over.applyParent(root)

        val paintOrder = root.orderedChildrenForPaintTraversal()
        assertEquals(listOf(under, over), paintOrder)

        val click = MouseClickEvent(mouseX = 12, mouseY = 12, mouseButton = MouseButton.LEFT)
        assertTrue(dispatchClick(root, click))
        assertEquals(1, overClicks)
        assertEquals(0, underClicks)
    }
}


