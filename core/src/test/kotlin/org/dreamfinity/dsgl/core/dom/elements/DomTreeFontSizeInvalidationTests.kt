package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DomTreeFontSizeInvalidationTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 10

            override fun measureText(text: String): Int = text.length * 6

            override fun measureText(text: String, fontId: String?, fontSize: Int?): Int {
                val glyphWidth = ((fontSize ?: 16) * 0.5f).roundToInt().coerceAtLeast(1)
                return text.length * glyphWidth
            }

            override fun fontHeight(fontId: String?, fontSize: Int?): Int = (fontSize ?: fontHeight).coerceAtLeast(1)

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanup() {
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `inspector font-size override relayouts text before repaint`() {
        val root =
            ContainerNode(key = "inspector.font-size.root").apply {
                display = Display.Block
                width = 240
            }
        val node =
            TextNode(TextSource.Static("inspector"), key = "inspector.font-size.text")
                .apply {
                    fontSize = 16
                }.applyParent(root)
        val tree = DomTree(root)

        tree.render(ctx, 240, 80)
        val baselineHeight = node.bounds.height

        StyleEngine
            .setInspectorOverrideLiteral(node, StyleProperty.FONT_SIZE, "32px")
            .getOrThrow()
        val commands = tree.paint(ctx)
        val draw = commands.filterIsInstance<RenderCommand.DrawText>().single()

        assertEquals(32, node.appliedComputedStyleSnapshot()?.fontSize)
        assertEquals(32, draw.fontSize)
        assertTrue(node.bounds.height > baselineHeight)
    }
}
