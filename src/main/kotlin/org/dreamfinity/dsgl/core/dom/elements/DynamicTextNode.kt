package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand

class DynamicTextNode(
    private val textProvider: () -> String,
    var color: Int = DsglColors.TEXT,
    key: Any? = null
) : DOMNode(key) {
    private fun currentText(): String = textProvider()

    override fun measure(ctx: UiMeasureContext): Size {
        val text = currentText()
        val contentWidth = width ?: ctx.measureText(text)
        val contentHeight = height ?: ctx.fontHeight
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        val text = currentText()
        addBorderCommands(out)
        out.add(RenderCommand.DrawText(text, contentX(), contentY(), color))
    }
}
