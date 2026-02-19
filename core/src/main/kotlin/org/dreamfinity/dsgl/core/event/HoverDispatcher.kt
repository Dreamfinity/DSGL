package org.dreamfinity.dsgl.core.event

import org.dreamfinity.dsgl.core.dom.DOMNode

private const val HOVER_DEBUG = false

/**
 * Returns the hover chain from root to the deepest hovered node.
 */
fun collectHoverChain(root: DOMNode, mouseX: Int, mouseY: Int): List<DOMNode> {
    val out = ArrayList<DOMNode>(8)
    collectHoverChain(root, mouseX, mouseY, out)
    return out
}

internal fun collectHoverChain(
    root: DOMNode,
    mouseX: Int,
    mouseY: Int,
    out: MutableList<DOMNode>
): Boolean {
    if (root.styleDisabled) return false
    if (!root.bounds.contains(mouseX, mouseY)) return false
    out.add(root)
    for (i in root.children.size - 1 downTo 0) {
        val child = root.children[i]
        if (collectHoverChain(child, mouseX, mouseY, out)) return true
    }
    return true
}

/**
 * Emits enter/leave/over events based on cursor movement.
 */
fun updateHover(
    root: DOMNode,
    prevHoverChain: MutableList<DOMNode>,
    mouseX: Int,
    mouseY: Int,
    mouseDX: Int,
    mouseDY: Int
) {
    val currHoverChain = ArrayList<DOMNode>(prevHoverChain.size + 4)
    collectHoverChain(root, mouseX, mouseY, currHoverChain)

    val minSize = minOf(prevHoverChain.size, currHoverChain.size)
    var commonPrefixLen = 0
    while (
        commonPrefixLen < minSize &&
        isSameHoverNode(prevHoverChain[commonPrefixLen], currHoverChain[commonPrefixLen])
    ) {
        commonPrefixLen++
    }

    for (i in prevHoverChain.size - 1 downTo commonPrefixLen) {
        prevHoverChain[i].setHoveredState(false)
        postMouseLeaveEvent(prevHoverChain[i], mouseX, mouseY)
    }
    for (i in commonPrefixLen until currHoverChain.size) {
        currHoverChain[i].setHoveredState(true)
        postMouseEnterEvent(currHoverChain[i], mouseX, mouseY)
    }
    for (i in 0 until commonPrefixLen) {
        currHoverChain[i].setHoveredState(true)
    }

    if (mouseDX != 0 || mouseDY != 0) {
        for (i in 0 until currHoverChain.size) {
            postMouseOverEvent(currHoverChain[i], mouseX, mouseY)
        }
    }

    prevHoverChain.clear()
    prevHoverChain.addAll(currHoverChain)
}

/**
 * Treats logically stable nodes as equal across rebuilds.
 *
 * Rebuilds create fresh DOM instances, so identity (`===`) alone makes hover diffing
 * emit synthetic leave/enter events while the cursor is stationary.
 */
private fun isSameHoverNode(prev: DOMNode, curr: DOMNode): Boolean {
    if (prev === curr) return true

    val prevKey = prev.key
    val currKey = curr.key
    if (prevKey != null || currKey != null) {
        return prevKey != null &&
            currKey != null &&
            prevKey == currKey &&
            prev.javaClass == curr.javaClass
    }

    // Root is recreated on every rebuild but is conceptually the same hover scope.
    if (prev.parent == null && curr.parent == null) {
        return prev.javaClass == curr.javaClass
    }

    return false
}

private fun postMouseEnterEvent(target: DOMNode, mouseX: Int, mouseY: Int) {
    val event = MouseEnterEvent(mouseX, mouseY)
    event.target = target
    EventBus.post(event)
    target.onmouseenter?.invoke(event)
    if (HOVER_DEBUG) {
        println("ENTER " + label(target))
    }
}

private fun postMouseLeaveEvent(target: DOMNode, mouseX: Int, mouseY: Int) {
    val event = MouseLeaveEvent(mouseX, mouseY)
    event.target = target
    EventBus.post(event)
    target.onmouseleave?.invoke(event)
    if (HOVER_DEBUG) {
        println("LEAVE " + label(target))
    }
}

private fun postMouseOverEvent(target: DOMNode, mouseX: Int, mouseY: Int) {
    val event = MouseOverEvent(mouseX, mouseY)
    event.target = target
    EventBus.post(event)
    target.onmouseover?.invoke(event)
    if (HOVER_DEBUG) {
        println("OVER " + label(target))
    }
}

private fun label(element: DOMNode): String {
    val keyPart = element.key?.let { " key=$it" } ?: ""
    return element.javaClass.simpleName + keyPart
}
