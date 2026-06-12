package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.Events
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.MouseWheelEvent

/**
 * Numeric input node.
 */
class NumberInputNode(
    value: Long = 0L,
    placeholder: String = "",
    var min: Long? = null,
    var max: Long? = null,
    key: Any? = null,
) : SingleLineInputNode(value.toString(), placeholder, key) {
    private val initialValue: Long = value
    var value: Long = initialValue
        private set

    init {
        EventBus.run {
            this@NumberInputNode.addEventListener(Events.WHEEL) { event: MouseWheelEvent ->
                if (!FocusManager.isFocused(this@NumberInputNode)) return@addEventListener
                val delta = if (event.dWheel > 0) 1L else -1L
                val previous = text
                setValue(this@NumberInputNode.value + delta)
                notifyUserValueChanged(previous)
                event.cancelled = true
            }
        }
    }

    override fun canAcceptText(next: String): Boolean {
        if (!super.canAcceptText(next)) return false
        if (next.isEmpty() || next == "-") return true
        return next.toLongOrNull() != null
    }

    override fun applyText(next: String) {
        text = next
        val parsed = next.toLongOrNull()
        if (parsed != null) {
            setValue(parsed)
        }
    }

    override fun currentParsedValue(): Any? = text.toLongOrNull()

    private fun setValue(next: Long) {
        var clamped = next
        if (min != null && clamped < min!!) clamped = min!!
        if (max != null && clamped > max!!) clamped = max!!
        this.value = clamped
        text = this.value.toString()
    }
}
