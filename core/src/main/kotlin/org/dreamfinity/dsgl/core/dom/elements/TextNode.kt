package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.support.TextLayoutEngine
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.TextWrap
import org.dreamfinity.dsgl.core.text.MinecraftFormattingParser
import org.dreamfinity.dsgl.core.text.TextStyleMetrics

/**
 * Static text node.
 */
class TextNode(
    private var textSource: TextSource,
    var color: Int = DsglColors.TEXT,
    key: Any? = null
) : DOMNode(key) {
    companion object {
        const val NORMAL_LINE_HEIGHT_MULTIPLIER: Float = DOMNode.NORMAL_LINE_HEIGHT_MULTIPLIER
    }

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
        val lineHeight = resolveEffectiveLineHeight(ctx)
        val parsed = parseTextForFormatting(this@TextNode.text)
        val plainText = parsed.plainText
        val baseFlags = baseTextStyleFlags()
        val measureRange: (Int, Int) -> Int = { start, end ->
            val safeStart = start.coerceIn(0, plainText.length)
            val safeEnd = end.coerceIn(safeStart, plainText.length)
            val baseWidth = ctx.measureText(plainText.substring(safeStart, safeEnd), fontId, fontSize)
            val extra = TextStyleMetrics.boldExtraPxForRange(
                plainText = plainText,
                spans = parsed.spans,
                baseFlags = baseFlags,
                rangeStart = safeStart,
                rangeEnd = safeEnd
            )
            baseWidth + extra
        }
        val contentLimit = resolvedContentLimit(availableOuterWidth)
        val wrapWidth = if (textWrap == TextWrap.Wrap) contentLimit else null
        val layout = TextLayoutEngine.layout(
            text = plainText,
            maxWidth = wrapWidth,
            wrap = textWrap,
            fontHeight = lineHeight,
            measureText = { value -> ctx.measureText(value, fontId, fontSize) },
            measureRange = measureRange
        )
        val naturalContentWidth = width ?: layout.maxLineWidth
        val contentWidth = contentLimit?.let { minOf(it, naturalContentWidth) } ?: naturalContentWidth
        val contentHeight = height ?: layout.totalHeight
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
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
        val lineHeight = resolveEffectiveLineHeight(ctx)
        val lineTopLeading = resolveEffectiveLineTopLeading(ctx)
        val parsed = parseTextForFormatting(this@TextNode.text)
        val plainText = parsed.plainText
        val baseFlags = baseTextStyleFlags()
        val measureRange: (Int, Int) -> Int = { start, end ->
            val safeStart = start.coerceIn(0, plainText.length)
            val safeEnd = end.coerceIn(safeStart, plainText.length)
            val baseWidth = ctx.measureText(plainText.substring(safeStart, safeEnd), fontId, fontSize)
            val extra = TextStyleMetrics.boldExtraPxForRange(
                plainText = plainText,
                spans = parsed.spans,
                baseFlags = baseFlags,
                rangeStart = safeStart,
                rangeEnd = safeEnd
            )
            baseWidth + extra
        }
        addBorderCommands(out)
        val wrapWidth = if (textWrap == TextWrap.Wrap) contentWidth() else null
        val layout = TextLayoutEngine.layout(
            text = plainText,
            maxWidth = wrapWidth,
            wrap = textWrap,
            fontHeight = lineHeight,
            measureText = { value -> ctx.measureText(value, fontId, fontSize) },
            measureRange = measureRange
        )
        val baseX = contentX()
        var lineY = contentY()
        layout.lines.forEach { line ->
            val spans = MinecraftFormattingParser.resolveStyleSpans(
                parsed = parsed,
                baseColor = color,
                baseFlags = baseFlags,
                rangeStart = line.startIndex,
                rangeEnd = line.endIndexExclusive
            ).map { span ->
                RenderCommand.TextStyleSpan(
                    start = span.start,
                    end = span.end,
                    color = span.color,
                    bold = span.flags.bold,
                    italic = span.flags.italic,
                    underline = span.flags.underline,
                    strikethrough = span.flags.strikethrough,
                    obfuscated = span.flags.obfuscated
                )
            }
            out.add(drawTextCommand(line.text, baseX, lineY + lineTopLeading, color, spans))
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
