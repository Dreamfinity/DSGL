package org.dreamfinity.dsgl.core.event

import org.dreamfinity.dsgl.core.dom.DOMNode

/**
 * Tracks and restores focused nodes between rebuilds.
 */
object FocusManager {
    private var focused: DOMNode? = null
    private var focusedKey: Any? = null
    private var focusedPath: IntArray? = null

    /** Current focused node, if any. */
    fun focusedNode(): DOMNode? = focused

    /** Returns true if the given node is focused. */
    fun isFocused(node: DOMNode): Boolean = focused === node

    /** Requests focus for a node, or clears it when null. */
    fun requestFocus(node: DOMNode?) {
        focused = node
        if (node == null) {
            focusedKey = null
            focusedPath = null
        } else {
            focusedKey = node.key
            focusedPath = buildPath(node)
        }
    }

    /** Clears current focus. */
    fun clearFocus() {
        focused = null
        focusedKey = null
        focusedPath = null
    }

    /** Walks up to find a focusable ancestor. */
    fun resolveFocusable(start: DOMNode?): DOMNode? {
        var current = start
        while (current != null) {
            if (current.focusable) return current
            current = current.parent
        }
        return null
    }

    /** Updates focus from an event target. */
    fun updateFocusFromTarget(target: DOMNode?) {
        val focusable = resolveFocusable(target)
        if (focusable != null) {
            requestFocus(focusable)
        } else {
            clearFocus()
        }
    }

    /**
     * Retains focus across rebuilds using node key or path.
     */
    fun retainFocus(root: DOMNode) {
        val currentKey = focusedKey
        val currentPath = focusedPath
        if (currentKey == null && currentPath == null) {
            focused = null
            return
        }

        var candidate: DOMNode? = null
        if (currentKey != null) {
            candidate = findByKey(root, currentKey)
        }
        if (candidate == null && currentPath != null) {
            candidate = findByPath(root, currentPath)
        }

        if (candidate != null && candidate.focusable) {
            focused = candidate
            focusedKey = candidate.key
            focusedPath = buildPath(candidate)
        } else {
            clearFocus()
        }
    }

    private fun buildPath(node: DOMNode): IntArray {
        val indices = ArrayList<Int>(4)
        var current: DOMNode? = node
        while (current?.parent != null) {
            val parent = current.parent!!
            val index = parent.children.indexOf(current)
            indices.add(index)
            current = parent
        }
        indices.reverse()
        return indices.toIntArray()
    }

    private fun findByPath(root: DOMNode, path: IntArray): DOMNode? {
        var current: DOMNode = root
        for (index in path) {
            if (index < 0 || index >= current.children.size) return null
            current = current.children[index]
        }
        return current
    }

    private fun findByKey(root: DOMNode, key: Any): DOMNode? {
        if (root.key == key) return root
        for (child in root.children) {
            val found = findByKey(child, key)
            if (found != null) return found
        }
        return null
    }
}
