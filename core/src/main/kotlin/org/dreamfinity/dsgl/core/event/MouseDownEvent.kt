package org.dreamfinity.dsgl.core.event

/**
 * Mouse button down event.
 */
data class MouseDownEvent(
    override var mouseX: Int,
    override var mouseY: Int,
    var mouseButton: MouseButton,
) : MouseEvent(mouseX, mouseY) {
    override val type: Events
        get() = Events.MOUSEDOWN
}
