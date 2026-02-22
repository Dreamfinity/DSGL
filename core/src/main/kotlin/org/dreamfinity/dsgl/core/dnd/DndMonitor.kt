package org.dreamfinity.dsgl.core.dnd

interface DndMonitorListener {
    fun onDragStart(active: ActiveDrag) {}
    fun onDragMove(active: ActiveDrag, over: Any?) {}
    fun onDragOver(active: ActiveDrag, over: Any?) {}
    fun onDragEnd(active: ActiveDrag, over: Any?, dropEffect: DropEffect) {}
    fun onDragCancel(active: ActiveDrag) {}
}

data class DragDropMonitorCallbacks(
    val onDragStart: ((ActiveDrag) -> Unit)? = null,
    val onDragMove: ((ActiveDrag, Any?) -> Unit)? = null,
    val onDragOver: ((ActiveDrag, Any?) -> Unit)? = null,
    val onDragEnd: ((ActiveDrag, Any?, DropEffect) -> Unit)? = null,
    val onDragCancel: ((ActiveDrag) -> Unit)? = null
)
