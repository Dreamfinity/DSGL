package org.dreamfinity.dsgl.core.ref

import org.dreamfinity.dsgl.core.dom.DOMNode
import java.util.IdentityHashMap

internal class RefManager {
    private data class AttachedRef(
        val target: RefTarget<ElementHandle>,
        val handle: NodeElementHandle
    )

    private val attachedByNode: MutableMap<DOMNode, AttachedRef> = IdentityHashMap()

    fun commit(root: DOMNode) {
        val visited = IdentityHashMap<DOMNode, Boolean>()
        traverse(root) { node ->
            visited[node] = true
            val newTarget = node.refTarget
            val previous = attachedByNode[node]
            if (newTarget == null) {
                if (previous != null) {
                    previous.target.set(null)
                    previous.handle.detach()
                    attachedByNode.remove(node)
                }
                return@traverse
            }

            if (previous == null) {
                val handle = NodeElementHandle(node)
                newTarget.set(handle)
                attachedByNode[node] = AttachedRef(newTarget, handle)
                return@traverse
            }

            if (previous.target !== newTarget) {
                previous.target.set(null)
                previous.handle.detach()
                val handle = NodeElementHandle(node)
                newTarget.set(handle)
                attachedByNode[node] = AttachedRef(newTarget, handle)
            }
        }

        val staleNodes = attachedByNode.keys.filter { node ->
            !visited.containsKey(node)
        }
        staleNodes.forEach { node ->
            val attached = attachedByNode.remove(node) ?: return@forEach
            attached.target.set(null)
            attached.handle.detach()
        }
    }

    fun clear() {
        attachedByNode.values.forEach { attached ->
            attached.target.set(null)
            attached.handle.detach()
        }
        attachedByNode.clear()
    }

    private fun traverse(node: DOMNode, block: (DOMNode) -> Unit) {
        block(node)
        node.children.forEach { child ->
            traverse(child, block)
        }
    }
}

