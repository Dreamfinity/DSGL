package org.dreamfinity.dsgl.core.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StyleDeclarationsHashTests {
    @Test
    fun `stable hash is independent from insertion order`() {
        val first =
            StyleDeclarations().apply {
                set(StyleProperty.WIDTH, StyleExpression.Literal("120"))
                set(StyleProperty.DISPLAY, StyleExpression.Literal("flex"))
                set(StyleProperty.GAP, StyleExpression.Literal("4"))
            }
        val second =
            StyleDeclarations().apply {
                set(StyleProperty.GAP, StyleExpression.Literal("4"))
                set(StyleProperty.WIDTH, StyleExpression.Literal("120"))
                set(StyleProperty.DISPLAY, StyleExpression.Literal("flex"))
            }

        assertEquals(first.toStableHash(), second.toStableHash())
    }

    @Test
    fun `stable hash changes when declaration value changes`() {
        val declarations =
            StyleDeclarations().apply {
                set(StyleProperty.WIDTH, StyleExpression.Literal("120"))
                set(StyleProperty.DISPLAY, StyleExpression.Literal("block"))
            }
        val before = declarations.toStableHash()

        declarations.set(StyleProperty.DISPLAY, StyleExpression.Literal("flex"))

        assertNotEquals(before, declarations.toStableHash())
    }
}
