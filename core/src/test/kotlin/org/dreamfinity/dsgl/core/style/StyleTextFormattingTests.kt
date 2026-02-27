package org.dreamfinity.dsgl.core.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StyleTextFormattingTests {
    @Test
    fun `parses text formatting literals`() {
        assertEquals(TextFormatting.None, parseTextFormatting("none"))
        assertEquals(TextFormatting.Minecraft, parseTextFormatting("minecraft"))
        assertEquals(TextFormatting.Minecraft, parseTextFormatting("MineCraft"))
    }

    @Test
    fun `rejects unsupported text formatting literal`() {
        assertFailsWith<IllegalStateException> {
            parseTextFormatting("legacy")
        }
    }

    @Test
    fun `parses font style toggles`() {
        assertEquals(FontWeight.Bold, parseFontWeight("bold"))
        assertEquals(FontWeight.Normal, parseFontWeight("normal"))
        assertEquals(FontStyle.Italic, parseFontStyle("italic"))
        assertEquals(FontStyle.Normal, parseFontStyle("normal"))
        assertEquals(TextDecoration.Underline, parseTextDecoration("underline"))
        assertEquals(TextDecoration.Strikethrough, parseTextDecoration("strikethrough"))
        assertEquals(TextDecoration.UnderlineStrikethrough, parseTextDecoration("underline-strikethrough"))
        assertEquals(true, parseBooleanLike("true"))
        assertEquals(false, parseBooleanLike("false"))
    }
}
