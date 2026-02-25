package org.dreamfinity.dsgl.core.event

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.AffineTransform2D

/**
 * Dispatches a click through the DOM tree; returns true if handled.
 */
fun dispatchClick(root: DOMNode, event: MouseClickEvent): Boolean {
    return dispatchClickInternal(root, event, AffineTransform2D.IDENTITY)
}

internal fun dispatchClickInternal(
    element: DOMNode,
    event: MouseClickEvent,
    parentTransform: AffineTransform2D
): Boolean {
    if (!element.isHitTestVisible()) {
        return false
    }

    val worldTransform = parentTransform.times(element.localTransformMatrix())
    val inverse = worldTransform.inverseOrNull() ?: return false
    val local = inverse.transform(event.mouseX.toFloat(), event.mouseY.toFloat())
    if (!element.bounds.contains(local.first, local.second)) {
        return false
    }

    for (i in element.children.size - 1 downTo 0) {
        if (dispatchClickInternal(element.children[i], event, worldTransform)) {
            return true
        }
    }

    return element.handleClick(event)
}
