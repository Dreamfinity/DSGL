package org.dreamfinity.dsgl.core.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CssLengthTests {
    @Test
    fun `parses px length with sign`() {
        val positive = parseCssLength("12px")
        val negative = parseCssLength("-4PX")

        assertEquals(CssLength(12f, CssUnit.Px), positive)
        assertEquals(CssLength(-4f, CssUnit.Px), negative)
    }

    @Test
    fun `parses spacing shorthand with px lengths`() {
        val insets = parseSpacingShorthand(
            raw = "12px 4px 6px 8px",
            allowNegative = true
        )

        assertEquals(12, insets.top)
        assertEquals(4, insets.right)
        assertEquals(6, insets.bottom)
        assertEquals(8, insets.left)
    }

    @Test
    fun `rejects spacing shorthand with whitespace between number and unit`() {
        assertFailsWith<IllegalStateException> {
            parseSpacingShorthand(
                raw = "12 px 4 px",
                allowNegative = true
            )
        }
    }

    @Test
    fun `padding rejects negative lengths`() {
        assertFailsWith<IllegalStateException> {
            parseSpacingShorthand(
                raw = "-4px",
                allowNegative = false
            )
        }
    }

    @Test
    fun `unitless lengths are treated as px with deprecation warning`() {
        val warnings = linkedMapOf<String, String>()
        val reporter = StyleWarningReporter { key, message ->
            warnings.putIfAbsent(key, message)
        }

        val parsed = parseCssLength(
            raw = "12",
            warningReporter = reporter
        )

        assertEquals(CssLength(12f, CssUnit.Px), parsed)
        assertEquals(1, warnings.size)
        assertTrue(warnings.values.single().contains("pixels"))
    }

    @Test
    fun `resolves px length directly`() {
        val px = CssLength(12f, CssUnit.Px).toPx(LengthContext())
        assertEquals(12f, px)
    }

    @Test
    fun `unknown units fail gracefully`() {
        val error = assertFailsWith<IllegalStateException> {
            parseCssLength("1em")
        }
        assertTrue(error.message?.contains("Unknown length unit") == true)
    }
}
