package org.dreamfinity.dsgl.core.event

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.UsedInteractionGeometryResolver
import org.dreamfinity.dsgl.core.dom.layout.AffineTransform2D
import org.dreamfinity.dsgl.core.dom.layout.Rect

/**
 * Dispatches a click through the DOM tree; returns true if handled.
 */
fun dispatchClick(root: DOMNode, event: MouseClickEvent): Boolean =
    dispatchClickInternal(
        element = root,
        event = event,
        parentTransform = AffineTransform2D.IDENTITY,
        parentInputClipRect = null,
    )

internal fun dispatchClickInternal(
    element: DOMNode,
    event: MouseClickEvent,
    parentTransform: AffineTransform2D,
    parentInputClipRect: Rect?,
): Boolean {
    if (!element.isHitTestVisible()) {
        return false
    }
    val projection =
        UsedInteractionGeometryResolver.projectNodeAtPoint(
            node = element,
            mouseX = event.mouseX,
            mouseY = event.mouseY,
            parentTransform = parentTransform,
            parentInputClipRect = parentInputClipRect,
        ) ?: return false

    val childInputClipRect = projection.childInputClipRect
    if (projection.canTraverseChildren) {
        UsedInteractionGeometryResolver.orderedChildrenForHitTraversal(element).forEach { child ->
            if (
                dispatchClickInternal(
                    element = child,
                    event = event,
                    parentTransform = projection.worldTransform,
                    parentInputClipRect = childInputClipRect,
                )
            ) {
                return true
            }
        }
    }

    if (!projection.selfContainsPoint) {
        return false
    }

    return element.handleClick(event)
}
