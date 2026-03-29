package org.dreamfinity.dsgl.core.dnd

import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.hooks.useEffect
import org.dreamfinity.dsgl.core.hooks.ref.ElementHandle
import org.dreamfinity.dsgl.core.hooks.ref.useRef
import java.util.WeakHashMap

private data class SortableState(
    var activeId: String? = null,
    var overId: String? = null,
    var insertPosition: InsertPosition = InsertPosition.APPEND
)

private data class SortableContainerRecord(
    val state: SortableState,
    var activeHookCount: Int = 0
)

private data class SortableWindowState(
    val byContainerId: MutableMap<String, SortableContainerRecord> = linkedMapOf()
)

private data class SortableBindingKey(
    val containerId: String,
    val nodeKey: Any
)

private val sortableStateByWindow: WeakHashMap<DsglWindow, SortableWindowState> = WeakHashMap()

private fun sortableWindowState(window: DsglWindow): SortableWindowState {
    return sortableStateByWindow.getOrPut(window) { SortableWindowState() }
}

private fun sortableState(window: DsglWindow, containerId: String): SortableState {
    val state = sortableWindowState(window)
    val record = state.byContainerId.getOrPut(containerId) {
        SortableContainerRecord(state = SortableState())
    }
    pruneUnboundSortableContainers(state = state, keepContainerId = containerId)
    return record.state
}

private fun retainSortableContainer(window: DsglWindow, containerId: String, renderState: SortableState) {
    val state = sortableWindowState(window)
    val existing = state.byContainerId[containerId]
    if (existing == null) {
        state.byContainerId[containerId] = SortableContainerRecord(
            state = renderState,
            activeHookCount = 1
        )
    } else {
        existing.activeHookCount += 1
    }
    pruneUnboundSortableContainers(state = state, keepContainerId = containerId)
}

private fun releaseSortableContainer(window: DsglWindow, containerId: String) {
    val state = sortableStateByWindow[window] ?: return
    val record = state.byContainerId[containerId] ?: return
    record.activeHookCount = (record.activeHookCount - 1).coerceAtLeast(0)
    if (record.activeHookCount == 0) {
        state.byContainerId.remove(containerId)
    }
    pruneUnboundSortableContainers(state = state, keepContainerId = null)
    if (state.byContainerId.isEmpty()) {
        sortableStateByWindow.remove(window)
    }
}

private fun pruneUnboundSortableContainers(state: SortableWindowState, keepContainerId: String?) {
    val iterator = state.byContainerId.entries.iterator()
    while (iterator.hasNext()) {
        val entry = iterator.next()
        if (entry.key == keepContainerId) {
            continue
        }
        if (entry.value.activeHookCount <= 0) {
            iterator.remove()
        }
    }
}

fun UiScope.useDraggable(
    id: String,
    nodeKey: Any = id,
    type: String = "default",
    data: Any? = null,
    previewMode: DragPreviewMode = DragPreviewMode.GHOST,
    hideSourceWhileDragging: Boolean = false,
    renderPreview: (DragPreviewScope.() -> Unit)? = null,
    renderPlaceholder: (PlaceholderScope.() -> Unit)? = null,
    onDragStart: ((DragStartEvent) -> Unit)? = null,
    onDrag: ((DragEvent) -> Unit)? = null,
    onDragEnd: ((DragEndEvent) -> Unit)? = null
): Draggable {
    return requireHookOwnerWindow().useDraggable(
        id = id,
        nodeKey = nodeKey,
        type = type,
        data = data,
        previewMode = previewMode,
        hideSourceWhileDragging = hideSourceWhileDragging,
        renderPreview = renderPreview,
        renderPlaceholder = renderPlaceholder,
        onDragStart = onDragStart,
        onDrag = onDrag,
        onDragEnd = onDragEnd
    )
}

