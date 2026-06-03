package org.dreamfinity.dsgl.core.select

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.portal.ScreenDomainId

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
    val ownerDomain: ScreenDomainId = ScreenDomainId.Application,
)

fun interface SelectClock {
    fun nowMs(): Long
}

object SystemSelectClock : SelectClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
