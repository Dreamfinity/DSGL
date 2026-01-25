package org.dreamfinity.dsgl.core.event

class MouseEnterEvent(mouseX: Int, mouseY: Int) : MouseEvent(mouseX, mouseY) {
    override val type: Events
        get() = Events.MOUSEENTER
}
