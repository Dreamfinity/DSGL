package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.dom.layout.AffineTransform2D
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.style.PositionMode

internal data class UsedInteractionProjection(
    val worldTransform: AffineTransform2D,
    val childInputClipRect: Rect?,
    val canTraverseChildren: Boolean,
    val selfContainsPoint: Boolean
)

internal data class UsedInteractionNodeGeometry(
    val usedBorderRect: Rect,
    val usedClipRect: Rect?,
    val visibleBorderRect: Rect?
)

internal object UsedInteractionGeometryResolver {
    fun projectNodeAtPoint(
        node: DOMNode,
        mouseX: Int,
        mouseY: Int,
        parentTransform: AffineTransform2D,
        parentInputClipRect: Rect?
    ): UsedInteractionProjection? {
        val worldTransform = parentTransform.times(node.localTransformMatrix())
        val inverse = worldTransform.inverseOrNull() ?: return null

        val selfClipRect = resolveSelfInputClipRect(node, parentInputClipRect)
        if (!node.isPointInsideInputClip(mouseX, mouseY, selfClipRect)) {
            return null
        }

        val localPoint = inverse.transform(mouseX.toFloat(), mouseY.toFloat())
        val selfContainsPoint = node.bounds.contains(localPoint.first, localPoint.second)
        val childInputClipRect = node.inputClipRectForChildren(selfClipRect)
        val canTraverseChildren = node.isPointInsideInputClip(mouseX, mouseY, childInputClipRect)

        return UsedInteractionProjection(
            worldTransform = worldTransform,
            childInputClipRect = childInputClipRect,
            canTraverseChildren = canTraverseChildren,
            selfContainsPoint = selfContainsPoint
        )
    }

    fun orderedChildrenForHitTraversal(node: DOMNode): List<DOMNode> {
        return node.orderedChildrenForHitTestingTraversal()
    }

    fun resolveNodeGeometry(node: DOMNode): UsedInteractionNodeGeometry {
        val usedClipRect = resolveNodeSelfInputClipRect(node)
        val visibleBorderRect = when (usedClipRect) {
            null -> node.bounds
            else -> node.bounds.intersection(usedClipRect)
        }
        return UsedInteractionNodeGeometry(
            usedBorderRect = node.bounds,
            usedClipRect = usedClipRect,
            visibleBorderRect = visibleBorderRect
        )
    }

    private fun resolveSelfInputClipRect(
        node: DOMNode,
        parentInputClipRect: Rect?
    ): Rect? {
        return if (node.position == PositionMode.Fixed) {
            node.fixedViewportClipRectForPromotedParticipation()
        } else {
            parentInputClipRect
        }
    }

    private fun resolveNodeSelfInputClipRect(node: DOMNode): Rect? {
        val chain = ArrayList<DOMNode>(8)
        var current: DOMNode? = node
        while (current != null) {
            chain += current
            current = current.parent
        }
        chain.reverse()
        var parentClipRect: Rect? = null
        var selfClipRect: Rect? = null
        for (index in chain.indices) {
            val currentNode = chain[index]
            selfClipRect = resolveSelfInputClipRect(currentNode, parentClipRect)
            if (index == chain.lastIndex) {
                break
            }
            parentClipRect = currentNode.inputClipRectForChildren(selfClipRect)
        }
        return selfClipRect
    }
}
