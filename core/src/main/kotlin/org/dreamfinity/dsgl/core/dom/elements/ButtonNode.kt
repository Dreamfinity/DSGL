package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.support.MeasuredTextRangeWidthSource
import org.dreamfinity.dsgl.core.dom.elements.support.TextLayoutEngine
import org.dreamfinity.dsgl.core.dom.layout.Insets
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.Events
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.render.TextDecorations
import org.dreamfinity.dsgl.core.render.TextStyleOverride
import org.dreamfinity.dsgl.core.render.TextStyleSpan
import org.dreamfinity.dsgl.core.render.TextWeight
import org.dreamfinity.dsgl.core.style.TextWrap
import org.dreamfinity.dsgl.core.text.MinecraftFormattingParser

/**
 * Clickable button node with centered text.
 */
class ButtonNode(
    var text: String,
    var textColor: Int = DsglColors.TEXT,
    var backgroundColor: Int = DsglColors.BUTTON,
    padding: Int = 4,
    key: Any? = null
) : DOMNode(key) {
    override val styleType: String = "button"
    private var onClickHandler: ((MouseClickEvent) -> Unit)? = null

    init {
        this.padding = Insets.all(padding)
        EventBus.run {
            this@ButtonNode.addEventListener(Events.CLICK) { event: MouseClickEvent ->
                if (this@ButtonNode.styleDisabled) return@addEventListener
                this@ButtonNode.onClickHandler?.invoke(event)
            }
        }
    }

    internal override fun measureForLayout(ctx: UiMeasureContext, availableOuterWidth: Int?): Size {
        return measureWithConstraint(ctx, availableOuterWidth)
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return measureWithConstraint(ctx, null)
    }

    private fun measureWithConstraint(ctx: UiMeasureContext, availableOuterWidth: Int?): Size {
        val textMetrics = resolveTextMetrics(ctx)
        val lineHeight = textMetrics.lineHeightPx
        val parsed = parseTextForFormatting(text)
        val plainText = parsed.plainText
        val baseFlags = baseTextStyleFlags()
        val measuredRanges = MeasuredTextRangeWidthSource(
            plainText = plainText,
            fontId = fontId,
            fontSizePx = textMetrics.fontSizePx,
            baseFlags = baseFlags,
            spans = parsed.spans,
            ctx = ctx
        )
        val contentLimit = resolvedContentLimit(availableOuterWidth)
        val wrapWidth = if (textWrap == TextWrap.Wrap) contentLimit else null
        val layout = TextLayoutEngine.layout(
            text = plainText,
            maxWidth = wrapWidth,
            wrap = textWrap,
            fontHeight = lineHeight,
            measureText = { value -> ctx.measureText(value, fontId, textMetrics.fontSizePx) },
            measureRange = measuredRanges::measureRange,
            measureRangeCacheKey = measuredRanges.cacheKey
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
        val textMetrics = resolveTextMetrics(ctx)
        val lineHeight = textMetrics.lineHeightPx
        val lineTopLeading = resolveEffectiveLineTopLeading(ctx)
        val parsed = parseTextForFormatting(text)
        val plainText = parsed.plainText
        val baseFlags = baseTextStyleFlags()
        val measuredRanges = MeasuredTextRangeWidthSource(
            plainText = plainText,
            fontId = fontId,
            fontSizePx = textMetrics.fontSizePx,
            baseFlags = baseFlags,
            spans = parsed.spans,
            ctx = ctx
        )
        out.add(RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, backgroundColor))
        addBackgroundImageCommand(out)
        addBorderCommands(out)
        val contentWidth = contentWidth()
        val contentHeight = contentHeight()
        val wrapWidth = if (textWrap == TextWrap.Wrap) contentWidth else null
        val layout = TextLayoutEngine.layout(
            text = plainText,
            maxWidth = wrapWidth,
            wrap = textWrap,
            fontHeight = lineHeight,
            measureText = { value -> ctx.measureText(value, fontId, textMetrics.fontSizePx) },
            measureRange = measuredRanges::measureRange,
            measureRangeCacheKey = measuredRanges.cacheKey
        )
        val textBlockHeight = layout.totalHeight
        val blockY = contentY() + (contentHeight - textBlockHeight) / 2
        layout.lines.forEachIndexed { index, line ->
            val lineX = contentX() + (contentWidth - line.width) / 2
            val lineY = blockY + index * layout.lineHeight + lineTopLeading
            val spans = MinecraftFormattingParser.resolveStyleSpans(
                parsed = parsed,
                baseColor = textColor,
                baseFlags = baseFlags,
                rangeStart = line.startIndex,
                rangeEnd = line.endIndexExclusive
            ).map { span ->
                TextStyleSpan(
                    start = span.start,
                    end = span.end,
                    style = TextStyleOverride(
                        color = span.color,
                        weight = if (span.flags.bold) TextWeight.Bold else TextWeight.Normal,
                        italic = span.flags.italic,
                        decorations = TextDecorations(
                            underline = span.flags.underline,
                            strikethrough = span.flags.strikethrough
                        ),
                        obfuscated = span.flags.obfuscated
                    )
                )
            }
            out.add(drawTextCommand(ctx, line.text, lineX, lineY, textColor, spans))
        }
    }

    /** Registers a click handler for this button. */
    fun onClick(handler: (MouseClickEvent) -> Unit) {
        onClickHandler = handler
    }

    internal fun syncClickFrom(template: ButtonNode) {
        onClickHandler = template.onClickHandler
    }

    override fun handleClick(event: MouseClickEvent): Boolean {
        if (styleDisabled) return false
        onClickHandler?.invoke(event)
        return onClickHandler != null
    }

    override fun defaultBackgroundColor(): Int = backgroundColor

    override fun applyBackgroundColor(value: Int?) {
        if (value != null) {
            backgroundColor = value
        }
    }

    override fun defaultForegroundColor(): Int = textColor

    override fun applyForegroundColor(value: Int) {
        textColor = value
    }
}
