package org.dreamfinity.dsgl.core.select

@DslMarker
annotation class SelectDsl

fun selectModel(
    id: String? = null,
    block: SelectModelBuilder.() -> Unit
): SelectModel {
    val builder = SelectModelBuilder()
    builder.block()
    return SelectModel(
        id = id,
        placeholderProvider = builder.placeholderProvider,
        entries = builder.buildEntries()
    )
}

@SelectDsl
class SelectModelBuilder {
    private val entries: MutableList<SelectEntry> = ArrayList()
    internal var placeholderProvider: (() -> String?)? = null

    fun placeholder(value: String) {
        placeholderProvider = { value }
    }

    fun placeholder(provider: () -> String?) {
        placeholderProvider = provider
    }

    fun option(
        id: String,
        label: String,
        block: SelectOptionBuilder.() -> Unit = {}
    ) {
        option(id = id, labelProvider = { label }, block = block)
    }

    fun option(
        id: String,
        labelProvider: () -> String,
        block: SelectOptionBuilder.() -> Unit = {}
    ) {
        val builder = SelectOptionBuilder()
        builder.block()
        entries += SelectEntry.Option(
            id = id,
            labelProvider = labelProvider,
            enabledProvider = builder.enabledProvider
        )
    }

    fun separator(id: String? = null) {
        entries += SelectEntry.Separator(id = id)
    }

    fun group(
        label: String,
        id: String? = null,
        block: SelectGroupBuilder.() -> Unit
    ) {
        group(labelProvider = { label }, id = id, block = block)
    }

    fun group(
        labelProvider: () -> String,
        id: String? = null,
        block: SelectGroupBuilder.() -> Unit
    ) {
        val builder = SelectGroupBuilder()
        builder.block()
        entries += SelectEntry.Group(
            id = id,
            labelProvider = labelProvider,
            entries = builder.buildEntries()
        )
    }

    internal fun buildEntries(): List<SelectEntry> = entries.toList()
}

@SelectDsl
class SelectGroupBuilder {
    private val entries: MutableList<SelectEntry> = ArrayList()

    fun option(
        id: String,
        label: String,
        block: SelectOptionBuilder.() -> Unit = {}
    ) {
        option(id = id, labelProvider = { label }, block = block)
    }

    fun option(
        id: String,
        labelProvider: () -> String,
        block: SelectOptionBuilder.() -> Unit = {}
    ) {
        val builder = SelectOptionBuilder()
        builder.block()
        entries += SelectEntry.Option(
            id = id,
            labelProvider = labelProvider,
            enabledProvider = builder.enabledProvider
        )
    }

    fun separator(id: String? = null) {
        entries += SelectEntry.Separator(id = id)
    }

    fun group(
        label: String,
        id: String? = null,
        block: SelectGroupBuilder.() -> Unit
    ) {
        group(labelProvider = { label }, id = id, block = block)
    }

    fun group(
        labelProvider: () -> String,
        id: String? = null,
        block: SelectGroupBuilder.() -> Unit
    ) {
        val builder = SelectGroupBuilder()
        builder.block()
        entries += SelectEntry.Group(
            id = id,
            labelProvider = labelProvider,
            entries = builder.buildEntries()
        )
    }

    internal fun buildEntries(): List<SelectEntry> = entries.toList()
}

@SelectDsl
class SelectOptionBuilder {
    internal var enabledProvider: () -> Boolean = { true }

    fun enabled(value: Boolean) {
        enabledProvider = { value }
    }

    fun enabledIf(predicate: () -> Boolean) {
        enabledProvider = predicate
    }
}
