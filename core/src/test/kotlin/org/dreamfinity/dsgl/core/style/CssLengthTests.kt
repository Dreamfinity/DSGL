package org.dreamfinity.dsgl.core.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CssLengthTests {
    @Test
    fun `parses supported units`() {
        assertEquals(CssLength(12f, CssUnit.Px), parseCssLength("12px"))
        assertEquals(CssLength(1.25f, CssUnit.Em), parseCssLength("1.25em"))
        assertEquals(CssLength(50f, CssUnit.Vw), parseCssLength("50vw"))
        assertEquals(CssLength(70f, CssUnit.Vh), parseCssLength("70vh"))
        assertEquals(CssLength(100f, CssUnit.Percent), parseCssLength("100%"))
    }

    @Test
    fun `unitless zero is accepted but non-zero is rejected`() {
        assertEquals(CssLength.ZERO_PX, parseCssLength("0"))
        val error = assertFailsWith<IllegalStateException> {
            parseCssLength("12")
        }
        assertTrue(error.message?.contains("Expected explicit unit") == true)
    }

    @Test
    fun `unknown units are rejected`() {
        val error = assertFailsWith<IllegalStateException> {
            parseCssLength("1rem")
        }
        assertTrue(error.message?.contains("Unknown length unit") == true)
    }

    @Test
    fun `rejects whitespace between number and unit`() {
        assertFailsWith<IllegalStateException> {
            parseCssLength("12 px")
        }
    }

    @Test
    fun `parses spacing shorthand with mixed units`() {
        val insets = parseSpacingLengthShorthand(
            raw = "1em 8px 2vh 5%",
            allowNegative = false
        )

        assertEquals(CssUnit.Em, insets.top.unit)
        assertEquals(CssUnit.Px, insets.right.unit)
        assertEquals(CssUnit.Vh, insets.bottom.unit)
        assertEquals(CssUnit.Percent, insets.left.unit)
    }

    @Test
    fun `resolves percent against horizontal and vertical axes`() {
        val context = LengthResolveContext(
            viewportWidthPx = 400f,
            viewportHeightPx = 200f,
            containingBlockWidthPx = 240f,
            containingBlockHeightPx = 80f,
            currentFontSizePx = 10f,
            inheritedFontSizePx = 12f
        )

        val horizontal = CssLength(50f, CssUnit.Percent)
            .resolvePx(context, LengthPercentBase.ContainerWidth)
        val vertical = CssLength(50f, CssUnit.Percent)
            .resolvePx(context, LengthPercentBase.ContainerHeight)

        assertEquals(120f, horizontal)
        assertEquals(40f, vertical)
    }

    @Test
    fun `resolves vw and vh against viewport`() {
        val context = LengthResolveContext(
            viewportWidthPx = 320f,
            viewportHeightPx = 180f
        )

        val vw = CssLength(10f, CssUnit.Vw).resolvePx(context, LengthPercentBase.ContainerWidth)
        val vh = CssLength(20f, CssUnit.Vh).resolvePx(context, LengthPercentBase.ContainerHeight)

        assertEquals(32f, vw)
        assertEquals(36f, vh)
    }

    @Test
    fun `em uses current font size and inherited base for font-size percent`() {
        val context = LengthResolveContext(
            viewportWidthPx = 0f,
            viewportHeightPx = 0f,
            containingBlockWidthPx = 0f,
            containingBlockHeightPx = 0f,
            currentFontSizePx = 12f,
            inheritedFontSizePx = 20f
        )

        val paddingEm = CssLength(1.5f, CssUnit.Em)
            .resolvePx(context, LengthPercentBase.ContainerWidth)
        val fontPercent = CssLength(125f, CssUnit.Percent)
            .resolvePx(context, LengthPercentBase.InheritedFontSize)

        assertEquals(18f, paddingEm)
        assertEquals(25f, fontPercent)
    }
}
