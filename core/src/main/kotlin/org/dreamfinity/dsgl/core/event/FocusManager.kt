package org.dreamfinity.dsgl.core.event

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.style.Display

/**
 * Tracks and restores focused nodes between rebuilds.
 */
object FocusManager {
    private var focused: DOMNode? = null
    private var focusedKey: Any? = null
    private var focusedPath: IntArray? = null
    private var lastRoot: DOMNode? = null

    /** Current focused node, if any. */
    fun focusedNode(): DOMNode? = focused

    /** Returns true if the given node is focused. */
    fun isFocused(node: DOMNode): Boolean = focused === node

    /** Requests focus for a node, or clears it when null. */
    fun requestFocus(node: DOMNode?) {
        val target = if (node?.styleDisabled == true) null else node
        val previous = focused
        if (previous === target) return

        if (previous != null && previous.styleFocused) {
            previous.setFocusedState(false)
        }
        if (previous != null) {
            postBlur(previous, target?.key)
        }

        focused = target
        if (target == null) {
            focusedKey = null
            focusedPath = null
        } else {
            focusedKey = target.key
            focusedPath = buildPath(target)
            target.setFocusedState(true)
            postFocus(target, previous?.key)
        }
    }

    /** Clears current focus. */
    fun clearFocus() {
        requestFocus(null)
    }

    /** Walks up to find a focusable ancestor. */
    fun resolveFocusable(start: DOMNode?): DOMNode? {
        var current = start
        while (current != null) {
            if (current.focusable && !current.styleDisabled) return current
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
        retainFocus(root, updateRootReference = true)
    }

    fun retainFocus(root: DOMNode, updateRootReference: Boolean) {
        if (updateRootReference) {
            lastRoot = root
        }
        val currentKey = focusedKey
        val currentPath = focusedPath
        if (currentKey == null && currentPath == null) {
            focused = null
            return
        }

        var focusCandidate: DOMNode? = null
        if (currentKey != null) {
            focusCandidate = findByKey(root, currentKey)
        }
        if (focusCandidate == null && currentPath != null) {
            focusCandidate = findByPath(root, currentPath)
        }

        if (focusCandidate != null && focusCandidate.focusable) {
            focused = focusCandidate
            focusedKey = focusCandidate.key
            focusedPath = buildPath(focusCandidate)
            focusCandidate.setFocusedState(true)
        } else {
            clearFocus()
        }
    }

    fun requestFocusByKey(key: Any?): Boolean {
        if (key == null) return false
        val root = lastRoot ?: return false
        return requestFocusByKey(root, key)
    }

    fun requestFocusByKey(root: DOMNode, key: Any?): Boolean {
        if (key == null) return false
        val target = findByKey(root, key) ?: return false
        val focusable = findFirstFocusable(target) ?: return false
        requestFocus(focusable)
        return true
    }

    fun requestFocusFirstInSubtree(rootKey: Any?): Boolean {
        if (rootKey == null) return false
        val root = lastRoot ?: return false
        return requestFocusFirstInSubtree(root, rootKey)
    }

    fun requestFocusFirstInSubtree(root: DOMNode, rootKey: Any?): Boolean {
        if (rootKey == null) return false
        val subtree = findByKey(root, rootKey) ?: return false
        val focusable = findFirstFocusable(subtree) ?: return false
        requestFocus(focusable)
        return true
    }

    fun isFocusWithinSubtree(rootKey: Any?): Boolean {
        if (rootKey == null) return false
        var current = focused
        while (current != null) {
            if (current.key == rootKey) return true
            current = current.parent
        }
        return false
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

    private fun findFirstFocusable(node: DOMNode): DOMNode? {
        if (node.focusable && !node.styleDisabled && node.display != Display.None) {
            return node
        }
        for (child in node.children) {
            val found = findFirstFocusable(child)
            if (found != null) return found
        }
        return null
    }
}
