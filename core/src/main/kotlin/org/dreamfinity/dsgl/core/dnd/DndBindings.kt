package org.dreamfinity.dsgl.core.dnd

import org.dreamfinity.dsgl.core.ComponentProps

data class DndListeners(
    val onDragStart: ((DragStartEvent) -> Unit)? = null,
    val onDrag: ((DragEvent) -> Unit)? = null,
    val onDragEnd: ((DragEndEvent) -> Unit)? = null,
    val onDragEnter: ((DragEnterEvent) -> Unit)? = null,
    val onDragOver: ((DragOverEvent) -> Unit)? = null,
    val onDragLeave: ((DragLeaveEvent) -> Unit)? = null,
    val onDrop: ((DropEvent) -> Unit)? = null
)

fun ComponentProps.applyDndListeners(listeners: DndListeners) {
    onDragStart = chain(onDragStart, listeners.onDragStart)
    onDrag = chain(onDrag, listeners.onDrag)
    onDragEnd = chain(onDragEnd, listeners.onDragEnd)
    onDragEnter = chain(onDragEnter, listeners.onDragEnter)
    onDragOver = chain(onDragOver, listeners.onDragOver)
    onDragLeave = chain(onDragLeave, listeners.onDragLeave)
    onDrop = chain(onDrop, listeners.onDrop)
}

fun mergeDndListeners(first: DndListeners, second: DndListeners): DndListeners {
    return DndListeners(
        onDragStart = chain(first.onDragStart, second.onDragStart),
        onDrag = chain(first.onDrag, second.onDrag),
        onDragEnd = chain(first.onDragEnd, second.onDragEnd),
        onDragEnter = chain(first.onDragEnter, second.onDragEnter),
        onDragOver = chain(first.onDragOver, second.onDragOver),
        onDragLeave = chain(first.onDragLeave, second.onDragLeave),
        onDrop = chain(first.onDrop, second.onDrop)
    )
}

private fun <T> chain(
    first: ((T) -> Unit)?,
    second: ((T) -> Unit)?
): ((T) -> Unit)? {
    if (first == null) return second
    if (second == null) return first
    return { event ->
        first(event)
        second(event)
    }
}