package org.dreamfinity.dsgl.core.colorpicker

class ColorRecentHistory(
    val capacity: Int = 64,
) {
    private val colors: MutableList<RgbaColor> = ArrayList(capacity.coerceAtLeast(1))

    fun snapshot(): List<RgbaColor> = colors.toList()

    fun clear() {
        colors.clear()
    }

    fun add(color: RgbaColor) {
        val normalized = color.normalized()
        val argb = normalized.toArgbInt()
        val index = colors.indexOfFirst { it.toArgbInt() == argb }
        if (index >= 0) {
            colors.removeAt(index)
        }
        colors.add(0, normalized)
        while (colors.size > capacity) {
            colors.removeAt(colors.lastIndex)
        }
    }
}
