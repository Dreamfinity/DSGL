package org.dreamfinity.dsgl.core.event

/**
 * Mouse enter event for a node's bounds.
 */
class MouseEnterEvent(mouseX: Int, mouseY: Int) : MouseEvent(mouseX, mouseY) {
    override val type: Events
        get() = Events.MOUSEENTER
}
