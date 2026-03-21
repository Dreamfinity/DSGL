package org.dreamfinity.dsgl.core.style

import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.TextNode
import org.dreamfinity.dsgl.core.dom.elements.TextSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class LineHeightStyleContractTests {
    @AfterTest
    fun cleanup() {
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `line-height validates normal and explicit length values`() {
        validateLiteralForProperty(StyleProperty.LINE_HEIGHT, "normal")
        validateLiteralForProperty(StyleProperty.LINE_HEIGHT, "18px")
        validateLiteralForProperty(StyleProperty.LINE_HEIGHT, "1.4em")
    }

    @Test
    fun `line-height reaches computed style from inline declarations`() {
        val node = ContainerNode(key = "line-height-node")
        node.inlineStyleDeclarations = StyleDeclarations().apply {
            set(StyleProperty.LINE_HEIGHT, StyleExpression.Literal("22px"))
        }

        StyleEngine.clearCache()
        StyleEngine.applyStylesRecursively(node)

        val computed = node.appliedComputedStyleSnapshot()
        assertNotNull(computed)
        val lineHeight = assertIs<LineHeightValue.Length>(computed.lineHeight)
        assertEquals("22px", lineHeight.value.toCssLiteral())
    }

    @Test
    fun `line-height inherits through computed style`() {
        val root = ContainerNode(key = "root")
        root.inlineStyleDeclarations = StyleDeclarations().apply {
            set(StyleProperty.LINE_HEIGHT, StyleExpression.Literal("24px"))
        }
        val child = TextNode(TextSource.Static("child"), key = "child").applyParent(root)

        StyleEngine.clearCache()
        StyleEngine.applyStylesRecursively(root)

        val computed = child.appliedComputedStyleSnapshot()
        assertNotNull(computed)
        val inherited = assertIs<LineHeightValue.Length>(computed.lineHeight)
        assertEquals("24px", inherited.value.toCssLiteral())
    }
}
