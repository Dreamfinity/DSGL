package org.dreamfinity.dsgl.core.colorpicker

data class ColorPickerState(
    val color: RgbaColor,
    val previous: RgbaColor = color,
    val mode: ColorFormatMode = ColorFormatMode.HEX,
    val alphaEnabled: Boolean = true,
    val closeOnSelect: Boolean = true
) {
    fun withColor(next: RgbaColor): ColorPickerState {
        val normalized = next.normalized()
        val merged = if (alphaEnabled) normalized else normalized.copy(a = 1f)
        return copy(color = merged)
    }

    fun withCommittedCurrent(): ColorPickerState {
        return copy(previous = color)
    }

    fun withRestoredPrevious(): ColorPickerState {
        return copy(color = previous)
    }
}
