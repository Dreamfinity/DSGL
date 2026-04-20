package org.dreamfinity.dsgl.core.select

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope

interface SelectHost {
    fun open(request: SelectOpenRequest)
    fun close(owner: Any)
    fun closeAll()
    fun isOpenFor(owner: Any): Boolean
    fun isOpen(): Boolean
}

data class SelectOpenRequest(
    val owner: Any,
    val modelToken: Long,
    val entries: List<SelectEntry>,
    val selectedId: String?,
    val anchorRect: Rect,
    val closeOnSelect: Boolean,
    val onSelect: ((String) -> Unit)? = null,
    val onClose: (() -> Unit)? = null,
    val fontId: String? = null,
    val fontSize: Int? = null,
    val ownerScope: OverlayOwnerScope = OverlayOwnerScope.Application,
)

fun interface SelectClock {
    fun nowMs(): Long
}

object SystemSelectClock : SelectClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
