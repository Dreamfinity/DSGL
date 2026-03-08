package org.dreamfinity.dsgl.core.system

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.render.RenderCommand

internal object SystemOverlayCommandDslRenderer {
    fun rebuildInto(parent: DOMNode, commands: List<RenderCommand>, keyPrefix: String) {
        parent.children.clear()
        commands.forEachIndexed { index, command ->
            SystemOverlayRawRenderCommandNode(
                renderCommand = command,
                key = "$keyPrefix-$index"
            ).applyParent(parent)
        }
    }
}
