package org.dreamfinity.dsgl.core.colorpicker

internal enum class ColorPickerDragTarget {
    None,
    Field,
    Hue,
    Alpha,
}

internal class ColorPickerTextInputSession {
    var activeKey: String? = null
    var buffer: String = ""

    fun begin(key: String, initialValue: String) {
        activeKey = key
        buffer = initialValue
    }

    fun clear() {
        activeKey = null
        buffer = ""
    }

    fun backspace() {
        if (buffer.isNotEmpty()) {
            buffer = buffer.dropLast(1)
        }
    }

    fun clearBuffer() {
        buffer = ""
    }

    fun append(ch: Char) {
        buffer += ch
    }
}

internal class ColorPickerInteractionSession {
    var dragTarget: ColorPickerDragTarget = ColorPickerDragTarget.None
    var hoverX: Int = Int.MIN_VALUE
    var hoverY: Int = Int.MIN_VALUE
    var modeDropdownOpen: Boolean = false
    val textInput: ColorPickerTextInputSession = ColorPickerTextInputSession()

    fun setHover(x: Int, y: Int) {
        hoverX = x
        hoverY = y
    }

    fun clearDragTarget() {
        dragTarget = ColorPickerDragTarget.None
    }

    fun hasActiveDragTarget(): Boolean = dragTarget != ColorPickerDragTarget.None

    fun resetAll() {
        clearDragTarget()
        modeDropdownOpen = false
        textInput.clear()
        hoverX = Int.MIN_VALUE
        hoverY = Int.MIN_VALUE
    }
}
