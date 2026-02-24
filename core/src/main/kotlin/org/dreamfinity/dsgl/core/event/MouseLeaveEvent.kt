package org.dreamfinity.dsgl.core.event

/**
 * Mouse leave event for a node's bounds.
 */
class MouseLeaveEvent(mouseX: Int, mouseY: Int) : MouseEvent(mouseX, mouseY) {
    override val type: Events
        get() = Events.MOUSELEAVE
}