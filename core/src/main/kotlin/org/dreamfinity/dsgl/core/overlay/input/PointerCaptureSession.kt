package org.dreamfinity.dsgl.core.overlay.input

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.RangeInputNode
import org.dreamfinity.dsgl.core.dom.elements.SingleLineInputNode
import org.dreamfinity.dsgl.core.dom.elements.TextAreaNode
import org.dreamfinity.dsgl.core.event.FocusManager

/**
 * Shared DOM-node pointer capture bookkeeping for domain root and portal dispatchers.
 */
class PointerCaptureSession {
    var target: DOMNode? = null
        private set

    private var targetKey: Any? = null
    private var targetClass: Class<out DOMNode>? = null
    private var focusKey: Any? = null

    val hasCapture: Boolean
        get() = target != null

    fun capture(target: DOMNode) {
        this.target = target
        targetKey = target.key
        targetClass = target.javaClass
        focusKey = FocusManager.focusedNode()?.key
    }

    fun release() {
        target?.cancelPointerCapture()
        reset()
    }

    fun restore(root: DOMNode, pointerPressed: Boolean) {
        if (target == null) return
        val cls = targetClass
        if (cls == null) {
            release()
            return
        }

        val key = targetKey
        if (key == null) {
            val captured = target
            if (shouldKeepUnkeyedCapture(captured, cls, root, pointerPressed)) {
                return
            }
            release()
            return
        }

        val restored = findByKeyAndClass(root, key, cls)
        if (restored != null) {
            target = restored
        } else {
            release()
        }
    }

    fun hasFocusChanged(): Boolean {
        if (focusKey == null) return false
        val captured = target
        val currentFocus = FocusManager.focusedNode()
        if (captured != null && isSameOrAncestor(captured, currentFocus)) return false
        return currentFocus?.key != focusKey
    }

    private fun reset() {
        target = null
        targetKey = null
        targetClass = null
        focusKey = null
    }

    private fun shouldKeepUnkeyedCapture(
        captured: DOMNode?,
        cls: Class<out DOMNode>,
        root: DOMNode,
        pointerPressed: Boolean,
    ): Boolean {
        if (captured == null) return false
        if (captured.javaClass != cls) return false
        return pointerPressed || isSameOrAncestor(root, captured)
    }

    companion object {
        fun resolveCaptureTarget(start: DOMNode?, mouseX: Int, mouseY: Int): DOMNode? {
            var current = start
            while (current != null) {
                when (current) {
                    is RangeInputNode -> return current
                    is SingleLineInputNode -> if (current.shouldCaptureTextSelectionDrag(mouseX, mouseY)) return current
                    is TextAreaNode -> if (current.shouldCaptureAnyDrag(mouseX, mouseY)) return current
                }
                if (current.shouldCapturePointerDrag(mouseX, mouseY)) {
                    return current
                }
                current = current.parent
            }
            return null
        }

        fun isSameOrAncestor(candidate: DOMNode, node: DOMNode?): Boolean {
            var current = node
            while (current != null) {
                if (current === candidate) return true
                current = current.parent
            }
            return false
        }

        private fun findByKeyAndClass(node: DOMNode, key: Any, cls: Class<out DOMNode>): DOMNode? {
            if (node.key == key && node.javaClass == cls) return node
            node.children.forEach { child ->
                val found = findByKeyAndClass(child, key, cls)
                if (found != null) return found
            }
            return null
        }
    }
}
