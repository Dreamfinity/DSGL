package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Insets
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.dispatchClick
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.StyleDeclarations
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PositionedLayoutFixedClippingBehaviorTests {
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
    fun `promoted fixed escapes ancestor overflow clipping but keeps paint hit symmetry`() {
        val fixedColor = 0x00_33_99_CC
        val root = ContainerNode(key = "fixed-overflow-root", stackLayout = true)
        val clippedAncestor = ContainerNode(key = "fixed-overflow-ancestor").apply {
            width = 90
            height = 46
            margin = Insets(top = 8, right = 0, bottom = 0, left = 12)
            overflowY = Overflow.Auto
            stackLayout = true
        }.applyParent(root)
        ContainerNode(key = "fixed-overflow-filler").apply {
            width = 80
            height = 220
        }.applyParent(clippedAncestor)

        var fixedClicks = 0
        val fixed = ButtonNode("fixed", key = "fixed-overflow-node", backgroundColor = fixedColor).apply {
            width = 44
            height = 20
            zIndex = 9_999
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "140px",
                StyleProperty.TOP to "90px"
            )
            onClick { fixedClicks += 1 }
        }.applyParent(clippedAncestor)

        val tree = DomTree(root)
        tree.render(ctx, 220, 160)
        val commands = tree.paint(ctx)

        assertTrue(fixed.containsGlobalPoint(145, 95))
        assertTrue(dispatchClick(root, MouseClickEvent(145, 95, MouseButton.LEFT)))
        assertEquals(1, fixedClicks)

        val fixedDrawIndex = commands.indexOfFirst { command ->
            command is RenderCommand.DrawRect &&
                command.color == fixedColor &&
                command.x == 140 &&
                command.y == 90
        }
        assertTrue(fixedDrawIndex > 0, "Expected fixed draw rect in root-level paint stream")

        val fixedClip = commands[fixedDrawIndex - 1] as? RenderCommand.PushClip
        assertNotNull(fixedClip, "Expected explicit fixed clip immediately before fixed draw")
        val rootViewport = root.scrollContainerState().viewportRect
        assertEquals(rootViewport.x, fixedClip.x)
        assertEquals(rootViewport.y, fixedClip.y)
        assertEquals(rootViewport.width, fixedClip.width)
        assertEquals(rootViewport.height, fixedClip.height)
    }

    @Test
    fun `promoted fixed still respects root viewport clipping`() {
        val root = ContainerNode(key = "fixed-root-clip-root")
        val fixed = ButtonNode("fixed", key = "fixed-root-clip-node").apply {
            width = 50
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "180px",
                StyleProperty.TOP to "24px"
            )
        }.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 200, 120)
        val commands = tree.paint(ctx)

        assertTrue(fixed.containsGlobalPoint(195, 30))
        assertFalse(
            fixed.containsGlobalPoint(225, 30),
            "Point inside fixed bounds but outside root viewport must be clipped"
        )

        val drawIndex = commands.indexOfFirst { command ->
            command is RenderCommand.DrawRect && command.x == 180 && command.y == 24
        }
        assertTrue(drawIndex > 0)
        val clipBeforeDraw = commands[drawIndex - 1] as? RenderCommand.PushClip
        assertNotNull(clipBeforeDraw)
        val rootViewport = root.scrollContainerState().viewportRect
        assertEquals(rootViewport.x, clipBeforeDraw.x)
        assertEquals(rootViewport.y, clipBeforeDraw.y)
        assertEquals(rootViewport.width, clipBeforeDraw.width)
        assertEquals(rootViewport.height, clipBeforeDraw.height)
    }

    @Test
    fun `non-fixed descendants still obey ancestor overflow clipping`() {
        val root = ContainerNode(key = "nonfixed-clip-root", stackLayout = true)
        val clippedAncestor = ContainerNode(key = "nonfixed-clip-ancestor").apply {
            width = 90
            height = 46
            margin = Insets(top = 8, right = 0, bottom = 0, left = 12)
            overflowY = Overflow.Scroll
        }.applyParent(root)

        var clicks = 0
        val absolute = ButtonNode("absolute", key = "nonfixed-clip-node").apply {
            width = 44
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "absolute",
                StyleProperty.LEFT to "140px",
                StyleProperty.TOP to "90px"
            )
            onClick { clicks += 1 }
        }.applyParent(clippedAncestor)

        val tree = DomTree(root)
        tree.render(ctx, 220, 160)

        assertEquals(140, absolute.bounds.x)
        assertEquals(90, absolute.bounds.y)
        assertFalse(absolute.containsGlobalPoint(145, 95))
        assertFalse(dispatchClick(root, MouseClickEvent(145, 95, MouseButton.LEFT)))
        assertEquals(0, clicks)
    }

    private fun styleDeclarations(vararg entries: Pair<StyleProperty, String>): StyleDeclarations {
        return StyleDeclarations().apply {
            entries.forEach { (property, literal) ->
                set(property, StyleExpression.Literal(literal))
            }
        }
    }
}
