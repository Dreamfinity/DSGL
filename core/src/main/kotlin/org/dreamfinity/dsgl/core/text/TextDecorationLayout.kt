package org.dreamfinity.dsgl.core.text

import kotlin.math.roundToInt

enum class DecorationType {
    Underline,
    Strikethrough
}

data class TextVisualLine(
    val lineIndex: Int,
    val lineTopY: Float,
    val baselineY: Float,
    val lineHeightPx: Float,
    val glyphStartIndex: Int,
    val glyphEndIndexExclusive: Int
)

data class GlyphDecorationSample(
    val lineIndex: Int,
    val glyphIndex: Int,
    val xStart: Float,
    val xEnd: Float,
    val color: Int,
    val underline: Boolean,
    val strikethrough: Boolean
)

data class DecorationFontMetrics(
    val emSize: Float,
    val lineHeightEm: Float,
    val ascenderEm: Float,
    val descenderEm: Float,
    val underlineYEm: Float?,
    val underlineThicknessEm: Float?
)

data class ResolvedLineDecorationMetrics(
    val underlineY: Float,
    val underlineThickness: Float,
    val strikethroughY: Float,
    val strikethroughThickness: Float
)

data class DecorationQuad(
    val type: DecorationType,
    val lineIndex: Int,
    val xStart: Float,
    val xEnd: Float,
    val y: Float,
    val thickness: Float,
    val color: Int
)

object TextDecorationLayout {
    fun scalePx(fontPx: Int, emSize: Float): Float {
        val safeEm = if (emSize > 0f) emSize else 1f
        return fontPx.coerceAtLeast(1) / safeEm
    }

    fun baselineY(lineTopY: Float, ascenderEm: Float, scalePx: Float): Float {
        return lineTopY + ascenderEm * scalePx
    }

    fun screenYFromYUpMetric(baselineY: Float, metricYUpEm: Float, scalePx: Float): Float {
        return baselineY - metricYUpEm * scalePx
    }

    fun resolveLineMetrics(
        line: TextVisualLine,
        fontMetrics: DecorationFontMetrics,
        fontPx: Int
    ): ResolvedLineDecorationMetrics {
        val lineHeight = line.lineHeightPx.coerceAtLeast(1f)
        val scale = scalePx(fontPx, fontMetrics.emSize)

        val rawUnderlineThickness = (fontMetrics.underlineThicknessEm ?: 0f) * scale
        val fallbackThickness = maxOf(1f, (0.06f * lineHeight).roundToInt().toFloat())
        val underlineThickness = when {
            rawUnderlineThickness < 0.5f -> fallbackThickness
            else -> maxOf(1f, rawUnderlineThickness.roundToInt().toFloat())
        }

        val fallbackUnderlineY = line.baselineY + 0.08f * lineHeight
        val metaUnderlineY = fontMetrics.underlineYEm?.let { metricY ->
            screenYFromYUpMetric(line.baselineY, metricY, scale)
        }
        val underlineY = when {
            metaUnderlineY == null -> fallbackUnderlineY
            metaUnderlineY <= line.baselineY + 0.5f -> fallbackUnderlineY
            else -> metaUnderlineY
        }.roundToInt().toFloat()

        val strikeY = (line.baselineY - 0.30f * lineHeight).roundToInt().toFloat()
        val strikeThickness = maxOf(1f, (0.06f * lineHeight).roundToInt().toFloat())

        val lineBottom = line.lineTopY + lineHeight
        val underlineMaxY = (lineBottom - underlineThickness).coerceAtLeast(line.lineTopY)
        val strikeMaxY = (lineBottom - strikeThickness).coerceAtLeast(line.lineTopY)

        return ResolvedLineDecorationMetrics(
            underlineY = underlineY.coerceIn(line.lineTopY, underlineMaxY),
            underlineThickness = underlineThickness,
            strikethroughY = strikeY.coerceIn(line.lineTopY, strikeMaxY),
            strikethroughThickness = strikeThickness
        )
    }

    fun buildDecorationQuads(
        lines: List<TextVisualLine>,
        glyphs: List<GlyphDecorationSample>,
        fontMetrics: DecorationFontMetrics,
        fontPx: Int
    ): List<DecorationQuad> {
        if (lines.isEmpty() || glyphs.isEmpty()) return emptyList()
        val lineByIndex = lines.associateBy { it.lineIndex }
        val lineMetricsByIndex = lines.associate { line ->
            line.lineIndex to resolveLineMetrics(
                line = line,
                fontMetrics = fontMetrics,
                fontPx = fontPx
            )
        }
        val out = ArrayList<DecorationQuad>(glyphs.size)
        glyphs.sortedWith(compareBy<GlyphDecorationSample> { it.lineIndex }.thenBy { it.glyphIndex }).forEach { glyph ->
            if (glyph.xEnd <= glyph.xStart) return@forEach
            val line = lineByIndex[glyph.lineIndex] ?: return@forEach
            if (glyph.glyphIndex < line.glyphStartIndex || glyph.glyphIndex >= line.glyphEndIndexExclusive) {
                return@forEach
            }
            val metrics = lineMetricsByIndex[glyph.lineIndex] ?: return@forEach
            if (glyph.underline) {
                appendMerged(
                    out = out,
                    quad = DecorationQuad(
                        type = DecorationType.Underline,
                        lineIndex = glyph.lineIndex,
                        xStart = glyph.xStart,
                        xEnd = glyph.xEnd,
                        y = metrics.underlineY,
                        thickness = metrics.underlineThickness,
                        color = glyph.color
                    )
                )
            }
            if (glyph.strikethrough) {
                appendMerged(
                    out = out,
                    quad = DecorationQuad(
                        type = DecorationType.Strikethrough,
                        lineIndex = glyph.lineIndex,
                        xStart = glyph.xStart,
                        xEnd = glyph.xEnd,
                        y = metrics.strikethroughY,
                        thickness = metrics.strikethroughThickness,
                        color = glyph.color
                    )
                )
            }
        }
        return out
    }

    private fun appendMerged(out: MutableList<DecorationQuad>, quad: DecorationQuad) {
        val last = out.lastOrNull()
        if (last != null &&
            last.type == quad.type &&
            last.lineIndex == quad.lineIndex &&
            kotlin.math.abs(last.xEnd - quad.xStart) <= 0.51f &&
            kotlin.math.abs(last.y - quad.y) <= 0.51f &&
            kotlin.math.abs(last.thickness - quad.thickness) <= 0.1f &&
            last.color == quad.color
        ) {
            out[out.lastIndex] = last.copy(xEnd = quad.xEnd)
            return
        }
        out += quad
    }
}

