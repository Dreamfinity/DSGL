package org.dreamfinity.dsgl.core.dom.layout

/** Size in pixels. */
data class Size(
    val width: Int,
    val height: Int,
)

/** Rectangle bounds in pixels. */
data class Rect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    fun contains(px: Int, py: Int): Boolean = px >= x && py >= y && px < x + width && py < y + height

    fun contains(px: Float, py: Float): Boolean =
        px >= x.toFloat() &&
            py >= y.toFloat() &&
            px < (x + width).toFloat() &&
            py < (y + height).toFloat()

    fun intersection(other: Rect): Rect? {
        val left = maxOf(x, other.x)
        val top = maxOf(y, other.y)
        val right = minOf(x + width, other.x + other.width)
        val bottom = minOf(y + height, other.y + other.height)
        if (right <= left || bottom <= top) {
            return null
        }
        return Rect(left, top, right - left, bottom - top)
    }
}
