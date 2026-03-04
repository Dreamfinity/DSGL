package org.dreamfinity.dsgl.core.components.modal.internal

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand

/**
 * Root node for modal host composition:
 * child[0] is regular content and children[1..] are full-viewport modal layers.
 */
internal class ModalHostNode(
    key: Any?
) : DOMNode(key) {
    override val styleType: String = "modal-host"

    override fun measure(ctx: UiMeasureContext): Size {
        val content = children.firstOrNull()
        val contentSize = content?.measure(ctx) ?: Size(0, 0)
        val totalWidth = (width ?: contentSize.width) + padding.horizontal + border.horizontal
        val totalHeight = (height ?: contentSize.height) + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        children.firstOrNull()?.render(ctx, x, y, width, height)
        if (children.size <= 1) return
        for (i in 1 until children.size) {
            children[i].render(ctx, x, y, width, height)
        }
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) = Unit
}
