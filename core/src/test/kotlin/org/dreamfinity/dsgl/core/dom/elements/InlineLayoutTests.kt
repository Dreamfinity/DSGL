package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Border
import org.dreamfinity.dsgl.core.dom.layout.Insets
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import kotlin.math.roundToInt
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

        val expectedLineHeight = expectedNormalLineHeightPx(ctx, 16)
        val expectedTotalHeight = expectedLineHeight * 2 + inline.gap + inline.padding.vertical + inline.border.vertical
        assertEquals(expectedTotalHeight, inline.bounds.height, "Inline height must include wrapped second line with line-box reservation.")
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

    @Test
    fun `inline wrappers with mixed text plus nested containers do not overlap between lines`() {
        val ctx = testMeasureContext()
        val root = ContainerNode(key = "root.mixed").apply {
            display = Display.Block
            width = 44
        }
        val inline = ContainerNode(key = "inline.mixed").apply {
            display = Display.Inline
            gap = 2
            padding = Insets.all(2)
            border = Border.all(1, 0xFF000000.toInt())
        }.applyParent(root)

        createMixedInlineWrapper("a", includeFlex = true).applyParent(inline)
        createMixedInlineWrapper("b", includeFlex = false).applyParent(inline)
        createMixedInlineWrapper("c", includeFlex = true).applyParent(inline)

        val measured = root.measure(ctx)
        root.render(ctx, 0, 0, measured.width, measured.height)

        assertOuterWidthsFitInlineContent(inline)
        assertContentFitsIntoInlineBounds(inline)
        assertNoVerticalOverlapBetweenSiblings(inline.children)
        inline.children.forEach { wrapper ->
            assertContainerChildrenFit(wrapper as ContainerNode)
        }
    }

    @Test
    fun `display section mixed inline children keep line heights stable without overlap`() {
        val ctx = testMeasureContext()
        val (root, inline) = createDisplayInlineScenario(rootWidth = 220, inlineContentWidth = 96)

        val measured = root.measure(ctx)
        root.render(ctx, 0, 0, measured.width, measured.height)

        val inlineContentWidth = inline.bounds.width - inline.border.horizontal - inline.padding.horizontal
        val chipChildren = inline.children.filter { (it.key as? String)?.startsWith("chip.") == true }
        assertTrue(
            chipChildren.all { chip -> chip.bounds.width + chip.margin.horizontal < inlineContentWidth },
            "Inline chips should remain shrink-to-fit and not stretch to full line width."
        )
        assertOuterWidthsFitInlineContent(inline)
        assertContentFitsIntoInlineBounds(inline)
        assertNoVerticalOverlapBetweenSiblings(inline.children)
        inline.children.filterIsInstance<ContainerNode>().forEach { assertContainerChildrenFit(it) }
    }

    @Test
    fun `display inline scenario width 132 has no overlap`() {
        val ctx = testMeasureContext()
        val (root, inline) = createDisplayInlineScenario(rootWidth = 260, inlineContentWidth = 132)

        val measured = root.measure(ctx)
        root.render(ctx, 0, 0, measured.width, measured.height)

        assertContentFitsIntoInlineBounds(inline)
        assertNoVerticalOverlapBetweenSiblings(inline.children)
    }

    @Test
    fun `display inline scenario width 112 has no overlap`() {
        val ctx = testMeasureContext()
        val (root, inline) = createDisplayInlineScenario(rootWidth = 260, inlineContentWidth = 112)

        val measured = root.measure(ctx)
        root.render(ctx, 0, 0, measured.width, measured.height)

        assertContentFitsIntoInlineBounds(inline)
        assertNoVerticalOverlapBetweenSiblings(inline.children)
    }

    @Test
    fun `inline container explicit width larger than parent is constrained without overlap`() {
        val ctx = testMeasureContext()
        val root = ContainerNode(key = "root.small").apply {
            display = Display.Block
            width = 60
        }
        val inline = ContainerNode(key = "inline.explicit.large").apply {
            display = Display.Inline
            width = 96
            gap = 2
            padding = Insets.all(3)
            border = Border.all(1, 0xFF000000.toInt())
        }.applyParent(root)

        createMixedInlineWrapper("x", includeFlex = true).applyParent(inline)
        createMixedInlineWrapper("y", includeFlex = false).applyParent(inline)

        val measured = root.measure(ctx)
        root.render(ctx, 0, 0, measured.width, measured.height)

        assertTrue(inline.bounds.width <= root.bounds.width, "Inline container must be constrained by parent width.")
        assertOuterWidthsFitInlineContent(inline)
        assertContentFitsIntoInlineBounds(inline)
        assertNoVerticalOverlapBetweenSiblings(inline.children)
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

    private fun createMixedInlineWrapper(key: String, includeFlex: Boolean): ContainerNode {
        val wrapper = ContainerNode(key = "$key.mixed.wrapper").apply {
            display = Display.Inline
            width = 24
            margin = Insets(1, 2, 1, 1)
            padding = Insets.all(1)
            border = Border.all(1, 0xFF000000.toInt())
            gap = 1
        }
        TextNode(TextSource.Static("label-$key"), key = "$key.label").applyParent(wrapper)
        if (includeFlex) {
            val flex = ContainerNode(key = "$key.flex").apply {
                display = Display.Flex
                flexDirection = FlexDirection.Row
                gap = 1
                padding = Insets.all(1)
                border = Border.all(1, 0xFF000000.toInt())
            }.applyParent(wrapper)
            ContainerNode(key = "$key.flex.a").apply {
                width = 9
                height = 4
            }.applyParent(flex)
            ContainerNode(key = "$key.flex.b").apply {
                width = 8
                height = 4
            }.applyParent(flex)
        } else {
            val block = ContainerNode(key = "$key.block").apply {
                display = Display.Block
                gap = 1
                padding = Insets.all(1)
                border = Border.all(1, 0xFF000000.toInt())
            }.applyParent(wrapper)
            TextNode(TextSource.Static("top"), key = "$key.block.top").applyParent(block)
            TextNode(TextSource.Static("bottom"), key = "$key.block.bottom").applyParent(block)
        }
        return wrapper
    }

    private fun createDisplayInlineScenario(rootWidth: Int, inlineContentWidth: Int): Pair<ContainerNode, ContainerNode> {
        val root = ContainerNode(key = "root.display.inline").apply {
            display = Display.Block
            width = rootWidth
        }
        val inline = ContainerNode(key = "display.inline.container").apply {
            display = Display.Inline
            width = inlineContentWidth
            gap = 2
            padding = Insets.all(3)
            border = Border.all(1, 0xFF000000.toInt())
        }.applyParent(root)

        listOf("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta").forEachIndexed { index, label ->
            ContainerNode(key = "chip.$index").apply {
                display = Display.Inline
                margin = Insets(1, 2, 1, 1)
                padding = Insets.all(2)
                border = Border.all(1, 0xFF000000.toInt())
            }.applyParent(inline).also { wrapper ->
                TextNode(TextSource.Static(label), key = "chip.$index.label").applyParent(wrapper)
            }
        }

        ContainerNode(key = "display.inline.flex.item").apply {
            display = Display.Inline
            margin = Insets(1, 2, 1, 1)
            padding = Insets.all(2)
            border = Border.all(1, 0xFF000000.toInt())
        }.applyParent(inline).also { wrapper ->
            TextNode(TextSource.Static("flex"), key = "display.inline.flex.label").applyParent(wrapper)
            ContainerNode(key = "display.inline.flex.container").apply {
                display = Display.Flex
                flexDirection = FlexDirection.Row
                gap = 1
                padding = Insets.all(1)
                border = Border.all(1, 0xFF000000.toInt())
            }.applyParent(wrapper).also { flex ->
                ContainerNode(key = "display.inline.flex.a").apply {
                    width = 8
                    height = 4
                }.applyParent(flex)
                ContainerNode(key = "display.inline.flex.b").apply {
                    width = 6
                    height = 4
                }.applyParent(flex)
            }
        }

        ContainerNode(key = "display.inline.block.item").apply {
            display = Display.Inline
            margin = Insets(1, 2, 1, 1)
            padding = Insets.all(2)
            border = Border.all(1, 0xFF000000.toInt())
        }.applyParent(inline).also { wrapper ->
            ContainerNode(key = "display.inline.block.container").apply {
                display = Display.Block
                gap = 1
                padding = Insets.all(1)
                border = Border.all(1, 0xFF000000.toInt())
            }.applyParent(wrapper).also { block ->
                TextNode(TextSource.Static("block"), key = "display.inline.block.label").applyParent(block)
                TextNode(TextSource.Static("A"), key = "display.inline.block.a").applyParent(block)
                TextNode(TextSource.Static("B"), key = "display.inline.block.b").applyParent(block)
            }
        }
        return root to inline
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

    private fun assertNoVerticalOverlapBetweenSiblings(children: List<DOMNode>) {
        for (i in children.indices) {
            val a = children[i]
            val aTop = a.bounds.y - a.margin.top
            val aBottom = a.bounds.y + a.bounds.height + a.margin.bottom
            for (j in i + 1 until children.size) {
                val b = children[j]
                val bTop = b.bounds.y - b.margin.top
                val bBottom = b.bounds.y + b.bounds.height + b.margin.bottom
                val separated = aBottom <= bTop || bBottom <= aTop
                if (!separated) {
                    val sameLine = a.bounds.y == b.bounds.y
                    assertTrue(sameLine, "Inline lines must not overlap: a=[$aTop,$aBottom], b=[$bTop,$bBottom]")
                }
            }
        }
    }

    private fun assertContainerChildrenFit(container: ContainerNode) {
        if (container.children.isEmpty()) return
        val contentBottom = container.bounds.height - container.border.bottom - container.padding.bottom
        val usedBottom = container.children.maxOf { child ->
            (child.bounds.y - container.bounds.y) + child.bounds.height + child.margin.bottom
        }
        assertTrue(
            usedBottom <= contentBottom,
            "Container child content should fit: key=${container.key} used=$usedBottom contentBottom=$contentBottom"
        )
    }

    private fun expectedNormalLineHeightPx(ctx: UiMeasureContext, fontSize: Int): Int {
        val fontHeight = ctx.fontHeight(fontId = null, fontSize = fontSize).coerceAtLeast(1)
        return (fontHeight * TextNode.NORMAL_LINE_HEIGHT_MULTIPLIER)
            .roundToInt()
            .coerceAtLeast(fontHeight)
            .coerceAtLeast(1)
    }

    private fun testMeasureContext(): UiMeasureContext {
        return object : UiMeasureContext {
            override val fontHeight: Int = 8
            override fun measureText(text: String): Int = text.length * 6
            override fun paint(commands: List<RenderCommand>) = Unit
        }
    }
}
