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
    fun `text node intrinsic height baseline uses measure-context font height`() {
        val node = TextNode(TextSource.Static("single line"), key = "text.single").apply {
            fontSize = 16
        }

        val measured = node.measure(ctx)

        assertEquals(expectedLineHeightPx(16), measured.height)
    }

    @Test
    fun `div with span text baseline reserves one font-height line`() {
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

        assertEquals(expectedLineHeightPx(16), span.bounds.height)
        assertEquals(expectedLineHeightPx(16), div.bounds.height)
    }

    @Test
    fun `many text rows baseline stays tightly packed by font-height-per-line`() {
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

        val expectedRowHeight = expectedLineHeightPx(fontSize)
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

        assertEquals(expectedLineHeightPx(12), withoutWorkaround.bounds.height)
        assertEquals(expectedLineHeightPx(20), withWorkaround.bounds.height)
        assertTrue(withWorkaround.bounds.height > withoutWorkaround.bounds.height)
    }

    @Test
    fun `font-size scaling baseline maps to font-height path`() {
        val small = TextNode(TextSource.Static("small"), key = "text.small").apply {
            fontSize = 12
        }
        val large = TextNode(TextSource.Static("large"), key = "text.large").apply {
            fontSize = 24
        }

        val smallMeasured = small.measure(ctx)
        val largeMeasured = large.measure(ctx)

        assertEquals(expectedLineHeightPx(12), smallMeasured.height)
        assertEquals(expectedLineHeightPx(24), largeMeasured.height)
        assertTrue(largeMeasured.height > smallMeasured.height)
    }

    private fun expectedLineHeightPx(fontSize: Int): Int {
        return ctx.fontHeight(fontId = null, fontSize = fontSize)
    }

}
