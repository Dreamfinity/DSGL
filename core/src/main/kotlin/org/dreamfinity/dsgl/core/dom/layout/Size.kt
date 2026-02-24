package org.dreamfinity.dsgl.core.dom.layout

/** Size in pixels. */
data class Size(val width: Int, val height: Int)

/** Rectangle bounds in pixels. */
data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun contains(px: Int, py: Int): Boolean {
        return px >= x && py >= y && px < x + width && py < y + height
    }
}