package org.dreamfinity.dsgl.core.dnd

enum class EffectAllowed {
    NONE,
    COPY,
    MOVE,
    LINK,
    COPY_LINK,
    COPY_MOVE,
    LINK_MOVE,
    ALL
}

enum class DropEffect {
    NONE,
    COPY,
    MOVE,
    LINK
}

data class DragImageSpec(
    val nodeKey: Any,
    val offsetX: Int = 0,
    val offsetY: Int = 0
)

/**
 * HTML-like DataTransfer payload for drag-and-drop.
 */
class DataTransfer {
    private val dataByType: LinkedHashMap<String, String> = linkedMapOf()
    var effectAllowed: EffectAllowed = EffectAllowed.ALL
    var dropEffect: DropEffect = DropEffect.NONE
    var ghostVisible: Boolean = true
    private var dragImageSpec: DragImageSpec? = null

    val types: Set<String>
        get() = dataByType.keys

    fun setData(type: String, value: String) {
        val normalized = type.trim()
        if (normalized.isEmpty()) return
        dataByType[normalized] = value
    }

    fun getData(type: String): String? {
        return dataByType[type.trim()]
    }

    fun clearData(type: String? = null) {
        if (type == null) {
            dataByType.clear()
            return
        }
        dataByType.remove(type.trim())
    }

    fun setDragImage(nodeKey: String, offsetX: Int, offsetY: Int) {
        setDragImage(nodeKey as Any, offsetX, offsetY)
    }

    fun setDragImage(nodeKey: Any, offsetX: Int, offsetY: Int) {
        dragImageSpec = DragImageSpec(nodeKey, offsetX, offsetY)
    }

    fun hideGhost() {
        ghostVisible = false
    }

    fun showGhost() {
        ghostVisible = true
    }

    internal fun currentDragImageSpec(): DragImageSpec? = dragImageSpec
}
