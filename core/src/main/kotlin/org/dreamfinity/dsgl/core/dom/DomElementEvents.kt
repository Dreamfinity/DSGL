package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.Events
import org.dreamfinity.dsgl.core.event.KeyboardKeyDownEvent
import org.dreamfinity.dsgl.core.event.KeyboardKeyUpEvent
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.MouseDownEvent
import org.dreamfinity.dsgl.core.event.MouseDragEvent
import org.dreamfinity.dsgl.core.event.MouseEnterEvent
import org.dreamfinity.dsgl.core.event.MouseLeaveEvent
import org.dreamfinity.dsgl.core.event.MouseMoveEvent
import org.dreamfinity.dsgl.core.event.MouseOverEvent
import org.dreamfinity.dsgl.core.event.MouseUpEvent
import org.dreamfinity.dsgl.core.event.MouseWheelEvent

fun DOMNode.onMouseEnter(handler: (MouseEnterEvent) -> Unit) {
    EventBus.run { this@onMouseEnter.addEventListener(Events.MOUSEENTER, handler) }
}

fun DOMNode.onMouseLeave(handler: (MouseLeaveEvent) -> Unit) {
    EventBus.run { this@onMouseLeave.addEventListener(Events.MOUSELEAVE, handler) }
}

fun DOMNode.onMouseOver(handler: (MouseOverEvent) -> Unit) {
    EventBus.run { this@onMouseOver.addEventListener(Events.MOUSEOVER, handler) }
}

fun DOMNode.onMouseMove(handler: (MouseMoveEvent) -> Unit) {
    EventBus.run { this@onMouseMove.addEventListener(Events.MOUSEMOVE, handler) }
}

fun DOMNode.onMouseDown(handler: (MouseDownEvent) -> Unit) {
    EventBus.run { this@onMouseDown.addEventListener(Events.MOUSEDOWN, handler) }
}

fun DOMNode.onMouseUp(handler: (MouseUpEvent) -> Unit) {
    EventBus.run { this@onMouseUp.addEventListener(Events.MOUSEUP, handler) }
}

fun DOMNode.onMouseClick(handler: (MouseClickEvent) -> Unit) {
    EventBus.run { this@onMouseClick.addEventListener(Events.CLICK, handler) }
}

fun DOMNode.onMouseDrag(handler: (MouseDragEvent) -> Unit) {
    EventBus.run { this@onMouseDrag.addEventListener(Events.DRAG, handler) }
}

fun DOMNode.onMouseWheel(handler: (MouseWheelEvent) -> Unit) {
    EventBus.run { this@onMouseWheel.addEventListener(Events.WHEEL, handler) }
}

fun DOMNode.onKeyDown(handler: (KeyboardKeyDownEvent) -> Unit) {
    EventBus.run { this@onKeyDown.addEventListener(Events.KEYDOWN, handler) }
}

fun DOMNode.onKeyUp(handler: (KeyboardKeyUpEvent) -> Unit) {
    EventBus.run { this@onKeyUp.addEventListener(Events.KEYUP, handler) }
}

fun DOMNode.onKeyPressed(handler: (KeyboardKeyDownEvent) -> Unit) = onKeyDown(handler)

fun DOMNode.onKeyReleased(handler: (KeyboardKeyUpEvent) -> Unit) = onKeyUp(handler)
