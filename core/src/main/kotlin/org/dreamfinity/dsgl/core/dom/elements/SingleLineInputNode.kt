package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.FocusGainEvent
import org.dreamfinity.dsgl.core.event.Events
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyInput
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.KeyboardKeyDownEvent
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.FocusLoseEvent
import org.dreamfinity.dsgl.core.event.postChange
import org.dreamfinity.dsgl.core.event.postInput
import org.dreamfinity.dsgl.core.render.RenderCommand

/**
 * Base class for single-line text inputs.
 */
open class SingleLineInputNode(
    text: String = "",
    var placeholder: String = "",
    key: Any? = null
) : DOMNode(key) {
    private val initialText: String = text

    override val focusable: Boolean = true
    var text: String = initialText
    var allowedChars: String? = null
    var minLength: Int? = null
    var maxLength: Int? = null
    var textColor: Int = DsglColors.TEXT
    var placeholderColor: Int = 0xFF8A8A8A.toInt()
    var backgroundColor: Int = 0xFF2E2E33.toInt()
    var focusedBackgroundColor: Int = 0xFF3A3A40.toInt()
    var minContentWidth: Int = 80
    private var valueAtFocusStart: String = this.text
    private var dirtySinceFocus: Boolean = false

    init {
        EventBus.run {
            this@SingleLineInputNode.addEventListener(Events.CLICK) { _: MouseClickEvent ->
                FocusManager.requestFocus(this@SingleLineInputNode)
            }
            this@SingleLineInputNode.addEventListener(Events.KEYDOWN) { event: KeyboardKeyDownEvent ->
                if (!FocusManager.isFocused(this@SingleLineInputNode)) return@addEventListener
                handleKey(event)
            }
            this@SingleLineInputNode.addEventListener(Events.FOCUS) { _: FocusGainEvent ->
                valueAtFocusStart = currentEventValue()
                dirtySinceFocus = false
            }
            this@SingleLineInputNode.addEventListener(Events.BLUR) { _: FocusLoseEvent ->
                commitCurrentValueChange()
            }
        }
    }

    protected open fun displayText(): String = this.text
    protected open fun currentEventValue(): String = this.text
    protected open fun currentParsedValue(): Any? = this.text

    protected open fun handleKey(event: KeyboardKeyDownEvent) {
        when (event.keyCode) {
            KeyCodes.ENTER -> {
                commitCurrentValueChange()
            }
            KeyCodes.BACKSPACE -> {
                if (text.isNotEmpty()) {
                    val previous = currentEventValue()
                    applyText(text.dropLast(1))
                    notifyUserValueChanged(previous)
                }
            }
            else -> {
                var ch = event.keyChar
                if (!isPrintable(ch)) return
                ch = KeyInput.applyShift(ch, KeyModifiers.shiftDown)
                if (allowedChars != null && !allowedChars!!.contains(ch)) return
                val next = text + ch
                if (!canAcceptText(next)) return
                val previous = currentEventValue()
                applyText(next)
                notifyUserValueChanged(previous)
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

    protected fun commitCurrentValueChange() {
        val current = currentEventValue()
        if (!dirtySinceFocus) return
        if (current == valueAtFocusStart) {
            dirtySinceFocus = false
            return
        }
        postChange(this, current, currentParsedValue())
        valueAtFocusStart = current
        dirtySinceFocus = false
    }

    protected fun notifyUserValueChanged(previousValue: String) {
        val current = currentEventValue()
        if (current == previousValue) return
        dirtySinceFocus = true
        postInput(this, current, currentParsedValue())
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
