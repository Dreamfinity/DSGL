package org.dreamfinity.dsgl.core.dnd

import org.dreamfinity.dsgl.core.hooks.ref.ElementHandle
import org.dreamfinity.dsgl.core.hooks.ref.RefTarget

data class Draggable(
    val id: String,
    val nodeKey: Any,
    val attributes: Map<String, String>,
    val listeners: DndListeners,
    val isDragging: Boolean,
    val activeTransform: Transform?,
    val setNodeRef: RefTarget<ElementHandle>,
    val data: Any?,
    val previewMode: DragPreviewMode,
    val hideSourceWhileDragging: Boolean,
    val renderPreview: (DragPreviewScope.() -> Unit)?,
    val renderPlaceholder: (PlaceholderScope.() -> Unit)?,
)

data class Droppable(
    val id: String,
    val nodeKey: Any,
    val isOver: Boolean,
    val active: ActiveDrag?,
    val listeners: DndListeners,
    val setNodeRef: RefTarget<ElementHandle>,
)

data class Sortable(
    val id: String,
    val containerId: String,
    val draggable: Draggable,
    val droppable: Droppable,
    val isDragging: Boolean,
    val isOver: Boolean,
    val overId: String?,
    val projection: SortableProjection,
    val listeners: DndListeners,
)
