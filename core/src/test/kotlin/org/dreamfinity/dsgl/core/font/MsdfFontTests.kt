package org.dreamfinity.dsgl.core.font

import org.dreamfinity.dsgl.core.dom.elements.support.TextLayoutEngine
import org.dreamfinity.dsgl.core.style.TextWrap
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MsdfFontTests {
    @Test
    fun `metadata parsing resolves known glyph by glyph index`() {
        val raw = loadResource("fonts/ubuntu/Ubuntu-Regular-meta.json")
        val meta = MsdfFontMetaParser.parse(raw)
        val glyph = meta.glyphByIndex(65)
        assertNotNull(glyph, "Glyph index 65 should exist in Ubuntu atlas metadata")
        assertTrue(glyph.drawable, "Glyph index 65 should include both plane and atlas bounds")
        assertTrue(meta.atlas.width > 0 && meta.atlas.height > 0, "Atlas dimensions must be positive")
    }

    @Test
    fun `parser ignores unknown keys and preserves glyph fields`() {
        val raw =
            """
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
                  "unicode": 65,
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
        val glyph = meta.glyphByIndex(65)
        assertNotNull(glyph)
        assertEquals(65, glyph.glyphIndex)
        assertEquals(65, glyph.codepoint)
        assertTrue(meta.atlas.width == 128 && meta.atlas.height == 128)
    }

    @Test
    fun `parser handles missing optional fields`() {
        val raw =
            """
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
        val glyph = meta.glyphByIndex(63)
        assertNotNull(glyph)
        assertTrue(!glyph.drawable)
        assertEquals(0f, meta.kerningByIndex(63, 63))
    }

    @Test
    fun `parser resolves codepoint from unicode and char fields`() {
        val raw =
            """
            {
              "atlas": { "type": "mtsdf", "distanceRange": 4, "size": 32, "width": 64, "height": 64, "yOrigin": "bottom" },
              "metrics": { "emSize": 1, "lineHeight": 1.1, "ascender": 0.8, "descender": -0.2 },
              "glyphs": [
                { "index": 65, "unicode": 65, "advance": 0.6 },
                { "index": 66, "char": "\uD83D\uDE00", "advance": 0.7 }
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
            "\u041F\u0440\u0438\u0432\u0435\u0442".toCodepointList(),
        )
        assertEquals(listOf(0x1F600), "\uD83D\uDE00".toCodepointList())
    }

    @Test
    fun `default fonts are discoverable from registry`() {
        val minecraft = FontRegistry.get(FontRegistry.FONT_MINECRAFT)
        val ubuntu = FontRegistry.get(FontRegistry.FONT_UBUNTU)
        val noto = FontRegistry.get(FontRegistry.FALLBACK_FONT_ID)
        assertNotNull(minecraft)
        assertNotNull(ubuntu)
        assertNotNull(noto)
    }

    @Test
    fun `shaper ascii positions are non-decreasing`() {
        val shaped = FontRegistry.shapeText("Hello", FontRegistry.FONT_MINECRAFT, 14)
        assertTrue(shaped.glyphs.isNotEmpty())
        var lastX = Float.NEGATIVE_INFINITY
        shaped.glyphs.forEach { glyph ->
            assertTrue(glyph.x >= lastX - 0.001f)
            lastX = glyph.x
        }
    }

    @Test
    fun `fallback segmentation uses noto sans when primary cannot display codepoint`() {
        val primary = FontRegistry.get(FontRegistry.FONT_MINECRAFT)
        val fallback = FontRegistry.get(FontRegistry.FALLBACK_FONT_ID)
        assertNotNull(primary)
        assertNotNull(fallback)
        val primaryAwt = primary.awtBaseFont
        val fallbackAwt = fallback.awtBaseFont
        assertNotNull(primaryAwt)
        assertNotNull(fallbackAwt)

        val codepoint = findFallbackOnlyCodepoint(primaryAwt, fallbackAwt) ?: return
        val text = "A${String(Character.toChars(codepoint))}B"

        val shaped = FontRegistry.shapeText(text, FontRegistry.FONT_MINECRAFT, 16)
        assertTrue(shaped.runs.size >= 2)
        assertTrue(shaped.runs.any { it.fontId == FontRegistry.FONT_MINECRAFT })
        assertTrue(shaped.runs.any { it.fontId == FontRegistry.FALLBACK_FONT_ID })
    }

    @Test
    fun `missing glyph in both fonts falls back without throwing`() {
        val missing = String(Character.toChars(0x10FFFF))
        val shaped =
            runCatching {
                FontRegistry.shapeText(missing, FontRegistry.FONT_MINECRAFT, 16)
            }
        assertTrue(shaped.isSuccess)
        assertTrue((shaped.getOrNull()?.width ?: -1f) >= 0f)
    }

    @Test
    fun `missing glyph substitution uses replacement codepoint in shaped output`() {
        val missing = String(Character.toChars(0x10FFFF))
        val shaped = FontRegistry.shapeText(missing, FontRegistry.FONT_MINECRAFT, 16)
        assertTrue(shaped.glyphs.isNotEmpty())
        assertTrue(shaped.glyphs.all { it.sourceCodepoint == 0xFFFD })
    }

    @Test
    fun `mixed-font width matches last pen position`() {
        val primary = FontRegistry.get(FontRegistry.FONT_MINECRAFT)
        val fallback = FontRegistry.get(FontRegistry.FALLBACK_FONT_ID)
        assertNotNull(primary)
        assertNotNull(fallback)
        val primaryAwt = primary.awtBaseFont
        val fallbackAwt = fallback.awtBaseFont
        assertNotNull(primaryAwt)
        assertNotNull(fallbackAwt)

        val codepoint = findFallbackOnlyCodepoint(primaryAwt, fallbackAwt) ?: return
        val text = "AB${String(Character.toChars(codepoint))}CD"
        val shaped = FontRegistry.shapeText(text, FontRegistry.FONT_MINECRAFT, 18)

        val lastPen = shaped.glyphs.maxOfOrNull { it.x + it.advance } ?: 0f
        assertTrue(abs(lastPen - shaped.width) <= 0.5f)
    }

    @Test
    fun `shape cache hits for repeated input`() {
        FontRegistry.clearLoadedCache()
        FontRegistry.resetShapeCacheStats()
        FontRegistry.shapeText("Cache me", FontRegistry.FONT_MINECRAFT, 14, formattingMode = "plain")
        FontRegistry.shapeText("Cache me", FontRegistry.FONT_MINECRAFT, 14, formattingMode = "plain")

        val stats = FontRegistry.shapeCacheStats()
        assertTrue(stats.requests >= 2)
        assertTrue(stats.hits >= 1)
        assertTrue(stats.misses >= 1)
    }

    @Test
    fun `shape cache remains bounded`() {
        FontRegistry.clearLoadedCache()
        FontRegistry.resetShapeCacheStats()
        for (i in 0 until 1_100) {
            FontRegistry.shapeText("entry-$i", FontRegistry.FONT_MINECRAFT, 12, formattingMode = "plain")
        }
        val stats = FontRegistry.shapeCacheStats()
        assertTrue(stats.entries <= stats.maxEntries)
        assertEquals(1024, stats.maxEntries)
    }

    @Test
    fun `probing caches preserve fallback segmentation and shaped output semantics`() {
        val primary = FontRegistry.get(FontRegistry.FONT_MINECRAFT)
        val fallback = FontRegistry.get(FontRegistry.FALLBACK_FONT_ID)
        assertNotNull(primary)
        assertNotNull(fallback)
        val primaryAwt = primary.awtBaseFont
        val fallbackAwt = fallback.awtBaseFont
        assertNotNull(primaryAwt)
        assertNotNull(fallbackAwt)
        val codepoint = findFallbackOnlyCodepoint(primaryAwt, fallbackAwt) ?: return
        val mixed = "A${String(Character.toChars(codepoint))}B${String(Character.toChars(0x10FFFF))}C"

        FontRegistry.clearLoadedCache()
        FontRegistry.resetShapeCacheStats()
        FontRegistry.resetTextHotPathStats()
        val cold =
            FontRegistry.shapeText(
                mixed,
                FontRegistry.FONT_MINECRAFT,
                16,
                formattingMode = "probe-semantic-cold",
            )
        val coldStats = FontRegistry.textHotPathStats()
        assertTrue(coldStats.requiresReplacementGlyphEvaluations > 0)

        FontRegistry.resetTextHotPathStats()
        val warm =
            FontRegistry.shapeText(
                mixed,
                FontRegistry.FONT_MINECRAFT,
                16,
                formattingMode = "probe-semantic-warm",
            )
        val warmStats = FontRegistry.textHotPathStats()
        assertTrue(warmStats.requiresReplacementGlyphCacheHits > 0)

        assertEquals(cold.runs.map { it.fontId }, warm.runs.map { it.fontId })
        assertEquals(cold.glyphs.map { it.fontId to it.glyphIndex }, warm.glyphs.map { it.fontId to it.glyphIndex })
        assertEquals(cold.glyphs.map { it.sourceCodepoint }, warm.glyphs.map { it.sourceCodepoint })
        assertTrue(abs(cold.width - warm.width) <= 0.01f)
    }

    @Test
    fun `wrapping respects max width and total height`() {
        val maxWidth = 128
        val fontSize = 14
        val lineHeight = FontRegistry.lineHeight(FontRegistry.FONT_MINECRAFT, fontSize)
        val text = "MSDF wrapping should keep lines within container width and avoid overlap for long text runs."

        val layout =
            TextLayoutEngine.layout(
                text = text,
                maxWidth = maxWidth,
                wrap = TextWrap.Wrap,
                fontHeight = lineHeight,
                measureText = { value -> FontRegistry.measureText(value, FontRegistry.FONT_MINECRAFT, fontSize) },
            )

        assertTrue(layout.lines.isNotEmpty())
        assertTrue(layout.lines.all { it.width <= maxWidth + 1 })
        assertEquals(layout.lines.size * layout.lineHeight, layout.totalHeight)
    }

    @Test
    fun `shapeTextRange matches shapeText on equivalent substring`() {
        val source = "A\uD83D\uDE00B\u0416C"
        val start = source.indexOf('B')
        val end = source.length
        val range =
            FontRegistry.shapeTextRange(
                text = source,
                startIndex = start,
                endIndexExclusive = end,
                fontId = FontRegistry.FONT_MINECRAFT,
                fontSize = 16,
                formattingMode = "plain",
            )
        val plain =
            FontRegistry.shapeText(
                text = source.substring(start, end),
                fontId = FontRegistry.FONT_MINECRAFT,
                fontSize = 16,
                formattingMode = "plain",
            )
        assertEquals(plain.glyphs.size, range.glyphs.size)
        assertEquals(plain.runs.size, range.runs.size)
        assertTrue(abs(plain.width - range.width) <= 0.01f)
        plain.glyphs.zip(range.glyphs).forEach { (a, b) ->
            assertEquals(a.fontId, b.fontId)
            assertEquals(a.glyphIndex, b.glyphIndex)
            assertTrue(abs(a.x - b.x) <= 0.01f)
            assertTrue(abs(a.advance - b.advance) <= 0.01f)
            assertEquals(a.charStart, b.charStart)
            assertEquals(a.charEnd, b.charEnd)
        }
    }

    @Test
    fun `shapeTextRange returns empty for empty or inverted range`() {
        val source = "Hello"
        val empty =
            FontRegistry.shapeTextRange(
                text = source,
                startIndex = 2,
                endIndexExclusive = 2,
                fontId = FontRegistry.FONT_MINECRAFT,
                fontSize = 14,
                formattingMode = "plain",
            )
        val inverted =
            FontRegistry.shapeTextRange(
                text = source,
                startIndex = 4,
                endIndexExclusive = 1,
                fontId = FontRegistry.FONT_MINECRAFT,
                fontSize = 14,
                formattingMode = "plain",
            )
        assertTrue(empty.glyphs.isEmpty())
        assertTrue(inverted.glyphs.isEmpty())
        assertEquals(0f, empty.width)
        assertEquals(0f, inverted.width)
    }

    @Test
    fun `png atlas fallback decode preserves bottom-origin row order`() {
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0xFFFF0000.toInt())
        image.setRGB(1, 0, 0xFF00FF00.toInt())
        image.setRGB(0, 1, 0xFF0000FF.toInt())
        image.setRGB(1, 1, 0xFFFFFFFF.toInt())

        val pngBytes =
            ByteArrayOutputStream().use { output ->
                ImageIO.write(image, "png", output)
                output.toByteArray()
            }
        val decoded = AtlasPayload(pngBytes).ensureDecoded()
        assertEquals(2, decoded.width)
        assertEquals(2, decoded.height)

        fun argbAt(index: Int): Int {
            val offset = index * 4
            val r = decoded.rgbaBytes[offset].toInt() and 0xFF
            val g = decoded.rgbaBytes[offset + 1].toInt() and 0xFF
            val b = decoded.rgbaBytes[offset + 2].toInt() and 0xFF
            val a = decoded.rgbaBytes[offset + 3].toInt() and 0xFF
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        assertEquals(0xFF0000FF.toInt(), argbAt(0))
        assertEquals(0xFFFFFFFF.toInt(), argbAt(1))
        assertEquals(0xFFFF0000.toInt(), argbAt(2))
        assertEquals(0xFF00FF00.toInt(), argbAt(3))
    }

    @Test
    fun `deflated atlas decode path remains supported`() {
        val expected =
            byteArrayOf(
                0x01,
                0x02,
                0x03,
                0x04,
            )
        val rawPayload =
            ByteArrayOutputStream().use { rawOut ->
                DataOutputStream(rawOut).use { data ->
                    data.writeInt(0x4453474C)
                    data.writeInt(1)
                    data.writeInt(1)
                    data.write(expected)
                }
                rawOut.toByteArray()
            }
        val deflated =
            ByteArrayOutputStream().use { compressedOut ->
                val deflater = Deflater(Deflater.BEST_SPEED, true)
                DeflaterOutputStream(compressedOut, deflater).use { zipOut ->
                    zipOut.write(rawPayload)
                }
                deflater.end()
                compressedOut.toByteArray()
            }

        val decoded = AtlasPayload(deflated).ensureDecoded()
        assertEquals(1, decoded.width)
        assertEquals(1, decoded.height)
        assertTrue(decoded.rgbaBytes.contentEquals(expected))
    }

    private fun findFallbackOnlyCodepoint(primary: Font, fallback: Font): Int? =
        (0x20..0x10FFFF).firstOrNull { cp ->
            Character.isValidCodePoint(cp) &&
                cp !in 0xD800..0xDFFF &&
                !primary.canDisplay(cp) &&
                fallback.canDisplay(cp)
        }

    private fun loadResource(path: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(path)
        assertNotNull(stream, "Missing test resource: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
