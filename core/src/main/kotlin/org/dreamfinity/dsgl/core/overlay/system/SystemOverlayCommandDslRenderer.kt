package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.render.RenderCommand

internal object SystemOverlayCommandDslRenderer {
    fun rebuildInto(parent: DOMNode, commands: List<RenderCommand>, keyPrefix: String): Boolean {
        val children = parent.children
        var changed = false

        commands.forEachIndexed { index, command ->
            val existing = children.getOrNull(index)
            if (existing is SystemOverlayRawRenderCommandNode) {
                if (existing.updateRenderCommand(command)) {
                    changed = true
                }
                SystemOverlayDebugCounters.onRawNodeReused()
                return@forEachIndexed
            }

            val replacement = SystemOverlayRawRenderCommandNode(
                renderCommand = command,
                key = "$keyPrefix-$index"
            )
            replacement.parent = parent
            if (existing == null) {
                children += replacement
            } else {
                existing.parent = null
                children[index] = replacement
                SystemOverlayDebugCounters.onRawNodeRemoved()
            }
            SystemOverlayDebugCounters.onRawNodeCreated()
            changed = true
        }

        while (children.size > commands.size) {
            val removed = children.removeAt(children.lastIndex)
            removed.parent = null
            SystemOverlayDebugCounters.onRawNodeRemoved()
            changed = true
        }
        return changed
    }
}
