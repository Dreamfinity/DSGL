package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand

/**
 * Static text node.
 */
class TextNode(
    var text: String,
    var color: Int = DsglColors.TEXT,
    key: Any? = null
) : DOMNode(key) {
    override val styleType: String = "text"

    override fun measure(ctx: UiMeasureContext): Size {
        val contentWidth = width ?: ctx.measureText(text)
        val contentHeight = height ?: ctx.fontHeight
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        addBorderCommands(out)
        out.add(RenderCommand.DrawText(text, contentX(), contentY(), color))
    }

    override fun defaultForegroundColor(): Int = color

    override fun applyForegroundColor(value: Int) {
        color = value
    }
}
