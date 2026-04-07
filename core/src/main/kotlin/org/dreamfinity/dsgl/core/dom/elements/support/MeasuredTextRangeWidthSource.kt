package org.dreamfinity.dsgl.core.dom.elements.support

import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.text.ParsedTextSpan
import org.dreamfinity.dsgl.core.text.TextFormattingFlags
import org.dreamfinity.dsgl.core.text.TextStyleFlags

internal class MeasuredTextRangeWidthSource(
    private val plainText: String,
    private val fontId: String?,
    private val fontSizePx: Int,
    private val baseFlags: TextStyleFlags,
    private val spans: List<ParsedTextSpan>,
    private val ctx: UiMeasureContext
) {
    data class SpanWidthKey(
        val start: Int,
        val end: Int,
        val flagsMask: Int
    )

    data class CacheKey(
        val backendFingerprint: Int,
        val fontId: String?,
        val fontSizePx: Int,
        val baseFlagsMask: Int,
        val spanWidthKey: List<SpanWidthKey>
    )

    private val rangeWidthCache: MutableMap<Long, Int> = HashMap()
    val cacheKey: CacheKey = CacheKey(
        backendFingerprint = backendFingerprint(ctx, fontId, fontSizePx),
        fontId = fontId,
        fontSizePx = fontSizePx,
        baseFlagsMask = baseFlags.mask(),
        spanWidthKey = spans.map { span ->
            SpanWidthKey(
                start = span.start,
                end = span.end,
                flagsMask = span.flags.mask()
            )
        }
    )

    fun measureRange(startIndex: Int, endIndexExclusive: Int): Int {
        val safeStart = startIndex.coerceIn(0, plainText.length)
        val safeEnd = endIndexExclusive.coerceIn(safeStart, plainText.length)
        if (safeEnd <= safeStart) return 0
        val key = packRange(safeStart, safeEnd)
        return rangeWidthCache.getOrPut(key) {
            ctx.measureTextRange(
                text = plainText,
                startIndex = safeStart,
                endIndexExclusive = safeEnd,
                fontId = fontId,
                fontSize = fontSizePx
            )
        }
    }

    private fun packRange(start: Int, endExclusive: Int): Long {
        return (start.toLong() shl 32) or (endExclusive.toLong() and 0xFFFF_FFFFL)
    }

    private fun TextStyleFlags.mask(): Int {
        var mask = 0
        if (bold) mask = mask or 1
        if (italic) mask = mask or (1 shl 1)
        if (underline) mask = mask or (1 shl 2)
        if (strikethrough) mask = mask or (1 shl 3)
        if (obfuscated) mask = mask or (1 shl 4)
        return mask
    }

    private fun TextFormattingFlags.mask(): Int {
        var mask = 0
        if (bold) mask = mask or 1
        if (italic) mask = mask or (1 shl 1)
        if (underline) mask = mask or (1 shl 2)
        if (strikethrough) mask = mask or (1 shl 3)
        if (obfuscated) mask = mask or (1 shl 4)
        return mask
    }

    companion object {
        private fun backendFingerprint(ctx: UiMeasureContext, fontId: String?, fontSizePx: Int): Int {
            val mWidth = ctx.measureText("M", fontId, fontSizePx)
            val iWidth = ctx.measureText("i", fontId, fontSizePx)
            return (mWidth * 31) + iWidth
        }
    }
}