internal fun DsglWindow.useDraggable(
    id: String,
    nodeKey: Any = id,
    type: String = "default",
    data: Any? = null,
    previewMode: DragPreviewMode = DragPreviewMode.GHOST,
    hideSourceWhileDragging: Boolean = false,
    renderPreview: (DragPreviewScope.() -> Unit)? = null,
    renderPlaceholder: (PlaceholderScope.() -> Unit)? = null,
    onDragStart: ((DragStartEvent) -> Unit)? = null,
    onDrag: ((DragEvent) -> Unit)? = null,
    onDragEnd: ((DragEndEvent) -> Unit)? = null
): Draggable {
    return hookRuntime().withComponentInstance(componentName = "useDraggable", key = nodeKey) {
        DndSystem.registerPayload(id, data)
        val ref by useRef<ElementHandle>()
        val monitor = DndSystem.monitor(nodeKey)
        val isDragging = monitor.isDragging && monitor.sourceKey == nodeKey
        val transform = if (isDragging) {
            Transform(
                x = monitor.previewX - monitor.cursorX.toDouble(),
                y = monitor.previewY - monitor.cursorY.toDouble()
            )
        } else {
            null
        }

        val listeners = DndListeners(
            onDragStart = { event ->
                event.dataTransfer.setData(DND_DATA_ID_MIME, id)
                event.dataTransfer.setData(DND_DATA_TYPE_MIME, type)
                onDragStart?.invoke(event)
            },
            onDrag = onDrag,
            onDragEnd = onDragEnd
        )

        Draggable(
            id = id,
            nodeKey = nodeKey,
            attributes = mapOf(
                "data-dnd-id" to id,
                "data-dnd-type" to type
            ),
            listeners = listeners,
            isDragging = isDragging,
            activeTransform = transform,
            setNodeRef = { value -> ref.current = value },
            data = data,
            previewMode = previewMode,
            hideSourceWhileDragging = hideSourceWhileDragging,
            renderPreview = renderPreview,
            renderPlaceholder = renderPlaceholder
        )
    }
}

fun UiScope.useDroppable(
    id: String,
    nodeKey: Any = id,
    accepts: (ActiveDrag) -> Boolean = { true },
    onDragOver: ((DragOverEvent, ActiveDrag?) -> Unit)? = null,
    onDrop: ((DropEvent, ActiveDrag?) -> Unit)? = null,
    onDragEnter: ((DragEnterEvent, ActiveDrag?) -> Unit)? = null,
    onDragLeave: ((DragLeaveEvent, ActiveDrag?) -> Unit)? = null
): Droppable {
    return requireHookOwnerWindow().useDroppable(
        id = id,
        nodeKey = nodeKey,
        accepts = accepts,
        onDragOver = onDragOver,
        onDrop = onDrop,
        onDragEnter = onDragEnter,
        onDragLeave = onDragLeave
    )
}

internal fun DsglWindow.useDroppable(
    id: String,
    nodeKey: Any = id,
    accepts: (ActiveDrag) -> Boolean = { true },
    onDragOver: ((DragOverEvent, ActiveDrag?) -> Unit)? = null,
    onDrop: ((DropEvent, ActiveDrag?) -> Unit)? = null,
    onDragEnter: ((DragEnterEvent, ActiveDrag?) -> Unit)? = null,
    onDragLeave: ((DragLeaveEvent, ActiveDrag?) -> Unit)? = null
): Droppable {
    return hookRuntime().withComponentInstance(componentName = "useDroppable", key = nodeKey) {
        val ref by useRef<ElementHandle>()
        val active = DndSystem.activeDrag()
        val isOver = active?.overKey == nodeKey
        val listeners = DndListeners(
            onDragEnter = { event ->
                val snapshot = activeFromEvent(event)
                onDragEnter?.invoke(event, snapshot)
            },
            onDragOver = { event ->
                val snapshot = activeFromEvent(event)
                if (accepts(snapshot)) {
                    event.acceptDrop(snapshot.dropEffect)
                }
                onDragOver?.invoke(event, snapshot)
            },
            onDragLeave = { event ->
                val snapshot = activeFromEvent(event)
                onDragLeave?.invoke(event, snapshot)
            },
            onDrop = { event ->
                val snapshot = activeFromEvent(event)
                onDrop?.invoke(event, snapshot)
            }
        )
        Droppable(
            id = id,
            nodeKey = nodeKey,
            isOver = isOver,
            active = active,
            listeners = listeners,
            setNodeRef = { value -> ref.current = value }
        )
    }
}

fun UiScope.useSortable(
    id: String,
    nodeKey: Any = id,
    containerId: String,
    items: List<String>,
    data: Any? = null,
    previewMode: DragPreviewMode = DragPreviewMode.ORIGINAL,
    hideSourceWhileDragging: Boolean = true
): Sortable {
    return requireHookOwnerWindow().useSortable(
        id = id,
        nodeKey = nodeKey,
        containerId = containerId,
        items = items,
        data = data,
        previewMode = previewMode,
        hideSourceWhileDragging = hideSourceWhileDragging
    )
}

