package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand

/**
 * Text node whose value is computed on each rebuild.
 */
class DynamicTextNode(
    private var textProvider: () -> String,
    var color: Int = DsglColors.TEXT,
    key: Any? = null
) : DOMNode(key) {
    override val styleType: String = "text"

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

    override fun defaultForegroundColor(): Int = color

    override fun applyForegroundColor(value: Int) {
        color = value
    }

    internal fun syncProviderFrom(template: DynamicTextNode) {
        textProvider = template.textProvider
    }
}
