package org.dreamfinity.dsgl.core.dom.layout

/**
 * Border thickness and color for each edge.
 */
data class Border(
    val top: Int,
    val right: Int,
    val bottom: Int,
    val left: Int,
    val color: Int
) {
    /** Combined left/right thickness. */
    val horizontal: Int
        get() = left + right

    /** Combined top/bottom thickness. */
    val vertical: Int
        get() = top + bottom

    companion object {
        /** No border. */
        val NONE: Border = Border(0, 0, 0, 0, 0)

        /** Same border on all sides. */
        fun all(width: Int, color: Int): Border =
            Border(width, width, width, width, color)

        /** Border with separate horizontal and vertical thicknesses. */
        fun horizontalVertical(horizontal: Int, vertical: Int, color: Int): Border =
            Border(vertical, horizontal, vertical, horizontal, color)
    }
}