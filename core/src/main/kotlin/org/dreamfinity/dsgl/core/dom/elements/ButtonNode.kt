package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.support.TextLayoutEngine
import org.dreamfinity.dsgl.core.dom.layout.Insets
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.Events
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.TextWrap

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

    override fun measure(ctx: UiMeasureContext): Size {
        val wrapWidth = if (textWrap == TextWrap.Wrap) width else null
        val layout = TextLayoutEngine.layout(
            text = text,
            maxWidth = wrapWidth,
            wrap = textWrap,
            fontHeight = ctx.fontHeight,
            measureText = ctx::measureText
        )
        val contentWidth = width ?: layout.maxLineWidth
        val contentHeight = height ?: layout.totalHeight
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        out.add(RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, backgroundColor))
        addBackgroundImageCommand(out)
        addBorderCommands(out)
        val contentWidth = contentWidth()
        val contentHeight = contentHeight()
        val wrapWidth = if (textWrap == TextWrap.Wrap) contentWidth else null
        val layout = TextLayoutEngine.layout(
            text = text,
            maxWidth = wrapWidth,
            wrap = textWrap,
            fontHeight = ctx.fontHeight,
            measureText = ctx::measureText
        )
        val textBlockHeight = layout.totalHeight
        val blockY = contentY() + (contentHeight - textBlockHeight) / 2
        layout.lines.forEachIndexed { index, line ->
            val lineX = contentX() + (contentWidth - line.width) / 2
            val lineY = blockY + index * layout.lineHeight
            out.add(RenderCommand.DrawText(line.text, lineX, lineY, textColor))
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
