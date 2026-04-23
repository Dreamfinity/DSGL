package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.dom.layout.AffineTransform2D
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.style.PositionMode

internal data class UsedInteractionProjection(
    val worldTransform: AffineTransform2D,
    val childInputClipRect: Rect?,
    val canTraverseChildren: Boolean,
    val selfContainsPoint: Boolean,
)

internal data class UsedInteractionNodeGeometry(
    val usedBorderRect: Rect,
    val usedClipRect: Rect?,
    val visibleBorderRect: Rect?,
)

internal object UsedInteractionGeometryResolver {
    fun projectNodeAtPoint(
        node: DOMNode,
        mouseX: Int,
        mouseY: Int,
        parentTransform: AffineTransform2D,
        parentInputClipRect: Rect?,
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
            selfContainsPoint = selfContainsPoint,
        )
    }

    fun orderedChildrenForHitTraversal(node: DOMNode): List<DOMNode> = node.orderedChildrenForHitTestingTraversal()

    fun resolveNodeGeometry(node: DOMNode): UsedInteractionNodeGeometry {
        val usedClipRect = resolveNodeSelfInputClipRect(node)
        val usedBorderRect = resolveUsedBorderRect(node)
        val visibleBorderRect =
            when (usedClipRect) {
                null -> usedBorderRect
                else -> usedBorderRect.intersection(usedClipRect)
            }
        return UsedInteractionNodeGeometry(
            usedBorderRect = usedBorderRect,
            usedClipRect = usedClipRect,
            visibleBorderRect = visibleBorderRect,
        )
    }

    private fun resolveSelfInputClipRect(node: DOMNode, parentInputClipRect: Rect?): Rect? =
        if (node.position == PositionMode.Fixed) {
            node.fixedViewportClipRectForPromotedParticipation()
        } else {
            parentInputClipRect
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

    private fun resolveUsedBorderRect(node: DOMNode): Rect {
        val world = node.worldTransformMatrix()
        val bounds = node.bounds
        val topLeft = world.transform(bounds.x.toFloat(), bounds.y.toFloat())
        val topRight = world.transform((bounds.x + bounds.width).toFloat(), bounds.y.toFloat())
        val bottomLeft = world.transform(bounds.x.toFloat(), (bounds.y + bounds.height).toFloat())
        val bottomRight = world.transform((bounds.x + bounds.width).toFloat(), (bounds.y + bounds.height).toFloat())

        val minX = minOf(topLeft.first, topRight.first, bottomLeft.first, bottomRight.first)
        val maxX = maxOf(topLeft.first, topRight.first, bottomLeft.first, bottomRight.first)
        val minY = minOf(topLeft.second, topRight.second, bottomLeft.second, bottomRight.second)
        val maxY = maxOf(topLeft.second, topRight.second, bottomLeft.second, bottomRight.second)

        val x =
            kotlin.math
                .floor(minX.toDouble())
                .toInt()
        val y =
            kotlin.math
                .floor(minY.toDouble())
                .toInt()
        val width =
            kotlin.math
                .ceil((maxX - minX).toDouble())
                .toInt()
                .coerceAtLeast(0)
        val height =
            kotlin.math
                .ceil((maxY - minY).toDouble())
                .toInt()
                .coerceAtLeast(0)
        return Rect(x, y, width, height)
    }
}
