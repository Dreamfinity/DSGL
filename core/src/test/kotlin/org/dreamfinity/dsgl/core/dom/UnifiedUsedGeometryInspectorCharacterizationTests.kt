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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun `core click and hover reach render-visible absolute outside ancestor bounds`() {
        val fixture = createAbsoluteOutsideAncestorFixture()
        fixture.tree.render(ctx, width = 260, height = 140)
        val drawRects = fixture.tree.paint(ctx).filterIsInstance<RenderCommand.DrawRect>()
        assertTrue(
            drawRects.any { it.color == fixture.childColor && it.x == 100 && it.y == 5 },
            "Render draws absolute child at its final positioned rect outside ancestor bounds"
        )

        var childClicks = 0
        fixture.child.onClick { childClicks += 1 }
        assertTrue(
            dispatchClick(fixture.root, MouseClickEvent(105, 10, MouseButton.LEFT)),
            "Core click now reaches visible absolute child outside ancestor-local bounds"
        )
        assertEquals(1, childClicks)
        val hoverChain = collectHoverChain(fixture.root, 105, 10)
        assertEquals(
            fixture.child.key,
            hoverChain.lastOrNull()?.key,
            "Core hover now resolves the same visible absolute target"
        )
    }

    @Test
    fun `inspector now picks render-visible absolute outside ancestor bounds`() {
        val fixture = createAbsoluteOutsideAncestorFixture()
        fixture.tree.render(ctx, width = 260, height = 140)
        val inspector = InspectorController()
        inspector.toggle()
        inspector.onLayoutCommitted(fixture.root, 11L)
        inspector.onCursorMoved(105, 10)

        assertEquals(
            fixture.child.key?.toString(),
            inspector.hoveredKey,
            "Inspector picking now follows shared used geometry for absolute-outside-ancestor case"
        )
    }

    @Test
    fun `render core interaction and inspector all target promoted fixed`() {
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
            fixture.fixed.key?.toString(),
            inspector.hoveredKey,
            "Inspector now resolves the same topmost promoted fixed node as render/core interaction"
        )
    }

    @Test
    fun `inspector picking ordering now matches positioned hit ordering in overlap`() {
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
            fixture.fixed.key?.toString(),
            inspector.hoveredKey,
            "Inspector now uses shared ordering for overlap picking"
        )
    }

    @Test
    fun `render interaction and inspector are aligned for positioned repros`() {
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
        assertEquals(absoluteFixture.child.key, absoluteHover, "Core hover reaches absolute child outside ancestor bounds")
        assertEquals(
            fixedFixture.fixed.key?.toString(),
            inspector.hoveredKey,
            "Inspector now matches core positioned ordering in overlap case"
        )
        var absoluteClicks = 0
        absoluteFixture.child.onClick { absoluteClicks += 1 }
        assertTrue(
            dispatchClick(absoluteFixture.root, MouseClickEvent(105, 10, MouseButton.LEFT)),
            "Core click reaches absolute-outside-ancestor case after shared used-geometry migration"
        )
        assertEquals(1, absoluteClicks)

        inspector.onLayoutCommitted(absoluteFixture.root, 22L)
        inspector.onCursorMoved(105, 10)
        assertEquals(
            absoluteFixture.child.key?.toString(),
            inspector.hoveredKey,
            "Inspector now matches core/app-host geometry for absolute-outside-ancestor case"
        )
    }

    @Test
    fun `core and inspector preserve fixed and non-fixed clip semantics`() {
        val root = ContainerNode(key = "clip-root", stackLayout = true)
        val overflowParent = ContainerNode(key = "clip-parent").apply {
            width = 80
            height = 40
            overflowY = org.dreamfinity.dsgl.core.style.Overflow.Hidden
            inlineStyleDeclarations = styleDeclarations(StyleProperty.POSITION to "relative")
        }.applyParent(root)
        val fixed = ButtonNode("fixed", key = "clip-fixed").apply {
            width = 40
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "180px",
                StyleProperty.TOP to "20px"
            )
        }.applyParent(overflowParent)
        val absolute = ButtonNode("absolute", key = "clip-absolute").apply {
            width = 40
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "absolute",
                StyleProperty.LEFT to "140px",
                StyleProperty.TOP to "90px"
            )
        }.applyParent(overflowParent)

        val tree = DomTree(root)
        tree.render(ctx, width = 200, height = 120)

        var fixedClicks = 0
        var absoluteClicks = 0
        fixed.onClick { fixedClicks += 1 }
        absolute.onClick { absoluteClicks += 1 }

        assertFalse(dispatchClick(root, MouseClickEvent(225, 28, MouseButton.LEFT)))
        assertEquals(0, fixedClicks, "Fixed remains clipped by root viewport in core interaction")

        assertFalse(dispatchClick(root, MouseClickEvent(145, 95, MouseButton.LEFT)))
        assertEquals(0, absoluteClicks, "Non-fixed still obeys ancestor overflow clipping in core interaction")

        val fixedHover = collectHoverChain(root, 225, 28).lastOrNull()?.key
        val absoluteHover = collectHoverChain(root, 145, 95).lastOrNull()?.key
        assertEquals(null, fixedHover, "Hover respects fixed root viewport clip outside root bounds")
        assertEquals(root.key, absoluteHover, "Hover respects non-fixed ancestor overflow clip")

        val inspector = InspectorController().also { it.toggle() }
        inspector.onLayoutCommitted(root, 31L)
        inspector.onNativeDomExpandedPanelRect(org.dreamfinity.dsgl.core.dom.layout.Rect(260, 20, 300, 220), 800, 600)
        inspector.onCursorMoved(185, 25)
        assertEquals(fixed.key?.toString(), inspector.hoveredKey, "Inspector picks fixed inside root viewport")
        inspector.onCursorMoved(145, 95)
        assertEquals(root.key?.toString(), inspector.hoveredKey, "Inspector keeps non-fixed ancestor overflow clipping")
        inspector.onCursorMoved(225, 28)
        assertNull(inspector.hoveredKey, "Inspector keeps fixed root viewport clipping outside viewport")
    }

    @Test
    fun `inspector highlight uses shared used geometry and clipping`() {
        val root = ContainerNode(key = "highlight-root", stackLayout = true).apply {
            width = 200
            height = 120
        }
        val overflowParent = ContainerNode(key = "highlight-parent").apply {
            width = 80
            height = 40
            overflowY = org.dreamfinity.dsgl.core.style.Overflow.Hidden
            inlineStyleDeclarations = styleDeclarations(StyleProperty.POSITION to "relative")
        }.applyParent(root)
        val fixed = ButtonNode("fixed", key = "highlight-fixed").apply {
            width = 40
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "180px",
                StyleProperty.TOP to "20px"
            )
        }.applyParent(overflowParent)
        val absolute = ButtonNode("absolute", key = "highlight-absolute").apply {
            width = 40
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "absolute",
                StyleProperty.LEFT to "140px",
                StyleProperty.TOP to "90px"
            )
        }.applyParent(overflowParent)

        val tree = DomTree(root)
        tree.render(ctx, width = 200, height = 120)
        val inspector = InspectorController().also { it.toggle() }
        inspector.onLayoutCommitted(root, 41L)
        inspector.onNativeDomExpandedPanelRect(org.dreamfinity.dsgl.core.dom.layout.Rect(260, 20, 300, 220), 800, 600)

        inspector.onCursorMoved(185, 25)
        inspector.buildDomSnapshot(800, 600)
        val fixedHighlight = inspector.overlayHoveredHighlight()
        assertNotNull(fixedHighlight)
        val fixedUsedGeometry = UsedInteractionGeometryResolver.resolveNodeGeometry(fixed)
        assertEquals(
            fixedUsedGeometry.visibleBorderRect ?: org.dreamfinity.dsgl.core.dom.layout.Rect(0, 0, 0, 0),
            fixedHighlight.borderRect
        )

        inspector.onCursorMoved(145, 95)
        inspector.buildDomSnapshot(800, 600)
        val clippedHighlight = inspector.overlayHoveredHighlight()
        assertNotNull(clippedHighlight)
        val rootUsedGeometry = UsedInteractionGeometryResolver.resolveNodeGeometry(root)
        assertEquals(
            rootUsedGeometry.visibleBorderRect ?: org.dreamfinity.dsgl.core.dom.layout.Rect(0, 0, 0, 0),
            clippedHighlight.borderRect,
            "Non-fixed clipped case highlights resolved fallback target with shared used geometry"
        )
    }

    @Test
    fun `relative positioned node highlight uses final rendered position`() {
        val root = ContainerNode(key = "relative-root")
        val relative = ButtonNode("relative", key = "relative-target").apply {
            width = 60
            height = 24
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "relative",
                StyleProperty.LEFT to "40px",
                StyleProperty.TOP to "18px"
            )
        }.applyParent(root)
        val sibling = ButtonNode("sibling", key = "relative-sibling").apply {
            width = 70
            height = 24
        }.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, width = 260, height = 140)
        val inspector = InspectorController().also { it.toggle() }
        inspector.onLayoutCommitted(root, 51L)
        inspector.onNativeDomExpandedPanelRect(org.dreamfinity.dsgl.core.dom.layout.Rect(260, 20, 300, 220), 800, 600)

        val usedGeometry = UsedInteractionGeometryResolver.resolveNodeGeometry(relative)
        val pickPointX = usedGeometry.usedBorderRect.x + 4
        val pickPointY = usedGeometry.usedBorderRect.y + 4
        inspector.onCursorMoved(pickPointX, pickPointY)
        inspector.buildDomSnapshot(800, 600)

        assertEquals(relative.key?.toString(), inspector.hoveredKey)
        val highlight = inspector.overlayHoveredHighlight()
        assertNotNull(highlight)
        assertEquals(
            usedGeometry.visibleBorderRect ?: org.dreamfinity.dsgl.core.dom.layout.Rect(0, 0, 0, 0),
            highlight.borderRect,
            "Relative highlight must use final rendered geometry, not original layout slot"
        )

        // Ensure we did not accidentally break neighboring static hit/highlight behavior.
        val siblingGeometry = UsedInteractionGeometryResolver.resolveNodeGeometry(sibling)
        inspector.onCursorMoved(siblingGeometry.usedBorderRect.x + 2, siblingGeometry.usedBorderRect.y + 2)
        inspector.buildDomSnapshot(800, 600)
        assertEquals(sibling.key?.toString(), inspector.hoveredKey)
    }

    @Test
    fun `inspector pick and highlight stay consistent for positioned overlap`() {
        val fixture = createPositionedOverlapFixture()
        fixture.tree.render(ctx, width = 220, height = 140)
        val inspector = InspectorController().also { it.toggle() }
        inspector.onLayoutCommitted(fixture.root, 61L)
        inspector.onNativeDomExpandedPanelRect(org.dreamfinity.dsgl.core.dom.layout.Rect(260, 20, 300, 220), 800, 600)

        inspector.onCursorMoved(10, 10)
        inspector.buildDomSnapshot(800, 600)

        assertEquals(fixture.fixed.key?.toString(), inspector.hoveredKey)
        val highlight = inspector.overlayHoveredHighlight()
        assertNotNull(highlight)
        val fixedGeometry = UsedInteractionGeometryResolver.resolveNodeGeometry(fixture.fixed)
        assertEquals(
            fixedGeometry.visibleBorderRect ?: org.dreamfinity.dsgl.core.dom.layout.Rect(0, 0, 0, 0),
            highlight.borderRect,
            "Inspector highlight must match picked node final used geometry in overlap cases"
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

