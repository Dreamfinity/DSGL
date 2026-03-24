package org.dreamfinity.dsgl.core.dom.elements.support

import org.dreamfinity.dsgl.core.style.TextWrap
import java.util.concurrent.atomic.AtomicLong

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
        val fontFingerprint: Int,
        val usesRangeMeasurement: Boolean,
        val rangeMeasureCacheKey: Any?
    )

    data class HotPathStats(
        val layoutCalls: Long,
        val cacheHits: Long,
        val cacheMisses: Long,
        val cacheBypassedForRangeMeasure: Long,
        val buildLinesCalls: Long,
        val appendWrappedSegmentCalls: Long,
        val findMaxFittingCalls: Long,
        val rangeMeasureCalls: Long,
        val plainMeasureCalls: Long,
        val substringSliceCalls: Long
    )

    private const val MAX_CACHE_SIZE: Int = 512
    private val cache: MutableMap<CacheKey, Layout> = object : LinkedHashMap<CacheKey, Layout>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, Layout>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }
    private val layoutCalls = AtomicLong(0L)
    private val cacheHits = AtomicLong(0L)
    private val cacheMisses = AtomicLong(0L)
    private val cacheBypassedForRangeMeasure = AtomicLong(0L)
    private val buildLinesCalls = AtomicLong(0L)
    private val appendWrappedSegmentCalls = AtomicLong(0L)
    private val findMaxFittingCalls = AtomicLong(0L)
    private val rangeMeasureCalls = AtomicLong(0L)
    private val plainMeasureCalls = AtomicLong(0L)
    private val substringSliceCalls = AtomicLong(0L)

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
        measureRange: ((startIndex: Int, endIndexExclusive: Int) -> Int)? = null,
        measureRangeCacheKey: Any? = null
    ): Layout {
        layoutCalls.incrementAndGet()
        val resolvedHeight = fontHeight.coerceAtLeast(1)
        val constrainedWidth = maxWidth?.coerceAtLeast(0)
        if (measureRange != null && measureRangeCacheKey == null) {
            cacheBypassedForRangeMeasure.incrementAndGet()
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
        val fingerprint = if (measureRange != null) {
            measureRangeCacheKey.hashCode()
        } else {
            measureText("M") * 31 + measureText("i")
        }
        val key = CacheKey(
            text = text,
            maxWidth = constrainedWidth,
            wrap = wrap,
            fontHeight = resolvedHeight,
            fontFingerprint = fingerprint,
            usesRangeMeasurement = measureRange != null,
            rangeMeasureCacheKey = measureRangeCacheKey
        )
        synchronized(cache) {
            cache[key]?.let {
                cacheHits.incrementAndGet()
                return it
            }
        }
        cacheMisses.incrementAndGet()

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

    fun hotPathStats(): HotPathStats {
        return HotPathStats(
            layoutCalls = layoutCalls.get(),
            cacheHits = cacheHits.get(),
            cacheMisses = cacheMisses.get(),
            cacheBypassedForRangeMeasure = cacheBypassedForRangeMeasure.get(),
            buildLinesCalls = buildLinesCalls.get(),
            appendWrappedSegmentCalls = appendWrappedSegmentCalls.get(),
            findMaxFittingCalls = findMaxFittingCalls.get(),
            rangeMeasureCalls = rangeMeasureCalls.get(),
            plainMeasureCalls = plainMeasureCalls.get(),
            substringSliceCalls = substringSliceCalls.get()
        )
    }

    fun resetHotPathStats() {
        layoutCalls.set(0L)
        cacheHits.set(0L)
        cacheMisses.set(0L)
        cacheBypassedForRangeMeasure.set(0L)
        buildLinesCalls.set(0L)
        appendWrappedSegmentCalls.set(0L)
        findMaxFittingCalls.set(0L)
        rangeMeasureCalls.set(0L)
        plainMeasureCalls.set(0L)
        substringSliceCalls.set(0L)
    }

    private fun buildLines(
        text: String,
        maxWidth: Int?,
        wrap: TextWrap,
        measureText: (String) -> Int,
        measureRange: ((startIndex: Int, endIndexExclusive: Int) -> Int)?
    ): List<Line> {
        buildLinesCalls.incrementAndGet()
        if (text.isEmpty()) {
            return listOf(Line(text = "", startIndex = 0, endIndexExclusive = 0, width = 0))
        }

        val lines = ArrayList<Line>()
        var logicalStart = 0
        while (logicalStart <= text.length) {
            val newlineIndex = text.indexOf('\n', logicalStart).let { if (it >= 0) it else text.length }
            substringSliceCalls.incrementAndGet()
            val segment = text.substring(logicalStart, newlineIndex)
            if (wrap == TextWrap.NoWrap || maxWidth == null || maxWidth <= 0) {
                lines.add(
                    Line(
                        text = segment,
                        startIndex = logicalStart,
                        endIndexExclusive = newlineIndex,
                        width = measureWidth(
                            measureRange = measureRange,
                            startIndex = logicalStart,
                            endIndexExclusive = newlineIndex,
                            fallbackMeasure = { measureText(segment) }
                        )
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
        appendWrappedSegmentCalls.incrementAndGet()
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

            substringSliceCalls.incrementAndGet()
            val lineText = segment.substring(localStart, localEndExclusive)
            out.add(
                Line(
                    text = lineText,
                    startIndex = globalStart + localStart,
                    endIndexExclusive = globalStart + localEndExclusive,
                    width = measureWidth(
                        measureRange = measureRange,
                        startIndex = globalStart + localStart,
                        endIndexExclusive = globalStart + localEndExclusive,
                        fallbackMeasure = { measureText(lineText) }
                    )
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
        findMaxFittingCalls.incrementAndGet()
        var low = (start + 1).coerceAtMost(segment.length)
        var high = segment.length
        var best = start
        while (low <= high) {
            val mid = (low + high) ushr 1
            val width = measureWidth(
                measureRange = measureRange,
                startIndex = globalStart + start,
                endIndexExclusive = globalStart + mid,
                fallbackMeasure = {
                    substringSliceCalls.incrementAndGet()
                    measureText(segment.substring(start, mid))
                }
            )
            if (width <= maxWidth) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return best
    }

    private fun measureWidth(
        measureRange: ((startIndex: Int, endIndexExclusive: Int) -> Int)?,
        startIndex: Int,
        endIndexExclusive: Int,
        fallbackMeasure: () -> Int
    ): Int {
        return if (measureRange != null) {
            rangeMeasureCalls.incrementAndGet()
            measureRange.invoke(startIndex, endIndexExclusive)
        } else {
            plainMeasureCalls.incrementAndGet()
            fallbackMeasure()
        }
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
