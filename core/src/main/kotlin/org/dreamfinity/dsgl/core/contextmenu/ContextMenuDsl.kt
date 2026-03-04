package org.dreamfinity.dsgl.core.contextmenu

@DslMarker
annotation class ContextMenuDsl

fun contextMenu(
    id: String? = null,
    block: ContextMenuBuilder.() -> Unit
): ContextMenuModel {
    val builder = ContextMenuBuilder()
    builder.block()
    return ContextMenuModel(id = id, entries = builder.buildEntries())
}

@ContextMenuDsl
class ContextMenuBuilder {
    private val entries: MutableList<MenuEntry> = ArrayList()

    fun item(
        label: String,
        id: String? = null,
        block: ContextMenuItemBuilder.() -> Unit = {}
    ) {
        item(labelProvider = { label }, id = id, block = block)
    }

    fun item(
        labelProvider: () -> String,
        id: String? = null,
        block: ContextMenuItemBuilder.() -> Unit = {}
    ) {
        val builder = ContextMenuItemBuilder()
        builder.block()
        entries += MenuEntry.Item(
            id = id,
            labelProvider = labelProvider,
            iconProvider = builder.iconProvider,
            hintProvider = builder.hintProvider,
            enabledProvider = builder.enabledProvider,
            checkedProvider = builder.checkedProvider,
            closeOnAction = builder.closeOnAction,
            onClick = builder.onClick
        )
    }

    fun submenu(
        label: String,
        id: String? = null,
        block: ContextMenuSubmenuBuilder.() -> Unit
    ) {
        submenu(labelProvider = { label }, id = id, block = block)
    }

    fun submenu(
        labelProvider: () -> String,
        id: String? = null,
        block: ContextMenuSubmenuBuilder.() -> Unit
    ) {
        val builder = ContextMenuSubmenuBuilder()
        builder.block()
        entries += MenuEntry.Submenu(
            id = id,
            labelProvider = labelProvider,
            iconProvider = builder.iconProvider,
            hintProvider = builder.hintProvider,
            enabledProvider = builder.enabledProvider,
            entries = builder.buildEntries()
        )
    }

    fun separator(id: String? = null) {
        entries += MenuEntry.Separator(id = id)
    }

    internal fun buildEntries(): List<MenuEntry> = entries.toList()
}

@ContextMenuDsl
class ContextMenuSubmenuBuilder {
    private val entries: MutableList<MenuEntry> = ArrayList()
    internal var iconProvider: (() -> String?)? = null
    internal var hintProvider: (() -> String?)? = null
    internal var enabledProvider: () -> Boolean = { true }

    fun icon(value: String) {
        iconProvider = { value }
    }

    fun icon(provider: () -> String?) {
        iconProvider = provider
    }

    fun hint(value: String) {
        hintProvider = { value }
    }

    fun hint(provider: () -> String?) {
        hintProvider = provider
    }

    fun enabled(value: Boolean) {
        enabledProvider = { value }
    }

    fun enabledIf(predicate: () -> Boolean) {
        enabledProvider = predicate
    }

    fun item(
        label: String,
        id: String? = null,
        block: ContextMenuItemBuilder.() -> Unit = {}
    ) {
        item(labelProvider = { label }, id = id, block = block)
    }

    fun item(
        labelProvider: () -> String,
        id: String? = null,
        block: ContextMenuItemBuilder.() -> Unit = {}
    ) {
        val builder = ContextMenuItemBuilder()
        builder.block()
        entries += MenuEntry.Item(
            id = id,
            labelProvider = labelProvider,
            iconProvider = builder.iconProvider,
            hintProvider = builder.hintProvider,
            enabledProvider = builder.enabledProvider,
            checkedProvider = builder.checkedProvider,
            closeOnAction = builder.closeOnAction,
            onClick = builder.onClick
        )
    }

    fun submenu(
        label: String,
        id: String? = null,
        block: ContextMenuSubmenuBuilder.() -> Unit
    ) {
        submenu(labelProvider = { label }, id = id, block = block)
    }

    fun submenu(
        labelProvider: () -> String,
        id: String? = null,
        block: ContextMenuSubmenuBuilder.() -> Unit
    ) {
        val builder = ContextMenuSubmenuBuilder()
        builder.block()
        entries += MenuEntry.Submenu(
            id = id,
            labelProvider = labelProvider,
            iconProvider = builder.iconProvider,
            hintProvider = builder.hintProvider,
            enabledProvider = builder.enabledProvider,
            entries = builder.buildEntries()
        )
    }

    fun separator(id: String? = null) {
        entries += MenuEntry.Separator(id = id)
    }

    internal fun buildEntries(): List<MenuEntry> = entries.toList()
}

@ContextMenuDsl
class ContextMenuItemBuilder {
    internal var onClick: (() -> Unit)? = null
    internal var iconProvider: (() -> String?)? = null
    internal var hintProvider: (() -> String?)? = null
    internal var enabledProvider: () -> Boolean = { true }
    internal var checkedProvider: (() -> Boolean)? = null
    internal var closeOnAction: Boolean = true

    fun onClick(action: () -> Unit) {
        onClick = action
    }

    fun icon(value: String) {
        iconProvider = { value }
    }

    fun icon(provider: () -> String?) {
        iconProvider = provider
    }

    fun hint(value: String) {
        hintProvider = { value }
    }

    fun hint(provider: () -> String?) {
        hintProvider = provider
    }

    fun enabled(value: Boolean) {
        enabledProvider = { value }
    }

    fun enabledIf(predicate: () -> Boolean) {
        enabledProvider = predicate
    }

    fun checked(value: Boolean) {
        checkedProvider = { value }
    }

    fun checkedIf(predicate: () -> Boolean) {
        checkedProvider = predicate
    }

    fun closeOnAction(value: Boolean) {
        closeOnAction = value
    }
}
