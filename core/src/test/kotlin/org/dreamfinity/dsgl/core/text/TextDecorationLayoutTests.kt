package org.dreamfinity.dsgl.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextDecorationLayoutTests {
    @Test
    fun `converts y-up font metrics to y-down screen coordinates`() {
        val fontPx = 16
        val scalePx = TextDecorationLayout.scalePx(fontPx = fontPx, emSize = 1f)
        val baseline = TextDecorationLayout.baselineY(
            lineTopY = 10f,
            ascenderEm = 0.932f,
            scalePx = scalePx
        )
        val underlineY = TextDecorationLayout.screenYFromYUpMetric(
            baselineY = baseline,
            metricYUpEm = -0.162f,
            scalePx = scalePx
        )

        assertEquals(24.912f, baseline, 0.0001f)
        assertTrue(underlineY > baseline)
    }

    @Test
    fun `uses fallback thickness and position for tiny underline metrics`() {
        val line = TextVisualLine(
            lineIndex = 0,
            lineTopY = 0f,
            baselineY = TextDecorationLayout.baselineY(
                lineTopY = 0f,
                ascenderEm = 0.694f,
                scalePx = TextDecorationLayout.scalePx(fontPx = 14, emSize = 1f)
            ),
            lineHeightPx = 11f,
            glyphStartIndex = 0,
            glyphEndIndexExclusive = 1
        )
        val metrics = DecorationFontMetrics(
            emSize = 1f,
            lineHeightEm = 0.746f,
            ascenderEm = 0.694f,
            descenderEm = 0f,
            underlineYEm = -0.0015f,
            underlineThicknessEm = 0.0015f
        )

        val resolved = TextDecorationLayout.resolveLineMetrics(
            line = line,
            fontMetrics = metrics,
            fontPx = 14
        )

        assertTrue(resolved.underlineThickness >= 1f)
        assertTrue(resolved.underlineY > line.baselineY)
    }

    @Test
    fun `splits decoration segments per visual line`() {
        val metrics = DecorationFontMetrics(
            emSize = 1f,
            lineHeightEm = 1f,
            ascenderEm = 0.8f,
            descenderEm = -0.2f,
            underlineYEm = -0.1f,
            underlineThicknessEm = 0.05f
        )
        val lines = listOf(
            TextVisualLine(
                lineIndex = 0,
                lineTopY = 0f,
                baselineY = 12f,
                lineHeightPx = 16f,
                glyphStartIndex = 0,
                glyphEndIndexExclusive = 2
            ),
            TextVisualLine(
                lineIndex = 1,
                lineTopY = 16f,
                baselineY = 28f,
                lineHeightPx = 16f,
                glyphStartIndex = 2,
                glyphEndIndexExclusive = 4
            )
        )
        val glyphs = listOf(
            GlyphDecorationSample(0, 0, 0f, 8f, 0xFFFFFFFF.toInt(), underline = true, strikethrough = false),
            GlyphDecorationSample(0, 1, 8f, 16f, 0xFFFFFFFF.toInt(), underline = true, strikethrough = false),
            GlyphDecorationSample(1, 2, 0f, 7f, 0xFFFFFFFF.toInt(), underline = true, strikethrough = false),
            GlyphDecorationSample(1, 3, 7f, 14f, 0xFFFFFFFF.toInt(), underline = true, strikethrough = false)
        )

        val quads = TextDecorationLayout.buildDecorationQuads(
            lines = lines,
            glyphs = glyphs,
            fontMetrics = metrics,
            fontPx = 16
        ).filter { it.type == DecorationType.Underline }

        assertEquals(2, quads.size)
        assertEquals(0, quads[0].lineIndex)
        assertEquals(1, quads[1].lineIndex)
    }

    @Test
    fun `splits segments when style color changes mid-line`() {
        val metrics = DecorationFontMetrics(
            emSize = 1f,
            lineHeightEm = 1f,
            ascenderEm = 0.8f,
            descenderEm = -0.2f,
            underlineYEm = -0.1f,
            underlineThicknessEm = 0.05f
        )
        val lines = listOf(
            TextVisualLine(
                lineIndex = 0,
                lineTopY = 0f,
                baselineY = 12f,
                lineHeightPx = 16f,
                glyphStartIndex = 0,
                glyphEndIndexExclusive = 3
            )
        )
        val colorA = 0xFFFF0000.toInt()
        val colorB = 0xFF00FF00.toInt()
        val glyphs = listOf(
            GlyphDecorationSample(0, 0, 0f, 6f, colorA, underline = true, strikethrough = false),
            GlyphDecorationSample(0, 1, 6f, 12f, colorB, underline = true, strikethrough = false),
            GlyphDecorationSample(0, 2, 12f, 18f, colorB, underline = true, strikethrough = true)
        )

        val quads = TextDecorationLayout.buildDecorationQuads(
            lines = lines,
            glyphs = glyphs,
            fontMetrics = metrics,
            fontPx = 16
        )
        val underline = quads.filter { it.type == DecorationType.Underline }
        val strike = quads.filter { it.type == DecorationType.Strikethrough }

        assertEquals(2, underline.size)
        assertEquals(colorA, underline[0].color)
        assertEquals(colorB, underline[1].color)
        assertEquals(1, strike.size)
    }
}

