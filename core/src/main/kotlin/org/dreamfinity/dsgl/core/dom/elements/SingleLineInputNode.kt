package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.Events
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyInput
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.KeyboardKeyDownEvent
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.render.RenderCommand

/**
 * Base class for single-line text inputs.
 */
open class SingleLineInputNode(
    text: String = "",
    var placeholder: String = "",
    key: Any? = null
) : DOMNode(key) {
    override val focusable: Boolean = true
    var text: String = text
    var allowedChars: String? = null
    var minLength: Int? = null
    var maxLength: Int? = null
    var textColor: Int = DsglColors.TEXT
    var placeholderColor: Int = 0xFF8A8A8A.toInt()
    var backgroundColor: Int = 0xFF2E2E33.toInt()
    var focusedBackgroundColor: Int = 0xFF3A3A40.toInt()
    var minContentWidth: Int = 80

    init {
        EventBus.run {
            this@SingleLineInputNode.addEventListener(Events.CLICK) { _: MouseClickEvent ->
                FocusManager.requestFocus(this@SingleLineInputNode)
            }
            this@SingleLineInputNode.addEventListener(Events.KEYDOWN) { event: KeyboardKeyDownEvent ->
                if (!FocusManager.isFocused(this@SingleLineInputNode)) return@addEventListener
                handleKey(event)
            }
        }
    }

    protected open fun displayText(): String = text

    protected open fun handleKey(event: KeyboardKeyDownEvent) {
        when (event.keyCode) {
            KeyCodes.BACKSPACE -> {
                if (text.isNotEmpty()) {
                    applyText(text.dropLast(1))
                }
            }
            else -> {
                var ch = event.keyChar
                if (!isPrintable(ch)) return
                ch = KeyInput.applyShift(ch, KeyModifiers.shiftDown)
                if (allowedChars != null && !allowedChars!!.contains(ch)) return
                val next = text + ch
                if (!canAcceptText(next)) return
                applyText(next)
            }
        }
    }

    protected fun isPrintable(ch: Char): Boolean {
        return ch >= ' ' && ch.code != 127
    }

    protected open fun canAcceptText(next: String): Boolean {
        if (maxLength != null && next.length > maxLength!!) return false
        return true
    }

    protected open fun applyText(next: String) {
        text = next
    }

    override fun measure(ctx: UiMeasureContext): Size {
        val display = if (text.isNotEmpty()) displayText() else placeholder
        val contentWidth = width ?: maxOf(ctx.measureText(display), minContentWidth)
        val contentHeight = height ?: ctx.fontHeight
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        val focused = FocusManager.isFocused(this)
        val bg = if (focused) focusedBackgroundColor else backgroundColor
        out.add(RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, bg))
        addBorderCommands(out)

        val showPlaceholder = text.isEmpty() && !focused && placeholder.isNotEmpty()
        val drawText = if (showPlaceholder) placeholder else displayText()
        if (drawText.isNotEmpty()) {
            val contentWidth = contentWidth()
            val contentHeight = contentHeight()
            val textX = contentX()
            val textY = contentY() + (contentHeight - ctx.fontHeight) / 2
            val color = if (showPlaceholder) placeholderColor else textColor
            out.add(RenderCommand.DrawText(drawText, textX, textY, color))
        }
    }
}
