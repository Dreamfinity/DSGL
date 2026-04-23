package org.dreamfinity.dsgl.core.dsl

import org.dreamfinity.dsgl.core.dnd.DragEndEvent
import org.dreamfinity.dsgl.core.dnd.DragEnterEvent
import org.dreamfinity.dsgl.core.dnd.DragEvent
import org.dreamfinity.dsgl.core.dnd.DragLeaveEvent
import org.dreamfinity.dsgl.core.dnd.DragOverEvent
import org.dreamfinity.dsgl.core.dnd.DragPreviewMode
import org.dreamfinity.dsgl.core.dnd.DragPreviewScope
import org.dreamfinity.dsgl.core.dnd.DragStartEvent
import org.dreamfinity.dsgl.core.dnd.DropEvent
import org.dreamfinity.dsgl.core.dnd.PlaceholderScope
import org.dreamfinity.dsgl.core.event.FocusGainEvent
import org.dreamfinity.dsgl.core.event.FocusLoseEvent
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
import org.dreamfinity.dsgl.core.event.ValueChangedEvent
import org.dreamfinity.dsgl.core.hooks.ref.ElementHandle
import org.dreamfinity.dsgl.core.hooks.ref.RefTarget

/**
 * Common visual and interaction props shared by most components.
 *
 * Event callbacks are wired into [org.dreamfinity.dsgl.core.event.EventBus].
 */
open class ComponentProps(
    var style: StyleScope.() -> Unit = {},
    var key: Any? = null,
    var id: String? = null,
    var className: String = "",
    var classes: Set<String> = emptySet(),
    var disabled: Boolean = false,
    var draggable: Boolean = false,
    var droppable: Boolean = false,
    var dragPreviewMode: DragPreviewMode = DragPreviewMode.GHOST,
    var hideSourceWhileDragging: Boolean = false,
    var dragPreview: (DragPreviewScope.() -> Unit)? = null,
    var dragPlaceholder: (PlaceholderScope.() -> Unit)? = null,
    var ref: RefTarget<ElementHandle>? = null,
    var onMouseEnter: ((MouseEnterEvent) -> Unit)? = null,
    var onMouseLeave: ((MouseLeaveEvent) -> Unit)? = null,
    var onMouseOver: ((MouseOverEvent) -> Unit)? = null,
    var onMouseMove: ((MouseMoveEvent) -> Unit)? = null,
    var onMouseDown: ((MouseDownEvent) -> Unit)? = null,
    var onMouseUp: ((MouseUpEvent) -> Unit)? = null,
    var onMouseClick: ((MouseClickEvent) -> Unit)? = null,
    var onMouseDrag: ((MouseDragEvent) -> Unit)? = null,
    var onMouseWheel: ((MouseWheelEvent) -> Unit)? = null,
    var onKeyDown: ((KeyboardKeyDownEvent) -> Unit)? = null,
    var onKeyUp: ((KeyboardKeyUpEvent) -> Unit)? = null,
    var onKeyPressed: ((KeyboardKeyDownEvent) -> Unit)? = null,
    var onKeyReleased: ((KeyboardKeyUpEvent) -> Unit)? = null,
    var onFocusGain: ((FocusGainEvent) -> Unit)? = null,
    var onFocusLose: ((FocusLoseEvent) -> Unit)? = null,
    var onInput: ((InputEvent) -> Unit)? = null,
    var onValueChange: ((ValueChangedEvent) -> Unit)? = null,
    var onDragStart: ((DragStartEvent) -> Unit)? = null,
    var onDrag: ((DragEvent) -> Unit)? = null,
    var onDragEnd: ((DragEndEvent) -> Unit)? = null,
    var onDragEnter: ((DragEnterEvent) -> Unit)? = null,
    var onDragOver: ((DragOverEvent) -> Unit)? = null,
    var onDragLeave: ((DragLeaveEvent) -> Unit)? = null,
    var onDrop: ((DropEvent) -> Unit)? = null,
) {
    fun style(block: StyleScope.() -> Unit) {
        style = block
    }

//    fun asFlexRow(): ComponentProps = asFlex(FlexDirection.Row)
//
//    fun asFlexColumn(): ComponentProps = asFlex(FlexDirection.Column)
//
//    private fun asFlex(direction: FlexDirection): ComponentProps {
//        val previous = style
//        style = {
//            display = Display.Flex
//            flexDirection = direction
//            previous()
//        }
//        return this
//    }
}
