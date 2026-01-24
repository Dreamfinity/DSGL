package org.dreamfinity.dsgl.core.dom.layout

data class Insets(
    val top: Int,
    val right: Int,
    val bottom: Int,
    val left: Int
) {
    val horizontal: Int
        get() = left + right
    val vertical: Int
        get() = top + bottom

    companion object {
        val ZERO: Insets = Insets(0, 0, 0, 0)

        fun all(value: Int): Insets = Insets(value, value, value, value)

        fun horizontalVertical(horizontal: Int, vertical: Int): Insets =
            Insets(vertical, horizontal, vertical, horizontal)
    }
}
