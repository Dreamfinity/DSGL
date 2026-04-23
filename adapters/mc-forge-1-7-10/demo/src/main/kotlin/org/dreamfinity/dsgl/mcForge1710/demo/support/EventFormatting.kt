package org.dreamfinity.dsgl.mcForge1710.demo.support

import org.dreamfinity.dsgl.core.dnd.*
import org.dreamfinity.dsgl.core.event.*

fun formatEventLine(hookName: String, event: Event, note: String? = null): String {
    val targetKey =
        event.target
            ?.key
            ?.toString() ?: "none"
    val coords =
        when (event) {
            is MouseEvent -> "xy=${event.mouseX},${event.mouseY}"
            else -> "xy=-"
        }
    val payload =
        when (event) {
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
            is DragStartEvent -> "source=${event.sourceKey ?: "none"} types=${event.dataTransfer.types.joinToString(
                ",",
            )}"
            is DragEvent -> {
                val effect =
                    event.dataTransfer.dropEffect.name
                        .lowercase()
                "source=${event.sourceKey ?: "none"} effect=$effect"
            }
            is DragEndEvent -> {
                val effect =
                    event.finalDropEffect.name
                        .lowercase()
                "drop=${event.didDrop} effect=$effect target=${event.dropTargetKey ?: "none"}"
            }
            is DragEnterEvent -> "source=${event.sourceKey ?: "none"}"
            is DragOverEvent -> {
                val effect =
                    event.dataTransfer.dropEffect.name
                        .lowercase()
                val accepted = event.dropAccepted || event.cancelled
                "source=${event.sourceKey ?: "none"} effect=$effect accepted=$accepted"
            }
            is DragLeaveEvent -> "source=${event.sourceKey ?: "none"}"
            is DropEvent -> "source=${event.sourceKey ?: "none"} types=${event.dataTransfer.types.joinToString(",")}"
            else -> ""
        }
    val notePart = if (note.isNullOrBlank()) "" else " note=$note"
    val raw =
        "$hookName ${event.type.name} target=$targetKey $coords $payload shift=${KeyModifiers.shiftDown} ctrl=${KeyModifiers.controlDown} meta=${KeyModifiers.metaDown} shortcut=${KeyModifiers.shortcutDown}$notePart"
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
