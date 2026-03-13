package org.dreamfinity.dsgl.core.event

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.AffineTransform2D
import org.dreamfinity.dsgl.core.dom.layout.Rect

/**
 * Dispatches a click through the DOM tree; returns true if handled.
 */
fun dispatchClick(root: DOMNode, event: MouseClickEvent): Boolean {
    return dispatchClickInternal(
        element = root,
        event = event,
        parentTransform = AffineTransform2D.IDENTITY,
        parentInputClipRect = null
    )
}

internal fun dispatchClickInternal(
    element: DOMNode,
    event: MouseClickEvent,
    parentTransform: AffineTransform2D,
    parentInputClipRect: Rect?
): Boolean {
    if (!element.isHitTestVisible()) {
        return false
    }
    if (!element.isPointInsideInputClip(event.mouseX, event.mouseY, parentInputClipRect)) {
        return false
    }

    val worldTransform = parentTransform.times(element.localTransformMatrix())
    val inverse = worldTransform.inverseOrNull() ?: return false
    val local = inverse.transform(event.mouseX.toFloat(), event.mouseY.toFloat())
    if (!element.bounds.contains(local.first, local.second)) {
        return false
    }

    val childInputClipRect = element.inputClipRectForChildren(parentInputClipRect)
    for (i in element.children.size - 1 downTo 0) {
        if (
            dispatchClickInternal(
                element = element.children[i],
                event = event,
                parentTransform = worldTransform,
                parentInputClipRect = childInputClipRect
            )
        ) {
            return true
        }
    }

    return element.handleClick(event)
}
