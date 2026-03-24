package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.support.TextLayoutEngine
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.font.FontRegistry
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.TextWrap
import java.awt.Font
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextPerformanceHotPathCharacterizationTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 10

        override fun measureText(text: String): Int {
            return FontRegistry.measureText(text, FontRegistry.FONT_MINECRAFT, FontRegistry.DEFAULT_FONT_SIZE)
        }

        override fun measureText(text: String, fontId: String?, fontSize: Int?): Int {
            return FontRegistry.measureText(text, fontId, fontSize)
        }

        override fun fontHeight(fontId: String?, fontSize: Int?): Int {
            return FontRegistry.lineHeight(fontId, fontSize)
        }

        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @BeforeTest
    fun resetInstrumentation() {
        FontRegistry.clearLoadedCache()
        FontRegistry.resetShapeCacheStats()
        FontRegistry.resetTextHotPathStats()
        TextLayoutEngine.clearCache()
        TextLayoutEngine.resetHotPathStats()
    }

    @Test
    fun `font registry shaping exercises per-codepoint probing hot path`() {
        val primary = FontRegistry.get(FontRegistry.FONT_MINECRAFT)?.awtBaseFont
        val fallback = FontRegistry.get(FontRegistry.FALLBACK_FONT_ID)?.awtBaseFont
        val fallbackOnlyCodepoint = findFallbackOnlyCodepoint(primary, fallback)
        val fallbackOnlyChar = String(Character.toChars(fallbackOnlyCodepoint))
        val missingChar = String(Character.toChars(0x10FFFF))
        val text = "A${fallbackOnlyChar}B${missingChar}C"

        FontRegistry.shapeText(text, FontRegistry.FONT_MINECRAFT, 16, formattingMode = "plain")

        val stats = FontRegistry.textHotPathStats()
        assertTrue(stats.shapeTextRangeCalls > 0)
        assertTrue(stats.shapeSegmentCalls > 0)
        assertTrue(stats.requiresReplacementGlyphCalls > 0)
        assertTrue(stats.canDisplayCalls > 0)
        assertTrue(stats.glyphIndexForCodepointCalls > 0)
    }

    @Test
    fun `wrapped text measurement routes through repeated range measurements`() {
        val node = TextNode(
            textSource = TextSource.Static(
                "Wrapped text baseline path should repeatedly measure many ranges while fitting lines for wrapping."
            ),
            key = "text.hotpath.wrap"
        ).apply {
            width = 120
            fontId = FontRegistry.FONT_MINECRAFT
            fontSize = 16
            textWrap = TextWrap.Wrap
        }

        node.measure(ctx)

        val layoutStats = TextLayoutEngine.hotPathStats()
        val fontStats = FontRegistry.textHotPathStats()
        assertTrue(layoutStats.layoutCalls > 0)
        assertTrue(layoutStats.cacheBypassedForRangeMeasure > 0)
        assertTrue(layoutStats.rangeMeasureCalls > 0)
        assertTrue(layoutStats.findMaxFittingCalls > 0)
        assertTrue(layoutStats.substringSliceCalls > 0)
        assertTrue(fontStats.shapeTextRangeCalls > 0)
    }

    @Test
    fun `cache boundaries are explicit for wrapped layout paths`() {
        val text = "Cache boundary characterization for wrapped layout"
        val measureText: (String) -> Int = { value ->
            FontRegistry.measureText(value, FontRegistry.FONT_MINECRAFT, 14)
        }

        TextLayoutEngine.layout(
            text = text,
            maxWidth = 90,
            wrap = TextWrap.Wrap,
            fontHeight = 14,
            measureText = measureText
        )
        TextLayoutEngine.layout(
            text = text,
            maxWidth = 90,
            wrap = TextWrap.Wrap,
            fontHeight = 14,
            measureText = measureText
        )

        val cachedStats = TextLayoutEngine.hotPathStats()
        assertTrue(cachedStats.cacheMisses > 0)
        assertTrue(cachedStats.cacheHits > 0)
        assertEquals(0, cachedStats.cacheBypassedForRangeMeasure)

        TextLayoutEngine.clearCache()
        TextLayoutEngine.resetHotPathStats()
        val measureRange: (Int, Int) -> Int = { start, end ->
            val safeStart = start.coerceIn(0, text.length)
            val safeEnd = end.coerceIn(safeStart, text.length)
            FontRegistry.measureText(text.substring(safeStart, safeEnd), FontRegistry.FONT_MINECRAFT, 14)
        }

        TextLayoutEngine.layout(
            text = text,
            maxWidth = 90,
            wrap = TextWrap.Wrap,
            fontHeight = 14,
            measureText = measureText,
            measureRange = measureRange
        )
        TextLayoutEngine.layout(
            text = text,
            maxWidth = 90,
            wrap = TextWrap.Wrap,
            fontHeight = 14,
            measureText = measureText,
            measureRange = measureRange
        )

        val rangeStats = TextLayoutEngine.hotPathStats()
        assertEquals(2, rangeStats.cacheBypassedForRangeMeasure)
        assertEquals(0, rangeStats.cacheHits)
        assertTrue(rangeStats.rangeMeasureCalls > 0)
    }

    @Test
    fun `small scroll-heavy text scenario re-enters wrapped measurement hot path on scroll updates`() {
        val root = ContainerNode(key = "scroll-hot-root").apply {
            display = Display.Block
            width = 300
            height = 120
            overflowY = Overflow.Scroll
        }
        val content = ContainerNode(key = "scroll-hot-content").apply {
            display = Display.Flex
            flexDirection = FlexDirection.Column
            width = 300
        }.applyParent(root)

        repeat(10) { index ->
            val row = ContainerNode(key = "scroll-hot-row-$index").apply {
                display = Display.Block
                width = 280
            }.applyParent(content)
            TextNode(
                textSource = TextSource.Static(
                    "Row $index wraps repeatedly to characterize current range-based text measurement under scroll-heavy updates."
                ),
                key = "scroll-hot-text-$index"
            ).apply {
                width = 260
                fontId = FontRegistry.FONT_MINECRAFT
                fontSize = 14
                textWrap = TextWrap.Wrap
            }.applyParent(row)
        }

        val tree = DomTree(root)
        tree.render(ctx, 300, 120)
        val layoutBeforeScroll = TextLayoutEngine.hotPathStats()
        val fontBeforeScroll = FontRegistry.textHotPathStats()
        val shapeCacheBeforeScroll = FontRegistry.shapeCacheStats()

        root.setScrollOffsets(0, 80)
        tree.render(ctx, 300, 120)

        val layoutAfterScroll = TextLayoutEngine.hotPathStats()
        val fontAfterScroll = FontRegistry.textHotPathStats()
        val shapeCacheAfterScroll = FontRegistry.shapeCacheStats()
        assertTrue(layoutAfterScroll.rangeMeasureCalls > layoutBeforeScroll.rangeMeasureCalls)
        assertTrue(layoutAfterScroll.findMaxFittingCalls > layoutBeforeScroll.findMaxFittingCalls)
        assertTrue(fontAfterScroll.shapeTextRangeCalls > fontBeforeScroll.shapeTextRangeCalls)
        assertTrue(shapeCacheAfterScroll.requests > shapeCacheBeforeScroll.requests)
    }

    private fun findFallbackOnlyCodepoint(primary: Font?, fallback: Font?): Int {
        requireNotNull(primary) { "Primary font AWT handle must be available for hot-path characterization" }
        requireNotNull(fallback) { "Fallback font AWT handle must be available for hot-path characterization" }

        val candidateCodepoints = listOf(
            0x1F642, // 🙂
            0x0E01,  // Thai
            0x0531,  // Armenian
            0x05D0,  // Hebrew
            0x16A0   // Runic
        )
        candidateCodepoints.firstOrNull { cp ->
            Character.isValidCodePoint(cp) && !primary.canDisplay(cp) && fallback.canDisplay(cp)
        }?.let { return it }

        for (cp in 0x20..0x2FFF) {
            if (!Character.isValidCodePoint(cp)) continue
            if (cp in 0xD800..0xDFFF) continue
            if (!primary.canDisplay(cp) && fallback.canDisplay(cp)) {
                return cp
            }
        }
        error("Could not find deterministic fallback-only codepoint for characterization test")
    }
}
