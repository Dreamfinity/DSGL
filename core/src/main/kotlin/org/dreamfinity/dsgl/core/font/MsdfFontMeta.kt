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
    val glyphIndex: Int,
    val codepoint: Int?,
    val advance: Float,
    val planeBounds: MsdfPlaneBounds?,
    val atlasBounds: MsdfAtlasBounds?
) {
    val drawable: Boolean
        get() = planeBounds != null && atlasBounds != null
}

data class MsdfKerningPair(
    val leftGlyphIndex: Int,
    val rightGlyphIndex: Int,
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
    val glyphsByIndex: Map<Int, MsdfGlyph>,
    val glyphsByCodepoint: Map<Int, MsdfGlyph>,
    val kerningPairsByIndex: Map<Long, Float>,
    val kerningPairsByCodepoint: Map<Long, Float>,
    val replacementGlyphIndex: Int?,
    val replacementCodepoint: Int?
) {
    private val tabWidthInSpaces: Int = 4
    private val denseGlyphsByIndex: Array<MsdfGlyph?> = run {
        val maxIndex = glyphsByIndex.keys.maxOrNull() ?: -1
        if (maxIndex < 0) {
            emptyArray()
        } else {
            arrayOfNulls<MsdfGlyph>(maxIndex + 1).also { dense ->
                glyphsByIndex.forEach { (index, glyph) ->
                    if (index >= 0 && index < dense.size) {
                        dense[index] = glyph
                    }
                }
            }
        }
    }
    private val cachedFallbackGlyph: MsdfGlyph? = run {
        val replacementByIndex = replacementGlyphIndex
            ?.takeIf { it >= 0 && it < denseGlyphsByIndex.size }
            ?.let { denseGlyphsByIndex[it] }
        if (replacementByIndex != null) return@run replacementByIndex
        val replacementByCodepoint = replacementCodepoint?.let(glyphsByCodepoint::get)
        if (replacementByCodepoint != null) return@run replacementByCodepoint
        glyphsByIndex.values.firstOrNull() ?: glyphsByCodepoint.values.firstOrNull()
    }

    fun glyphByIndex(glyphIndex: Int): MsdfGlyph? {
        if (glyphIndex >= 0 && glyphIndex < denseGlyphsByIndex.size) {
            return denseGlyphsByIndex[glyphIndex]
        }
        return glyphsByIndex[glyphIndex]
    }

    fun glyph(codepoint: Int): MsdfGlyph? = glyphsByCodepoint[codepoint]

    fun fallbackGlyph(): MsdfGlyph? {
        return cachedFallbackGlyph
    }

    fun glyphOrFallback(codepoint: Int): MsdfGlyph? {
        return glyph(codepoint) ?: fallbackGlyph()
    }

    fun resolveGlyphRun(codepoint: Int, fontSizePx: Int): MsdfGlyphRunItem? {
        val size = fontSizePx.coerceAtLeast(1)
        return when (codepoint) {
            ' '.code, 0x00A0 -> {
                val explicitSpaceGlyph = glyph(codepoint) ?: glyph(' '.code) ?: glyph(0x00A0)
                MsdfGlyphRunItem(
                    sourceCodepoint = codepoint,
                    kerningCodepoint = explicitSpaceGlyph?.codepoint ?: codepoint,
                    glyph = explicitSpaceGlyph,
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
                        kerningCodepoint = direct.codepoint ?: codepoint,
                        glyph = direct,
                        advancePx = advancePx(direct, size),
                        draw = direct.drawable,
                        usedFallback = false
                    )
                }
                val fallback = fallbackGlyph() ?: return null
                MsdfGlyphRunItem(
                    sourceCodepoint = codepoint,
                    kerningCodepoint = fallback.codepoint ?: codepoint,
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
        val direct = kerningPairsByCodepoint[key]
        if (direct != null) return direct

        val leftGlyph = glyph(leftCodepoint)
        val rightGlyph = glyph(rightCodepoint)
        if (leftGlyph != null && rightGlyph != null) {
            return kerningByIndex(leftGlyph.glyphIndex, rightGlyph.glyphIndex)
        }
        return 0f
    }

    fun kerningByIndex(leftGlyphIndex: Int, rightGlyphIndex: Int): Float {
        val key = kerningKey(leftGlyphIndex, rightGlyphIndex)
        return kerningPairsByIndex[key] ?: 0f
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

    fun kerningPxByIndex(leftGlyphIndex: Int, rightGlyphIndex: Int, fontSizePx: Int): Float {
        return kerningByIndex(leftGlyphIndex, rightGlyphIndex) * scalePx(fontSizePx)
    }

    fun measureTextWidth(text: String, fontSizePx: Int): Int {
        if (text.isEmpty()) return 0
        val size = fontSizePx.coerceAtLeast(1)
        var total = 0f
        var previousKerningCodepoint: Int? = null
        forEachCodepoint(text) loop@{ cp ->
            if (cp == '\n'.code || cp == '\r'.code) {
                previousKerningCodepoint = null
                return@loop
            }

            val runItem = resolveGlyphRun(cp, size)
            if (runItem == null) {
                previousKerningCodepoint = null
                return@loop
            }
            val previous = previousKerningCodepoint
            if (previous != null) {
                total += kerningPx(previous, runItem.kerningCodepoint, size)
            }
            total += runItem.advancePx
            previousKerningCodepoint = runItem.kerningCodepoint
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

        val fallback = fallbackGlyph()
        if (fallback != null) {
            return (advancePx(fallback, size) * 0.5f).coerceAtLeast(1f)
        }

        return (lineHeightPx(size) * 0.25f).coerceAtLeast(1f)
    }

    private fun scalePx(fontSizePx: Int): Float {
        val safeEm = if (metrics.emSize > 0f) metrics.emSize else 1f
        return fontSizePx.coerceAtLeast(1) / safeEm
    }

    companion object {
        fun kerningKey(left: Int, right: Int): Long {
            return (left.toLong() shl 32) or (right.toLong() and 0xFFFF_FFFFL)
        }
    }
}

