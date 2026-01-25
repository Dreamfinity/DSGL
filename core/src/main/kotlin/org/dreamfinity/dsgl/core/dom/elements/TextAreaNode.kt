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
 * Multiline text area node.
 */
class TextAreaNode(
    var text: String = "",
    var placeholder: String = "",
    key: Any? = null
) : DOMNode(key) {
    override val focusable: Boolean = true
    var textColor: Int = DsglColors.TEXT
    var placeholderColor: Int = 0xFF8A8A8A.toInt()
    var backgroundColor: Int = 0xFF2E2E33.toInt()
    var focusedBackgroundColor: Int = 0xFF3A3A40.toInt()
    var minContentWidth: Int = 200
    var minContentHeight: Int = 60

    init {
        EventBus.run {
            this@TextAreaNode.addEventListener(Events.CLICK) { _: MouseClickEvent ->
                FocusManager.requestFocus(this@TextAreaNode)
            }
            this@TextAreaNode.addEventListener(Events.KEYDOWN) { event: KeyboardKeyDownEvent ->
                if (!FocusManager.isFocused(this@TextAreaNode)) return@addEventListener
                handleKey(event)
            }
        }
    }

    private fun handleKey(event: KeyboardKeyDownEvent) {
        when (event.keyCode) {
            KeyCodes.BACKSPACE -> {
                if (text.isNotEmpty()) {
                    text = text.dropLast(1)
                }
            }
            KeyCodes.ENTER -> {
                text += "\n"
            }
            else -> {
                var ch = event.keyChar
                if (!isPrintable(ch)) return
                ch = KeyInput.applyShift(ch, KeyModifiers.shiftDown)
                text += ch
            }
        }
    }

    private fun isPrintable(ch: Char): Boolean {
        return ch >= ' ' && ch.code != 127
    }

    override fun measure(ctx: UiMeasureContext): Size {
        val display = if (text.isNotEmpty()) text else placeholder
        val lines = display.split("\n")
        val maxLineWidth = lines.maxOfOrNull { ctx.measureText(it) } ?: 0
        val contentWidth = width ?: maxOf(maxLineWidth, minContentWidth)
        val contentHeight = height ?: maxOf(lines.size * ctx.fontHeight, minContentHeight)
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
//        println("Building render command for $this")
        val focused = FocusManager.isFocused(this)
        val bg = if (focused) focusedBackgroundColor else backgroundColor
        out.add(RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, bg))
        addBorderCommands(out)

        val showPlaceholder = text.isEmpty() && !focused && placeholder.isNotEmpty()
        val drawText = if (showPlaceholder) placeholder else text
        val color = if (showPlaceholder) placeholderColor else textColor
        val lines = drawText.split("\n")
        var cursorY = contentY()
        lines.forEach { line ->
            out.add(RenderCommand.DrawText(line, contentX(), cursorY, color))
            cursorY += ctx.fontHeight
        }
    }
}
