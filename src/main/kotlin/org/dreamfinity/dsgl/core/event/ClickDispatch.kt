package org.dreamfinity.dsgl.core.event

import org.dreamfinity.dsgl.core.dom.DOMNode

fun dispatchClick(root: DOMNode, event: MouseClickEvent): Boolean {
    return dispatchClickInternal(root, event)
}

internal fun dispatchClickInternal(element: DOMNode, event: MouseClickEvent): Boolean {
    if (!element.bounds.contains(event.mouseX, event.mouseY)) {
        return false
    }

    for (i in element.children.size - 1 downTo 0) {
        if (dispatchClickInternal(element.children[i], event)) {
            return true
        }
    }

    return element.handleClick(event)
}
