package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.style.CssLength
import org.dreamfinity.dsgl.core.style.PositionMode
import org.dreamfinity.dsgl.core.style.StyleProperty

internal object PositionedLayoutModel {
    data class OffsetPrecedenceResolution(
        val sourceProperty: StyleProperty?,
        val value: CssLength?
    )

    data class OrderingPriority(
        val positionedBucket: Int,
        val zIndex: Int,
        val domOrder: Int
    )

    data class ChildEntry(
        val node: DOMNode,
        val priority: OrderingPriority
    )

    fun isPositioned(node: DOMNode): Boolean {
        return node.position != PositionMode.Static
    }

    private fun effectiveOrderingZIndex(node: DOMNode): Int {
        return if (isPositioned(node)) node.zIndex else 0
    }

    fun orderingPriority(node: DOMNode, domOrder: Int): OrderingPriority {
        return OrderingPriority(
            positionedBucket = if (isPositioned(node)) 1 else 0,
            zIndex = effectiveOrderingZIndex(node),
            domOrder = domOrder
        )
    }

    fun rootStackingScope(node: DOMNode): DOMNode {
        var current = node
        while (current.parent != null) {
            current = current.parent!!
        }
        return current
    }

    fun sharesRootStackingScope(first: DOMNode, second: DOMNode): Boolean {
        return rootStackingScope(first) === rootStackingScope(second)
    }

    fun containingBlockForAbsolute(node: DOMNode): DOMNode {
        var current = node.parent
        while (current != null) {
            if (isPositioned(current)) {
                return current
            }
            current = current.parent
        }
        return rootStackingScope(node)
    }

    fun fixedViewportRoot(node: DOMNode): DOMNode {
        return rootStackingScope(node)
    }

    fun resolveHorizontalOffset(left: CssLength?, right: CssLength?): OffsetPrecedenceResolution {
        return when {
            left != null -> OffsetPrecedenceResolution(StyleProperty.LEFT, left)
            right != null -> OffsetPrecedenceResolution(StyleProperty.RIGHT, right)
            else -> OffsetPrecedenceResolution(null, null)
        }
    }

    fun resolveVerticalOffset(top: CssLength?, bottom: CssLength?): OffsetPrecedenceResolution {
        return when {
            top != null -> OffsetPrecedenceResolution(StyleProperty.TOP, top)
            bottom != null -> OffsetPrecedenceResolution(StyleProperty.BOTTOM, bottom)
            else -> OffsetPrecedenceResolution(null, null)
        }
    }

    fun orderedChildrenForPaint(parent: DOMNode): List<DOMNode> {
        val children = parent.children
        if (children.size <= 1) return children
        var hasPositioned = false
        children.forEach { child ->
            if (isPositioned(child)) {
                hasPositioned = true
                return@forEach
            }
        }
        if (!hasPositioned) {
            return children
        }

        return children
            .withIndex()
            .map { indexed -> ChildEntry(indexed.value, orderingPriority(indexed.value, indexed.index)) }
            .sortedWith(
                compareBy(
                    { it.priority.positionedBucket },
                    { it.priority.zIndex },
                    { it.priority.domOrder }
                )
            )
            .map { it.node }
    }

    fun orderedChildrenForHitTesting(parent: DOMNode): List<DOMNode> {
        return orderedChildrenForPaint(parent).asReversed()
    }
}

