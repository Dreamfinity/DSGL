package org.dreamfinity.dsgl.core.font

import org.dreamfinity.dsgl.core.dom.elements.support.TextLayoutEngine
import org.dreamfinity.dsgl.core.style.TextWrap
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MsdfFontTests {
    @Test
    fun `metadata parsing resolves known glyph`() {
        val raw = loadResource("fonts/ubuntu/Ubuntu-Regular-meta.json")
        val meta = MsdfFontMetaParser.parse(raw)
        val glyph = meta.glyphOrFallback('A'.code)
        assertNotNull(glyph, "Glyph for 'A' should be present in Ubuntu regular atlas metadata.")
        assertTrue(glyph.drawable, "Glyph for 'A' should include both plane and atlas bounds.")
        assertTrue(meta.atlas.width > 0 && meta.atlas.height > 0, "Atlas dimensions must be positive.")
    }

    @Test
    fun `measurement is stable and missing glyph does not throw`() {
        val raw = loadResource("fonts/ubuntu/Ubuntu-Regular-meta.json")
        val meta = MsdfFontMetaParser.parse(raw)

        val widthA = meta.measureTextWidth("Hello MSDF", 14)
        val widthB = meta.measureTextWidth("Hello MSDF", 14)
        assertEquals(widthA, widthB, "Width must be deterministic for identical input.")
        assertTrue(widthA > 0, "Measured width should be positive for non-empty text.")

        val missingRun = runCatching {
            meta.measureTextWidth("missing: \uD83E\uDDEA\uD83E\uDD84", 14)
        }
        assertTrue(missingRun.isSuccess, "Missing glyphs must never throw during measurement.")
        assertTrue((missingRun.getOrNull() ?: 0) >= 0)
    }

    @Test
    fun `wrapping respects max width and total height`() {
        val raw = loadResource("fonts/minecraft/MinecraftDefault-Regular-meta.json")
        val meta = MsdfFontMetaParser.parse(raw)
        val fontSize = 12
        val maxWidth = 128
        val lineHeight = meta.lineHeightPx(fontSize)
        val text = "MSDF wrapping should keep lines within container width and avoid overlap for long text runs."

        val layout = TextLayoutEngine.layout(
            text = text,
            maxWidth = maxWidth,
            wrap = TextWrap.Wrap,
            fontHeight = lineHeight,
            measureText = { value -> meta.measureTextWidth(value, fontSize) }
        )

        assertTrue(layout.lines.isNotEmpty())
        assertTrue(layout.lines.all { it.width <= maxWidth + 1 }, "Wrapped lines must fit max width (rounding tolerance 1px).")
        assertEquals(layout.lines.size * layout.lineHeight, layout.totalHeight)
    }

    @Test
    fun `default fonts are discoverable from registry`() {
        val minecraft = FontRegistry.get(FontRegistry.FONT_MINECRAFT)
        val ubuntu = FontRegistry.get(FontRegistry.FONT_UBUNTU)
        assertNotNull(minecraft, "Default minecraft font must be registered.")
        assertNotNull(ubuntu, "Default ubuntu font must be registered.")
    }

    @Test
    fun `parser ignores unknown keys and preserves known glyph`() {
        val raw = """
            {
              "atlas": {
                "type": "mtsdf",
                "distanceRange": 4,
                "size": 32,
                "width": 128,
                "height": 128,
                "yOrigin": "bottom",
                "unknownAtlasField": "ignored"
              },
              "metrics": {
                "emSize": 1,
                "lineHeight": 1.2,
                "ascender": 0.9,
                "descender": -0.2,
                "extraMetricsField": true
              },
              "glyphs": [
                {
                  "index": 65,
                  "advance": 0.6,
                  "planeBounds": { "left": 0, "bottom": 0, "right": 0.5, "top": 0.8, "ignored": 1 },
                  "atlasBounds": { "left": 1, "bottom": 2, "right": 10, "top": 20 }
                }
              ],
              "kerning": [],
              "unknownTopLevel": { "nested": 1 }
            }
        """.trimIndent()

        val meta = MsdfFontMetaParser.parse(raw)
        val glyph = meta.glyphOrFallback(65)
        assertNotNull(glyph)
        assertEquals(65, glyph.codepoint)
        assertTrue(meta.atlas.width == 128 && meta.atlas.height == 128)
    }

    @Test
    fun `parser handles missing optional fields`() {
        val raw = """
            {
              "atlas": {
                "type": "mtsdf",
                "distanceRange": 4,
                "size": 32,
                "width": 64,
                "height": 64,
                "yOrigin": "bottom"
              },
              "metrics": {
                "emSize": 1,
                "lineHeight": 1.1,
                "ascender": 0.8,
                "descender": -0.2
              },
              "glyphs": [
                { "index": 63, "advance": 0.5 }
              ]
            }
        """.trimIndent()

        val meta = MsdfFontMetaParser.parse(raw)
        val glyph = meta.glyphOrFallback(63)
        assertNotNull(glyph)
        assertTrue(!glyph.drawable, "Glyph without bounds should parse and remain non-drawable.")
        assertEquals(0f, meta.kerning(63, 63))
    }

    @Test
    fun `parser resolves codepoint from unicode and char fields`() {
        val raw = """
            {
              "atlas": { "type": "mtsdf", "distanceRange": 4, "size": 32, "width": 64, "height": 64, "yOrigin": "bottom" },
              "metrics": { "emSize": 1, "lineHeight": 1.1, "ascender": 0.8, "descender": -0.2 },
              "glyphs": [
                { "unicode": 65, "advance": 0.6 },
                { "char": "\uD83D\uDE00", "advance": 0.7 }
              ]
            }
        """.trimIndent()
        val meta = MsdfFontMetaParser.parse(raw)
        assertNotNull(meta.glyph(65))
        assertNotNull(meta.glyph(0x1F600))
    }

    @Test
    fun `codepoint extraction handles ascii cyrillic and surrogate pairs`() {
        assertEquals(listOf(72, 101, 108, 108, 111), "Hello".toCodepointList())
        assertEquals(
            listOf(0x041F, 0x0440, 0x0438, 0x0432, 0x0435, 0x0442),
            "Привет".toCodepointList()
        )
        assertEquals(listOf(0x1F600), "\uD83D\uDE00".toCodepointList())
    }

    @Test
    fun `missing codepoint falls back deterministically`() {
        val raw = loadResource("fonts/ubuntu/Ubuntu-Regular-meta.json")
        val meta = MsdfFontMetaParser.parse(raw)
        val fallbackRun = meta.resolveGlyphRun(0x1F600, 14)
        assertNotNull(fallbackRun)
        assertTrue(fallbackRun.usedFallback)
        val fallback = fallbackRun.glyph
        assertNotNull(fallback)
        assertTrue(
            fallback.codepoint == '?'.code || fallback.codepoint == 0xFFFD || meta.glyph(fallback.codepoint) != null
        )
    }

    @Test
    fun `missing space glyph stays advance-only and never falls back`() {
        val raw = loadResource("fonts/ubuntu/Ubuntu-Regular-meta.json")
        val parsed = MsdfFontMetaParser.parse(raw)
        val meta = parsed.copy(
            glyphsByCodepoint = parsed.glyphsByCodepoint - ' '.code - 0x00A0
        )

        val spaceRun = meta.resolveGlyphRun(' '.code, 14)
        assertNotNull(spaceRun)
        assertFalse(spaceRun.draw)
        assertFalse(spaceRun.usedFallback)
        assertTrue(spaceRun.advancePx > 0f)
    }

    @Test
    fun `space contributes width without fallback drawing`() {
        val raw = loadResource("fonts/ubuntu/Ubuntu-Regular-meta.json")
        val parsed = MsdfFontMetaParser.parse(raw)
        val meta = parsed.copy(
            glyphsByCodepoint = parsed.glyphsByCodepoint - ' '.code - 0x00A0
        )
        val fontSize = 14
        val runA = meta.resolveGlyphRun('A'.code, fontSize)
        val runSpace = meta.resolveGlyphRun(' '.code, fontSize)
        assertNotNull(runA)
        assertNotNull(runSpace)

        val expected = (
            runA.advancePx +
                meta.kerning(runA.kerningCodepoint, runSpace.kerningCodepoint) * fontSize +
                runSpace.advancePx +
                meta.kerning(runSpace.kerningCodepoint, runA.kerningCodepoint) * fontSize +
                runA.advancePx
            ).roundToInt()
        val actual = meta.measureTextWidth("A A", fontSize)
        assertEquals(expected, actual)
        assertFalse(runSpace.draw)
    }

    private fun loadResource(path: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(path)
        assertNotNull(stream, "Missing test resource: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
