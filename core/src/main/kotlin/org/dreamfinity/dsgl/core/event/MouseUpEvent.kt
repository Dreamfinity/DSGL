package org.dreamfinity.dsgl.core.event

/**
 * Mouse button up event.
 */
data class MouseUpEvent(
    override var mouseX: Int,
    override var mouseY: Int,
    var mouseButton: MouseButton
) : MouseEvent(mouseX, mouseY) {
    override val type: Events
        get() = Events.MOUSEUP
}