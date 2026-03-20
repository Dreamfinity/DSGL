package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.dispatchClick
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleDeclarations
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PositionedLayoutRelativeBehaviorTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @AfterTest
    fun cleanup() {
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `relative applies visible offset to final geometry`() {
        val root = ContainerNode(key = "root")
        val child = ContainerNode(key = "relative-child").apply {
            width = 40
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "relative",
                StyleProperty.LEFT to "12px",
                StyleProperty.TOP to "8px"
            )
        }
        child.applyParent(root)

        renderTree(root)

        assertFalse(child.containsGlobalPoint(child.bounds.x + 2, child.bounds.y + 2))
        assertTrue(child.containsGlobalPoint(child.bounds.x + 14, child.bounds.y + 10))
    }

    @Test
    fun `relative preserves normal-flow layout slot`() {
        val root = ContainerNode(key = "flow-root")
        val first = ContainerNode(key = "first-relative").apply {
            width = 50
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "relative",
                StyleProperty.LEFT to "24px",
                StyleProperty.TOP to "16px"
            )
        }
        val second = ContainerNode(key = "second-static").apply {
            width = 30
            height = 12
        }
        first.applyParent(root)
        second.applyParent(root)

        renderTree(root)

        assertEquals(first.bounds.y + first.bounds.height, second.bounds.y)
    }

    @Test
    fun `relative hit-testing tracks visible shifted rect`() {
        val root = ContainerNode(key = "hit-root")
        var clicks = 0
        val button = ButtonNode("relative", key = "relative-button").apply {
            width = 40
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "relative",
                StyleProperty.LEFT to "30px"
            )
            onClick { clicks += 1 }
        }
        button.applyParent(root)

        renderTree(root)

        assertFalse(dispatchClick(root, MouseClickEvent(button.bounds.x + 2, button.bounds.y + 2, MouseButton.LEFT)))
        assertEquals(0, clicks)

        assertTrue(dispatchClick(root, MouseClickEvent(button.bounds.x + 32, button.bounds.y + 2, MouseButton.LEFT)))
        assertEquals(1, clicks)
    }

    @Test
    fun `relative horizontal precedence prefers left over right`() {
        val root = ContainerNode(key = "horizontal-root")
        val child = ContainerNode(key = "horizontal-relative").apply {
            width = 30
            height = 16
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "relative",
                StyleProperty.LEFT to "20px",
                StyleProperty.RIGHT to "5px"
            )
        }
        child.applyParent(root)

        renderTree(root)

        assertFalse(child.containsGlobalPoint(child.bounds.x + 2, child.bounds.y + 2))
        assertTrue(child.containsGlobalPoint(child.bounds.x + 22, child.bounds.y + 2))
    }

    @Test
    fun `relative vertical precedence prefers top over bottom`() {
        val root = ContainerNode(key = "vertical-root")
        val child = ContainerNode(key = "vertical-relative").apply {
            width = 30
            height = 16
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "relative",
                StyleProperty.TOP to "15px",
                StyleProperty.BOTTOM to "4px"
            )
        }
        child.applyParent(root)

        renderTree(root)

        assertFalse(child.containsGlobalPoint(child.bounds.x + 2, child.bounds.y + 2))
        assertTrue(child.containsGlobalPoint(child.bounds.x + 2, child.bounds.y + 17))
    }

    @Test
    fun `static continues to ignore offsets for visible behavior`() {
        val root = ContainerNode(key = "static-root")
        val child = ContainerNode(key = "static-child").apply {
            width = 40
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "static",
                StyleProperty.LEFT to "50px",
                StyleProperty.TOP to "30px"
            )
        }
        child.applyParent(root)

        renderTree(root)

        assertTrue(child.containsGlobalPoint(child.bounds.x + 2, child.bounds.y + 2))
        assertFalse(child.containsGlobalPoint(child.bounds.x + 52, child.bounds.y + 32))
    }

    @Test
    fun `inspector overrides reposition for relative but not static`() {
        val root = ContainerNode(key = "inspector-root")
        val child = ContainerNode(key = "inspector-child").apply {
            width = 40
            height = 20
        }
        child.applyParent(root)
        val tree = DomTree(root)
        tree.render(ctx, 220, 140)

        StyleEngine.setInspectorOverrideLiteral(child, StyleProperty.POSITION, "static").getOrThrow()
        StyleEngine.setInspectorOverrideLiteral(child, StyleProperty.LEFT, "30px").getOrThrow()
        StyleEngine.setInspectorOverrideLiteral(child, StyleProperty.TOP, "10px").getOrThrow()
        tree.render(ctx, 220, 140)

        assertTrue(child.containsGlobalPoint(child.bounds.x + 2, child.bounds.y + 2))
        assertFalse(child.containsGlobalPoint(child.bounds.x + 52, child.bounds.y + 24))

        StyleEngine.setInspectorOverrideLiteral(child, StyleProperty.POSITION, "relative").getOrThrow()
        tree.render(ctx, 220, 140)

        assertFalse(child.containsGlobalPoint(child.bounds.x + 2, child.bounds.y + 2))
        assertTrue(child.containsGlobalPoint(child.bounds.x + 52, child.bounds.y + 24))
    }

    private fun renderTree(root: ContainerNode) {
        DomTree(root).render(ctx, 240, 160)
    }

    private fun styleDeclarations(vararg entries: Pair<StyleProperty, String>): StyleDeclarations {
        return StyleDeclarations().apply {
            entries.forEach { (property, literal) ->
                set(property, StyleExpression.Literal(literal))
            }
        }
    }
}
