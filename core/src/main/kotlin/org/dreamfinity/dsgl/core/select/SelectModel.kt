package org.dreamfinity.dsgl.core.select

import java.util.concurrent.atomic.AtomicLong

private object SelectModelIds {
    private val nextToken = AtomicLong(1L)

    fun next(): Long = nextToken.getAndIncrement()
}

data class SelectModel(
    val id: String? = null,
    val placeholderProvider: (() -> String?)? = null,
    val entries: List<SelectEntry>,
    internal val token: Long = SelectModelIds.next(),
)

sealed class SelectEntry(
    open val id: String?,
) {
    data class Option(
        override val id: String,
        val labelProvider: () -> String,
        val enabledProvider: () -> Boolean = { true },
    ) : SelectEntry(id)

    data class Separator(
        override val id: String? = null,
    ) : SelectEntry(id)

    data class Group(
        override val id: String? = null,
        val labelProvider: () -> String,
        val entries: List<SelectEntry>,
    ) : SelectEntry(id)
}
