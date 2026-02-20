package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.Events
import org.dreamfinity.dsgl.core.event.FocusLoseEvent
import org.dreamfinity.dsgl.core.event.ValueChangedEvent
import org.dreamfinity.dsgl.core.event.FocusGainEvent
import org.dreamfinity.dsgl.core.event.DragEndEvent
import org.dreamfinity.dsgl.core.event.DragEnterEvent
import org.dreamfinity.dsgl.core.event.DragEvent
import org.dreamfinity.dsgl.core.event.DragLeaveEvent
import org.dreamfinity.dsgl.core.event.DragOverEvent
import org.dreamfinity.dsgl.core.event.DragStartEvent
import org.dreamfinity.dsgl.core.event.DropEvent
import org.dreamfinity.dsgl.core.event.InputEvent
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

/** Registers a mouse enter handler for this node. */
fun DOMNode.onMouseEnter(handler: (MouseEnterEvent) -> Unit) {
    EventBus.run { this@onMouseEnter.addEventListener(Events.MOUSEENTER, handler) }
}

/** Registers a mouse leave handler for this node. */
fun DOMNode.onMouseLeave(handler: (MouseLeaveEvent) -> Unit) {
    EventBus.run { this@onMouseLeave.addEventListener(Events.MOUSELEAVE, handler) }
}

/** Registers a mouse over handler for this node. */
fun DOMNode.onMouseOver(handler: (MouseOverEvent) -> Unit) {
    EventBus.run { this@onMouseOver.addEventListener(Events.MOUSEOVER, handler) }
}

/** Registers a mouse move handler for this node. */
fun DOMNode.onMouseMove(handler: (MouseMoveEvent) -> Unit) {
    EventBus.run { this@onMouseMove.addEventListener(Events.MOUSEMOVE, handler) }
}

/** Registers a mouse down handler for this node. */
fun DOMNode.onMouseDown(handler: (MouseDownEvent) -> Unit) {
    EventBus.run { this@onMouseDown.addEventListener(Events.MOUSEDOWN, handler) }
}

/** Registers a mouse up handler for this node. */
fun DOMNode.onMouseUp(handler: (MouseUpEvent) -> Unit) {
    EventBus.run { this@onMouseUp.addEventListener(Events.MOUSEUP, handler) }
}

/** Registers a mouse click handler for this node. */
fun DOMNode.onMouseClick(handler: (MouseClickEvent) -> Unit) {
    EventBus.run { this@onMouseClick.addEventListener(Events.CLICK, handler) }
}

/** Registers a mouse drag handler for this node. */
fun DOMNode.onMouseDrag(handler: (MouseDragEvent) -> Unit) {
    EventBus.run { this@onMouseDrag.addEventListener(Events.DRAG, handler) }
}

/** Registers a mouse wheel handler for this node. */
fun DOMNode.onMouseWheel(handler: (MouseWheelEvent) -> Unit) {
    EventBus.run { this@onMouseWheel.addEventListener(Events.WHEEL, handler) }
}

/** Registers a key down handler for this node. */
fun DOMNode.onKeyDown(handler: (KeyboardKeyDownEvent) -> Unit) {
    EventBus.run { this@onKeyDown.addEventListener(Events.KEYDOWN, handler) }
}

/** Registers a key up handler for this node. */
fun DOMNode.onKeyUp(handler: (KeyboardKeyUpEvent) -> Unit) {
    EventBus.run { this@onKeyUp.addEventListener(Events.KEYUP, handler) }
}

/** Alias for [onKeyDown]. */
fun DOMNode.onKeyPressed(handler: (KeyboardKeyDownEvent) -> Unit) = onKeyDown(handler)

/** Alias for [onKeyUp]. */
fun DOMNode.onKeyReleased(handler: (KeyboardKeyUpEvent) -> Unit) = onKeyUp(handler)

/** Registers a focus handler for this node. */
fun DOMNode.onFocus(handler: (FocusGainEvent) -> Unit) {
    EventBus.run { this@onFocus.addEventListener(Events.FOCUS, handler) }
}

/** Registers a blur handler for this node. */
fun DOMNode.onBlur(handler: (FocusLoseEvent) -> Unit) {
    EventBus.run { this@onBlur.addEventListener(Events.BLUR, handler) }
}

/** Registers an input handler for this node. */
fun DOMNode.onInput(handler: (InputEvent) -> Unit) {
    EventBus.run { this@onInput.addEventListener(Events.INPUT, handler) }
}

/** Registers a change handler for this node. */
fun DOMNode.onChange(handler: (ValueChangedEvent) -> Unit) {
    EventBus.run { this@onChange.addEventListener(Events.CHANGE, handler) }
}

/** Registers a drag start handler for this node. */
fun DOMNode.onDragStart(handler: (DragStartEvent) -> Unit) {
    EventBus.run { this@onDragStart.addEventListener(Events.DRAGSTART, handler) }
}

/** Registers a drag handler for this node. */
fun DOMNode.onDrag(handler: (DragEvent) -> Unit) {
    EventBus.run { this@onDrag.addEventListener(Events.DRAGGING, handler) }
}

/** Registers a drag end handler for this node. */
fun DOMNode.onDragEnd(handler: (DragEndEvent) -> Unit) {
    EventBus.run { this@onDragEnd.addEventListener(Events.DRAGEND, handler) }
}

/** Registers a drag enter handler for this node. */
fun DOMNode.onDragEnter(handler: (DragEnterEvent) -> Unit) {
    EventBus.run { this@onDragEnter.addEventListener(Events.DRAGENTER, handler) }
}

/** Registers a drag over handler for this node. */
fun DOMNode.onDragOver(handler: (DragOverEvent) -> Unit) {
    EventBus.run { this@onDragOver.addEventListener(Events.DRAGOVER, handler) }
}

/** Registers a drag leave handler for this node. */
fun DOMNode.onDragLeave(handler: (DragLeaveEvent) -> Unit) {
    EventBus.run { this@onDragLeave.addEventListener(Events.DRAGLEAVE, handler) }
}

/** Registers a drop handler for this node. */
fun DOMNode.onDrop(handler: (DropEvent) -> Unit) {
    EventBus.run { this@onDrop.addEventListener(Events.DROP, handler) }
}
