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
}
