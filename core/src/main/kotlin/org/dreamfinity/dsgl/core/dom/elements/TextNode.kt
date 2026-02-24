package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.support.TextLayoutEngine
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.TextWrap

/**
 * Static text node.
 */
class TextNode(
    private var textSource: TextSource,
    var color: Int = DsglColors.TEXT,
    key: Any? = null
) : DOMNode(key) {
    override val styleType: String = "text"

    var text: String = textSource.resolve()
        private set

    internal override fun measureForLayout(ctx: UiMeasureContext, availableOuterWidth: Int?): Size {
        return measureWithConstraint(ctx, availableOuterWidth)
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return measureWithConstraint(ctx, null)
    }

    private fun measureWithConstraint(ctx: UiMeasureContext, availableOuterWidth: Int?): Size {
        val contentLimit = resolvedContentLimit(availableOuterWidth)
        val wrapWidth = if (textWrap == TextWrap.Wrap) contentLimit else null
        val layout = TextLayoutEngine.layout(
            text = this@TextNode.text,
            maxWidth = wrapWidth,
            wrap = textWrap,
            fontHeight = ctx.fontHeight,
            measureText = ctx::measureText
        )
        val naturalContentWidth = width ?: layout.maxLineWidth
        val contentWidth = contentLimit?.let { minOf(it, naturalContentWidth) } ?: naturalContentWidth
        val contentHeight = height ?: layout.totalHeight
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    private fun resolvedContentLimit(availableOuterWidth: Int?): Int? {
        if (width != null) return width
        if (availableOuterWidth == null) return null
        val extras = margin.horizontal + padding.horizontal + border.horizontal
        return (availableOuterWidth - extras).coerceAtLeast(0)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        addBorderCommands(out)
        val wrapWidth = if (textWrap == TextWrap.Wrap) contentWidth() else null
        val layout = TextLayoutEngine.layout(
            text = this@TextNode.text,
            maxWidth = wrapWidth,
            wrap = textWrap,
            fontHeight = ctx.fontHeight,
            measureText = ctx::measureText
        )
        val baseX = contentX()
        var lineY = contentY()
        layout.lines.forEach { line ->
            out.add(RenderCommand.DrawText(line.text, baseX, lineY, color))
            lineY += layout.lineHeight
        }
    }

    override fun defaultForegroundColor(): Int = color

    override fun applyForegroundColor(value: Int) {
        color = value
    }

    internal fun syncSourceFrom(template: TextNode) {
        textSource = template.textSource
        text = textSource.resolve()
    }
}
