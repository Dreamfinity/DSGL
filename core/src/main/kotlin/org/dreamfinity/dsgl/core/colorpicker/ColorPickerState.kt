package org.dreamfinity.dsgl.core.colorpicker

data class ColorPickerState(
    val color: RgbaColor,
    val previous: RgbaColor = color,
    val mode: ColorFormatMode = ColorFormatMode.HEX,
    val rgbOrder: RgbChannelOrder = RgbChannelOrder.RGBA,
    val alphaEnabled: Boolean = true,
    val closeOnSelect: Boolean = true,
) {
    constructor(
        color: RgbaColor,
        previous: RgbaColor = color,
        mode: ColorFormatMode = ColorFormatMode.HEX,
        alphaEnabled: Boolean = true,
        closeOnSelect: Boolean = true,
    ) : this(
        color = color,
        previous = previous,
        mode = mode,
        rgbOrder = RgbChannelOrder.RGBA,
        alphaEnabled = alphaEnabled,
        closeOnSelect = closeOnSelect,
    )

    fun withColor(next: RgbaColor): ColorPickerState {
        val normalized = next.normalized()
        val merged = if (alphaEnabled) normalized else normalized.copy(a = 1f)
        return copy(color = merged)
    }

    fun withCommittedCurrent(): ColorPickerState = copy(previous = color)

    fun withRestoredPrevious(): ColorPickerState = copy(color = previous)
}
