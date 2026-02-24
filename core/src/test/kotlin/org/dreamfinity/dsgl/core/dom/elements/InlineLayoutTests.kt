package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.layout.Border
import org.dreamfinity.dsgl.core.dom.layout.Insets
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineLayoutTests {
    @Test
    fun `inline child measured with parent width constraint expands height after wrap`() {
        val ctx = testMeasureContext()
        val root = ContainerNode(key = "root").apply {
            display = Display.Block
            width = 30
        }
        val inline = ContainerNode(key = "inline").apply {
            display = Display.Inline
            gap = 2
            padding = Insets.all(1)
            border = Border.all(1, 0xFF000000.toInt())
        }.applyParent(root)

        repeat(3) { index ->
            ContainerNode(key = "item.$index").apply {
                display = Display.Inline
                width = 10
                height = 4
                margin = Insets(1, 1, 1, 1)
            }.applyParent(inline)
        }

        val measured = root.measure(ctx)
        root.render(ctx, 0, 0, measured.width, measured.height)

        assertEquals(18, inline.bounds.height, "Inline height must include wrapped second line.")
        assertEquals(2, inline.children.map { it.bounds.y }.distinct().size, "Inline items should wrap to two lines.")
        assertOuterWidthsFitInlineContent(inline)
        assertContentFitsIntoInlineBounds(inline)
    }

    @Test
    fun `inline flow with nested flex and block items keeps content inside measured bounds`() {
        val ctx = testMeasureContext()
        val root = ContainerNode(key = "root.nested").apply {
            display = Display.Block
            width = 36
        }
        val inline = ContainerNode(key = "inline.nested").apply {
            display = Display.Inline
            gap = 3
            padding = Insets.all(2)
            border = Border.all(1, 0xFF000000.toInt())
        }.applyParent(root)

        createInlineFlexItem("flex", 8, 7).applyParent(inline)
        createInlineBlockItem("block", 10, 6).applyParent(inline)
        ContainerNode(key = "simple").apply {
            display = Display.Inline
            width = 12
            height = 4
            margin = Insets(1, 2, 1, 1)
            border = Border.all(1, 0xFF000000.toInt())
            padding = Insets.all(1)
        }.applyParent(inline)

        val measured = root.measure(ctx)
        root.render(ctx, 0, 0, measured.width, measured.height)

        assertTrue(inline.children.map { it.bounds.y }.distinct().size >= 2, "Nested inline items must wrap.")
        assertOuterWidthsFitInlineContent(inline)
        assertContentFitsIntoInlineBounds(inline)
    }

    private fun createInlineFlexItem(key: String, aWidth: Int, bWidth: Int): ContainerNode {
        val wrapper = ContainerNode(key = "$key.wrapper").apply {
            display = Display.Inline
            margin = Insets(1, 2, 1, 1)
            padding = Insets.all(1)
            border = Border.all(1, 0xFF000000.toInt())
        }
        val flex = ContainerNode(key = "$key.flex").apply {
            display = Display.Flex
            flexDirection = FlexDirection.Row
            gap = 1
            padding = Insets.all(1)
        }.applyParent(wrapper)
        ContainerNode(key = "$key.a").apply {
            width = aWidth
            height = 4
        }.applyParent(flex)
        ContainerNode(key = "$key.b").apply {
            width = bWidth
            height = 4
        }.applyParent(flex)
        return wrapper
    }

    private fun createInlineBlockItem(key: String, topWidth: Int, bottomWidth: Int): ContainerNode {
        val wrapper = ContainerNode(key = "$key.wrapper").apply {
            display = Display.Inline
            margin = Insets(1, 2, 1, 1)
            padding = Insets.all(1)
            border = Border.all(1, 0xFF000000.toInt())
        }
        val block = ContainerNode(key = "$key.block").apply {
            display = Display.Block
            gap = 1
            padding = Insets.all(1)
        }.applyParent(wrapper)
        ContainerNode(key = "$key.top").apply {
            width = topWidth
            height = 3
        }.applyParent(block)
        ContainerNode(key = "$key.bottom").apply {
            width = bottomWidth
            height = 3
        }.applyParent(block)
        return wrapper
    }

    private fun assertContentFitsIntoInlineBounds(inline: ContainerNode) {
        val contentBottom = inline.bounds.height - inline.border.bottom - inline.padding.bottom
        val usedBottom = inline.children.maxOf { child ->
            (child.bounds.y - inline.bounds.y) + child.bounds.height + child.margin.bottom
        }
        assertTrue(
            usedBottom <= contentBottom,
            "Inline child content should fit into measured bounds: used=$usedBottom, contentBottom=$contentBottom"
        )
    }

    private fun assertOuterWidthsFitInlineContent(inline: ContainerNode) {
        val contentWidth = inline.bounds.width - inline.border.horizontal - inline.padding.horizontal
        val maxOuterWidth = inline.children.maxOf { child ->
            child.bounds.width + child.margin.horizontal
        }
        assertTrue(
            maxOuterWidth <= contentWidth,
            "Inline child outer width should fit content width: maxOuterWidth=$maxOuterWidth, contentWidth=$contentWidth"
        )
    }

    private fun testMeasureContext(): UiMeasureContext {
        return object : UiMeasureContext {
            override val fontHeight: Int = 8
            override fun measureText(text: String): Int = text.length * 6
            override fun paint(commands: List<RenderCommand>) = Unit
        }
    }
}
