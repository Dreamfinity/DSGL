package org.dreamfinity.dsgl.core.event

class MouseOverEvent(mouseX: Int, mouseY: Int) : MouseEvent(mouseX, mouseY) {
    override val type: Events
        get() = Events.MOUSEOVER
}
