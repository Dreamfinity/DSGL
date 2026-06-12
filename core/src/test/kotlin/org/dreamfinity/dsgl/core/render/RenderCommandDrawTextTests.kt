package org.dreamfinity.dsgl.core.render

import org.dreamfinity.dsgl.core.style.TextFormatting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class RenderCommandDrawTextTests {
    @Test
    fun `withColor preserves all draw text fields except color`() {
        val spans =
            listOf(
                RenderCommand.TextStyleSpan(
                    start = 0,
                    end = 4,
                    color = 0xFF11AA33.toInt(),
                    bold = true,
                    italic = true,
                    underline = true,
                    strikethrough = false,
                    obfuscated = true,
                ),
            )
        val original =
            RenderCommand.DrawText(
                text = "Demo",
                x = 12,
                y = 34,
                color = 0xFF445566.toInt(),
                fontId = "ubuntu",
                fontSize = 16,
                textFormatting = TextFormatting.Minecraft,
                bold = true,
                italic = false,
                underline = true,
                strikethrough = true,
                obfuscated = false,
                textStyleSpans = spans,
                sourceKey = "node.demo",
            )

        val recolored = original.withColor(0xAA778899.toInt())

        assertNotSame(original, recolored)
        assertEquals(0xAA778899.toInt(), recolored.color)
        assertEquals(original.text, recolored.text)
        assertEquals(original.x, recolored.x)
        assertEquals(original.y, recolored.y)
        assertEquals(original.fontId, recolored.fontId)
        assertEquals(original.fontSize, recolored.fontSize)
        assertEquals(original.textFormatting, recolored.textFormatting)
        assertEquals(original.bold, recolored.bold)
        assertEquals(original.italic, recolored.italic)
        assertEquals(original.underline, recolored.underline)
        assertEquals(original.strikethrough, recolored.strikethrough)
        assertEquals(original.obfuscated, recolored.obfuscated)
        assertSame(spans, recolored.textStyleSpans)
        assertEquals(original.sourceKey, recolored.sourceKey)
    }
}
