package org.dreamfinity.dsgl.core.font

import kotlin.math.ceil
import kotlin.math.roundToInt

typealias FontId = String

data class MsdfAtlasInfo(
    val type: String,
    val distanceRange: Float,
    val size: Float,
    val width: Int,
    val height: Int,
    val yOrigin: String
)

data class MsdfMetrics(
    val emSize: Float,
    val lineHeight: Float,
    val ascender: Float,
    val descender: Float,
    val underlineY: Float? = null,
    val underlineThickness: Float? = null
)

data class MsdfPlaneBounds(
    val left: Float,
    val bottom: Float,
    val right: Float,
    val top: Float
)

data class MsdfAtlasBounds(
    val left: Float,
    val bottom: Float,
    val right: Float,
    val top: Float
)

data class MsdfGlyph(
    val codepoint: Int,
    val advance: Float,
    val planeBounds: MsdfPlaneBounds?,
    val atlasBounds: MsdfAtlasBounds?
) {
    val drawable: Boolean
        get() = planeBounds != null && atlasBounds != null
}

data class MsdfKerningPair(
    val leftCodepoint: Int,
    val rightCodepoint: Int,
    val advance: Float
)

data class MsdfGlyphRunItem(
    val sourceCodepoint: Int,
    val kerningCodepoint: Int,
    val glyph: MsdfGlyph?,
    val advancePx: Float,
    val draw: Boolean,
    val usedFallback: Boolean
)

data class MsdfFontMeta(
    val atlas: MsdfAtlasInfo,
    val metrics: MsdfMetrics,
    val glyphsByCodepoint: Map<Int, MsdfGlyph>,
    val kerningPairs: Map<Long, Float>,
    val replacementCodepoint: Int?
) {
    private val tabWidthInSpaces: Int = 4

    fun glyph(codepoint: Int): MsdfGlyph? = glyphsByCodepoint[codepoint]

    fun fallbackGlyph(): MsdfGlyph? {
        val replacement = replacementCodepoint?.let(glyphsByCodepoint::get)
        if (replacement != null) return replacement
        return glyphsByCodepoint.values.firstOrNull()
    }

    fun glyphOrFallback(codepoint: Int): MsdfGlyph? {
        return glyph(codepoint) ?: fallbackGlyph()
    }

    fun resolveGlyphRun(codepoint: Int, fontSizePx: Int): MsdfGlyphRunItem? {
        val size = fontSizePx.coerceAtLeast(1)
        return when (codepoint) {
            ' '.code, 0x00A0 -> {
                MsdfGlyphRunItem(
                    sourceCodepoint = codepoint,
                    kerningCodepoint = codepoint,
                    glyph = glyph(codepoint),
                    advancePx = spaceAdvancePx(size),
                    draw = false,
                    usedFallback = false
                )
            }

            '\t'.code -> {
                MsdfGlyphRunItem(
                    sourceCodepoint = codepoint,
                    kerningCodepoint = codepoint,
                    glyph = null,
                    advancePx = spaceAdvancePx(size) * tabWidthInSpaces,
                    draw = false,
                    usedFallback = false
                )
            }

            else -> {
                val direct = glyph(codepoint)
                if (direct != null) {
                    return MsdfGlyphRunItem(
                        sourceCodepoint = codepoint,
                        kerningCodepoint = direct.codepoint,
                        glyph = direct,
                        advancePx = advancePx(direct, size),
                        draw = direct.drawable,
                        usedFallback = false
                    )
                }
                val fallback = fallbackGlyph() ?: return null
                MsdfGlyphRunItem(
                    sourceCodepoint = codepoint,
                    kerningCodepoint = fallback.codepoint,
                    glyph = fallback,
                    advancePx = advancePx(fallback, size),
                    draw = fallback.drawable,
                    usedFallback = true
                )
            }
        }
    }

    fun kerning(leftCodepoint: Int, rightCodepoint: Int): Float {
        val key = kerningKey(leftCodepoint, rightCodepoint)
        return kerningPairs[key] ?: 0f
    }

    fun lineHeightPx(fontSizePx: Int): Int {
        val scale = scalePx(fontSizePx)
        return ceil(metrics.lineHeight * scale).toInt().coerceAtLeast(1)
    }

    fun advancePx(glyph: MsdfGlyph, fontSizePx: Int): Float {
        return glyph.advance * scalePx(fontSizePx)
    }

    fun kerningPx(leftCodepoint: Int, rightCodepoint: Int, fontSizePx: Int): Float {
        return kerning(leftCodepoint, rightCodepoint) * scalePx(fontSizePx)
    }

    fun measureTextWidth(text: String, fontSizePx: Int): Int {
        if (text.isEmpty()) return 0
        val size = fontSizePx.coerceAtLeast(1)
        var total = 0f
        var previousCodepoint: Int? = null
        forEachCodepoint(text) { cp ->
            if (cp == '\n'.code || cp == '\r'.code) {
                previousCodepoint = null
                return@forEachCodepoint
            }

            val runItem = resolveGlyphRun(cp, size)
            if (runItem == null) {
                previousCodepoint = null
                return@forEachCodepoint
            }
            val previous = previousCodepoint
            if (previous != null) {
                total += kerningPx(previous, runItem.kerningCodepoint, size)
            }
            total += runItem.advancePx
            previousCodepoint = runItem.kerningCodepoint
        }
        return total.roundToInt().coerceAtLeast(0)
    }

    private fun spaceAdvancePx(fontSizePx: Int): Float {
        val size = fontSizePx.coerceAtLeast(1)
        val explicitSpaceGlyph = glyph(' '.code) ?: glyph(0x00A0)
        if (explicitSpaceGlyph != null) {
            return advancePx(explicitSpaceGlyph, size).coerceAtLeast(0f)
        }

        val proxyN = glyph('n'.code) ?: glyph('N'.code)
        if (proxyN != null) {
            return (advancePx(proxyN, size) * 0.5f).coerceAtLeast(0f)
        }

        val proxyZero = glyph('0'.code)
        if (proxyZero != null) {
            return (advancePx(proxyZero, size) * 0.5f).coerceAtLeast(0f)
        }

        return (lineHeightPx(size) * 0.25f).coerceAtLeast(1f)
    }

    private fun scalePx(fontSizePx: Int): Float {
        val safeEm = if (metrics.emSize > 0f) metrics.emSize else 1f
        return fontSizePx.coerceAtLeast(1) / safeEm
    }

    companion object {
        fun kerningKey(leftCodepoint: Int, rightCodepoint: Int): Long {
            return (leftCodepoint.toLong() shl 32) or (rightCodepoint.toLong() and 0xFFFF_FFFFL)
        }
    }
}
