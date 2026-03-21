package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Display
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
        }
        val span = ContainerNode(key = "span.inline").apply {
            display = Display.Inline
        }.applyParent(div)
        TextNode(TextSource.Static("row"), key = "span.text").apply {
            fontSize = 16
        }.applyParent(span)

        val measured = div.measure(ctx)
        div.render(ctx, 0, 0, measured.width, measured.height)

        assertEquals(expectedNormalLineHeightPx(16), span.bounds.height)
        assertEquals(expectedNormalLineHeightPx(16), div.bounds.height)
    }

    @Test
    fun `many text rows baseline stays tightly packed by text node normal line-height`() {
        val rowCount = 20
        val fontSize = 16
        val root = ContainerNode(key = "rows.root").apply {
            display = Display.Flex
            flexDirection = org.dreamfinity.dsgl.core.style.FlexDirection.Column
            width = 320
            gap = 0
        }
        repeat(rowCount) { index ->
            val row = ContainerNode(key = "row.$index").apply {
                display = Display.Block
            }.applyParent(root)
            TextNode(TextSource.Static("row $index"), key = "row.$index.text").apply {
                this.fontSize = fontSize
            }.applyParent(row)
        }

        val measured = root.measure(ctx)
        root.render(ctx, 0, 0, measured.width, measured.height)

        val expectedRowHeight = expectedNormalLineHeightPx(fontSize)
        assertEquals(expectedRowHeight * rowCount, measured.height)
        root.children.forEach { row ->
            assertEquals(expectedRowHeight, row.bounds.height)
        }
        assertTrue(
            measured.height < (fontSize * rowCount),
            "Baseline is intentionally tighter than nominal font-size stacking in this model."
        )
    }

    @Test
    fun `minHeight 1em workaround characterization increases row height in current model`() {
        val root = ContainerNode(key = "root").apply {
            display = Display.Block
            width = 320
        }
        val withoutWorkaround = ContainerNode(key = "row.without").apply {
            display = Display.Block
            applyStyle {
                fontSize(20.px)
            }
        }
        TextNode(TextSource.Static("row"), key = "row.without.text").apply {
            applyStyle {
                fontSize(12.px)
            }
        }.applyParent(withoutWorkaround)
        withoutWorkaround.applyParent(root)

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

        assertEquals(expectedNormalLineHeightPx(12), withoutWorkaround.bounds.height)
        assertTrue(withWorkaround.bounds.height > withoutWorkaround.bounds.height)
    }

    @Test
    fun `font-size scaling baseline maps to explicit normal line-height rule`() {
        val small = TextNode(TextSource.Static("small"), key = "text.small").apply {
            fontSize = 12
        }
        val large = TextNode(TextSource.Static("large"), key = "text.large").apply {
            fontSize = 24
        }

        val smallMeasured = small.measure(ctx)
        val largeMeasured = large.measure(ctx)

        assertEquals(expectedNormalLineHeightPx(12), smallMeasured.height)
        assertEquals(expectedNormalLineHeightPx(24), largeMeasured.height)
        assertTrue(largeMeasured.height > smallMeasured.height)
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

    private fun expectedNormalLineHeightPx(fontSize: Int): Int {
        val fontHeight = ctx.fontHeight(fontId = null, fontSize = fontSize).coerceAtLeast(1)
        return (fontHeight * TextNode.NORMAL_LINE_HEIGHT_MULTIPLIER)
            .roundToInt()
            .coerceAtLeast(fontHeight)
            .coerceAtLeast(1)
    }

}
