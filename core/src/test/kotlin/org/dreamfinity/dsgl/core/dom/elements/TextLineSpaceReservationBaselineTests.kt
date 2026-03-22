package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.layout.FontLineMetrics
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.StyleEngine
import kotlin.math.ceil
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

    private val nativeMetricsCtx = object : UiMeasureContext {
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

        override fun fontLineMetrics(fontId: String?, fontSize: Int?): FontLineMetrics {
            return FontLineMetrics(
                emSize = 1f,
                lineHeightEm = 0.9166667f,
                ascenderEm = 0.75f,
                descenderEm = -0.16666667f
            )
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

    @Test
    fun `font-size em resolution uses inherited semantic base`() {
        val root = ContainerNode(key = "font-size.base.root").apply {
            display = Display.Block
            applyStyle {
                fontSize(20.px)
            }
        }
        val text = TextNode(TextSource.Static("em"), key = "font-size.base.text").apply {
            applyStyle {
                fontSize(2.em)
            }
        }.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 320, 120)

        val computedFontSize = text.appliedComputedStyleSnapshot()?.fontSize
        assertEquals(40, computedFontSize)
    }

    @Test
    fun `changing text font-size changes parent reserved row height in ordinary case`() {
        fun reservedHeightFor(emValue: Float): Int {
            val root = ContainerNode(key = "font-size.grow.root.$emValue").apply {
                display = Display.Block
                width = 320
            }
            val row = ContainerNode(key = "font-size.grow.row.$emValue").apply {
                display = Display.Block
                padding = org.dreamfinity.dsgl.core.dom.layout.Insets.all(1)
                border = org.dreamfinity.dsgl.core.dom.layout.Border.all(1, 0xFF617A90.toInt())
            }.applyParent(root)
            TextNode(TextSource.Static("grow"), key = "font-size.grow.text.$emValue").apply {
                applyStyle {
                    fontSize(emValue.em)
                }
            }.applyParent(row)
            val tree = DomTree(root)
            tree.render(ctx, 320, 120)
            return row.bounds.height
        }

        val h1 = reservedHeightFor(1f)
        val h2 = reservedHeightFor(2f)
        val h10 = reservedHeightFor(10f)
        assertTrue(h2 > h1)
        assertTrue(h10 > h2)
    }

    @Test
    fun `demo-like flex overflow audit keeps measured text reservation and produces scroll`() {
        val viewportHeight = 120
        val root = ContainerNode(key = "demo.audit.root").apply {
            display = Display.Flex
            flexDirection = FlexDirection.Column
            width = 260
            height = viewportHeight
            overflowY = Overflow.Auto
            gap = 0
        }
        repeat(20) { index ->
            val row = ContainerNode(key = "demo.audit.row.$index").apply {
                display = Display.Block
                padding = org.dreamfinity.dsgl.core.dom.layout.Insets.all(1)
                border = org.dreamfinity.dsgl.core.dom.layout.Border.all(1, 0xFF617A90.toInt())
            }.applyParent(root)
            TextNode(TextSource.Static("Hi there, #$index"), key = "demo.audit.text.$index").apply {
                applyStyle {
                    fontSize(10.em)
                }
            }.applyParent(row)
        }

        val tree = DomTree(root)
        tree.render(ctx, 260, viewportHeight)

        val firstRow = root.children.first() as ContainerNode
        val firstText = firstRow.children.first() as TextNode

        val computedFontSize = firstText.appliedComputedStyleSnapshot()?.fontSize ?: -1
        val resolvedFontMetric = invokeProtectedInt(firstText, "resolveFontSize", ctx)
        val resolvedLineHeight = invokeProtectedInt(firstText, "resolveEffectiveLineHeight", ctx)
        val measuredTextHeight = firstText.measure(ctx).height
        val measuredTextForLayoutHeight = firstText.measureForLayout(ctx, 260).height
        val rowLineBoxFloor = invokeProtectedInt(firstRow, "resolveEffectiveLineHeight", ctx)
        val rowMeasuredHeight = firstRow.measure(ctx).height
        val rowRenderedHeight = firstRow.bounds.height
        val state = root.scrollContainerState()

        assertEquals(160, computedFontSize)
        assertEquals(ctx.fontHeight(fontId = null, fontSize = computedFontSize), resolvedFontMetric)
        assertTrue(resolvedLineHeight >= resolvedFontMetric)
        assertEquals(resolvedLineHeight, measuredTextHeight)
        assertTrue(measuredTextForLayoutHeight >= measuredTextHeight)
        assertTrue(rowMeasuredHeight > rowLineBoxFloor)
        assertTrue(rowRenderedHeight >= rowMeasuredHeight)
        assertTrue(state.contentExtent.height > state.viewportRect.height)
        assertTrue(state.maxScrollY > 0)
    }

    @Test
    fun `normal line-height uses native metrics when available`() {
        val node = TextNode(TextSource.Static("native"), key = "native.metrics.normal").apply {
            fontSize = 16
        }
        StyleEngine.applyStylesRecursively(node)

        val measured = node.measure(nativeMetricsCtx)
        val resolved = invokeProtectedInt(node, "resolveEffectiveLineHeight", nativeMetricsCtx)
        val nativeExpected = expectedNativeNormalLineHeightPx(16)
        val fallbackExpected = expectedNormalLineHeightPx(16)

        assertEquals(nativeExpected, resolved)
        assertEquals(nativeExpected, measured.height)
        assertTrue(nativeExpected != fallbackExpected, "Native-metrics test must differ from fallback heuristic.")
    }

    @Test
    fun `native ascender descender and line-height scale with font size`() {
        val small = TextNode(TextSource.Static("small"), key = "native.metrics.small").apply {
            fontSize = 16
        }
        val large = TextNode(TextSource.Static("large"), key = "native.metrics.large").apply {
            fontSize = 32
        }

        val smallLine = invokeProtectedInt(small, "resolveEffectiveLineHeight", nativeMetricsCtx)
        val largeLine = invokeProtectedInt(large, "resolveEffectiveLineHeight", nativeMetricsCtx)
        val smallAsc = invokeProtectedFloat(small, "resolveEffectiveAscenderPx", nativeMetricsCtx)
        val largeAsc = invokeProtectedFloat(large, "resolveEffectiveAscenderPx", nativeMetricsCtx)
        val smallDesc = invokeProtectedFloat(small, "resolveEffectiveDescenderPx", nativeMetricsCtx)
        val largeDesc = invokeProtectedFloat(large, "resolveEffectiveDescenderPx", nativeMetricsCtx)

        assertEquals(expectedNativeNormalLineHeightPx(16), smallLine)
        assertEquals(expectedNativeNormalLineHeightPx(32), largeLine)
        assertTrue(largeLine > smallLine)
        assertTrue(largeAsc > smallAsc)
        assertTrue(largeDesc > smallDesc)
    }

    @Test
    fun `explicit line-height adds symmetric leading for text draw origin`() {
        val root = ContainerNode(key = "native.leading.root").apply {
            display = Display.Block
            width = 320
        }
        val node = TextNode(TextSource.Static("lead"), key = "native.leading.text").apply {
            fontSize = 16
            applyStyle {
                lineHeight(24.px)
            }
        }.applyParent(root)

        val tree = DomTree(root)
        tree.render(nativeMetricsCtx, 320, 80)

        val commands = mutableListOf<RenderCommand>()
        node.buildRenderCommands(nativeMetricsCtx, commands)
        val draw = commands.filterIsInstance<RenderCommand.DrawText>().single()
        val nativeLineHeight = expectedNativeNormalLineHeightPx(16)
        val expectedTopLeading = ((24 - nativeLineHeight).coerceAtLeast(0) / 2f).roundToInt()
        val expectedY = node.bounds.y + node.border.top + node.padding.top + expectedTopLeading

        assertEquals(expectedY, draw.y)
    }

    @Test
    fun `render line advance stays coherent with measured line-height`() {
        val root = ContainerNode(key = "native.coherence.root").apply {
            display = Display.Block
            width = 320
        }
        val node = TextNode(TextSource.Static("a\nb\nc"), key = "native.coherence.text").apply {
            fontSize = 16
            applyStyle {
                lineHeight(24.px)
            }
        }.applyParent(root)
        val tree = DomTree(root)
        tree.render(nativeMetricsCtx, 320, 160)

        val commands = mutableListOf<RenderCommand>()
        node.buildRenderCommands(nativeMetricsCtx, commands)
        val draws = commands.filterIsInstance<RenderCommand.DrawText>()
        val lineHeight = invokeProtectedInt(node, "resolveEffectiveLineHeight", nativeMetricsCtx)

        assertEquals(3, draws.size)
        assertEquals(lineHeight, draws[1].y - draws[0].y)
        assertEquals(lineHeight, draws[2].y - draws[1].y)
        assertEquals(lineHeight * 3, node.measure(nativeMetricsCtx).height)
    }

    @Test
    fun `native metrics path keeps ordinary row reservation non-collapsed`() {
        val root = ContainerNode(key = "native.reservation.root").apply {
            display = Display.Block
            width = 260
        }
        val row = ContainerNode(key = "native.reservation.row").apply {
            display = Display.Block
        }.applyParent(root)
        TextNode(TextSource.Static("row"), key = "native.reservation.text").apply {
            applyStyle {
                fontSize(10.em)
            }
        }.applyParent(row)

        val tree = DomTree(root)
        tree.render(nativeMetricsCtx, 260, 120)

        assertTrue(row.bounds.height >= expectedNativeNormalLineHeightPx(160))
    }

    private fun expectedNormalLineHeightPx(fontSize: Int): Int {
        val fontHeight = ctx.fontHeight(fontId = null, fontSize = fontSize).coerceAtLeast(1)
        return (fontHeight * TextNode.NORMAL_LINE_HEIGHT_MULTIPLIER)
            .roundToInt()
            .coerceAtLeast(fontHeight)
            .coerceAtLeast(1)
    }

    private fun expectedNativeNormalLineHeightPx(fontSize: Int): Int {
        return ceil(0.9166667f * fontSize).toInt().coerceAtLeast(1)
    }

    private fun invokeProtectedInt(node: DOMNode, methodName: String, ctx: UiMeasureContext): Int {
        val method = DOMNode::class.java.getDeclaredMethod(methodName, UiMeasureContext::class.java)
        method.isAccessible = true
        return method.invoke(node, ctx) as Int
    }

    private fun invokeProtectedFloat(node: DOMNode, methodName: String, ctx: UiMeasureContext): Float {
        val method = DOMNode::class.java.getDeclaredMethod(methodName, UiMeasureContext::class.java)
        method.isAccessible = true
        return method.invoke(node, ctx) as Float
    }

}
