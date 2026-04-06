package org.dreamfinity.dsgl.core.font

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal class AwtTextShaper(
    private val catalog: FontCatalog,
    private val metrics: FontMetricsService
) : TextShaper {
    private companion object {
        const val GLYPH_INDEX_NULL_SENTINEL: Int = Int.MIN_VALUE
        const val MAX_SHAPE_CACHE_ENTRIES: Int = 1024
    }

    private data class ShapeCacheKey(
        val primaryFontId: FontId,
        val fallbackFontId: FontId?,
        val fontPx: Int,
        val text: String,
        val rangeStart: Int,
        val rangeEnd: Int,
        val directionFlags: Int,
        val formattingMode: String
    )

    private data class FontCodepointKey(
        val fontId: FontId,
        val codepoint: Int
    )

    private data class MutableShapingSegment(
        val font: LoadedMsdfFont,
        val text: StringBuilder = StringBuilder(),
        val sourceStartByChar: MutableList<Int> = ArrayList(),
        val sourceEndByChar: MutableList<Int> = ArrayList(),
        var charStart: Int = Int.MAX_VALUE,
        var charEnd: Int = 0
    )

    private val shapeCache: MutableMap<ShapeCacheKey, ShapedText> =
        object : LinkedHashMap<ShapeCacheKey, ShapedText>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ShapeCacheKey, ShapedText>?): Boolean {
                return size > MAX_SHAPE_CACHE_ENTRIES
            }
        }
    private val derivedFontCache: MutableMap<String, Font> = ConcurrentHashMap()
    private val fontRenderContext: FontRenderContext = FontRenderContext(AffineTransform(), true, true)
    private val shapeCacheRequests = AtomicLong(0L)
    private val shapeCacheHits = AtomicLong(0L)
    private val shapeCacheMisses = AtomicLong(0L)
    private val canDisplayByFontCodepoint: MutableMap<FontCodepointKey, Boolean> = ConcurrentHashMap()
    private val glyphIndexByFontCodepoint: MutableMap<FontCodepointKey, Int> = ConcurrentHashMap()
    private val requiresReplacementGlyphByFontCodepoint: MutableMap<FontCodepointKey, Boolean> = ConcurrentHashMap()
    private val shapeTextRangeCalls = AtomicLong(0L)
    private val shapeSegmentCalls = AtomicLong(0L)
    private val requiresReplacementGlyphCalls = AtomicLong(0L)
    private val requiresReplacementGlyphCacheHits = AtomicLong(0L)
    private val requiresReplacementGlyphCacheMisses = AtomicLong(0L)
    private val requiresReplacementGlyphEvaluations = AtomicLong(0L)
    private val canDisplayCalls = AtomicLong(0L)
    private val canDisplayCacheHits = AtomicLong(0L)
    private val canDisplayCacheMisses = AtomicLong(0L)
    private val canDisplayAwtCalls = AtomicLong(0L)
    private val glyphIndexForCodepointCalls = AtomicLong(0L)
    private val glyphIndexCacheHits = AtomicLong(0L)
    private val glyphIndexCacheMisses = AtomicLong(0L)
    private val glyphIndexVectorBuildCalls = AtomicLong(0L)

    override fun shapeText(
        text: String,
        fontId: FontId?,
        fontSize: Int?,
        formattingMode: String
    ): ShapedText {
        return shapeTextRange(
            text = text,
            startIndex = 0,
            endIndexExclusive = text.length,
            fontId = fontId,
            fontSize = fontSize,
            formattingMode = formattingMode
        )
    }

    override fun shapeTextRange(
        text: String,
        startIndex: Int,
        endIndexExclusive: Int,
        fontId: FontId?,
        fontSize: Int?,
        formattingMode: String
    ): ShapedText {
        shapeTextRangeCalls.incrementAndGet()
        val (safeStart, safeEnd) = normalizeRange(text, startIndex, endIndexExclusive)
        if (safeEnd <= safeStart) {
            return ShapedText(glyphs = emptyList(), runs = emptyList(), width = 0f)
        }
        val fontPx = metrics.resolveFontSize(fontSize)
        val primary = catalog.get(fontId) ?: return ShapedText(
            glyphs = emptyList(),
            runs = emptyList(),
            width = fallbackMeasureText(text, fontSize).toFloat()
        )
        val fallback = catalog.getExact(FontRegistry.FALLBACK_FONT_ID)
            ?.takeIf { it.descriptor.fontId != primary.descriptor.fontId && it.awtBaseFont != null }
        val cacheKey = ShapeCacheKey(
            primaryFontId = primary.descriptor.fontId,
            fallbackFontId = fallback?.descriptor?.fontId,
            fontPx = fontPx,
            text = text,
            rangeStart = safeStart,
            rangeEnd = safeEnd,
            directionFlags = Font.LAYOUT_LEFT_TO_RIGHT,
            formattingMode = formattingMode
        )
        synchronized(shapeCache) {
            shapeCacheRequests.incrementAndGet()
            shapeCache[cacheKey]?.let {
                shapeCacheHits.incrementAndGet()
                return it
            }
        }
        shapeCacheMisses.incrementAndGet()
        val shaped = shapeSingleLine(
            text = text,
            startIndex = safeStart,
            endIndexExclusive = safeEnd,
            primary = primary,
            fallback = fallback,
            fontPx = fontPx
        )
        synchronized(shapeCache) {
            shapeCache[cacheKey] = shaped
        }
        return shaped
    }

    override fun measureText(text: String, fontId: FontId?, fontSize: Int?): Int {
        if (text.isEmpty()) return 0
        if (text.contains('\n')) {
            return text.lineSequence().maxOfOrNull { line -> measureText(line, fontId, fontSize) } ?: 0
        }
        return shapeText(text, fontId, fontSize).width.toInt().coerceAtLeast(0)
    }

    override fun measureTextRange(
        text: String,
        startIndex: Int,
        endIndexExclusive: Int,
        fontId: FontId?,
        fontSize: Int?
    ): Int {
        return shapeTextRange(text, startIndex, endIndexExclusive, fontId, fontSize).width.toInt().coerceAtLeast(0)
    }

    override fun clearCaches() {
        synchronized(shapeCache) {
            shapeCache.clear()
        }
        canDisplayByFontCodepoint.clear()
        glyphIndexByFontCodepoint.clear()
        requiresReplacementGlyphByFontCodepoint.clear()
        derivedFontCache.clear()
        resetShapeCacheStats()
        resetTextHotPathStats()
    }

    override fun resetShapeCacheStats() {
        shapeCacheRequests.set(0L)
        shapeCacheHits.set(0L)
        shapeCacheMisses.set(0L)
    }

    override fun shapeCacheStats(): FontRegistry.ShapeCacheStats {
        val entries = synchronized(shapeCache) { shapeCache.size }
        return FontRegistry.ShapeCacheStats(
            requests = shapeCacheRequests.get(),
            hits = shapeCacheHits.get(),
            misses = shapeCacheMisses.get(),
            entries = entries,
            maxEntries = MAX_SHAPE_CACHE_ENTRIES
        )
    }

    override fun resetTextHotPathStats() {
        shapeTextRangeCalls.set(0L)
        shapeSegmentCalls.set(0L)
        requiresReplacementGlyphCalls.set(0L)
        requiresReplacementGlyphCacheHits.set(0L)
        requiresReplacementGlyphCacheMisses.set(0L)
        requiresReplacementGlyphEvaluations.set(0L)
        canDisplayCalls.set(0L)
        canDisplayCacheHits.set(0L)
        canDisplayCacheMisses.set(0L)
        canDisplayAwtCalls.set(0L)
        glyphIndexForCodepointCalls.set(0L)
        glyphIndexCacheHits.set(0L)
        glyphIndexCacheMisses.set(0L)
        glyphIndexVectorBuildCalls.set(0L)
    }

    override fun textHotPathStats(): FontRegistry.TextHotPathStats {
        return FontRegistry.TextHotPathStats(
            shapeTextRangeCalls = shapeTextRangeCalls.get(),
            shapeSegmentCalls = shapeSegmentCalls.get(),
            requiresReplacementGlyphCalls = requiresReplacementGlyphCalls.get(),
            requiresReplacementGlyphCacheHits = requiresReplacementGlyphCacheHits.get(),
            requiresReplacementGlyphCacheMisses = requiresReplacementGlyphCacheMisses.get(),
            requiresReplacementGlyphEvaluations = requiresReplacementGlyphEvaluations.get(),
            canDisplayCalls = canDisplayCalls.get(),
            canDisplayCacheHits = canDisplayCacheHits.get(),
            canDisplayCacheMisses = canDisplayCacheMisses.get(),
            canDisplayAwtCalls = canDisplayAwtCalls.get(),
            glyphIndexForCodepointCalls = glyphIndexForCodepointCalls.get(),
            glyphIndexCacheHits = glyphIndexCacheHits.get(),
            glyphIndexCacheMisses = glyphIndexCacheMisses.get(),
            glyphIndexVectorBuildCalls = glyphIndexVectorBuildCalls.get()
        )
    }

    private fun fallbackMeasureText(text: String, fontSize: Int?): Int {
        val px = metrics.resolveFontSize(fontSize)
        return (text.codePointCount(0, text.length) * (px * 0.6f)).toInt().coerceAtLeast(0)
    }

    private fun shapeSingleLine(
        text: String,
        startIndex: Int,
        endIndexExclusive: Int,
        primary: LoadedMsdfFont,
        fallback: LoadedMsdfFont?,
        fontPx: Int
    ): ShapedText {
        val segments = buildShapingSegments(
            text = text,
            startIndex = startIndex,
            endIndexExclusive = endIndexExclusive,
            primary = primary,
            fallback = fallback
        )
        if (segments.isEmpty()) {
            return ShapedText(glyphs = emptyList(), runs = emptyList(), width = 0f)
        }
        val allGlyphs = ArrayList<ShapedGlyph>((endIndexExclusive - startIndex).coerceAtLeast(8))
        val runs = ArrayList<ShapedTextRun>(segments.size)
        var penX = 0f

        segments.forEach { segment ->
            val shapedRun = shapeSegment(
                sourceText = text,
                sourceRangeStart = startIndex,
                segment = segment,
                fontPx = fontPx,
                penX = penX
            )
            allGlyphs += shapedRun.glyphs
            runs += shapedRun
            penX += shapedRun.advance
        }
        return ShapedText(
            glyphs = allGlyphs,
            runs = runs,
            width = penX.coerceAtLeast(0f)
        )
    }

    private fun buildShapingSegments(
        text: String,
        startIndex: Int,
        endIndexExclusive: Int,
        primary: LoadedMsdfFont,
        fallback: LoadedMsdfFont?
    ): List<MutableShapingSegment> {
        val out = ArrayList<MutableShapingSegment>(4)
        var segment: MutableShapingSegment? = null
        var index = startIndex
        while (index < endIndexExclusive) {
            val start = index
            val codepoint = Character.codePointAt(text, index)
            index += Character.charCount(codepoint)
            val end = index
            val localStart = start - startIndex
            val localEnd = end - startIndex

            val primaryNeedsReplacement = requiresReplacementGlyph(primary, codepoint)
            val fallbackNeedsReplacement = fallback?.let { requiresReplacementGlyph(it, codepoint) } ?: true
            val selectedFont = when {
                !primaryNeedsReplacement -> primary
                fallback != null && !fallbackNeedsReplacement -> fallback
                fallback != null -> fallback
                else -> primary
            }
            val replacementNeeded = when (selectedFont.descriptor.fontId) {
                primary.descriptor.fontId -> primaryNeedsReplacement
                else -> fallbackNeedsReplacement
            }

            if (segment == null || segment.font.descriptor.fontId != selectedFont.descriptor.fontId) {
                segment = MutableShapingSegment(font = selectedFont)
                out += segment
            }
            if (localStart < segment.charStart) segment.charStart = localStart
            if (localEnd > segment.charEnd) segment.charEnd = localEnd
            if (replacementNeeded) {
                segment.text.append('\uFFFD')
                segment.sourceStartByChar += localStart
                segment.sourceEndByChar += localEnd
            } else {
                segment.text.appendCodePoint(codepoint)
                val charCount = Character.charCount(codepoint)
                repeat(charCount) {
                    segment.sourceStartByChar += localStart
                    segment.sourceEndByChar += localEnd
                }
            }
        }
        return out
    }

    private fun shapeSegment(
        sourceText: String,
        sourceRangeStart: Int,
        segment: MutableShapingSegment,
        fontPx: Int,
        penX: Float
    ): ShapedTextRun {
        shapeSegmentCalls.incrementAndGet()
        val font = deriveAwtFont(segment.font, fontPx)
        if (font == null || segment.text.isEmpty()) {
            return ShapedTextRun(
                fontId = segment.font.descriptor.fontId,
                charStart = if (segment.charStart == Int.MAX_VALUE) 0 else segment.charStart,
                charEnd = segment.charEnd,
                glyphs = emptyList(),
                advance = 0f
            )
        }
        val chars = segment.text.toString().toCharArray()
        val glyphVector = runCatching {
            font.layoutGlyphVector(
                fontRenderContext,
                chars,
                0,
                chars.size,
                Font.LAYOUT_LEFT_TO_RIGHT
            )
        }.getOrElse {
            font.createGlyphVector(fontRenderContext, segment.text.toString())
        }
        val glyphCount = glyphVector.numGlyphs
        val positions = glyphVector.getGlyphPositions(0, glyphCount + 1, null)
        val runGlyphs = ArrayList<ShapedGlyph>(glyphCount)
        for (i in 0 until glyphCount) {
            val charIndex = glyphVector.getGlyphCharIndex(i)
                .coerceIn(0, (segment.sourceStartByChar.size - 1).coerceAtLeast(0))
            val sourceStart = segment.sourceStartByChar.getOrElse(charIndex) { 0 }
            val sourceEnd = segment.sourceEndByChar.getOrElse(charIndex) { sourceStart + 1 }
            val sourceGlobalStart = sourceRangeStart + sourceStart
            val sourceCodepoint = if (charIndex in chars.indices) {
                Character.codePointAt(chars, charIndex, chars.size)
            } else if (sourceGlobalStart in sourceText.indices) {
                Character.codePointAt(sourceText, sourceGlobalStart)
            } else {
                '?'.code
            }
            val x = penX + positions[i * 2]
            val y = positions[i * 2 + 1]
            val advance = positions[(i + 1) * 2] - positions[i * 2]
            runGlyphs += ShapedGlyph(
                fontId = segment.font.descriptor.fontId,
                glyphIndex = glyphVector.getGlyphCode(i),
                x = x,
                y = y,
                advance = advance,
                charStart = sourceStart,
                charEnd = sourceEnd,
                sourceCodepoint = sourceCodepoint
            )
        }
        val runAdvance = positions[glyphCount * 2].coerceAtLeast(0f)
        return ShapedTextRun(
            fontId = segment.font.descriptor.fontId,
            charStart = if (segment.charStart == Int.MAX_VALUE) 0 else segment.charStart,
            charEnd = segment.charEnd,
            glyphs = runGlyphs,
            advance = runAdvance
        )
    }

    private fun deriveAwtFont(font: LoadedMsdfFont, fontPx: Int): Font? {
        val base = font.awtBaseFont ?: return null
        val key = "${font.descriptor.fontId}@${fontPx.coerceAtLeast(1)}"
        return derivedFontCache.getOrPut(key) {
            base.deriveFont(fontPx.coerceAtLeast(1).toFloat())
        }
    }

    private fun canDisplay(font: LoadedMsdfFont, codepoint: Int): Boolean {
        canDisplayCalls.incrementAndGet()
        val key = FontCodepointKey(font.descriptor.fontId, codepoint)
        canDisplayByFontCodepoint[key]?.let { cached ->
            canDisplayCacheHits.incrementAndGet()
            return cached
        }
        canDisplayCacheMisses.incrementAndGet()
        val awt = font.awtBaseFont ?: return false
        canDisplayAwtCalls.incrementAndGet()
        val resolved = runCatching { awt.canDisplay(codepoint) }.getOrDefault(false)
        canDisplayByFontCodepoint[key] = resolved
        return resolved
    }

    private fun requiresReplacementGlyph(font: LoadedMsdfFont, codepoint: Int): Boolean {
        requiresReplacementGlyphCalls.incrementAndGet()
        val key = FontCodepointKey(font.descriptor.fontId, codepoint)
        requiresReplacementGlyphByFontCodepoint[key]?.let { cached ->
            requiresReplacementGlyphCacheHits.incrementAndGet()
            return cached
        }
        requiresReplacementGlyphCacheMisses.incrementAndGet()
        requiresReplacementGlyphEvaluations.incrementAndGet()
        val resolved = if (!Character.isValidCodePoint(codepoint)) {
            true
        } else if (!canDisplay(font, codepoint)) {
            true
        } else {
            val glyphIndex = glyphIndexForCodepoint(font, codepoint)
            if (glyphIndex == null || glyphIndex < 0) {
                true
            } else {
                val missingIndex = font.preferredMissingGlyphIndex
                if (missingIndex != null && glyphIndex == missingIndex) {
                    true
                } else {
                    glyphIndex == 0 && (missingIndex == null || missingIndex == 0)
                }
            }
        }
        requiresReplacementGlyphByFontCodepoint[key] = resolved
        return resolved
    }

    private fun glyphIndexForCodepoint(font: LoadedMsdfFont, codepoint: Int): Int? {
        glyphIndexForCodepointCalls.incrementAndGet()
        if (!Character.isValidCodePoint(codepoint)) return null
        val key = FontCodepointKey(font.descriptor.fontId, codepoint)
        glyphIndexByFontCodepoint[key]?.let { cached ->
            glyphIndexCacheHits.incrementAndGet()
            return if (cached == GLYPH_INDEX_NULL_SENTINEL) null else cached
        }
        glyphIndexCacheMisses.incrementAndGet()
        glyphIndexVectorBuildCalls.incrementAndGet()
        val resolved = computeGlyphIndexForCodepoint(font.awtBaseFont, codepoint)
        glyphIndexByFontCodepoint[key] = resolved ?: GLYPH_INDEX_NULL_SENTINEL
        return resolved
    }

    private fun computeGlyphIndexForCodepoint(font: Font?, codepoint: Int): Int? {
        if (font == null || !Character.isValidCodePoint(codepoint)) return null
        val text = String(Character.toChars(codepoint))
        return runCatching {
            val vector = font.createGlyphVector(fontRenderContext, text)
            if (vector.numGlyphs <= 0) {
                null
            } else {
                vector.getGlyphCode(0).takeIf { it >= 0 }
            }
        }.getOrNull()
    }

    private fun normalizeRange(text: String, startIndex: Int, endIndexExclusive: Int): Pair<Int, Int> {
        var start = startIndex.coerceIn(0, text.length)
        var end = endIndexExclusive.coerceIn(start, text.length)
        if (start > 0 &&
            start < text.length &&
            Character.isLowSurrogate(text[start]) &&
            Character.isHighSurrogate(text[start - 1])
        ) {
            start -= 1
        }
        if (end > start &&
            end < text.length &&
            Character.isLowSurrogate(text[end]) &&
            Character.isHighSurrogate(text[end - 1])
        ) {
            end -= 1
        }
        return start to end
    }
}
