package org.dreamfinity.dsgl.core.dnd

import org.dreamfinity.dsgl.core.event.Events
import org.dreamfinity.dsgl.core.event.MouseEvent

abstract class DragDropEvent(
    override var mouseX: Int,
    override var mouseY: Int,
    val sourceKey: Any?,
    val dataTransfer: DataTransfer,
) : MouseEvent(mouseX, mouseY)

data class DragStartEvent(
    private val x: Int,
    private val y: Int,
    private val dragSourceKey: Any?,
    private val transfer: DataTransfer,
) : DragDropEvent(x, y, dragSourceKey, transfer) {
    override val type: Events
        get() = Events.DRAGSTART
}

data class DragEvent(
    private val x: Int,
    private val y: Int,
    private val dragSourceKey: Any?,
    private val transfer: DataTransfer,
) : DragDropEvent(x, y, dragSourceKey, transfer) {
    override val type: Events
        get() = Events.DRAGGING
}

data class DragEndEvent(
    private val x: Int,
    private val y: Int,
    private val dragSourceKey: Any?,
    private val transfer: DataTransfer,
    val didDrop: Boolean,
    val finalDropEffect: DropEffect,
    val dropTargetKey: Any?,
) : DragDropEvent(x, y, dragSourceKey, transfer) {
    override val type: Events
        get() = Events.DRAGEND
}

data class DragEnterEvent(
    private val x: Int,
    private val y: Int,
    private val dragSourceKey: Any?,
    private val transfer: DataTransfer,
) : DragDropEvent(x, y, dragSourceKey, transfer) {
    override val type: Events
        get() = Events.DRAGENTER
}

class DragOverEvent(
    x: Int,
    y: Int,
    dragSourceKey: Any?,
    transfer: DataTransfer,
) : DragDropEvent(x, y, dragSourceKey, transfer) {
    override val type: Events
        get() = Events.DRAGOVER

    var dropAccepted: Boolean = false
        private set

    fun preventDefault() {
        dropAccepted = true
    }

    fun acceptDrop(effect: DropEffect? = null) {
        dropAccepted = true
        if (effect != null) {
            dataTransfer.dropEffect = effect
        }
    }
}

data class DragLeaveEvent(
    private val x: Int,
    private val y: Int,
    private val dragSourceKey: Any?,
    private val transfer: DataTransfer,
) : DragDropEvent(x, y, dragSourceKey, transfer) {
    override val type: Events
        get() = Events.DRAGLEAVE
}

class DropEvent(
    x: Int,
    y: Int,
    dragSourceKey: Any?,
    transfer: DataTransfer,
) : DragDropEvent(x, y, dragSourceKey, transfer) {
    override val type: Events
        get() = Events.DROP

    var dropAccepted: Boolean = false
        private set

    fun preventDefault() {
        dropAccepted = true
    }

    fun acceptDrop(effect: DropEffect? = null) {
        dropAccepted = true
        if (effect != null) {
            dataTransfer.dropEffect = effect
        }
    }
}
