package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.TextNode
import org.dreamfinity.dsgl.core.dom.elements.TextSource
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.style.StyleEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DomTreeCachingTests {
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
}
