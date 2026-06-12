package org.dreamfinity.dsgl.core.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class StyleValueParsingTransformTests {
    @Test
    fun `parse transform resolves translate scale rotate`() {
        val parsed = parseTransform("translate(12, -3) scale(1.5, 0.5) rotate(45deg)")
        assertEquals(12f, parsed.translateX)
        assertEquals(-3f, parsed.translateY)
        assertEquals(1.5f, parsed.scaleX)
        assertEquals(0.5f, parsed.scaleY)
        assertEquals(45f, parsed.rotateDeg)
    }

    @Test
    fun `parse transform origin supports percentages`() {
        val origin = parseTransformOrigin("25% 75%")
        assertEquals(0.25f, origin.originX)
        assertEquals(0.75f, origin.originY)
    }

    @Test
    fun `parse opacity clamps between zero and one`() {
        assertEquals(1f, parseOpacity("2.0"))
        assertEquals(0f, parseOpacity("-1.0"))
        assertTrue(parseOpacity("0.42") in 0.41f..0.43f)
    }

    @Test
    fun `invalid transform literal fails validation`() {
        assertFails {
            validateLiteralForProperty(StyleProperty.TRANSFORM, "bogus(1)")
        }
    }
}
