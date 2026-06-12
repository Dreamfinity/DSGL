package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.components.modal.internal.ModalPortalAnchorNode
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.TextNode
import org.dreamfinity.dsgl.core.dom.elements.TextSource
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.render.RenderCommandChunk
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.UiTransform
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DomTreeCachingTests {
    private class CountingRectNode(
        private var color: Int,
        key: Any? = null,
    ) : DOMNode(key) {
        var buildCount: Int = 0
            private set

        override val styleType: String = "counting-rect"

        override fun measure(ctx: UiMeasureContext): Size = Size(10, 10)

        override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
            buildCount += 1
            out += RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, color)
        }

        fun setColor(next: Int) {
            if (next == color) return
            color = next
            markRenderCommandsDirty()
        }
    }

    private class TestRectNode(
        private val color: Int,
        key: Any? = null,
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

    private class LegacyManualChildrenNode(
        private val color: Int,
        key: Any? = null,
    ) : DOMNode(key) {
        override val styleType: String = "legacy-manual"

        override fun measure(ctx: UiMeasureContext): Size = Size(10, 10)

        override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
            out += RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, color)
            children.forEach { child ->
                child.appendRenderCommands(ctx, out)
            }
        }
    }

    private val ctx =
        object : UiMeasureContext {
            override fun measureText(text: String): Int = text.length * 6

            override fun measureText(text: String, fontId: String?, fontSize: Int?): Int = text.length * 6

            override val fontHeight: Int = 9

            override fun fontHeight(fontId: String?, fontSize: Int?): Int = 9

            override fun paint(commands: List<RenderCommand>) = Unit
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

    @Test
    fun `leaf visual mutation rebuilds only affected chunk path`() {
        val root = ContainerNode(key = "root")
        val left = CountingRectNode(color = 0xFF4477AA.toInt(), key = "left").applyParent(root)
        val right = CountingRectNode(color = 0xFFAA7744.toInt(), key = "right").applyParent(root)
        val tree = DomTree(root)

        tree.render(ctx, 320, 180)
        tree.paint(ctx)
        assertEquals(1, left.buildCount)
        assertEquals(1, right.buildCount)

        left.setColor(0xFF55CC88.toInt())
        tree.paint(ctx)

        assertEquals(2, left.buildCount)
        assertEquals(1, right.buildCount)
        val stats = tree.paintStats()
        assertTrue(stats.chunkNodesRebuiltLastFrame >= 2)
    }

    @Test
    fun `transform and opacity commands remain balanced`() {
        val root = ContainerNode(key = "root")
        val node = CountingRectNode(color = 0xFF22AA55.toInt(), key = "balanced").applyParent(root)
        node.transform = UiTransform(translateX = 4f, translateY = 3f, scaleX = 1f, scaleY = 1f, rotateDeg = 0f)
        node.opacity = 0.6f
        val tree = DomTree(root)

        tree.render(ctx, 320, 180)
        val commands = tree.paint(ctx)

        val pushTransform = commands.count { it is RenderCommand.PushTransform }
        val popTransform = commands.count { it == RenderCommand.PopTransform }
        val pushOpacity = commands.count { it is RenderCommand.PushOpacity }
        val popOpacity = commands.count { it == RenderCommand.PopOpacity }
        assertEquals(pushTransform, popTransform)
        assertEquals(pushOpacity, popOpacity)
    }

    @Test
    fun `display none removes stale child commands`() {
        val root = ContainerNode(key = "root")
        val node = CountingRectNode(color = 0xFF3399CC.toInt(), key = "toggle").applyParent(root)
        val tree = DomTree(root)

        tree.render(ctx, 320, 180)
        val initial = tree.paint(ctx)
        assertTrue(initial.any { it is RenderCommand.DrawRect })

        node.display = Display.None
        val afterHide = tree.paint(ctx)
        assertTrue(
            afterHide.none { command ->
                command is RenderCommand.DrawRect &&
                    command.color == 0xFF3399CC.toInt()
            },
        )
    }

    @Test
    fun `modal portal does not duplicate child commands in chunk assembly`() {
        val host = ModalPortalAnchorNode(key = "modal-host")
        CountingRectNode(color = 0xFFAA3300.toInt(), key = "content").applyParent(host)
        CountingRectNode(color = 0xFF0033AA.toInt(), key = "floating-layer").applyParent(host)
        val tree = DomTree(host)

        tree.render(ctx, 320, 180)
        val commands = tree.paint(ctx)
        val drawRectColors = commands.filterIsInstance<RenderCommand.DrawRect>().map { it.color }

        assertEquals(2, drawRectColors.size)
        assertEquals(1, drawRectColors.count { it == 0xFFAA3300.toInt() })
        assertEquals(1, drawRectColors.count { it == 0xFF0033AA.toInt() })
    }

    @Test
    fun `legacy manual child rendering does not duplicate in chunk assembly`() {
        val root = LegacyManualChildrenNode(color = 0xFF1255AA.toInt(), key = "legacy-root")
        CountingRectNode(color = 0xFF22AA55.toInt(), key = "legacy-child").applyParent(root)
        val tree = DomTree(root)

        tree.render(ctx, 320, 180)
        val commands = tree.paint(ctx)
        val drawRectColors = commands.filterIsInstance<RenderCommand.DrawRect>().map { it.color }

        assertEquals(2, drawRectColors.size)
        assertEquals(1, drawRectColors.count { it == 0xFF1255AA.toInt() })
        assertEquals(1, drawRectColors.count { it == 0xFF22AA55.toInt() })
    }

    @Test
    fun `render command chunk does not hold dom node references`() {
        val hasNodeField =
            RenderCommandChunk::class.java.declaredFields.any { field ->
                DOMNode::class.java.isAssignableFrom(field.type)
            }
        assertTrue(!hasNodeField)
    }
}