internal fun DsglWindow.useSortable(
    id: String,
    nodeKey: Any = id,
    containerId: String,
    items: List<String>,
    data: Any? = null,
    previewMode: DragPreviewMode = DragPreviewMode.ORIGINAL,
    hideSourceWhileDragging: Boolean = true
): Sortable {
    val state = sortableState(this, containerId)
    hookRuntime().withComponentInstance(
        componentName = "useSortableBinding",
        key = SortableBindingKey(containerId = containerId, nodeKey = nodeKey)
    ) {
        useEffect {
            retainSortableContainer(
                window = this@useSortable,
                containerId = containerId,
                renderState = state
            )
            onDispose {
                releaseSortableContainer(window = this@useSortable, containerId = containerId)
            }
        }
    }
    val sortableType = "sortable:$containerId"
    val draggable = useDraggable(
        id = id,
        nodeKey = nodeKey,
        type = sortableType,
        data = data,
        previewMode = previewMode,
        hideSourceWhileDragging = hideSourceWhileDragging,
        onDragStart = {
            state.activeId = id
            state.overId = null
            state.insertPosition = InsertPosition.APPEND
        },
        onDragEnd = {
            state.activeId = null
            state.overId = null
            state.insertPosition = InsertPosition.APPEND
        }
    )
    val droppable = useDroppable(
        id = id,
        nodeKey = nodeKey,
        accepts = { active -> active.type == sortableType && active.id != id },
        onDragOver = { event, active ->
            if (active == null || active.id == id) return@useDroppable
            state.overId = id
            state.insertPosition = if (event.mouseY >= event.target!!.bounds.y + event.target!!.bounds.height / 2) {
                InsertPosition.AFTER
            } else {
                InsertPosition.BEFORE
            }
        },
        onDrop = { event, active ->
            if (active == null || active.id == id) return@useDroppable
            state.overId = id
            state.insertPosition = if (event.mouseY >= event.target!!.bounds.y + event.target!!.bounds.height / 2) {
                InsertPosition.AFTER
            } else {
                InsertPosition.BEFORE
            }
        }
    )

    val projected = SortableProjection(
        activeId = state.activeId,
        overId = state.overId,
        insertPosition = state.insertPosition,
        newIndex = projectedIndex(items, state.activeId, state.overId, state.insertPosition)
    )
    val mergedListeners = mergeDndListeners(draggable.listeners, droppable.listeners)
    return Sortable(
        id = id,
        containerId = containerId,
        draggable = draggable,
        droppable = droppable,
        isDragging = draggable.isDragging,
        isOver = droppable.isOver,
        overId = state.overId,
        projection = projected,
        listeners = mergedListeners
    )
}

fun UiScope.useDragDropMonitor(callbacks: DragDropMonitorCallbacks) {
    requireHookOwnerWindow().hookRuntime().withComponentInstance(componentName = "useDragDropMonitor") {
        val callbackRef by useRef(callbacks)
        callbackRef.current = callbacks
        useEffect {
            val subscription = DndRuntime.engine.subscribe(object : DndMonitorListener {
                override fun onDragStart(active: ActiveDrag) {
                    callbackRef.current?.onDragStart?.invoke(active)
                }

                override fun onDragMove(active: ActiveDrag, over: Any?) {
                    callbackRef.current?.onDragMove?.invoke(active, over)
                }

                override fun onDragOver(active: ActiveDrag, over: Any?) {
                    callbackRef.current?.onDragOver?.invoke(active, over)
                }

                override fun onDragEnd(active: ActiveDrag, over: Any?, dropEffect: DropEffect) {
                    callbackRef.current?.onDragEnd?.invoke(active, over, dropEffect)
                }

                override fun onDragCancel(active: ActiveDrag) {
                    callbackRef.current?.onDragCancel?.invoke(active)
                }
            })
            onDispose {
                subscription.close()
            }
        }
    }
}

private fun activeFromEvent(event: DragDropEvent): ActiveDrag {
    val id = event.dataTransfer.getData(DND_DATA_ID_MIME)
    val type = event.dataTransfer.getData(DND_DATA_TYPE_MIME)
    val payload = DndSystem.payload(id)
    return ActiveDrag(
        id = id,
        type = type,
        sourceKey = event.sourceKey,
        overKey = event.target?.key,
        data = payload,
        cursorX = event.mouseX,
        cursorY = event.mouseY,
        transform = Transform(0.0, 0.0),
        dropEffect = event.dataTransfer.dropEffect,
        dataTransfer = event.dataTransfer
    )
}

private fun projectedIndex(
    items: List<String>,
    activeId: String?,
    overId: String?,
    insertPosition: InsertPosition
): Int? {
    if (activeId == null) return null
    val moved = reorderByDnD(
        items = items,
        activeId = activeId,
        overId = overId,
        insertPosition = insertPosition
    ) { value -> value }
    return moved.indexOf(activeId).takeIf { it >= 0 }
}
