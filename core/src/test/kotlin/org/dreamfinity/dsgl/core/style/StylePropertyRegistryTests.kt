package org.dreamfinity.dsgl.core.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StylePropertyRegistryTests {
    @Test
    fun `registry defines descriptor for every style property`() {
        val registered = StylePropertyRegistry.all.map { it.property }.toSet()
        assertEquals(StyleProperty.entries.toSet(), registered)
        assertEquals(StyleProperty.entries.size, StylePropertyRegistry.all.size)
    }

    @Test
    fun `every descriptor is discoverable by property`() {
        StyleProperty.entries.forEach { property ->
            val descriptor = StylePropertyRegistry.descriptor(property)
            assertEquals(property, descriptor.property)
            if (descriptor.valueType == StyleEditorValueType.EnumChoice ||
                descriptor.valueType == StyleEditorValueType.ColorHex ||
                descriptor.valueType == StyleEditorValueType.StringPreset
            ) {
                assertTrue(descriptor.enumOptions.isNotEmpty())
            }
        }
    }

    @Test
    fun `position and z-index expose property-aware grammar metadata`() {
        val position = StylePropertyRegistry.descriptor(StyleProperty.POSITION)
        assertEquals(StyleValueGrammarKind.Enum, position.grammarKind)
        assertEquals(StyleInspectorEditorKind.EnumSelect, position.inspectorEditorKind)
        assertEquals("absolute", StylePropertyRegistry.parseEnumLiteral(StyleProperty.POSITION, "Absolute"))

        val zIndex = StylePropertyRegistry.descriptor(StyleProperty.Z_INDEX)
        assertEquals(StyleValueGrammarKind.UnitlessInt, zIndex.grammarKind)
        assertEquals(StyleInspectorEditorKind.NumericInput, zIndex.inspectorEditorKind)
        assertEquals(-7, StylePropertyRegistry.parseUnitlessIntLiteral(StyleProperty.Z_INDEX, "-7"))
    }

    @Test
    fun `line-height uses dedicated grammar metadata`() {
        val lineHeight = StylePropertyRegistry.descriptor(StyleProperty.LINE_HEIGHT)
        assertEquals(StyleEditorValueType.LineHeight, lineHeight.valueType)
        assertEquals(StyleValueGrammarKind.LineHeight, lineHeight.grammarKind)
        assertEquals(StyleInspectorEditorKind.NumericInput, lineHeight.inspectorEditorKind)
        assertEquals(listOf("normal"), lineHeight.enumOptions)

        assertEquals(
            LineHeightValue.Normal,
            StylePropertyRegistry.parseLineHeightLiteral(StyleProperty.LINE_HEIGHT, "normal")
        )
        assertEquals(
            LineHeightValue.Length(CssLength(24f, CssUnit.Px)),
            StylePropertyRegistry.parseLineHeightLiteral(StyleProperty.LINE_HEIGHT, "24px")
        )
    }

    @Test
    fun `offset properties use length-like grammar and numeric inspector editor`() {
        listOf(
            StyleProperty.LEFT,
            StyleProperty.TOP,
            StyleProperty.RIGHT,
            StyleProperty.BOTTOM
        ).forEach { property ->
            val descriptor = StylePropertyRegistry.descriptor(property)
            assertEquals(StyleValueGrammarKind.LengthLike, descriptor.grammarKind)
            assertEquals(StyleInspectorEditorKind.NumericInput, descriptor.inspectorEditorKind)
            assertEquals(StyleEditorValueType.OptionalLengthPx, descriptor.valueType)
        }
    }

    @Test
    fun `registry includes text style editable properties`() {
        val properties = StylePropertyRegistry.all.map { it.property }.toSet()
        assertTrue(StyleProperty.FOREGROUND_COLOR in properties)
        assertTrue(StyleProperty.FONT_SIZE in properties)
        assertTrue(StyleProperty.LINE_HEIGHT in properties)
        assertTrue(StyleProperty.TEXT_WRAP in properties)
        assertTrue(StyleProperty.TEXT_FORMATTING in properties)
        assertTrue(StyleProperty.ALIGN in properties)
    }
}
