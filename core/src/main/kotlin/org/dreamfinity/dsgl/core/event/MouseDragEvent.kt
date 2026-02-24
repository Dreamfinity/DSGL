package org.dreamfinity.dsgl.core.event

/**
 * Mouse drag event with deltas.
 */
data class MouseDragEvent(
    var lastMouseX: Int,
    var lastMouseY: Int,
    var dx: Int,
    var dy: Int,
    var mouseButton: MouseButton
) : MouseEvent(lastMouseX, lastMouseY) {
    override val type: Events
        get() = Events.DRAG
}