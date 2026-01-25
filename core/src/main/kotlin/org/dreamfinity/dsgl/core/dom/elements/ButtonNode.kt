package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Insets
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.Events
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.render.RenderCommand

class ButtonNode(
    var text: String,
    var textColor: Int = DsglColors.TEXT,
    var backgroundColor: Int = DsglColors.BUTTON,
    padding: Int = 4,
    key: Any? = null
) : DOMNode(key) {
    private var onClickHandler: ((MouseClickEvent) -> Unit)? = null

    init {
        this.padding = Insets.all(padding)
    }

    override fun measure(ctx: UiMeasureContext): Size {
        val contentWidth = width ?: ctx.measureText(text)
        val contentHeight = height ?: ctx.fontHeight
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        out.add(RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, backgroundColor))
        addBorderCommands(out)
        val textWidth = ctx.measureText(text)
        val contentWidth = contentWidth()
        val contentHeight = contentHeight()
        val textX = contentX() + (contentWidth - textWidth) / 2
        val textY = contentY() + (contentHeight - ctx.fontHeight) / 2
        out.add(RenderCommand.DrawText(text, textX, textY, textColor))
    }

    fun onClick(handler: (MouseClickEvent) -> Unit) {
        onClickHandler = handler
        EventBus.run {
            this@ButtonNode.addEventListener(Events.CLICK) { event: MouseClickEvent ->
                handler(event)
            }
        }
    }

    override fun handleClick(event: MouseClickEvent): Boolean {
        onClickHandler?.invoke(event)
        return onClickHandler != null
    }
}
