package org.dreamfinity.dsgl.mcForge1710.scissorsHelper

import kotlin.math.max
import kotlin.math.min

data class ScissorsArea(val x: Int, val y: Int, val width: Int, val height: Int)

infix fun ScissorsArea.intersectionWith(another: ScissorsArea?): ScissorsArea {
    return another?.let {
        val x1 = max(this.x, another.x)
        val x2 = min(this.x + this.width, another.x + another.width)
        val y1 = max(this.y, another.y)
        val y2 = min(this.y + this.height, another.y + another.height)
        ScissorsArea(
            x1,
            y1,
            max(0, x2 - x1),
            max(0, y2 - y1)
        )
    } ?: this
}
