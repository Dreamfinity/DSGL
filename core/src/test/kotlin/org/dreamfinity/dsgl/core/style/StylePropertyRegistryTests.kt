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
    fun `registry includes text style editable properties`() {
        val properties = StylePropertyRegistry.all.map { it.property }.toSet()
        assertTrue(StyleProperty.FOREGROUND_COLOR in properties)
        assertTrue(StyleProperty.FONT_SIZE in properties)
        assertTrue(StyleProperty.TEXT_WRAP in properties)
        assertTrue(StyleProperty.TEXT_FORMATTING in properties)
        assertTrue(StyleProperty.ALIGN in properties)
    }
}
