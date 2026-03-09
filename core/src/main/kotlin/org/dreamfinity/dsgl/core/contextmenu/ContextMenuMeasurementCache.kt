package org.dreamfinity.dsgl.core.contextmenu

import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext

class ContextMenuMeasurementCache(
    private val maxEntries: Int = 192
) {
    data class EntrySnapshot(
        val id: String?,
        val kind: Int,
        val label: String,
        val icon: String?,
        val hint: String?,
        val enabled: Boolean,
        val checked: Boolean
    )

    data class Measurement(
        val snapshots: List<EntrySnapshot>,
        val rowHeight: Int,
        val separatorHeight: Int,
        val entryHeights: IntArray,
        val entryOffsets: IntArray,
        val totalContentHeight: Int,
        val maxLabelWidth: Int,
        val maxHintWidth: Int,
        val panelWidth: Int,
        val indicatorWidth: Int
    )

    data class Key(
        val menuToken: Long,
        val styleHash: Int,
        val fontHash: Int,
        val dpiKey: Int,
        val entriesHash: Int
    )

    private val cache: MutableMap<Key, Measurement> =
        object : LinkedHashMap<Key, Measurement>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Measurement>?): Boolean {
                return size > maxEntries
            }
        }

    var computeCount: Long = 0L
        private set

    fun measure(
        menuToken: Long,
        entries: List<MenuEntry>,
        style: ContextMenuStyle,
        fontId: String?,
        fontSize: Int?,
        ctx: UiMeasureContext,
        dpiScale: Float
    ): Measurement {
        val snapshots = buildSnapshots(entries)
        val fingerprint = fingerprint(snapshots)
        val key = Key(
            menuToken = menuToken,
            styleHash = style.hashCode(),
            fontHash = 31 * (fontId?.hashCode() ?: 0) + (fontSize ?: 0),
            dpiKey = (dpiScale * 1000f).toInt(),
            entriesHash = fingerprint
        )
        synchronized(cache) {
            cache[key]?.let { return it }
        }

        computeCount += 1
        val rowHeight = (ctx.fontHeight(fontId, fontSize) + style.rowPaddingY * 2)
            .coerceAtLeast(14)
        val separatorHeight = style.separatorHeight.coerceAtLeast(2)

        val entryHeights = IntArray(snapshots.size)
        val entryOffsets = IntArray(snapshots.size)
        var offset = 0
        var maxLabelWidth = 0
        var maxHintWidth = 0
        var maxIndicatorWidth = 0

        snapshots.forEachIndexed { index, snapshot ->
            entryOffsets[index] = offset
            val height = if (snapshot.kind == KIND_SEPARATOR) separatorHeight else rowHeight
            entryHeights[index] = height
            offset += height + style.rowGap
            if (snapshot.kind == KIND_SEPARATOR) {
                return@forEachIndexed
            }

            val labelWidth = ctx.measureText(snapshot.label, fontId, fontSize)
            if (labelWidth > maxLabelWidth) {
                maxLabelWidth = labelWidth
            }

            val indicatorText = when {
                snapshot.checked -> ContextMenuGlyphs.CHECK_MARK
                !snapshot.icon.isNullOrEmpty() -> snapshot.icon
                else -> null
            }
            if (!indicatorText.isNullOrEmpty()) {
                val indicatorWidth = ctx.measureText(indicatorText, fontId, fontSize)
                if (indicatorWidth > maxIndicatorWidth) {
                    maxIndicatorWidth = indicatorWidth
                }
            }

            val hintText = when {
                !snapshot.hint.isNullOrEmpty() -> snapshot.hint
                snapshot.kind == KIND_SUBMENU -> ContextMenuGlyphs.SUBMENU_ARROW
                else -> null
            }
            if (!hintText.isNullOrEmpty()) {
                val hintWidth = ctx.measureText(hintText, fontId, fontSize)
                if (hintWidth > maxHintWidth) {
                    maxHintWidth = hintWidth
                }
            }
        }

        if (offset > 0) {
            offset -= style.rowGap
        }

        val measuredIndicatorWidth = maxIndicatorWidth.coerceAtLeast(style.iconColumnMinWidth.coerceAtLeast(0))
        val measuredWidth =
            style.rowPaddingX +
                measuredIndicatorWidth +
                style.contentSpacing +
                maxLabelWidth +
                (if (maxHintWidth > 0) style.hintSpacing + maxHintWidth else 0) +
                style.rowPaddingX
        val panelWidth = measuredWidth.coerceAtLeast(style.minPanelWidth)

        val measured = Measurement(
            snapshots = snapshots,
            rowHeight = rowHeight,
            separatorHeight = separatorHeight,
            entryHeights = entryHeights,
            entryOffsets = entryOffsets,
            totalContentHeight = offset,
            maxLabelWidth = maxLabelWidth,
            maxHintWidth = maxHintWidth,
            panelWidth = panelWidth,
            indicatorWidth = measuredIndicatorWidth
        )
        synchronized(cache) {
            cache[key] = measured
        }
        return measured
    }

    private fun buildSnapshots(entries: List<MenuEntry>): List<EntrySnapshot> {
        if (entries.isEmpty()) return emptyList()
        val out = ArrayList<EntrySnapshot>(entries.size)
        entries.forEach { entry ->
            when (entry) {
                is MenuEntry.Item -> {
                    out += EntrySnapshot(
                        id = entry.id,
                        kind = KIND_ITEM,
                        label = entry.labelProvider.invoke(),
                        icon = entry.iconProvider?.invoke(),
                        hint = entry.hintProvider?.invoke(),
                        enabled = entry.enabledProvider.invoke(),
                        checked = entry.checkedProvider?.invoke() == true
                    )
                }

                is MenuEntry.Submenu -> {
                    out += EntrySnapshot(
                        id = entry.id,
                        kind = KIND_SUBMENU,
                        label = entry.labelProvider.invoke(),
                        icon = entry.iconProvider?.invoke(),
                        hint = entry.hintProvider?.invoke(),
                        enabled = entry.enabledProvider.invoke(),
                        checked = false
                    )
                }

                is MenuEntry.Separator -> {
                    out += EntrySnapshot(
                        id = entry.id,
                        kind = KIND_SEPARATOR,
                        label = "",
                        icon = null,
                        hint = null,
                        enabled = false,
                        checked = false
                    )
                }
            }
        }
        return out
    }

    private fun fingerprint(snapshots: List<EntrySnapshot>): Int {
        if (snapshots.isEmpty()) return 0
        var hash = 1
        snapshots.forEach { snapshot ->
            hash = 31 * hash + snapshot.kind
            hash = 31 * hash + (snapshot.id?.hashCode() ?: 0)
            hash = 31 * hash + snapshot.label.hashCode()
            hash = 31 * hash + (snapshot.icon?.hashCode() ?: 0)
            hash = 31 * hash + (snapshot.hint?.hashCode() ?: 0)
            hash = 31 * hash + if (snapshot.enabled) 1 else 0
            hash = 31 * hash + if (snapshot.checked) 1 else 0
        }
        return hash
    }

    companion object {
        const val KIND_ITEM: Int = 1
        const val KIND_SUBMENU: Int = 2
        const val KIND_SEPARATOR: Int = 3
    }
}
