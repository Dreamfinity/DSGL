package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.dom.elements.support.TextLayoutEngine
import org.dreamfinity.dsgl.core.style.TextWrap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextLayoutEngineTests {
    private val measure: (String) -> Int = { value -> value.length }

    @Test
    fun `wrap mode breaks lines by width`() {
        val layout =
            TextLayoutEngine.layout(
                text = "one two three",
                maxWidth = 5,
                wrap = TextWrap.Wrap,
                fontHeight = 9,
                measureText = measure,
            )

        assertTrue(layout.lines.size > 1)
        assertTrue(layout.lines.all { it.width <= 5 })
        assertEquals(layout.lines.size * 9, layout.totalHeight)
    }

    @Test
    fun `nowrap mode keeps single line`() {
        val layout =
            TextLayoutEngine.layout(
                text = "one two three",
                maxWidth = 5,
                wrap = TextWrap.NoWrap,
                fontHeight = 9,
                measureText = measure,
            )

        assertEquals(1, layout.lines.size)
        assertEquals(
            "one two three",
            layout.lines
                .single()
                .text,
        )
    }

    @Test
    fun `wrap mode hard breaks long words`() {
        val layout =
            TextLayoutEngine.layout(
                text = "abcdefghij",
                maxWidth = 4,
                wrap = TextWrap.Wrap,
                fontHeight = 10,
                measureText = measure,
            )

        assertEquals(listOf("abcd", "efgh", "ij"), layout.lines.map { it.text })
        assertTrue(layout.lines.all { it.width <= 4 })
    }

    @Test
    fun `newline indices are preserved for caret mapping`() {
        val layout =
            TextLayoutEngine.layout(
                text = "ab\ncd",
                maxWidth = null,
                wrap = TextWrap.NoWrap,
                fontHeight = 8,
                measureText = measure,
            )

        assertEquals(2, layout.lines.size)
        assertEquals(0, layout.lineForCaret(2))
        assertEquals(1, layout.lineForCaret(3))
    }
}
