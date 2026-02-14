package org.dreamfinity.dsgl.mc1710.demo.support

import org.dreamfinity.dsgl.core.event.Event
import org.dreamfinity.dsgl.core.event.FocusLoseEvent
import org.dreamfinity.dsgl.core.event.ValueChangedEvent
import org.dreamfinity.dsgl.core.event.FocusGainEvent
import org.dreamfinity.dsgl.core.event.InputEvent
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.KeyboardKeyDownEvent
import org.dreamfinity.dsgl.core.event.KeyboardKeyUpEvent
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.MouseDownEvent
import org.dreamfinity.dsgl.core.event.MouseDragEvent
import org.dreamfinity.dsgl.core.event.MouseEvent
import org.dreamfinity.dsgl.core.event.MouseUpEvent
import org.dreamfinity.dsgl.core.event.MouseWheelEvent

fun formatEventLine(
    hookName: String,
    event: Event,
    note: String? = null
): String {
    val targetKey = event.target?.key?.toString() ?: "none"
    val coords = when (event) {
        is MouseEvent -> "xy=${event.mouseX},${event.mouseY}"
        else -> "xy=-"
    }
    val payload = when (event) {
        is MouseDownEvent -> "btn=${event.mouseButton.name.lowercase()}"
        is MouseUpEvent -> "btn=${event.mouseButton.name.lowercase()}"
        is MouseClickEvent -> "btn=${event.mouseButton.name.lowercase()}"
        is MouseDragEvent -> "drag=${event.dx},${event.dy} btn=${event.mouseButton.name.lowercase()}"
        is MouseWheelEvent -> "wheel=${event.dWheel}"
        is KeyboardKeyDownEvent -> "key=${event.keyCode} char=${safeChar(event.keyChar)}"
        is KeyboardKeyUpEvent -> "key=${event.keyCode} char=${safeChar(event.keyChar)}"
        is FocusGainEvent -> "prev=${event.previousTargetKey ?: "none"}"
        is FocusLoseEvent -> "next=${event.nextTargetKey ?: "none"}"
        is InputEvent -> "value=${event.value} parsed=${event.parsedValue ?: "null"}"
        is ValueChangedEvent -> "value=${event.value} parsed=${event.parsedValue ?: "null"}"
        else -> ""
    }
    val notePart = if (note.isNullOrBlank()) "" else " note=$note"
    val raw = "$hookName ${event.type.name} target=$targetKey $coords $payload shift=${KeyModifiers.shiftDown}$notePart"
    return truncateForPanel(raw, 118)
}

fun truncateForPanel(value: String, maxLen: Int): String {
    if (value.length <= maxLen) return value
    if (maxLen <= 3) return value.take(maxLen)
    return value.take(maxLen - 3) + "..."
}

private fun safeChar(ch: Char): String {
    if (ch == '\u0000') return "\\0"
    if (ch == '\n') return "\\n"
    if (ch == '\r') return "\\r"
    if (ch == '\t') return "\\t"
    if (ch.code < 32) return "\\u%04x".format(ch.code)
    return ch.toString()
}
