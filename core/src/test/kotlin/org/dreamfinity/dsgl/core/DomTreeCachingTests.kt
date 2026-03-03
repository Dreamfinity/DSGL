package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.TextNode
import org.dreamfinity.dsgl.core.dom.elements.TextSource
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DomTreeCachingTests {
    private class TestRectNode(
        private val color: Int,
        key: Any? = null
    ) : DOMNode(key) {
        var throwOnBuild: Boolean = false
        override val styleType: String = "test-rect"

        override fun measure(ctx: UiMeasureContext): Size = Size(10, 10)

        override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
            if (throwOnBuild) {
                error("test command failure")
            }
            out += RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, color)
        }
    }

    private val ctx = object : UiMeasureContext {
        override fun measureText(text: String): Int = text.length * 6
        override fun measureText(text: String, fontId: String?, fontSize: Int?): Int = text.length * 6
        override val fontHeight: Int = 9
        override fun fontHeight(fontId: String?, fontSize: Int?): Int = 9
        override fun paint(commands: List<org.dreamfinity.dsgl.core.render.RenderCommand>) = Unit
    }

    @AfterTest
    fun cleanup() {
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `paint command list is reused when nothing changed`() {
        val root = ContainerNode(key = "root")
        TextNode(TextSource.Static("hello"), key = "text").applyParent(root)
        val tree = DomTree(root)

        tree.render(ctx, 320, 180)
        val first = tree.paint(ctx)
        val statsAfterFirst = tree.paintStats()
        val second = tree.paint(ctx)
        val statsAfterSecond = tree.paintStats()

        assertSame(first, second)
        assertTrue(statsAfterSecond.commandRebuilds == statsAfterFirst.commandRebuilds)
    }

    @Test
    fun `paint keeps previous commands when rebuild throws`() {
        val root = ContainerNode(key = "root")
        val rect = TestRectNode(color = 0xFF00AA00.toInt(), key = "rect").applyParent(root)
        val tree = DomTree(root)

        tree.render(ctx, 320, 180)
        val first = tree.paint(ctx).toList()
        assertTrue(first.isNotEmpty())

        rect.throwOnBuild = true
        tree.markVisualDirty()
        val second = tree.paint(ctx).toList()

        assertEquals(first, second, "DomTree should keep previous committed commands when rebuild fails.")
    }
}
