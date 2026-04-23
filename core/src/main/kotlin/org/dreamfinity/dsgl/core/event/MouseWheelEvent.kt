package org.dreamfinity.dsgl.core.event

/**
 * Mouse wheel event with scroll delta.
 */
data class MouseWheelEvent(
    override var mouseX: Int,
    override var mouseY: Int,
    var dWheel: Int,
) : MouseEvent(mouseX, mouseY) {
    override val type: Events
        get() = Events.WHEEL
}
