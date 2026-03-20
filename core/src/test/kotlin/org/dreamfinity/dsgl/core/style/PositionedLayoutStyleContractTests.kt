package org.dreamfinity.dsgl.core.style

import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PositionedLayoutStyleContractTests {
    @Test
    fun `position and offset properties are exposed in style key registry`() {
        assertEquals(StyleProperty.POSITION, StyleProperty.fromKeyOrNull("position"))
        assertEquals(StyleProperty.LEFT, StyleProperty.fromKeyOrNull("left"))
        assertEquals(StyleProperty.TOP, StyleProperty.fromKeyOrNull("top"))
        assertEquals(StyleProperty.RIGHT, StyleProperty.fromKeyOrNull("right"))
        assertEquals(StyleProperty.BOTTOM, StyleProperty.fromKeyOrNull("bottom"))
        assertEquals(StyleProperty.Z_INDEX, StyleProperty.fromKeyOrNull("z-index"))
        assertEquals(StyleProperty.Z_INDEX, StyleProperty.fromKeyOrNull("zindex"))
    }

    @Test
    fun `position z-index and offsets parse through style validation`() {
        listOf("static", "relative", "absolute", "fixed").forEach { value ->
            validateLiteralForProperty(StyleProperty.POSITION, value)
        }
        validateLiteralForProperty(StyleProperty.Z_INDEX, "-7")
        validateLiteralForProperty(StyleProperty.LEFT, "12px")
        validateLiteralForProperty(StyleProperty.TOP, "1.5em")
        validateLiteralForProperty(StyleProperty.RIGHT, "auto")
        validateLiteralForProperty(StyleProperty.BOTTOM, "0")
    }

    @Test
    fun `position z-index and offsets reach computed style`() {
        val node = ContainerNode(key = "positioned-style-node")
        val expectations = listOf(
            "static" to PositionMode.Static,
            "relative" to PositionMode.Relative,
            "absolute" to PositionMode.Absolute,
            "fixed" to PositionMode.Fixed
        )

        expectations.forEach { (literal, expectedPosition) ->
            node.inlineStyleDeclarations = StyleDeclarations().apply {
                set(StyleProperty.POSITION, StyleExpression.Literal(literal))
                set(StyleProperty.LEFT, StyleExpression.Literal("10px"))
                set(StyleProperty.TOP, StyleExpression.Literal("2em"))
                set(StyleProperty.RIGHT, StyleExpression.Literal("auto"))
                set(StyleProperty.BOTTOM, StyleExpression.Literal("0"))
                set(StyleProperty.Z_INDEX, StyleExpression.Literal("13"))
            }
            StyleEngine.clearCache()
            StyleEngine.applyStylesRecursively(node)
            val computed = node.appliedComputedStyleSnapshot()
            assertNotNull(computed)
            assertEquals(expectedPosition, computed.position)
            assertEquals(13, computed.zIndex)
            assertEquals("10px", computed.left?.toCssLiteral())
            assertEquals("2em", computed.top?.toCssLiteral())
            assertEquals(null, computed.right)
            assertEquals("0px", computed.bottom?.toCssLiteral())
        }
    }
}

