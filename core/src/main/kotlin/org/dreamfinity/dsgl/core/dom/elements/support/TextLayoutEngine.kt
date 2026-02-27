package org.dreamfinity.dsgl.core.dom.elements.support

import org.dreamfinity.dsgl.core.style.TextWrap

object TextLayoutEngine {
    data class Line(
        val text: String,
        val startIndex: Int,
        val endIndexExclusive: Int,
        val width: Int
    ) {
        val length: Int
            get() = endIndexExclusive - startIndex
    }

    data class Layout(
        val lines: List<Line>,
        val maxLineWidth: Int,
        val totalHeight: Int,
        val lineHeight: Int
    ) {
        fun lineForCaret(caretIndex: Int): Int {
            if (lines.isEmpty()) return 0
            lines.forEachIndexed { index, line ->
                if (caretIndex in line.startIndex..line.endIndexExclusive) {
                    return index
                }
            }
            return lines.lastIndex
        }
    }

    private data class CacheKey(
        val text: String,
        val maxWidth: Int?,
        val wrap: TextWrap,
        val fontHeight: Int,
        val fontFingerprint: Int
    )

    private const val MAX_CACHE_SIZE: Int = 512
    private val cache: MutableMap<CacheKey, Layout> = object : LinkedHashMap<CacheKey, Layout>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, Layout>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    fun clearCache() {
        synchronized(cache) {
            cache.clear()
        }
    }

    fun layout(
        text: String,
        maxWidth: Int?,
        wrap: TextWrap,
        fontHeight: Int,
        measureText: (String) -> Int,
        measureRange: ((startIndex: Int, endIndexExclusive: Int) -> Int)? = null
    ): Layout {
        val resolvedHeight = fontHeight.coerceAtLeast(1)
        val constrainedWidth = maxWidth?.coerceAtLeast(0)
        if (measureRange != null) {
            val lines = buildLines(text, constrainedWidth, wrap, measureText, measureRange)
            val maxLineWidth = lines.maxOfOrNull { it.width } ?: 0
            val totalHeight = lines.size.coerceAtLeast(1) * resolvedHeight
            return Layout(
                lines = lines,
                maxLineWidth = maxLineWidth,
                totalHeight = totalHeight,
                lineHeight = resolvedHeight
            )
        }
        val fingerprint = measureText("M") * 31 + measureText("i")
        val key = CacheKey(text, constrainedWidth, wrap, resolvedHeight, fingerprint)
        synchronized(cache) {
            cache[key]?.let { return it }
        }

        val lines = buildLines(text, constrainedWidth, wrap, measureText, measureRange)
        val maxLineWidth = lines.maxOfOrNull { it.width } ?: 0
        val totalHeight = lines.size.coerceAtLeast(1) * resolvedHeight
        val result =
            Layout(lines = lines, maxLineWidth = maxLineWidth, totalHeight = totalHeight, lineHeight = resolvedHeight)

        synchronized(cache) {
            cache[key] = result
        }
        return result
    }

    private fun buildLines(
        text: String,
        maxWidth: Int?,
        wrap: TextWrap,
        measureText: (String) -> Int,
        measureRange: ((startIndex: Int, endIndexExclusive: Int) -> Int)?
    ): List<Line> {
        if (text.isEmpty()) {
            return listOf(Line(text = "", startIndex = 0, endIndexExclusive = 0, width = 0))
        }

        val lines = ArrayList<Line>()
        var logicalStart = 0
        while (logicalStart <= text.length) {
            val newlineIndex = text.indexOf('\n', logicalStart).let { if (it >= 0) it else text.length }
            val segment = text.substring(logicalStart, newlineIndex)
            if (wrap == TextWrap.NoWrap || maxWidth == null || maxWidth <= 0) {
                lines.add(
                    Line(
                        text = segment,
                        startIndex = logicalStart,
                        endIndexExclusive = newlineIndex,
                        width = measureRange?.invoke(logicalStart, newlineIndex) ?: measureText(segment)
                    )
                )
            } else {
                appendWrappedSegment(lines, segment, logicalStart, maxWidth, measureText, measureRange)
            }

            if (newlineIndex == text.length) {
                break
            }
            logicalStart = newlineIndex + 1
            if (logicalStart == text.length) {
                lines.add(Line(text = "", startIndex = logicalStart, endIndexExclusive = logicalStart, width = 0))
                break
            }
        }

        return if (lines.isEmpty()) {
            listOf(Line(text = "", startIndex = 0, endIndexExclusive = 0, width = 0))
        } else {
            lines
        }
    }

    private fun appendWrappedSegment(
        out: MutableList<Line>,
        segment: String,
        globalStart: Int,
        maxWidth: Int,
        measureText: (String) -> Int,
        measureRange: ((startIndex: Int, endIndexExclusive: Int) -> Int)?
    ) {
        if (segment.isEmpty()) {
            out.add(Line(text = "", startIndex = globalStart, endIndexExclusive = globalStart, width = 0))
            return
        }

        var localStart = 0
        while (localStart < segment.length) {
            var localEndExclusive = findMaxFittingEnd(
                segment = segment,
                start = localStart,
                maxWidth = maxWidth,
                globalStart = globalStart,
                measureText = measureText,
                measureRange = measureRange
            )
            if (localEndExclusive <= localStart) {
                localEndExclusive = (localStart + 1).coerceAtMost(segment.length)
            } else if (localEndExclusive < segment.length) {
                val wrapOpportunity = lastWhitespaceBreak(segment, localStart, localEndExclusive)
                if (wrapOpportunity != null && wrapOpportunity > localStart) {
                    localEndExclusive = wrapOpportunity
                }
            }

            val lineText = segment.substring(localStart, localEndExclusive)
            out.add(
                Line(
                    text = lineText,
                    startIndex = globalStart + localStart,
                    endIndexExclusive = globalStart + localEndExclusive,
                    width = measureRange?.invoke(globalStart + localStart, globalStart + localEndExclusive)
                        ?: measureText(lineText)
                )
            )
            localStart = localEndExclusive
        }
    }

    private fun findMaxFittingEnd(
        segment: String,
        start: Int,
        maxWidth: Int,
        globalStart: Int,
        measureText: (String) -> Int,
        measureRange: ((startIndex: Int, endIndexExclusive: Int) -> Int)?
    ): Int {
        var low = (start + 1).coerceAtMost(segment.length)
        var high = segment.length
        var best = start
        while (low <= high) {
            val mid = (low + high) ushr 1
            val width = measureRange?.invoke(globalStart + start, globalStart + mid)
                ?: measureText(segment.substring(start, mid))
            if (width <= maxWidth) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return best
    }

    private fun lastWhitespaceBreak(segment: String, start: Int, endExclusive: Int): Int? {
        var index = endExclusive
        while (index > start + 1) {
            val ch = segment[index - 1]
            if (ch.isWhitespace()) {
                return index
            }
            index--
        }
        return null
    }
}
