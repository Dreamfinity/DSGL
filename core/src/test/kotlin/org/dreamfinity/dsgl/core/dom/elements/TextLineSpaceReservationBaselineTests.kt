package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.StyleEngine
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextLineSpaceReservationBaselineTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 10

        override fun measureText(text: String): Int = text.length * 6

        override fun measureText(text: String, fontId: String?, fontSize: Int?): Int {
            val glyphWidth = ((fontSize ?: 16) * 0.5f).roundToInt().coerceAtLeast(1)
            return text.length * glyphWidth
        }

        override fun fontHeight(fontId: String?, fontSize: Int?): Int {
            val size = (fontSize ?: 16).coerceAtLeast(1)
            return (size * 0.625f).roundToInt().coerceAtLeast(1)
        }

        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @AfterTest
    fun cleanup() {
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `text node intrinsic height baseline uses explicit normal line-height rule`() {
        val node = TextNode(TextSource.Static("single line"), key = "text.single").apply {
            fontSize = 16
        }

        val measured = node.measure(ctx)

        assertEquals(expectedNormalLineHeightPx(16), measured.height)
    }

    @Test
    fun `div with span text baseline reserves one normal line-height line`() {
        val div = ContainerNode(key = "div.root").apply {
            display = Display.Block
            width = 320
            applyStyle {
                fontSize(20.px)
            }
        }
        val span = ContainerNode(key = "span.inline").apply {
            display = Display.Inline
        }.applyParent(div)
        TextNode(TextSource.Static("row"), key = "span.text").apply {
            applyStyle {
                fontSize(12.px)
            }
        }.applyParent(span)

        val tree = DomTree(div)
        tree.render(ctx, 320, 120)

        val expectedContainerLineHeight = expectedNormalLineHeightPx(20)
        assertEquals(expectedContainerLineHeight, span.bounds.height)
        assertEquals(expectedContainerLineHeight, div.measure(ctx).height)
        assertTrue(
            span.bounds.height > expectedNormalLineHeightPx(12),
            "Container reserves a line box from its own computed line-height baseline, not only child text height."
        )
    }

    @Test
    fun `many text rows in flex-column reserve container line-box height and stack cleanly`() {
        val rowCount = 20
        val containerFontSize = 20
        val textFontSize = 12
        val root = ContainerNode(key = "rows.root").apply {
            display = Display.Flex
            flexDirection = FlexDirection.Column
            width = 320
            gap = 0
        }
        repeat(rowCount) { index ->
            val row = ContainerNode(key = "row.$index").apply {
                display = Display.Block
                applyStyle {
                    fontSize(containerFontSize.px)
                }
            }.applyParent(root)
            TextNode(TextSource.Static("row $index"), key = "row.$index.text").apply {
                applyStyle {
                    fontSize(textFontSize.px)
                }
            }.applyParent(row)
        }

        val tree = DomTree(root)
        tree.render(ctx, 320, 400)

        val expectedRowHeight = expectedNormalLineHeightPx(containerFontSize)
        val totalReservedHeight = root.children.sumOf { it.bounds.height }
        assertEquals(expectedRowHeight * rowCount, totalReservedHeight)
        root.children.forEachIndexed { index, row ->
            assertEquals(expectedRowHeight, row.bounds.height)
            assertEquals(index * expectedRowHeight, row.bounds.y)
        }
    }

    @Test
    fun `minHeight 1em workaround is not required for ordinary text row reservation`() {
        val root = ContainerNode(key = "root").apply {
            display = Display.Block
            width = 320
        }
        val ordinaryRow = ContainerNode(key = "row.ordinary").apply {
            display = Display.Block
            applyStyle {
                fontSize(20.px)
            }
        }
        TextNode(TextSource.Static("row"), key = "row.ordinary.text").apply {
            applyStyle {
                fontSize(12.px)
            }
        }.applyParent(ordinaryRow)
        ordinaryRow.applyParent(root)

        val withWorkaround = ContainerNode(key = "row.with").apply {
            display = Display.Block
            applyStyle {
                fontSize(20.px)
                minHeight = 1.em
            }
        }.applyParent(root)
        TextNode(TextSource.Static("row"), key = "row.with.text").apply {
            applyStyle {
                fontSize(12.px)
            }
        }.applyParent(withWorkaround)

        val tree = DomTree(root)
        tree.render(ctx, 320, 120)

        val expectedOrdinaryLineBoxHeight = expectedNormalLineHeightPx(20)
        assertEquals(expectedOrdinaryLineBoxHeight, ordinaryRow.bounds.height)
        assertTrue(
            ordinaryRow.bounds.height >= expectedOrdinaryLineBoxHeight,
            "Ordinary text rows now meet line-box reservation without minHeight workaround."
        )
        assertTrue(
            withWorkaround.bounds.height >= ordinaryRow.bounds.height,
            "Workaround may still increase explicit minimums, but ordinary line-box reservation no longer depends on it."
        )
    }

    @Test
    fun `scroll content height grows naturally from stacked text rows`() {
        val rowCount = 30
        val rowFontSize = 20
        val root = ContainerNode(key = "scroll.viewport").apply {
            display = Display.Block
            width = 220
            height = 80
            overflowY = Overflow.Scroll
        }
        val list = ContainerNode(key = "scroll.content").apply {
            display = Display.Flex
            flexDirection = FlexDirection.Column
            width = 220
        }.applyParent(root)
        repeat(rowCount) { index ->
            val row = ContainerNode(key = "scroll.row.$index").apply {
                display = Display.Block
                applyStyle {
                    fontSize(rowFontSize.px)
                }
            }.applyParent(list)
            TextNode(TextSource.Static("item $index"), key = "scroll.row.$index.text").applyParent(row)
        }

        val tree = DomTree(root)
        tree.render(ctx, 220, 80)

        val state = root.scrollContainerState()
        val expectedTotalContentHeight = expectedNormalLineHeightPx(rowFontSize) * rowCount
        assertTrue(state.contentExtent.height >= expectedTotalContentHeight)
        assertTrue(state.maxScrollY > 0)
    }

    @Test
    fun `explicit line-height override drives text node intrinsic height`() {
        val node = TextNode(TextSource.Static("single line"), key = "text.override").apply {
            fontSize = 16
            applyStyle {
                lineHeight(24.px)
            }
        }
        StyleEngine.applyStylesRecursively(node)

        val measured = node.measure(ctx)

        assertEquals(24, measured.height)
    }

    @Test
    fun `explicit line-height override affects container reserved row height`() {
        val root = ContainerNode(key = "line-height.root").apply {
            display = Display.Block
            width = 320
        }
        val row = ContainerNode(key = "line-height.row").apply {
            display = Display.Block
            applyStyle {
                fontSize(20.px)
                lineHeight(26.px)
            }
        }.applyParent(root)
        TextNode(TextSource.Static("row"), key = "line-height.text").apply {
            applyStyle {
                fontSize(12.px)
            }
        }.applyParent(row)

        val tree = DomTree(root)
        tree.render(ctx, 320, 120)

        assertEquals(26, row.bounds.height)
    }

    @Test
    fun `text width measurement remains stable when line-height changes`() {
        val node = TextNode(TextSource.Static("width-check"), key = "text.width").apply {
            fontSize = 16
        }

        StyleEngine.applyStylesRecursively(node)
        val baselineWidth = node.measure(ctx).width

        node.inlineStyleDeclarations.set(
            org.dreamfinity.dsgl.core.style.StyleProperty.LINE_HEIGHT,
            org.dreamfinity.dsgl.core.style.StyleExpression.Literal("32px")
        )
        StyleEngine.clearCache()
        StyleEngine.applyStylesRecursively(node)
        val overriddenWidth = node.measure(ctx).width

        assertEquals(baselineWidth, overriddenWidth)
    }

    @Test
    fun `inspector line-height override affects measured text intrinsic height`() {
        val root = ContainerNode(key = "root").apply {
            display = Display.Block
        }
        val node = TextNode(TextSource.Static("inspector"), key = "text.inspector").apply {
            fontSize = 16
        }.applyParent(root)

        StyleEngine.applyStylesRecursively(root)
        val baselineHeight = node.measure(ctx).height
        assertEquals(expectedNormalLineHeightPx(16), baselineHeight)

        StyleEngine.setInspectorOverrideLiteral(node, org.dreamfinity.dsgl.core.style.StyleProperty.LINE_HEIGHT, "22px")
            .getOrThrow()
        StyleEngine.applyStylesRecursively(root)
        val overriddenHeight = node.measure(ctx).height

        assertEquals(22, overriddenHeight)
        assertTrue(overriddenHeight > baselineHeight)
    }

    @Test
    fun `non-text child container sizing remains unchanged`() {
        val root = ContainerNode(key = "non-text.root").apply {
            display = Display.Block
            width = 200
        }
        val row = ContainerNode(key = "non-text.row").apply {
            display = Display.Block
            applyStyle {
                fontSize(20.px)
            }
        }.applyParent(root)
        ImageNode(url = "minecraft:textures/blocks/stone.png", imageWidth = 40, imageHeight = 30, key = "non-text.image")
            .applyParent(row)

        val tree = DomTree(root)
        tree.render(ctx, 200, 80)

        assertEquals(30, row.bounds.height)
    }

    private fun expectedNormalLineHeightPx(fontSize: Int): Int {
        val fontHeight = ctx.fontHeight(fontId = null, fontSize = fontSize).coerceAtLeast(1)
        return (fontHeight * TextNode.NORMAL_LINE_HEIGHT_MULTIPLIER)
            .roundToInt()
            .coerceAtLeast(fontHeight)
            .coerceAtLeast(1)
    }

}
