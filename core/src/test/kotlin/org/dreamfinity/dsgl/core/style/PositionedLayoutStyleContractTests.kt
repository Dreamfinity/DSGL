package org.dreamfinity.dsgl.core.style

import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PositionedLayoutStyleContractTests {
    @Test
    fun `position and z-index properties are exposed in style key registry`() {
        assertEquals(StyleProperty.POSITION, StyleProperty.fromKeyOrNull("position"))
        assertEquals(StyleProperty.Z_INDEX, StyleProperty.fromKeyOrNull("z-index"))
        assertEquals(StyleProperty.Z_INDEX, StyleProperty.fromKeyOrNull("zindex"))
    }

    @Test
    fun `position values parse through style validation`() {
        listOf("static", "relative", "absolute", "fixed").forEach { value ->
            validateLiteralForProperty(StyleProperty.POSITION, value)
        }
        validateLiteralForProperty(StyleProperty.Z_INDEX, "-7")
    }

    @Test
    fun `position and z-index reach computed style`() {
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
                set(StyleProperty.Z_INDEX, StyleExpression.Literal("13"))
            }
            StyleEngine.clearCache()
            StyleEngine.applyStylesRecursively(node)
            val computed = node.appliedComputedStyleSnapshot()
            assertNotNull(computed)
            assertEquals(expectedPosition, computed.position)
            assertEquals(13, computed.zIndex)
        }
    }
}

