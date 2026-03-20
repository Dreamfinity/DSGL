package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.style.PositionMode

internal object PositionedLayoutModel {
    fun isPositioned(node: DOMNode): Boolean {
        return node.position != PositionMode.Static
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

    fun orderedChildrenForPaint(parent: DOMNode): List<DOMNode> {
        val children = parent.children
        if (children.size <= 1) return children
        var hasPositionedOrCustomZ = false
        children.forEach { child ->
            if (isPositioned(child) || child.zIndex != 0) {
                hasPositionedOrCustomZ = true
                return@forEach
            }
        }
        if (!hasPositionedOrCustomZ) {
            return children
        }

        return children
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<DOMNode>>(
                    { if (isPositioned(it.value)) 1 else 0 },
                    { it.value.zIndex },
                    { it.index }
                )
            )
            .map { it.value }
    }

    fun orderedChildrenForHitTesting(parent: DOMNode): List<DOMNode> {
        return orderedChildrenForPaint(parent).asReversed()
    }
}

