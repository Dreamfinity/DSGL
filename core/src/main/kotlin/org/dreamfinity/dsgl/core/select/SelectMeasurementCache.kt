package org.dreamfinity.dsgl.core.select

import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext

class SelectMeasurementCache(
    private val maxEntries: Int = 192,
) {
    data class EntrySnapshot(
        val kind: Int,
        val id: String?,
        val optionId: String?,
        val label: String,
        val enabled: Boolean,
        val depth: Int,
    )

    data class Measurement(
        val snapshots: List<EntrySnapshot>,
        val rowHeight: Int,
        val separatorHeight: Int,
        val entryHeights: IntArray,
        val entryOffsets: IntArray,
        val totalContentHeight: Int,
        val panelWidth: Int,
    )

    data class Key(
        val modelToken: Long,
        val styleHash: Int,
        val fontHash: Int,
        val dpiKey: Int,
        val entriesHash: Int,
    )

    private val cache: MutableMap<Key, Measurement> =
        object : LinkedHashMap<Key, Measurement>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Measurement>?): Boolean =
                size > maxEntries
        }

    var computeCount: Long = 0L
        private set

    fun measure(
        modelToken: Long,
        entries: List<SelectEntry>,
        style: SelectStyle,
        ctx: UiMeasureContext,
        dpiScale: Float,
        fontId: String?,
        fontSize: Int?,
    ): Measurement {
        val snapshots = flattenEntries(entries)
        val fingerprint = fingerprint(snapshots)
        val resolvedFontId = fontId ?: style.fontId
        val resolvedFontSize = fontSize ?: style.fontSize
        val key =
            Key(
                modelToken = modelToken,
                styleHash = style.hashCode(),
                fontHash = 31 * (resolvedFontId?.hashCode() ?: 0) + (resolvedFontSize ?: 0),
                dpiKey = (dpiScale * 1000f).toInt(),
                entriesHash = fingerprint,
            )
        synchronized(cache) {
            cache[key]?.let { return it }
        }

        computeCount += 1
        val rowHeight = (ctx.fontHeight(resolvedFontId, resolvedFontSize) + style.rowPaddingY * 2).coerceAtLeast(14)
        val separatorHeight = style.separatorHeight.coerceAtLeast(2)
        val entryHeights = IntArray(snapshots.size)
        val entryOffsets = IntArray(snapshots.size)
        var offset = 0
        var maxWidth = style.minPanelWidth

        snapshots.forEachIndexed { index, snapshot ->
            entryOffsets[index] = offset
            val height = if (snapshot.kind == KIND_SEPARATOR) separatorHeight else rowHeight
            entryHeights[index] = height
            offset += height + style.rowGap

            val labelWidth =
                if (snapshot.label.isEmpty()) {
                    0
                } else {
                    ctx.measureText(
                        snapshot.label,
                        resolvedFontId,
                        resolvedFontSize,
                    )
                }
            val width =
                when (snapshot.kind) {
                    KIND_OPTION -> {
                        style.rowPaddingX +
                            style.markerColumnWidth +
                            style.markerGap +
                            snapshot.depth * style.groupIndentX +
                            labelWidth +
                            style.rowPaddingX
                    }

                    KIND_GROUP -> {
                        style.rowPaddingX +
                            snapshot.depth * style.groupIndentX +
                            labelWidth +
                            style.rowPaddingX
                    }

                    else -> style.minPanelWidth
                }
            if (width > maxWidth) {
                maxWidth = width
            }
        }

        if (offset > 0) {
            offset -= style.rowGap
        }

        val measurement =
            Measurement(
                snapshots = snapshots,
                rowHeight = rowHeight,
                separatorHeight = separatorHeight,
                entryHeights = entryHeights,
                entryOffsets = entryOffsets,
                totalContentHeight = offset,
                panelWidth = maxWidth.coerceAtLeast(style.minPanelWidth),
            )
        synchronized(cache) {
            cache[key] = measurement
        }
        return measurement
    }

    private fun flattenEntries(entries: List<SelectEntry>): List<EntrySnapshot> {
        if (entries.isEmpty()) return emptyList()
        val out = ArrayList<EntrySnapshot>(entries.size)
        appendEntries(entries, depth = 0, out = out)
        return out
    }

    private fun appendEntries(entries: List<SelectEntry>, depth: Int, out: MutableList<EntrySnapshot>) {
        entries.forEach { entry ->
            when (entry) {
                is SelectEntry.Option -> {
                    out +=
                        EntrySnapshot(
                            kind = KIND_OPTION,
                            id = entry.id,
                            optionId = entry.id,
                            label = entry.labelProvider.invoke(),
                            enabled = entry.enabledProvider.invoke(),
                            depth = depth,
                        )
                }

                is SelectEntry.Separator -> {
                    out +=
                        EntrySnapshot(
                            kind = KIND_SEPARATOR,
                            id = entry.id,
                            optionId = null,
                            label = "",
                            enabled = false,
                            depth = depth,
                        )
                }

                is SelectEntry.Group -> {
                    out +=
                        EntrySnapshot(
                            kind = KIND_GROUP,
                            id = entry.id,
                            optionId = null,
                            label = entry.labelProvider.invoke(),
                            enabled = false,
                            depth = depth,
                        )
                    appendEntries(entry.entries, depth = depth + 1, out = out)
                }
            }
        }
    }

    private fun fingerprint(snapshots: List<EntrySnapshot>): Int {
        if (snapshots.isEmpty()) return 0
        var hash = 1
        snapshots.forEach { snapshot ->
            hash = 31 * hash + snapshot.kind
            hash = 31 * hash + (snapshot.id?.hashCode() ?: 0)
            hash = 31 * hash + (snapshot.optionId?.hashCode() ?: 0)
            hash = 31 * hash + snapshot.label.hashCode()
            hash = 31 * hash + if (snapshot.enabled) 1 else 0
            hash = 31 * hash + snapshot.depth
        }
        return hash
    }

    companion object {
        const val KIND_OPTION: Int = 1
        const val KIND_SEPARATOR: Int = 2
        const val KIND_GROUP: Int = 3
    }
}
