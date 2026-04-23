package org.dreamfinity.dsgl.core.text

import org.dreamfinity.dsgl.core.dom.elements.support.TextLayoutEngine
import org.dreamfinity.dsgl.core.font.MsdfFontMetaParser
import org.dreamfinity.dsgl.core.style.TextFormatting
import org.dreamfinity.dsgl.core.style.TextWrap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MinecraftFormattingParserTests {
    @Test
    fun `parses legacy colors and reset into plain text with spans`() {
        val parsed =
            MinecraftFormattingParser.parse(
                text = "\u00A7aHi \u00A7bWorld\u00A7r!",
                mode = TextFormatting.Minecraft,
            )

        assertEquals("Hi World!", parsed.plainText)
        val spans = MinecraftFormattingParser.resolveColorSpans(parsed, 0xFF336699.toInt())
        assertEquals(2, spans.size)
        assertEquals(0, spans[0].start)
        assertEquals(3, spans[0].end)
        assertEquals(0xFF55FF55.toInt(), spans[0].color)
        assertEquals(3, spans[1].start)
        assertEquals(8, spans[1].end)
        assertEquals(0xFF55FFFF.toInt(), spans[1].color)
    }

    @Test
    fun `supports escaped prefix trailing prefix and unknown code safely`() {
        val escaped = MinecraftFormattingParser.parse("Cost: \u00A7\u00A751", TextFormatting.Minecraft)
        assertEquals("Cost: \u00A751", escaped.plainText)

        val trailing = MinecraftFormattingParser.parse("Test\u00A7", TextFormatting.Minecraft)
        assertEquals("Test\u00A7", trailing.plainText)

        val unknown = MinecraftFormattingParser.parse("A\u00A7zB", TextFormatting.Minecraft)
        assertEquals("A\u00A7zB", unknown.plainText)
    }

    @Test
    fun `legacy palette contains expected key colors`() {
        assertEquals(0x000000, MinecraftLegacyColors.rgb('0'))
        assertEquals(0xFFFFFF, MinecraftLegacyColors.rgb('f'))
        assertEquals(0xFF5555, MinecraftLegacyColors.rgb('c'))
        assertEquals(0x55FF55, MinecraftLegacyColors.rgb('a'))
    }

    @Test
    fun `measurement is invariant between formatted and plain text`() {
        val rawMeta = loadResource("fonts/ubuntu/Ubuntu-Regular-meta.json")
        val meta = MsdfFontMetaParser.parse(rawMeta)
        val fontSize = 14

        val raw = "\u00A7aHello \u00A7bworld\u00A7r!"
        val plain = "Hello world!"
        val parsed = MinecraftFormattingParser.parse(raw, TextFormatting.Minecraft)
        assertEquals(plain, parsed.plainText)

        val widthFromFormatted = meta.measureTextWidth(parsed.plainText, fontSize)
        val widthFromPlain = meta.measureTextWidth(plain, fontSize)
        assertEquals(widthFromPlain, widthFromFormatted)
    }

    @Test
    fun `modern hex sequence is parsed when complete`() {
        val parsed =
            MinecraftFormattingParser.parse(
                text = "\u00A7x\u00A7F\u00A7F\u00A70\u00A70\u00A70\u00A70R",
                mode = TextFormatting.Minecraft,
            )
        assertEquals("R", parsed.plainText)
        val spans = MinecraftFormattingParser.resolveColorSpans(parsed, 0xAA000000.toInt())
        assertEquals(1, spans.size)
        assertEquals(0xAAFF0000.toInt(), spans[0].color)
    }

    @Test
    fun `format flags toggle and reset to base style`() {
        val parsed =
            MinecraftFormattingParser.parse(
                text = "\u00A7lA\u00A7oB\u00A7nC\u00A7mD\u00A7kE\u00A7rF",
                mode = TextFormatting.Minecraft,
            )
        assertEquals("ABCDEF", parsed.plainText)

        val baseFlags =
            TextStyleFlags(
                bold = false,
                italic = true,
                underline = false,
                strikethrough = false,
                obfuscated = false,
            )
        val spans =
            MinecraftFormattingParser.resolveStyleSpans(
                parsed = parsed,
                baseColor = 0xFF445566.toInt(),
                baseFlags = baseFlags,
            )
        assertTrue(spans.isNotEmpty())
        assertEquals(true, spans[0].flags.bold)
        assertEquals(true, spans[1].flags.bold)
        assertEquals(true, spans[1].flags.italic)
        assertEquals(true, spans[2].flags.underline)
        assertEquals(true, spans[3].flags.strikethrough)
        assertEquals(true, spans[4].flags.obfuscated)
        assertEquals(
            true,
            spans
                .last()
                .flags.italic,
            "§r should reset to base italic=true",
        )
        assertEquals(
            false,
            spans
                .last()
                .flags.bold,
            "§r should reset bold to base bold=false",
        )
    }

    @Test
    fun `bold advance extra is applied deterministically`() {
        val rawMeta = loadResource("fonts/ubuntu/Ubuntu-Regular-meta.json")
        val meta = MsdfFontMetaParser.parse(rawMeta)
        val parsed = MinecraftFormattingParser.parse("A\u00A7lB\u00A7rC", TextFormatting.Minecraft)
        val plain = parsed.plainText
        assertEquals("ABC", plain)

        val baseWidth = meta.measureTextWidth(plain, 12)
        val extra =
            TextStyleMetrics.boldExtraPxForRange(
                plainText = plain,
                spans = parsed.spans,
                baseFlags = TextStyleFlags.NONE,
            )
        assertEquals(BOLD_ADVANCE_EXTRA_PX, extra)
        assertEquals(baseWidth + BOLD_ADVANCE_EXTRA_PX, baseWidth + extra)
    }

    @Test
    fun `obfuscation selector is deterministic and time-varying`() {
        val fixedA =
            ObfuscationTextSelector.selectCandidateIndex(
                sourceKey = "node.key",
                lineIndex = 1,
                glyphIndexInLine = 4,
                timeSlice = 10L,
                originalCodepoint = 'A'.code,
                candidateCount = 16,
            )
        val fixedB =
            ObfuscationTextSelector.selectCandidateIndex(
                sourceKey = "node.key",
                lineIndex = 1,
                glyphIndexInLine = 4,
                timeSlice = 10L,
                originalCodepoint = 'A'.code,
                candidateCount = 16,
            )
        val changed =
            ObfuscationTextSelector.selectCandidateIndex(
                sourceKey = "node.key",
                lineIndex = 1,
                glyphIndexInLine = 4,
                timeSlice = 11L,
                originalCodepoint = 'A'.code,
                candidateCount = 16,
            )
        assertEquals(fixedA, fixedB)
        assertTrue(fixedA in 0 until 16)
        assertTrue(changed in 0 until 16)
        val rowA =
            (0 until 16).map { index ->
                ObfuscationTextSelector.selectCandidateIndex(
                    sourceKey = "node.key",
                    lineIndex = 0,
                    glyphIndexInLine = index,
                    timeSlice = 25L,
                    originalCodepoint = 'X'.code,
                    candidateCount = 32,
                )
            }
        val rowB =
            (0 until 16).map { index ->
                ObfuscationTextSelector.selectCandidateIndex(
                    sourceKey = "node.key",
                    lineIndex = 0,
                    glyphIndexInLine = index,
                    timeSlice = 26L,
                    originalCodepoint = 'X'.code,
                    candidateCount = 32,
                )
            }
        assertTrue(rowA.toSet().size >= 8, "Obfuscated row should produce varied symbols")
        val changedSlots = rowA.indices.count { rowA[it] != rowB[it] }
        assertTrue(changedSlots >= 8, "Obfuscated symbols should refresh across time slices")
        assertFalse(ObfuscationTextSelector.shouldObfuscateCodepoint(' '.code))
        assertFalse(ObfuscationTextSelector.shouldObfuscateCodepoint('\n'.code))
    }

    @Test
    fun `decoration spans can be split per wrapped line`() {
        val rawMeta = loadResource("fonts/minecraft/MinecraftDefault-Regular-meta.json")
        val meta = MsdfFontMetaParser.parse(rawMeta)
        val parsed =
            MinecraftFormattingParser.parse(
                text = "\u00A7nUnderline decoration wraps across multiple lines in a narrow panel",
                mode = TextFormatting.Minecraft,
            )
        val fontSize = 12
        val lineHeight = meta.lineHeightPx(fontSize)
        val layout =
            TextLayoutEngine.layout(
                text = parsed.plainText,
                maxWidth = 72,
                wrap = TextWrap.Wrap,
                fontHeight = lineHeight,
                measureText = { value -> meta.measureTextWidth(value, fontSize) },
            )
        assertTrue(layout.lines.size >= 2)
        layout.lines.forEach { line ->
            val lineSpans =
                MinecraftFormattingParser.resolveStyleSpans(
                    parsed = parsed,
                    baseColor = 0xFFFFFFFF.toInt(),
                    baseFlags = TextStyleFlags.NONE,
                    rangeStart = line.startIndex,
                    rangeEnd = line.endIndexExclusive,
                )
            assertTrue(lineSpans.all { it.start >= 0 && it.end <= line.text.length })
            if (line.text.isNotEmpty()) {
                assertTrue(lineSpans.any { it.flags.underline })
            }
        }
    }

    private fun loadResource(path: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(path)
        assertNotNull(stream, "Missing test resource: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
