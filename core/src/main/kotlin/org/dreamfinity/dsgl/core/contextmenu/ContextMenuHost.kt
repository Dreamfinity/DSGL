package org.dreamfinity.dsgl.core.contextmenu

import org.dreamfinity.dsgl.core.dom.layout.Rect

interface ContextMenuHost {
    fun openAtCursor(model: ContextMenuModel, x: Int, y: Int)
    fun openAnchored(model: ContextMenuModel, anchorRect: Rect)
    fun closeAll()
    fun isOpen(): Boolean
}

fun interface ContextMenuClock {
    fun nowMs(): Long
}

object SystemContextMenuClock : ContextMenuClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}

data class ContextMenuTriggerScope(
    val mouseX: Int,
    val mouseY: Int,
    val anchorRect: Rect?,
    private val host: ContextMenuHost
) {
    fun openMenu(model: ContextMenuModel) {
        host.openAtCursor(model, mouseX, mouseY)
    }

    fun openMenuAnchored(model: ContextMenuModel, anchor: Rect = anchorRect ?: Rect(mouseX, mouseY, 0, 0)) {
        host.openAnchored(model, anchor)
    }

    fun closeMenus() {
        host.closeAll()
    }
}
