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
    var imageWidth: Int = 0,
    var imageHeight: Int = 0,
    key: Any? = null,
) : DOMNode(key) {
    override val styleType: String = "img"

    internal override fun measureForLayout(ctx: UiMeasureContext, availableOuterWidth: Int?): Size =
        measureWithConstraint(availableOuterWidth)

    override fun measure(ctx: UiMeasureContext): Size = measureWithConstraint(null)

    private fun measureWithConstraint(availableOuterWidth: Int?): Size {
        val contentLimit = resolvedContentLimit(availableOuterWidth)
        val contentWidth = width ?: imageWidth
        val resolvedWidth = contentLimit?.let { minOf(it, contentWidth) } ?: contentWidth
        val contentHeight = height ?: imageHeight
        val totalWidth = resolvedWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    private fun resolvedContentLimit(availableOuterWidth: Int?): Int? {
        val explicit = width
        val extras = margin.horizontal + padding.horizontal + border.horizontal
        val constrainedByParent = availableOuterWidth?.let { (it - extras).coerceAtLeast(0) }
        return when {
            explicit != null && constrainedByParent != null -> minOf(explicit, constrainedByParent)
            explicit != null -> explicit
            else -> constrainedByParent
        }
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        val w = contentWidth()
        val h = contentHeight()
        out.add(RenderCommand.DrawImage(url, contentX(), contentY(), w, h))
        addBorderCommands(out)
    }

    override fun defaultBackgroundImage(): String = url

    override fun applyBackgroundImage(value: String?) {
        if (!value.isNullOrBlank()) {
            url = value
        }
    }
}
