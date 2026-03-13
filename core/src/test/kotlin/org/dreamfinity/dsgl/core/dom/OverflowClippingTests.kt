package org.dreamfinity.dsgl.core.dom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Insets
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Overflow

class OverflowClippingTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @Test
    fun `overflow hidden container emits clip around child paint`() {
        val childColor = 0xFF0A8A3C.toInt()
        val root = ContainerNode(key = "root")
        val clip = ContainerNode(backgroundColor = 0xFF1B2330.toInt(), key = "clip").apply {
            width = 80
            height = 40
            overflow = Overflow.Hidden
        }.applyParent(root)
        FixedPaintNode(color = childColor, nodeWidth = 120, nodeHeight = 20, key = "child")
            .applyParent(clip)

        val tree = DomTree(root)
        tree.render(ctx, 200, 120)
        val commands = tree.paint(ctx, applyStyles = false)

        val clipPushIndex = commands.indexOfFirst { command ->
            command is RenderCommand.PushClip &&
                command.x == clip.bounds.x &&
                command.y == clip.bounds.y &&
                command.width == clip.bounds.width &&
                command.height == clip.bounds.height
        }
        assertTrue(clipPushIndex >= 0)

        val childDrawIndex = commands.indexOfFirst { command ->
            command is RenderCommand.DrawRect && command.color == childColor
        }
        assertTrue(childDrawIndex > clipPushIndex)

        val clipPopIndex = commands.indices.firstOrNull { index ->
            index > childDrawIndex && commands[index] is RenderCommand.PopClip
        } ?: -1
        assertTrue(clipPopIndex > childDrawIndex)

        val pushCount = commands.count { it is RenderCommand.PushClip }
        val popCount = commands.count { it is RenderCommand.PopClip }
        assertEquals(pushCount, popCount)
    }

    @Test
    fun `overflow clipping preserves child geometry for partial visibility`() {
        val childColor = 0xFFB0522D.toInt()
        val root = ContainerNode(key = "root")
        val clip = ContainerNode(backgroundColor = 0xFF18212C.toInt(), key = "clip").apply {
            width = 80
            height = 40
            overflow = Overflow.Hidden
        }.applyParent(root)
        val child = FixedPaintNode(color = childColor, nodeWidth = 120, nodeHeight = 28, key = "child")
            .applyParent(clip)

        val tree = DomTree(root)
        tree.render(ctx, 220, 120)
        val commands = tree.paint(ctx, applyStyles = false)

        assertEquals(120, child.bounds.width)
        assertTrue(child.bounds.x + child.bounds.width > clip.bounds.x + clip.bounds.width)

        val childDraw = commands
            .filterIsInstance<RenderCommand.DrawRect>()
            .firstOrNull { it.color == childColor }
            ?: error("child draw command missing")
        assertEquals(120, childDraw.width)
    }

    @Test
    fun `nested overflow containers emit nested clip commands`() {
        val leafColor = 0xFF3F7FBF.toInt()
        val root = ContainerNode(key = "root")
        val outer = ContainerNode(backgroundColor = 0xFF101820.toInt(), key = "outer").apply {
            width = 100
            height = 80
            overflow = Overflow.Hidden
        }.applyParent(root)
        val inner = ContainerNode(backgroundColor = 0xFF182230.toInt(), key = "inner").apply {
            width = 60
            height = 40
            margin = Insets(top = 10, right = 0, bottom = 0, left = 20)
            overflow = Overflow.Hidden
        }.applyParent(outer)
        FixedPaintNode(color = leafColor, nodeWidth = 120, nodeHeight = 24, key = "leaf")
            .applyParent(inner)

        val tree = DomTree(root)
        tree.render(ctx, 240, 180)
        val commands = tree.paint(ctx, applyStyles = false)

        val outerClipIndex = commands.indexOfFirst { command ->
            command is RenderCommand.PushClip &&
                command.x == outer.bounds.x &&
                command.y == outer.bounds.y &&
                command.width == outer.bounds.width &&
                command.height == outer.bounds.height
        }
        val innerClipIndex = commands.indexOfFirst { command ->
            command is RenderCommand.PushClip &&
                command.x == inner.bounds.x &&
                command.y == inner.bounds.y &&
                command.width == inner.bounds.width &&
                command.height == inner.bounds.height
        }
        val leafDrawIndex = commands.indexOfFirst { command ->
            command is RenderCommand.DrawRect && command.color == leafColor
        }

        assertTrue(outerClipIndex >= 0)
        assertTrue(innerClipIndex > outerClipIndex)
        assertTrue(leafDrawIndex > innerClipIndex)

        val pushCount = commands.count { it is RenderCommand.PushClip }
        val popCount = commands.count { it is RenderCommand.PopClip }
        assertEquals(pushCount, popCount)
    }

    private class FixedPaintNode(
        private val color: Int,
        private val nodeWidth: Int,
        private val nodeHeight: Int,
        key: Any? = null
    ) : DOMNode(key) {
        init {
            width = nodeWidth
            height = nodeHeight
        }

        override fun measure(ctx: UiMeasureContext): Size = Size(nodeWidth, nodeHeight)

        override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
            out += RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, color)
            super.buildRenderCommands(ctx, out)
        }
    }
}
