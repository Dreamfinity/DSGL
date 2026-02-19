package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand

/**
 * Image node that draws a resource, file, or URL (host-dependent).
 */
class ImageNode(
    var url: String,
    var imageWidth: Int,
    var imageHeight: Int,
    key: Any? = null
) : DOMNode(key) {
    override val styleType: String = "img"

    override fun measure(ctx: UiMeasureContext): Size {
        val contentWidth = width ?: imageWidth
        val contentHeight = height ?: imageHeight
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        val w = contentWidth()
        val h = contentHeight()
        out.add(RenderCommand.DrawImage(url, contentX(), contentY(), w, h))
        addBorderCommands(out)
    }

    override fun defaultBackgroundImage(): String? = url

    override fun applyBackgroundImage(value: String?) {
        if (!value.isNullOrBlank()) {
            url = value
        }
    }
}
