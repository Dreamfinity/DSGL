package org.dreamfinity.dsgl.core.dnd

import org.dreamfinity.dsgl.core.dsl.ComponentProps

fun ComponentProps.applyDraggable(descriptor: Draggable) {
    this.draggable = true
    dragPreviewMode = descriptor.previewMode
    hideSourceWhileDragging = descriptor.hideSourceWhileDragging
    dragPreview = descriptor.renderPreview
    dragPlaceholder = descriptor.renderPlaceholder
    ref = descriptor.setNodeRef
    applyDndListeners(descriptor.listeners)
}

fun ComponentProps.applyDroppable(descriptor: Droppable) {
    this.droppable = true
    ref = descriptor.setNodeRef
    applyDndListeners(descriptor.listeners)
}

fun ComponentProps.applySortable(descriptor: Sortable) {
    applyDraggable(descriptor.draggable)
    applyDroppable(descriptor.droppable)
    applyDndListeners(descriptor.listeners)
}