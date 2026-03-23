package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.collectHoverChain
import org.dreamfinity.dsgl.core.event.dispatchClick
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleDeclarations
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedUsedGeometryInspectorCharacterizationTests {
    private val ctx = object : UiMeasureContext {
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
    fun `render-visible absolute outside ancestor is missed by current core interaction traversal`() {
        val fixture = createAbsoluteOutsideAncestorFixture()
        fixture.tree.render(ctx, width = 260, height = 140)
        val drawRects = fixture.tree.paint(ctx).filterIsInstance<RenderCommand.DrawRect>()
        assertTrue(
            drawRects.any { it.color == fixture.childColor && it.x == 100 && it.y == 5 },
            "Render draws absolute child at its final positioned rect outside ancestor bounds"
        )

        assertFalse(
            dispatchClick(fixture.root, MouseClickEvent(105, 10, MouseButton.LEFT)),
            "Current click traversal misses visible absolute child because ancestor bounds gate recursion"
        )
        val hoverChain = collectHoverChain(fixture.root, 105, 10)
        assertEquals(
            fixture.root.key,
            hoverChain.lastOrNull()?.key,
            "Current hover traversal also misses absolute child and stops at ancestor-gated chain"
        )
    }

    @Test
    fun `render-visible absolute outside ancestor is missed by inspector picking traversal`() {
        val fixture = createAbsoluteOutsideAncestorFixture()
        fixture.tree.render(ctx, width = 260, height = 140)
        val inspector = InspectorController()
        inspector.toggle()
        inspector.onLayoutCommitted(fixture.root, 11L)
        inspector.onCursorMoved(105, 10)

        assertEquals(
            fixture.root.key?.toString(),
            inspector.hoveredKey,
            "Inspector picking currently misses visible absolute child when parent-local bounds gate recursion"
        )
    }

    @Test
    fun `render and core interaction target promoted fixed while inspector misses it`() {
        val fixture = createPositionedOverlapFixture()
        fixture.tree.render(ctx, width = 220, height = 140)
        val drawRects = fixture.tree.paint(ctx).filterIsInstance<RenderCommand.DrawRect>()

        val fixedPaintIndex = drawRects.indexOfFirst { it.color == fixture.fixedColor && it.x == 8 && it.y == 8 }
        val laterPaintIndex = drawRects.indexOfFirst { it.color == fixture.laterColor && it.x == 0 && it.y == 0 }
        assertTrue(fixedPaintIndex > laterPaintIndex, "Render paints promoted fixed above later sibling content")

        val hoverChain = collectHoverChain(fixture.root, 10, 10)
        assertEquals(fixture.fixed.key, hoverChain.lastOrNull()?.key, "Core hover traversal resolves promoted fixed")

        var fixedClicks = 0
        var laterClicks = 0
        fixture.fixed.onClick { fixedClicks += 1 }
        fixture.later.onClick { laterClicks += 1 }
        assertTrue(dispatchClick(fixture.root, MouseClickEvent(10, 10, MouseButton.LEFT)))
        assertEquals(1, fixedClicks)
        assertEquals(0, laterClicks)

        val inspector = InspectorController()
        inspector.toggle()
        inspector.onLayoutCommitted(fixture.root, 1L)
        inspector.onCursorMoved(10, 10)
        assertEquals(
            fixture.later.key?.toString(),
            inspector.hoveredKey,
            "Inspector currently uses a different pick path and misses the topmost promoted fixed node"
        )
    }

    @Test
    fun `inspector picking ordering diverges from positioned hit ordering in same overlap`() {
        val fixture = createPositionedOverlapFixture()
        fixture.tree.render(ctx, width = 220, height = 140)

        assertEquals(
            listOf(fixture.fixed, fixture.laterContainer, fixture.earlyContainer),
            fixture.root.orderedChildrenForHitTestingTraversal(),
            "Positioned hit traversal resolves promoted fixed first"
        )

        val inspector = InspectorController()
        inspector.toggle()
        inspector.onLayoutCommitted(fixture.root, 2L)
        inspector.onCursorMoved(10, 10)

        assertEquals(
            fixture.later.key?.toString(),
            inspector.hoveredKey,
            "Inspector still traverses raw reverse child recursion for hover/picking in this"
        )
    }

    @Test
    fun `render interaction and inspector divergence summary stays explicit`() {
        val absoluteFixture = createAbsoluteOutsideAncestorFixture()
        absoluteFixture.tree.render(ctx, width = 260, height = 140)

        val fixedFixture = createPositionedOverlapFixture()
        fixedFixture.tree.render(ctx, width = 220, height = 140)

        val inspector = InspectorController().also { it.toggle() }
        inspector.onLayoutCommitted(fixedFixture.root, 21L)
        inspector.onCursorMoved(10, 10)

        val fixedHover = collectHoverChain(fixedFixture.root, 10, 10).lastOrNull()?.key
        val absoluteHover = collectHoverChain(absoluteFixture.root, 105, 10).lastOrNull()?.key

        assertEquals(fixedFixture.fixed.key, fixedHover, "Core hover sees promoted fixed in overlap case")
        assertEquals(absoluteFixture.root.key, absoluteHover, "Core hover misses absolute child outside ancestor bounds")
        assertEquals(
            fixedFixture.later.key?.toString(),
            inspector.hoveredKey,
            "Inspector diverges from core positioned ordering in overlap case"
        )
        assertFalse(
            dispatchClick(absoluteFixture.root, MouseClickEvent(105, 10, MouseButton.LEFT)),
            "Click traversal divergence for absolute-outside-ancestor case remains characterized"
        )
    }

    private data class PositionedOverlapFixture(
        val tree: DomTree,
        val root: ContainerNode,
        val earlyContainer: ContainerNode,
        val laterContainer: ContainerNode,
        val fixed: ButtonNode,
        val later: ButtonNode,
        val fixedColor: Int,
        val laterColor: Int
    )

    private data class AbsoluteOutsideAncestorFixture(
        val tree: DomTree,
        val root: ContainerNode,
        val ancestor: ContainerNode,
        val child: ButtonNode,
        val childColor: Int
    )

    private fun createPositionedOverlapFixture(): PositionedOverlapFixture {
        val fixedColor = 0x00_27_83_B4
        val laterColor = 0x00_B4_5D_27
        val root = ContainerNode(key = "root", stackLayout = true)
        val early = ContainerNode(key = "early", stackLayout = true).apply {
            width = 120
            height = 60
        }
        val laterContainer = ContainerNode(key = "later-container", stackLayout = true).apply {
            width = 120
            height = 60
        }
        val fixed = ButtonNode("fixed", backgroundColor = fixedColor, key = "fixed").apply {
            width = 72
            height = 24
            zIndex = 9_999
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "8px",
                StyleProperty.TOP to "8px"
            )
        }.applyParent(early)
        val later = ButtonNode("later", backgroundColor = laterColor, key = "later").apply {
            width = 72
            height = 24
        }.applyParent(laterContainer)
        early.applyParent(root)
        laterContainer.applyParent(root)
        return PositionedOverlapFixture(
            tree = DomTree(root),
            root = root,
            earlyContainer = early,
            laterContainer = laterContainer,
            fixed = fixed,
            later = later,
            fixedColor = fixedColor,
            laterColor = laterColor
        )
    }

    private fun createAbsoluteOutsideAncestorFixture(): AbsoluteOutsideAncestorFixture {
        val childColor = 0x00_1F_9A_55
        val root = ContainerNode(key = "abs-root")
        val ancestor = ContainerNode(key = "abs-ancestor").apply {
            width = 40
            height = 40
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "relative"
            )
        }.applyParent(root)
        val child = ButtonNode("abs-child", backgroundColor = childColor, key = "abs-child").apply {
            width = 36
            height = 16
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "absolute",
                StyleProperty.LEFT to "100px",
                StyleProperty.TOP to "5px"
            )
        }.applyParent(ancestor)
        return AbsoluteOutsideAncestorFixture(
            tree = DomTree(root),
            root = root,
            ancestor = ancestor,
            child = child,
            childColor = childColor
        )
    }

    private fun styleDeclarations(vararg entries: Pair<StyleProperty, String>): StyleDeclarations {
        return StyleDeclarations().apply {
            entries.forEach { (property, literal) ->
                set(property, StyleExpression.Literal(literal))
            }
        }
    }
}
