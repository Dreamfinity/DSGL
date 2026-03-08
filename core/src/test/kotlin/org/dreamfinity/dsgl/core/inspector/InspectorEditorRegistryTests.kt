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
    fun `parses numeric literal into number and unit`() {
        val parsed = InspectorEditorRegistry.parseNumberUnit("12.5vh")
        assertNotNull(parsed)
        assertEquals("12.5", parsed.numberText)
        assertEquals(CssUnit.Vh, parsed.unit)
        assertFalse(parsed.isAuto)
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
