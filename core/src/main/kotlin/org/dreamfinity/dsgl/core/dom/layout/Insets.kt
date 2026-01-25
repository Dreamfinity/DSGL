package org.dreamfinity.dsgl.core.dom.layout

/**
 * Spacing around content or between nodes.
 */
data class Insets(
    val top: Int,
    val right: Int,
    val bottom: Int,
    val left: Int
) {
    /** Combined left/right value. */
    val horizontal: Int
        get() = left + right
    /** Combined top/bottom value. */
    val vertical: Int
        get() = top + bottom

    companion object {
        /** Zero insets. */
        val ZERO: Insets = Insets(0, 0, 0, 0)

        /** Same inset value on all sides. */
        fun all(value: Int): Insets = Insets(value, value, value, value)

        /** Insets with separate horizontal and vertical values. */
        fun horizontalVertical(horizontal: Int, vertical: Int): Insets =
            Insets(vertical, horizontal, vertical, horizontal)
    }
}
