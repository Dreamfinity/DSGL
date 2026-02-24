package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.ItemStackRef
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand

/**
 * Node that draws a platform-specific item stack.
 */
class ItemStackNode(
    var stack: ItemStackRef,
    var size: Int = 18,
    var rotYDeg: Double = 160.0,
    var rotXDeg: Double = -11.0,
    key: Any? = null
) : DOMNode(key) {
    override val styleType: String = "itemstack"

    override fun measure(ctx: UiMeasureContext): Size {
        val contentWidth = width ?: size
        val nameHeight = ctx.fontHeight + 2
        val contentHeight = height ?: (size + nameHeight)
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        addBorderCommands(out)
        out.add(
            RenderCommand.DrawItemStack(
                stack,
                contentX(),
                contentY(),
                contentWidth(),
                size,
                rotYDeg,
                rotXDeg
            )
        )
    }
}