package org.dreamfinity.dsgl.core.contextmenu

import java.util.concurrent.atomic.AtomicLong

private object ContextMenuIds {
    private val nextToken = AtomicLong(1L)
    fun next(): Long = nextToken.getAndIncrement()
}

data class ContextMenuModel(
    val id: String? = null,
    val entries: List<MenuEntry>,
    val fontId: String? = null,
    val fontSize: Int? = null,
    internal val token: Long = ContextMenuIds.next()
)

sealed class MenuEntry(
    open val id: String?
) {
    data class Item(
        override val id: String? = null,
        val labelProvider: () -> String,
        val iconProvider: (() -> String?)? = null,
        val hintProvider: (() -> String?)? = null,
        val enabledProvider: () -> Boolean = { true },
        val checkedProvider: (() -> Boolean)? = null,
        val closeOnAction: Boolean = true,
        val onClick: (() -> Unit)? = null
    ) : MenuEntry(id)

    data class Submenu(
        override val id: String? = null,
        val labelProvider: () -> String,
        val iconProvider: (() -> String?)? = null,
        val hintProvider: (() -> String?)? = null,
        val enabledProvider: () -> Boolean = { true },
        val entries: List<MenuEntry>,
        internal val token: Long = ContextMenuIds.next()
    ) : MenuEntry(id)

    data class Separator(
        override val id: String? = null
    ) : MenuEntry(id)
}
