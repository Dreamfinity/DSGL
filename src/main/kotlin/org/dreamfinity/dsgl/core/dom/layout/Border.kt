package org.dreamfinity.dsgl.core.dom.layout

data class Border(
    val top: Int,
    val right: Int,
    val bottom: Int,
    val left: Int,
    val color: Int
) {
    val horizontal: Int
        get() = left + right
    val vertical: Int
        get() = top + bottom

    companion object {
        val NONE: Border = Border(0, 0, 0, 0, 0)

        fun all(width: Int, color: Int): Border =
            Border(width, width, width, width, color)

        fun horizontalVertical(horizontal: Int, vertical: Int, color: Int): Border =
            Border(vertical, horizontal, vertical, horizontal, color)
    }
}
