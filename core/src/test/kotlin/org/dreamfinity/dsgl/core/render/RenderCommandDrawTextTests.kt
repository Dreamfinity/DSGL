package org.dreamfinity.dsgl.core.render

import org.dreamfinity.dsgl.core.style.TextFormatting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class RenderCommandDrawTextTests {
    @Test
    fun `withColor preserves all draw text fields except color`() {
        val metrics = TextRenderMetrics(
            fontSizePx = 16,
            lineHeightPx = 20,
            nativeLineHeightPx = 18,
            ascenderPx = 12f,
            descenderPx = 4f,
            topLeadingPx = 1f,
            bottomLeadingPx = 1f
        )
        val spans = listOf(
            TextStyleSpan(
                start = 0,
                end = 4,
                style = TextStyleOverride(
                    color = 0xFF11AA33.toInt(),
                    weight = TextWeight.Bold,
                    italic = true,
                    decorations = TextDecorations(underline = true),
                    obfuscated = true
                )
            )
        )
        val original = RenderCommand.DrawText(
            text = "Demo",
            x = 12,
            y = 34,
            fontId = "ubuntu",
            textFormatting = TextFormatting.Minecraft,
            renderMode = TextRenderMode.Raster2D,
            metrics = metrics,
            baseStyle = TextRenderStyle(
                color = 0xFF445566.toInt(),
                weight = TextWeight.Bold,
                italic = false,
                decorations = TextDecorations(underline = true, strikethrough = true),
                obfuscated = false
            ),
            styleSpans = spans,
            sourceKey = "node.demo"
        )

        val recolored = original.withColor(0xAA778899.toInt())

        assertNotSame(original, recolored)
        assertEquals(0xAA778899.toInt(), recolored.baseStyle.color)
        assertEquals(original.text, recolored.text)
        assertEquals(original.x, recolored.x)
        assertEquals(original.y, recolored.y)
        assertEquals(original.fontId, recolored.fontId)
        assertEquals(original.textFormatting, recolored.textFormatting)
        assertEquals(original.renderMode, recolored.renderMode)
        assertEquals(original.metrics, recolored.metrics)
        assertEquals(original.baseStyle.copy(color = 0xAA778899.toInt()), recolored.baseStyle)
        assertSame(spans, recolored.styleSpans)
        assertEquals(original.sourceKey, recolored.sourceKey)
    }
}
