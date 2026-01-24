package org.dreamfinity.dsgl.core.event

class MouseMoveEvent(
    mouseX: Int,
    mouseY: Int,
    val prevX: Int = mouseX,
    val prevY: Int = mouseY
) : MouseEvent(mouseX, mouseY) {
    override val type: Events
        get() = Events.MOUSEMOVE
}
