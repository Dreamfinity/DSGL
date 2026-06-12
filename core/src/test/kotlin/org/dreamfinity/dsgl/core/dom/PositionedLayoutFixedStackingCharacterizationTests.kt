package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Insets
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.dispatchClick
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.PositionMode
import org.dreamfinity.dsgl.core.style.StyleDeclarations
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PositionedLayoutFixedStackingCharacterizationTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanup() {
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `nested fixed geometry is still root anchored in current model`() {
        val root = ContainerNode(key = "fixed-geom-root", stackLayout = true)
        val ancestor =
            ContainerNode(key = "fixed-geom-ancestor").apply {
                width = 140
                height = 90
                margin = Insets(top = 40, right = 0, bottom = 0, left = 60)
            }
        val fixed =
            ContainerNode(key = "fixed-geom-node").apply {
                width = 24
                height = 12
                zIndex = 9_999
                inlineStyleDeclarations =
                    styleDeclarations(
                        StyleProperty.POSITION to "fixed",
                        StyleProperty.LEFT to "9px",
                        StyleProperty.TOP to "11px",
                    )
            }
        fixed.applyParent(ancestor)
        ancestor.applyParent(root)

        renderTree(root, width = 260, height = 180)

        assertEquals(9, fixed.bounds.x)
        assertEquals(11, fixed.bounds.y)
        assertTrue(fixed.containsGlobalPoint(10, 12))
        assertFalse(fixed.containsGlobalPoint(70, 52))
    }

    @Test
    fun `nested fixed high z-index now paints above later root sibling content`() {
        val fixedColor = 0x00_55_AA_11
        val laterColor = 0x00_AA_33_55

        val root = ContainerNode(key = "fixed-paint-root", stackLayout = true)
        val earlySubtree =
            ContainerNode(key = "fixed-paint-early").apply {
                width = 120
                height = 60
            }
        val laterSubtree =
            ContainerNode(key = "fixed-paint-later").apply {
                width = 120
                height = 60
            }
        val fixed =
            ButtonNode("fixed", backgroundColor = fixedColor, key = "fixed-paint-node").apply {
                width = 72
                height = 24
                zIndex = 9_999
                inlineStyleDeclarations =
                    styleDeclarations(
                        StyleProperty.POSITION to "fixed",
                        StyleProperty.LEFT to "8px",
                        StyleProperty.TOP to "8px",
                    )
            }
        val later =
            ButtonNode("later", backgroundColor = laterColor, key = "fixed-paint-later-node").apply {
                width = 72
                height = 24
            }
        fixed.applyParent(earlySubtree)
        later.applyParent(laterSubtree)
        earlySubtree.applyParent(root)
        laterSubtree.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 220, 140)
        val commands = tree.paint(ctx)
        val drawRects = commands.filterIsInstance<RenderCommand.DrawRect>()

        val fixedPaintIndex =
            drawRects.indexOfFirst { rect ->
                rect.color == fixedColor && rect.x == 8 && rect.y == 8
            }
        val laterPaintIndex =
            drawRects.indexOfFirst { rect ->
                rect.color == laterColor && rect.x == 0 && rect.y == 0
            }

        assertTrue(fixedPaintIndex >= 0, "Expected fixed draw rect in paint command stream")
        assertTrue(laterPaintIndex >= 0, "Expected later sibling draw rect in paint command stream")
        assertTrue(
            fixedPaintIndex > laterPaintIndex,
            "Fixed should participate in root paint ordering and paint above lower-priority later sibling content",
        )
    }

    @Test
    fun `nested fixed high z-index now wins hit over later root sibling subtree`() {
        val root = ContainerNode(key = "fixed-hit-root", stackLayout = true)
        val earlySubtree =
            ContainerNode(key = "fixed-hit-early").apply {
                width = 120
                height = 60
            }
        val laterSubtree =
            ContainerNode(key = "fixed-hit-later").apply {
                width = 120
                height = 60
            }

        var fixedClicks = 0
        var laterClicks = 0

        val fixed =
            ButtonNode("fixed", key = "fixed-hit-node").apply {
                width = 72
                height = 24
                zIndex = 9_999
                inlineStyleDeclarations =
                    styleDeclarations(
                        StyleProperty.POSITION to "fixed",
                        StyleProperty.LEFT to "8px",
                        StyleProperty.TOP to "8px",
                    )
                onClick { fixedClicks += 1 }
            }
        val later =
            ButtonNode("later", key = "fixed-hit-later-node").apply {
                width = 72
                height = 24
                onClick { laterClicks += 1 }
            }

        fixed.applyParent(earlySubtree)
        later.applyParent(laterSubtree)
        earlySubtree.applyParent(root)
        laterSubtree.applyParent(root)

        renderTree(root, width = 220, height = 140)

        assertTrue(fixed.containsGlobalPoint(10, 10))
        assertTrue(dispatchClick(root, MouseClickEvent(10, 10, MouseButton.LEFT)))
        assertEquals(1, fixedClicks)
        assertEquals(0, laterClicks)
    }

    @Test
    fun `fixed is promoted into root ordering while logical parent ownership remains unchanged`() {
        val root = ContainerNode(key = "fixed-local-root", stackLayout = true)
        val earlySubtree = ContainerNode(key = "fixed-local-early").applyParent(root)
        val laterSubtree = ContainerNode(key = "fixed-local-later").applyParent(root)
        val nestedFixed =
            ContainerNode(key = "fixed-local-nested")
                .apply {
                    position = PositionMode.Fixed
                    zIndex = 9_999
                    inlineStyleDeclarations =
                        styleDeclarations(
                            StyleProperty.POSITION to "fixed",
                            StyleProperty.LEFT to "4px",
                            StyleProperty.TOP to "4px",
                        )
                }.applyParent(earlySubtree)

        assertSame(earlySubtree, nestedFixed.parent)
        assertEquals(listOf(earlySubtree, laterSubtree, nestedFixed), root.orderedChildrenForPaintTraversal())
        assertEquals(listOf(nestedFixed, laterSubtree, earlySubtree), root.orderedChildrenForHitTestingTraversal())
        assertTrue(earlySubtree.orderedChildrenForPaintTraversal().isEmpty())
        assertTrue(earlySubtree.orderedChildrenForHitTestingTraversal().isEmpty())
    }

    @Test
    fun `paint and hit traversals are symmetric with fixed root participation`() {
        val root = ContainerNode(key = "fixed-sym-root", stackLayout = true)
        val earlySubtree = ContainerNode(key = "fixed-sym-early").applyParent(root)
        val laterSubtree = ContainerNode(key = "fixed-sym-later").applyParent(root)
        val fixed =
            ContainerNode(key = "fixed-sym-nested")
                .apply {
                    position = PositionMode.Fixed
                    zIndex = 9_999
                    inlineStyleDeclarations =
                        styleDeclarations(
                            StyleProperty.POSITION to "fixed",
                            StyleProperty.LEFT to "6px",
                            StyleProperty.TOP to "6px",
                        )
                }.applyParent(earlySubtree)
        val nonFixedNested = ContainerNode(key = "fixed-sym-nested-normal").applyParent(earlySubtree)

        val rootPaint = root.orderedChildrenForPaintTraversal()
        val rootHit = root.orderedChildrenForHitTestingTraversal()
        val earlyPaint = earlySubtree.orderedChildrenForPaintTraversal()
        val earlyHit = earlySubtree.orderedChildrenForHitTestingTraversal()

        assertEquals(rootPaint.reversed(), rootHit)
        assertEquals(earlyPaint.reversed(), earlyHit)
        assertEquals(listOf(earlySubtree, laterSubtree, fixed), rootPaint)
        assertEquals(listOf(nonFixedNested), earlyPaint)
    }

    @Test
    fun `non-fixed parent-local ordering remains unchanged`() {
        val root = ContainerNode(key = "fixed-nonfixed-root", stackLayout = true)
        val earlySubtree = ContainerNode(key = "fixed-nonfixed-early").applyParent(root)
        val laterSubtree = ContainerNode(key = "fixed-nonfixed-later").applyParent(root)
        val earlyA = ContainerNode(key = "fixed-nonfixed-a").applyParent(earlySubtree)
        val earlyB = ContainerNode(key = "fixed-nonfixed-b").applyParent(earlySubtree)

        assertEquals(listOf(earlySubtree, laterSubtree), root.orderedChildrenForPaintTraversal())
        assertEquals(listOf(earlyA, earlyB), earlySubtree.orderedChildrenForPaintTraversal())
        assertEquals(listOf(earlyB, earlyA), earlySubtree.orderedChildrenForHitTestingTraversal())
    }

    private fun renderTree(root: ContainerNode, width: Int, height: Int) {
        DomTree(root).render(ctx, width, height)
    }

    private fun styleDeclarations(vararg entries: Pair<StyleProperty, String>): StyleDeclarations =
        StyleDeclarations().apply {
            entries.forEach { (property, literal) ->
                set(property, StyleExpression.Literal(literal))
            }
        }
}
