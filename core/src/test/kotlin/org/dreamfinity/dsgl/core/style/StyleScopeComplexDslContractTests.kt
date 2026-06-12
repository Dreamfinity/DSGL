package org.dreamfinity.dsgl.core.style

import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dsl.StyleBorder
import org.dreamfinity.dsgl.core.dsl.StyleScope
import kotlin.test.Test
import kotlin.test.assertEquals

class StyleScopeComplexDslContractTests {
    @Test
    fun `border supports property assignment as complex value`() {
        val node = ContainerNode(key = "style.border.property")

        StyleScope(node).apply {
            border = StyleBorder(width = 3.px, color = 0xFF9EC4E3.toInt())
        }

        assertLiteral(node, StyleProperty.BORDER_WIDTH, "3px")
        assertLiteral(node, StyleProperty.BORDER_COLOR, "#FF9EC4E3")
    }

    @Test
    fun `border supports dsl function forms via object and builder scope`() {
        val objectNode = ContainerNode(key = "style.border.function.object")
        val builderNode = ContainerNode(key = "style.border.function.builder")

        StyleScope(objectNode).apply {
            border(StyleBorder(width = 2.px, color = 0xFF445566.toInt()))
        }
        StyleScope(builderNode).apply {
            border {
                width = 2.px
                color = 0xFF445566.toInt()
            }
        }

        assertLiteral(objectNode, StyleProperty.BORDER_WIDTH, "2px")
        assertLiteral(objectNode, StyleProperty.BORDER_COLOR, "#FF445566")
        assertLiteral(builderNode, StyleProperty.BORDER_WIDTH, "2px")
        assertLiteral(builderNode, StyleProperty.BORDER_COLOR, "#FF445566")
    }

    @Test
    fun `transform-origin supports property assignment and dsl function forms`() {
        val propertyNode = ContainerNode(key = "style.transform-origin.property")
        val valueFnNode = ContainerNode(key = "style.transform-origin.function.value")
        val builderFnNode = ContainerNode(key = "style.transform-origin.function.builder")
        val paramsFnNode = ContainerNode(key = "style.transform-origin.function.params")

        StyleScope(propertyNode).apply {
            transformOrigin = TransformOrigin(0.5f, 0.25f)
        }
        StyleScope(valueFnNode).apply {
            transformOrigin(TransformOrigin(0.5f, 0.25f))
        }
        StyleScope(builderFnNode).apply {
            transformOrigin {
                x = 0.5f
                y = 0.25f
            }
        }
        StyleScope(paramsFnNode).apply {
            transformOrigin(0.5f, 0.25f)
        }

        assertLiteral(propertyNode, StyleProperty.TRANSFORM_ORIGIN, "0.5 0.25")
        assertLiteral(valueFnNode, StyleProperty.TRANSFORM_ORIGIN, "0.5 0.25")
        assertLiteral(builderFnNode, StyleProperty.TRANSFORM_ORIGIN, "0.5 0.25")
        assertLiteral(paramsFnNode, StyleProperty.TRANSFORM_ORIGIN, "0.5 0.25")
    }

    @Test
    fun `transform-origin params function preserves clamping behavior`() {
        val node = ContainerNode(key = "style.transform-origin.clamped")

        StyleScope(node).apply {
            transformOrigin(-0.2f, 1.4f)
        }

        assertLiteral(node, StyleProperty.TRANSFORM_ORIGIN, "0.0 1.0")
    }

    @Test
    fun `margin builder supports side-based configuration`() {
        val node = ContainerNode(key = "style.margin.builder")

        StyleScope(node).apply {
            margin {
                top = 1.px
                right = 2.px
                bottom = 3.px
                left = 4.px
            }
        }

        assertLiteral(node, StyleProperty.MARGIN, "1px 2px 3px 4px")
    }

    @Test
    fun `padding builder supports all horizontal vertical helpers`() {
        val node = ContainerNode(key = "style.padding.builder")

        StyleScope(node).apply {
            padding {
                all(1.px)
                horizontal(4.px)
                vertical(2.px)
            }
        }

        assertLiteral(node, StyleProperty.PADDING, "2px 4px 2px 4px")
    }

    @Test
    fun `line-height builder supports normal and explicit length`() {
        val normalNode = ContainerNode(key = "style.line-height.builder.normal")
        val lengthNode = ContainerNode(key = "style.line-height.builder.length")

        StyleScope(normalNode).apply {
            lineHeight {
                normal()
            }
        }
        StyleScope(lengthNode).apply {
            lineHeight {
                length(24.px)
            }
        }

        assertLiteral(normalNode, StyleProperty.LINE_HEIGHT, "normal")
        assertLiteral(lengthNode, StyleProperty.LINE_HEIGHT, "24px")
    }

    @Test
    fun `overflow builder supports axis configuration`() {
        val node = ContainerNode(key = "style.overflow.builder")

        StyleScope(node).apply {
            overflow {
                x = Overflow.Hidden
                y = Overflow.Scroll
            }
        }

        assertLiteral(node, StyleProperty.OVERFLOW_X, "hidden")
        assertLiteral(node, StyleProperty.OVERFLOW_Y, "scroll")
    }

    @Test
    fun `inset builder supports partial updates`() {
        val node = ContainerNode(key = "style.inset.builder")

        StyleScope(node).apply {
            inset {
                left = 5.px
                top = 3.px
            }
        }

        assertLiteral(node, StyleProperty.LEFT, "5px")
        assertLiteral(node, StyleProperty.TOP, "3px")
        val rightValue = node.inlineStyleDeclarations.get(StyleProperty.RIGHT)
        val bottomValue = node.inlineStyleDeclarations.get(StyleProperty.BOTTOM)
        assertEquals(null, rightValue)
        assertEquals(null, bottomValue)
    }

    private fun assertLiteral(node: ContainerNode, property: StyleProperty, expected: String) {
        val value = (node.inlineStyleDeclarations.get(property) as? StyleExpression.Literal)?.value
        assertEquals(expected, value)
    }
}
