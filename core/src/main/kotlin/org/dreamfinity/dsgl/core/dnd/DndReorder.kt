package org.dreamfinity.dsgl.core.dnd

fun <T> reorderByDnD(
    items: List<T>,
    activeId: String?,
    overId: String?,
    insertPosition: InsertPosition,
    idOf: (T) -> String
): List<T> {
    if (activeId.isNullOrBlank()) return items
    val fromIndex = items.indexOfFirst { idOf(it) == activeId }
    if (fromIndex < 0) return items

    val mutable = items.toMutableList()
    val moved = mutable.removeAt(fromIndex)
    val targetIndex = when {
        overId.isNullOrBlank() || insertPosition == InsertPosition.APPEND -> mutable.size
        else -> {
            val overIndex = mutable.indexOfFirst { idOf(it) == overId }
            if (overIndex < 0) {
                mutable.size
            } else if (insertPosition == InsertPosition.AFTER) {
                overIndex + 1
            } else {
                overIndex
            }
        }
    }.coerceIn(0, mutable.size)

    mutable.add(targetIndex, moved)
    return if (items.sameOrderAs(mutable, idOf)) items else mutable
}

private fun <T> List<T>.sameOrderAs(other: List<T>, idOf: (T) -> String): Boolean {
    if (this.size != other.size) return false
    this.indices.forEach { index ->
        if (idOf(this[index]) != idOf(other[index])) return false
    }
    return true
}

