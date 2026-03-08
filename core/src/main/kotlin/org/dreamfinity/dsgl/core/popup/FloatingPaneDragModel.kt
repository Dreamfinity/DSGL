package org.dreamfinity.dsgl.core.popup

import org.dreamfinity.dsgl.core.dom.layout.Rect
import kotlin.math.abs

class FloatingPaneDragModel(
    private val moveThresholdPx: Int = 2
) {
    private var startRect: Rect = Rect(0, 0, 0, 0)
    private var dragOffsetX: Int = 0
    private var dragOffsetY: Int = 0

    var dragging: Boolean = false
        private set

    var moved: Boolean = false
        private set

    fun begin(mouseX: Int, mouseY: Int, rect: Rect) {
        startRect = rect
        dragOffsetX = mouseX - rect.x
        dragOffsetY = mouseY - rect.y
        dragging = true
        moved = false
    }

    fun update(
        mouseX: Int,
        mouseY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        clamp: (Rect, Int, Int) -> Rect
    ): Rect {
        if (!dragging) return startRect
        val target = Rect(
            x = mouseX - dragOffsetX,
            y = mouseY - dragOffsetY,
            width = startRect.width,
            height = startRect.height
        )
        val clamped = clamp(target, viewportWidth, viewportHeight)
        if (!moved && (abs(clamped.x - startRect.x) >= moveThresholdPx || abs(clamped.y - startRect.y) >= moveThresholdPx)) {
            moved = true
        }
        return clamped
    }

    fun end() {
        dragging = false
    }
}
