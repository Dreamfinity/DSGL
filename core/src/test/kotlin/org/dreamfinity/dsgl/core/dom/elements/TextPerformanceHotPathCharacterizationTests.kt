package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.support.MeasuredTextRangeWidthSource
import org.dreamfinity.dsgl.core.dom.elements.support.TextLayoutEngine
import org.dreamfinity.dsgl.core.dom.UsedInteractionGeometryResolver
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.collectHoverChain
import org.dreamfinity.dsgl.core.font.FontRegistry
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.TextWrap
import java.awt.Font
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

        override fun measureTextRange(
            text: String,
            startIndex: Int,
            endIndexExclusive: Int,
            fontId: String?,
            fontSize: Int?
        ): Int {
            val shaped = FontRegistry.shapeTextRange(
                text = text,
                startIndex = startIndex,
                endIndexExclusive = endIndexExclusive,
                fontId = fontId,
                fontSize = fontSize,
                formattingMode = "plain"
            )
            return shaped.width.toInt().coerceAtLeast(0)
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
    fun `wrapped text semantics remain unchanged between legacy and optimized range-width paths`() {
        val text = "Wrapping semantics should remain stable across optimized range-width source changes."
        val legacy = legacyWrappedLayout(text = text, width = 120, fontSize = 16)
        val optimized = optimizedWrappedLayout(text = text, width = 120, fontSize = 16)

        assertEquals(legacy.lines.map { it.text }, optimized.lines.map { it.text })
        assertEquals(legacy.lines.map { it.startIndex to it.endIndexExclusive }, optimized.lines.map { it.startIndex to it.endIndexExclusive })
        assertEquals(legacy.lines.map { it.width }, optimized.lines.map { it.width })
        assertEquals(legacy.maxLineWidth, optimized.maxLineWidth)
        assertEquals(legacy.totalHeight, optimized.totalHeight)
    }

    @Test
    fun `wrapped text node path uses cache-keyed range source instead of unconditional bypass`() {
        val root = ContainerNode(key = "text.wrap.root").apply {
            display = Display.Block
            width = 180
            height = 120
        }
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
        }.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 180, 120)
        tree.paint(ctx)

        val layoutStats = TextLayoutEngine.hotPathStats()
        val fontStats = FontRegistry.textHotPathStats()
        assertTrue(layoutStats.layoutCalls > 0)
        assertEquals(0, layoutStats.cacheBypassedForRangeMeasure)
        assertTrue(layoutStats.cacheHits > 0)
        assertTrue(layoutStats.rangeMeasureCalls > 0)
        assertTrue(layoutStats.rangeMeasureCalls > layoutStats.plainMeasureCalls)
        assertTrue(layoutStats.findMaxFittingCalls > 0)
        assertEquals(0, layoutStats.temporarySegmentSubstringCalls)
        assertTrue(layoutStats.finalLineTextSubstringCalls > 0)
        assertEquals(
            layoutStats.finalLineTextSubstringCalls +
                    layoutStats.probeMeasureSubstringCalls +
                    layoutStats.temporarySegmentSubstringCalls,
            layoutStats.substringSliceCalls
        )
        assertTrue(fontStats.shapeTextRangeCalls > 0)
    }

    @Test
    fun `wrapped button path uses range measurement without fallback substring measurement`() {
        val root = ContainerNode(key = "button.wrap.root").apply {
            display = Display.Block
            width = 220
            height = 120
        }
        ButtonNode(
            text = "Button wrapping should use authoritative range measurement over substring-based fallback.",
            key = "button.wrap.hotpath"
        ).apply {
            width = 140
            fontId = FontRegistry.FONT_MINECRAFT
            fontSize = 16
            textWrap = TextWrap.Wrap
        }.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 220, 120)
        tree.paint(ctx)

        val layoutStats = TextLayoutEngine.hotPathStats()
        assertTrue(layoutStats.layoutCalls > 0)
        assertEquals(0, layoutStats.cacheBypassedForRangeMeasure)
        assertTrue(layoutStats.rangeMeasureCalls > 0)
        assertTrue(layoutStats.rangeMeasureCalls > layoutStats.plainMeasureCalls)
        assertEquals(0, layoutStats.temporarySegmentSubstringCalls)
        assertTrue(layoutStats.finalLineTextSubstringCalls > 0)
    }

    @Test
    fun `wrapped optimized path limits substring materialization to final line output boundary`() {
        val text = "Final materialization boundary should keep hot internal probes on index ranges."
        val source = MeasuredTextRangeWidthSource(
            plainText = text,
            fontId = FontRegistry.FONT_MINECRAFT,
            fontSizePx = 16,
            baseFlags = baseTextFlags(),
            spans = emptyList(),
            ctx = ctx
        )
        TextLayoutEngine.layout(
            text = text,
            maxWidth = 90,
            wrap = TextWrap.Wrap,
            fontHeight = FontRegistry.lineHeight(FontRegistry.FONT_MINECRAFT, 16),
            measureText = { value -> ctx.measureText(value, FontRegistry.FONT_MINECRAFT, 16) },
            measureRange = source::measureRange,
            measureRangeCacheKey = source.cacheKey
        )

        val stats = TextLayoutEngine.hotPathStats()
        assertEquals(0, stats.temporarySegmentSubstringCalls)
        assertEquals(0, stats.probeMeasureSubstringCalls)
        assertTrue(stats.finalLineTextSubstringCalls > 0)
        assertEquals(
            stats.finalLineTextSubstringCalls + stats.probeMeasureSubstringCalls + stats.temporarySegmentSubstringCalls,
            stats.substringSliceCalls
        )
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
        assertTrue(cachedStats.probeMeasureSubstringCalls > 0)

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
        assertEquals(0, rangeStats.probeMeasureSubstringCalls)

        TextLayoutEngine.clearCache()
        TextLayoutEngine.resetHotPathStats()
        val rangeSource = MeasuredTextRangeWidthSource(
            plainText = text,
            fontId = FontRegistry.FONT_MINECRAFT,
            fontSizePx = 14,
            baseFlags = baseTextFlags(),
            spans = emptyList(),
            ctx = ctx
        )
        TextLayoutEngine.layout(
            text = text,
            maxWidth = 90,
            wrap = TextWrap.Wrap,
            fontHeight = 14,
            measureText = measureText,
            measureRange = rangeSource::measureRange,
            measureRangeCacheKey = rangeSource.cacheKey
        )
        TextLayoutEngine.layout(
            text = text,
            maxWidth = 90,
            wrap = TextWrap.Wrap,
            fontHeight = 14,
            measureText = measureText,
            measureRange = rangeSource::measureRange,
            measureRangeCacheKey = rangeSource.cacheKey
        )
        val keyedStats = TextLayoutEngine.hotPathStats()
        assertEquals(0, keyedStats.cacheBypassedForRangeMeasure)
        assertTrue(keyedStats.cacheMisses > 0)
        assertTrue(keyedStats.cacheHits > 0)
        assertEquals(0, keyedStats.temporarySegmentSubstringCalls)
        assertEquals(0, keyedStats.probeMeasureSubstringCalls)
    }

    @Test
    fun `cache keyed wrapped layout respects text style font and width inputs`() {
        val text = "Cache key correctness for wrapped optimized measurement"
        val measureText: (String) -> Int = { value ->
            ctx.measureText(value, FontRegistry.FONT_MINECRAFT, 14)
        }
        val source14 = MeasuredTextRangeWidthSource(
            plainText = text,
            fontId = FontRegistry.FONT_MINECRAFT,
            fontSizePx = 14,
            baseFlags = baseTextFlags(),
            spans = emptyList(),
            ctx = ctx
        )
        val source18 = MeasuredTextRangeWidthSource(
            plainText = text,
            fontId = FontRegistry.FONT_MINECRAFT,
            fontSizePx = 18,
            baseFlags = baseTextFlags(),
            spans = emptyList(),
            ctx = ctx
        )
        val first = TextLayoutEngine.layout(
            text = text,
            maxWidth = 90,
            wrap = TextWrap.Wrap,
            fontHeight = FontRegistry.lineHeight(FontRegistry.FONT_MINECRAFT, 14),
            measureText = measureText,
            measureRange = source14::measureRange,
            measureRangeCacheKey = source14.cacheKey
        )
        val second = TextLayoutEngine.layout(
            text = text,
            maxWidth = 90,
            wrap = TextWrap.Wrap,
            fontHeight = FontRegistry.lineHeight(FontRegistry.FONT_MINECRAFT, 18),
            measureText = { value -> ctx.measureText(value, FontRegistry.FONT_MINECRAFT, 18) },
            measureRange = source18::measureRange,
            measureRangeCacheKey = source18.cacheKey
        )
        val differentWidth = TextLayoutEngine.layout(
            text = text,
            maxWidth = 70,
            wrap = TextWrap.Wrap,
            fontHeight = FontRegistry.lineHeight(FontRegistry.FONT_MINECRAFT, 14),
            measureText = measureText,
            measureRange = source14::measureRange,
            measureRangeCacheKey = source14.cacheKey
        )

        val stats = TextLayoutEngine.hotPathStats()
        assertTrue(stats.cacheMisses >= 3)
        assertTrue(second.maxLineWidth != first.maxLineWidth || second.lines.size != first.lines.size)
        assertTrue(differentWidth.maxLineWidth <= 70)
    }

    @Test
    fun `optimized wrapped path reduces structural hot-path work versus legacy two-pass baseline`() {
        val text = "Structural hot-path reduction should be visible when wrapped layout avoids legacy per-pass repeated range shaping."
        val legacyStats = runLegacyTwoPassLayout(text = text, width = 120, fontSize = 16)
        val optimizedStats = runOptimizedTwoPassLayout(text = text, width = 120, fontSize = 16)

        assertTrue(legacyStats.rangeSubstringCalls > 0)
        assertEquals(0, optimizedStats.rangeSubstringCalls)
        assertTrue(optimizedStats.layoutStats.rangeMeasureCalls < legacyStats.layoutStats.rangeMeasureCalls)
        assertTrue(optimizedStats.fontStats.shapeTextRangeCalls < legacyStats.fontStats.shapeTextRangeCalls)
        assertEquals(0, optimizedStats.layoutStats.temporarySegmentSubstringCalls)
        assertEquals(0, optimizedStats.layoutStats.probeMeasureSubstringCalls)
    }

    @Test
    fun `small scroll-heavy text scenario keeps semantics and reduces wrapped-measure work`() {
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
        assertTrue(content.children.all { it.bounds.height > 0 })

        root.setScrollOffsets(0, 80)
        tree.render(ctx, 300, 120)

        val layoutAfterScroll = TextLayoutEngine.hotPathStats()
        val fontAfterScroll = FontRegistry.textHotPathStats()
        val shapeCacheAfterScroll = FontRegistry.shapeCacheStats()
        val rangeCallsDelta = layoutAfterScroll.rangeMeasureCalls - layoutBeforeScroll.rangeMeasureCalls
        val shapeCallsDelta = fontAfterScroll.shapeTextRangeCalls - fontBeforeScroll.shapeTextRangeCalls
        assertTrue(layoutAfterScroll.cacheHits > 0)
        assertTrue(rangeCallsDelta >= 0)
        assertTrue(shapeCallsDelta >= 0)
        assertTrue(
            rangeCallsDelta < layoutBeforeScroll.rangeMeasureCalls,
            "Scroll update should not repeat the full initial wrapped range-measure workload"
        )
        assertTrue(
            shapeCallsDelta < fontBeforeScroll.shapeTextRangeCalls,
            "Scroll update should not repeat full wrapped range shaping workload"
        )
        assertTrue(shapeCacheAfterScroll.requests > shapeCacheBeforeScroll.requests)
    }

    @Test
    fun `interaction and inspector picking remain aligned for wrapped text node`() {
        val root = ContainerNode(key = "interaction-root").apply {
            display = Display.Block
            width = 240
            height = 120
        }
        val text = TextNode(
            textSource = TextSource.Static("Wrapped interaction smoke test for optimized text path."),
            key = "interaction-text"
        ).apply {
            width = 120
            fontId = FontRegistry.FONT_MINECRAFT
            fontSize = 16
            textWrap = TextWrap.Wrap
        }.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 240, 120)
        val geometry = UsedInteractionGeometryResolver.resolveNodeGeometry(text)
        assertNotNull(geometry)
        val px = geometry.usedBorderRect.x + 2
        val py = geometry.usedBorderRect.y + 2
        val hoverChain = collectHoverChain(root, px, py)
        assertEquals(text.key, hoverChain.lastOrNull()?.key)

        val inspector = InspectorController().also { it.toggle() }
        inspector.onLayoutCommitted(root, 1L)
        inspector.onCursorMoved(px, py)
        assertEquals(text.key?.toString(), inspector.hoveredKey)
    }

    private data class ProfileSnapshot(
        val layoutStats: TextLayoutEngine.HotPathStats,
        val fontStats: FontRegistry.TextHotPathStats,
        val rangeSubstringCalls: Long
    )

    private fun runLegacyTwoPassLayout(text: String, width: Int, fontSize: Int): ProfileSnapshot {
        FontRegistry.clearLoadedCache()
        FontRegistry.resetShapeCacheStats()
        FontRegistry.resetTextHotPathStats()
        TextLayoutEngine.clearCache()
        TextLayoutEngine.resetHotPathStats()
        var rangeSubstringCalls = 0L

        val measureText: (String) -> Int = { value ->
            ctx.measureText(value, FontRegistry.FONT_MINECRAFT, fontSize)
        }
        val measureRange: (Int, Int) -> Int = { start, end ->
            val safeStart = start.coerceIn(0, text.length)
            val safeEnd = end.coerceIn(safeStart, text.length)
            rangeSubstringCalls += 1L
            ctx.measureText(text.substring(safeStart, safeEnd), FontRegistry.FONT_MINECRAFT, fontSize)
        }
        repeat(2) {
            TextLayoutEngine.layout(
                text = text,
                maxWidth = width,
                wrap = TextWrap.Wrap,
                fontHeight = FontRegistry.lineHeight(FontRegistry.FONT_MINECRAFT, fontSize),
                measureText = measureText,
                measureRange = measureRange
            )
        }
        return ProfileSnapshot(
            layoutStats = TextLayoutEngine.hotPathStats(),
            fontStats = FontRegistry.textHotPathStats(),
            rangeSubstringCalls = rangeSubstringCalls
        )
    }

    private fun runOptimizedTwoPassLayout(text: String, width: Int, fontSize: Int): ProfileSnapshot {
        FontRegistry.clearLoadedCache()
        FontRegistry.resetShapeCacheStats()
        FontRegistry.resetTextHotPathStats()
        TextLayoutEngine.clearCache()
        TextLayoutEngine.resetHotPathStats()

        val measureText: (String) -> Int = { value ->
            ctx.measureText(value, FontRegistry.FONT_MINECRAFT, fontSize)
        }
        val rangeSource = MeasuredTextRangeWidthSource(
            plainText = text,
            fontId = FontRegistry.FONT_MINECRAFT,
            fontSizePx = fontSize,
            baseFlags = baseTextFlags(),
            spans = emptyList(),
            ctx = ctx
        )
        repeat(2) {
            TextLayoutEngine.layout(
                text = text,
                maxWidth = width,
                wrap = TextWrap.Wrap,
                fontHeight = FontRegistry.lineHeight(FontRegistry.FONT_MINECRAFT, fontSize),
                measureText = measureText,
                measureRange = rangeSource::measureRange,
                measureRangeCacheKey = rangeSource.cacheKey
            )
        }
        return ProfileSnapshot(
            layoutStats = TextLayoutEngine.hotPathStats(),
            fontStats = FontRegistry.textHotPathStats(),
            rangeSubstringCalls = 0L
        )
    }

    private fun legacyWrappedLayout(text: String, width: Int, fontSize: Int): TextLayoutEngine.Layout {
        return TextLayoutEngine.layout(
            text = text,
            maxWidth = width,
            wrap = TextWrap.Wrap,
            fontHeight = FontRegistry.lineHeight(FontRegistry.FONT_MINECRAFT, fontSize),
            measureText = { value -> ctx.measureText(value, FontRegistry.FONT_MINECRAFT, fontSize) },
            measureRange = { start, end ->
                val safeStart = start.coerceIn(0, text.length)
                val safeEnd = end.coerceIn(safeStart, text.length)
                ctx.measureText(text.substring(safeStart, safeEnd), FontRegistry.FONT_MINECRAFT, fontSize)
            }
        )
    }

    private fun optimizedWrappedLayout(text: String, width: Int, fontSize: Int): TextLayoutEngine.Layout {
        val source = MeasuredTextRangeWidthSource(
            plainText = text,
            fontId = FontRegistry.FONT_MINECRAFT,
            fontSizePx = fontSize,
            baseFlags = baseTextFlags(),
            spans = emptyList(),
            ctx = ctx
        )
        return TextLayoutEngine.layout(
            text = text,
            maxWidth = width,
            wrap = TextWrap.Wrap,
            fontHeight = FontRegistry.lineHeight(FontRegistry.FONT_MINECRAFT, fontSize),
            measureText = { value -> ctx.measureText(value, FontRegistry.FONT_MINECRAFT, fontSize) },
            measureRange = source::measureRange,
            measureRangeCacheKey = source.cacheKey
        )
    }

    private fun baseTextFlags() = org.dreamfinity.dsgl.core.text.TextStyleFlags(
        bold = false,
        italic = false,
        underline = false,
        strikethrough = false,
        obfuscated = false
    )

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
