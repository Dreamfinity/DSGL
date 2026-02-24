package org.dreamfinity.dsgl.core.event

/**
 * Mouse click event.
 */
data class MouseClickEvent(
    override var mouseX: Int,
    override var mouseY: Int,
    var mouseButton: MouseButton
) : MouseEvent(mouseX, mouseY) {
    override val type: Events
        get() = Events.CLICK
}