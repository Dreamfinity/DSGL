package org.dreamfinity.dsgl.core.dnd

const val DND_DATA_ID_MIME: String = "application/x-dsgl-dnd-id"
const val DND_DATA_TYPE_MIME: String = "application/x-dsgl-dnd-type"

data class Transform(
    val x: Double,
    val y: Double
)

data class ActiveDrag(
    val id: String?,
    val type: String?,
    val sourceKey: Any?,
    val overKey: Any?,
    val data: Any?,
    val cursorX: Int,
    val cursorY: Int,
    val transform: Transform,
    val dropEffect: DropEffect,
    val dataTransfer: DataTransfer
)

enum class InsertPosition {
    BEFORE,
    AFTER,
    APPEND
}

data class SortableProjection(
    val activeId: String?,
    val overId: String?,
    val insertPosition: InsertPosition,
    val newIndex: Int?
)