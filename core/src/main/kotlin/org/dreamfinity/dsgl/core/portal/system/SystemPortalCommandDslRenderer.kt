package org.dreamfinity.dsgl.core.portal.system

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.render.RenderCommand

internal object SystemPortalCommandDslRenderer {
    fun rebuildInto(parent: DOMNode, commands: List<RenderCommand>, keyPrefix: String): Boolean {
        val children = parent.children
        var changed = false

        commands.forEachIndexed { index, command ->
            val existing = children.getOrNull(index)
            if (existing is SystemPortalRawRenderCommandNode) {
                if (existing.updateRenderCommand(command)) {
                    changed = true
                }
                SystemPortalDebugCounters.onRawNodeReused()
                return@forEachIndexed
            }

            val replacement =
                SystemPortalRawRenderCommandNode(
                    renderCommand = command,
                    key = "$keyPrefix-$index",
                )
            replacement.parent = parent
            if (existing == null) {
                children += replacement
            } else {
                existing.parent = null
                children[index] = replacement
                SystemPortalDebugCounters.onRawNodeRemoved()
            }
            SystemPortalDebugCounters.onRawNodeCreated()
            changed = true
        }

        while (children.size > commands.size) {
            val removed = children.removeAt(children.lastIndex)
            removed.parent = null
            SystemPortalDebugCounters.onRawNodeRemoved()
            changed = true
        }
        return changed
    }
}
