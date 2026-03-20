package org.dreamfinity.dsgl.core.inspector

import org.dreamfinity.dsgl.core.style.CssUnit
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InspectorEditorRegistryTests {
    @Test
    fun `maps enum and font properties to dropdown editors`() {
        val display = InspectorEditorRegistry.describe(
            property = StyleProperty.DISPLAY,
            literal = "block",
            expression = StyleExpression.Literal("block")
        )
        assertEquals(InspectorEditorKind.EnumSelect, display.kind)
        assertTrue(display.options.isNotEmpty())

        val font = InspectorEditorRegistry.describe(
            property = StyleProperty.FONT_ID,
            literal = "minecraft",
            expression = StyleExpression.Literal("minecraft")
        )
        assertEquals(InspectorEditorKind.FontSelect, font.kind)
        assertTrue(font.options.isNotEmpty())
    }

    @Test
    fun `position is exposed as enum select with explicit options`() {
        val position = InspectorEditorRegistry.describe(
            property = StyleProperty.POSITION,
            literal = "relative",
            expression = StyleExpression.Literal("relative")
        )

        assertEquals(InspectorEditorKind.EnumSelect, position.kind)
        assertEquals(listOf("static", "relative", "absolute", "fixed"), position.options)
    }

    @Test
    fun `z-index uses unitless numeric grammar in inspector`() {
        val descriptor = InspectorEditorRegistry.describe(
            property = StyleProperty.Z_INDEX,
            literal = "5",
            expression = StyleExpression.Literal("5")
        )
        assertEquals(InspectorEditorKind.NumericInput, descriptor.kind)
        assertFalse(descriptor.supportsUnits)

        val parsed = InspectorEditorRegistry.parseNumericLiteral(StyleProperty.Z_INDEX, "5")
        assertNotNull(parsed)
        assertEquals("5", parsed.numberText)
        assertEquals(null, parsed.unit)
        assertFalse(parsed.isAuto)

        assertEquals("7", InspectorEditorRegistry.formatNumericLiteral(StyleProperty.Z_INDEX, "7", "px"))
    }

    @Test
    fun `length-like numeric properties keep unit-aware grammar`() {
        val width = InspectorEditorRegistry.describe(
            property = StyleProperty.WIDTH,
            literal = "12px",
            expression = StyleExpression.Literal("12px")
        )
        assertEquals(InspectorEditorKind.NumericInput, width.kind)
        assertTrue(width.supportsUnits)

        val parsed = InspectorEditorRegistry.parseNumericLiteral(StyleProperty.WIDTH, "12.5vh")
        assertNotNull(parsed)
        assertEquals("12.5", parsed.numberText)
        assertEquals(CssUnit.Vh, parsed.unit)

        assertEquals("18em", InspectorEditorRegistry.formatNumericLiteral(StyleProperty.WIDTH, "18", "em"))
    }

    @Test
    fun `parses auto as special numeric state`() {
        val parsed = InspectorEditorRegistry.parseNumberUnit("auto")
        assertNotNull(parsed)
        assertTrue(parsed.isAuto)
        assertEquals(CssUnit.Px, parsed.unit)
    }

    @Test
    fun `formats numeric and unit back to css literal`() {
        assertEquals("18em", InspectorEditorRegistry.formatNumberUnit("18", CssUnit.Em))
        assertEquals("0px", InspectorEditorRegistry.formatNumberUnit("bad", CssUnit.Px))
    }

    @Test
    fun `detects color-like values for preview`() {
        val stringColor = InspectorEditorRegistry.describe(
            property = StyleProperty.BACKGROUND_IMAGE,
            literal = "#AABBCC",
            expression = StyleExpression.Literal("#AABBCC")
        )
        assertEquals(InspectorEditorKind.StringInput, stringColor.kind)
        assertTrue(stringColor.showColorPreview)

        val plain = InspectorEditorRegistry.describe(
            property = StyleProperty.BACKGROUND_IMAGE,
            literal = "textures/gui/options_background.png",
            expression = StyleExpression.Literal("textures/gui/options_background.png")
        )
        assertFalse(plain.showColorPreview)
    }
}

