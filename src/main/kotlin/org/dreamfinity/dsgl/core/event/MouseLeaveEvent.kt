package org.dreamfinity.dsgl.core.event

class MouseLeaveEvent(mouseX: Int, mouseY: Int) : MouseEvent(mouseX, mouseY) {
    override val type: Events
        get() = Events.MOUSELEAVE
}
