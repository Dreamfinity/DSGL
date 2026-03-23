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
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StackingContextChildContextBehaviorTests {
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
    fun `child-context creation uses explicit positioned plus non-default z-index trigger`() {
        val root = ContainerNode(key = "trigger-root")
        val owner = ContainerNode(key = "trigger-owner").applyParent(root)

        val staticHigh = ContainerNode(key = "static-high").apply {
            position = PositionMode.Static
            zIndex = 5
        }.applyParent(owner)
        val positionedDefaultZ = ContainerNode(key = "positioned-default").apply {
            position = PositionMode.Relative
            zIndex = 0
        }.applyParent(owner)
        val positionedExplicitZ = ContainerNode(key = "positioned-explicit").apply {
            position = PositionMode.Relative
            zIndex = 3
        }.applyParent(owner)
        val fixedExplicitZ = ContainerNode(key = "fixed-explicit").apply {
            position = PositionMode.Fixed
            zIndex = 4
        }.applyParent(owner)

        assertFalse(PositionedLayoutModel.matchesChildContextTrigger(staticHigh))
        assertFalse(PositionedLayoutModel.matchesChildContextTrigger(positionedDefaultZ))
        assertTrue(PositionedLayoutModel.matchesChildContextTrigger(positionedExplicitZ))
        assertTrue(PositionedLayoutModel.matchesChildContextTrigger(fixedExplicitZ))

        val ownerContext = owner.stackingContextScaffoldForTraversalOwner()
        val explicitParticipant = ownerContext.participants.firstOrNull { it.node === positionedExplicitZ }
        assertNotNull(explicitParticipant)
        assertEquals(PositionedLayoutModel.StackingParticipantKind.ChildContext, explicitParticipant.kind)

        val fixedInOwner = ownerContext.participants.firstOrNull { it.node === fixedExplicitZ }
        assertEquals(null, fixedInOwner)

        val rootContext = root.stackingContextScaffoldForTraversalOwner()
        val fixedInRoot = rootContext.participants.firstOrNull { it.node === fixedExplicitZ }
        assertNotNull(fixedInRoot)
        assertEquals(PositionedLayoutModel.StackingParticipantKind.ChildContext, fixedInRoot.kind)
        assertNotNull(fixedInRoot.rootContextPromotionTarget)
    }

    @Test
    fun `child context participation is atomic in parent ordering`() {
        val root = ContainerNode(key = "atomic-root", stackLayout = true)
        val lowerContext = ContainerNode(key = "atomic-lower").apply {
            position = PositionMode.Relative
            zIndex = 1
        }.applyParent(root)
        val higherContext = ContainerNode(key = "atomic-higher").apply {
            position = PositionMode.Relative
            zIndex = 2
        }.applyParent(root)

        var lowerDescendantClicks = 0
        var higherDescendantClicks = 0

        ButtonNode("lower-descendant", key = "atomic-lower-desc", backgroundColor = 0x00_11_44_88).apply {
            width = 72
            height = 24
            position = PositionMode.Relative
            zIndex = 9_999
            onClick { lowerDescendantClicks += 1 }
        }.applyParent(lowerContext)
        ButtonNode("higher-descendant", key = "atomic-higher-desc", backgroundColor = 0x00_88_22_44).apply {
            width = 72
            height = 24
            onClick { higherDescendantClicks += 1 }
        }.applyParent(higherContext)

        renderTree(root, width = 240, height = 140)

        assertEquals(listOf(lowerContext, higherContext), root.orderedChildrenForPaintTraversal())
        assertTrue(dispatchClick(root, MouseClickEvent(10, 10, MouseButton.LEFT)))
        assertEquals(0, lowerDescendantClicks)
        assertEquals(1, higherDescendantClicks)
    }

    @Test
    fun `local ordering inside child context remains by local z-index and dom tie-break`() {
        val root = ContainerNode(key = "local-root", stackLayout = true)
        val contextOwner = ContainerNode(key = "local-owner").apply {
            position = PositionMode.Relative
            zIndex = 2
            stackLayout = true
        }.applyParent(root)

        var lowClicks = 0
        var highClicks = 0
        val low = ButtonNode("low", key = "local-low").apply {
            width = 72
            height = 24
            position = PositionMode.Relative
            zIndex = 1
            onClick { lowClicks += 1 }
        }.applyParent(contextOwner)
        val high = ButtonNode("high", key = "local-high").apply {
            width = 72
            height = 24
            position = PositionMode.Relative
            zIndex = 5
            onClick { highClicks += 1 }
        }.applyParent(contextOwner)

        renderTree(root, width = 240, height = 140)

        assertEquals(listOf(low, high), contextOwner.orderedChildrenForPaintTraversal())
        assertEquals(listOf(high, low), contextOwner.orderedChildrenForHitTestingTraversal())
        assertTrue(dispatchClick(root, MouseClickEvent(10, 10, MouseButton.LEFT)))
        assertEquals(0, lowClicks)
        assertEquals(1, highClicks)
    }

    @Test
    fun `recursive paint traversal respects parent context order then child local order`() {
        val lowColor = 0x00_12_34_56
        val highColor = 0x00_65_43_21
        val root = ContainerNode(key = "paint-root", stackLayout = true)
        val lowerContext = ContainerNode(key = "paint-lower").apply {
            position = PositionMode.Relative
            zIndex = 1
        }.applyParent(root)
        val higherContext = ContainerNode(key = "paint-higher").apply {
            position = PositionMode.Relative
            zIndex = 2
        }.applyParent(root)

        ButtonNode("lower", backgroundColor = lowColor, key = "paint-lower-btn").apply {
            width = 72
            height = 24
            position = PositionMode.Relative
            zIndex = 9_999
        }.applyParent(lowerContext)
        ButtonNode("higher", backgroundColor = highColor, key = "paint-higher-btn").apply {
            width = 72
            height = 24
            zIndex = 0
        }.applyParent(higherContext)

        val tree = DomTree(root)
        tree.render(ctx, 240, 140)
        val drawRects = tree.paint(ctx).filterIsInstance<RenderCommand.DrawRect>()
        val lowIndex = drawRects.indexOfFirst { it.color == lowColor && it.x == 0 && it.y == 0 }
        val highIndex = drawRects.indexOfFirst { it.color == highColor && it.x == 0 && it.y == 0 }

        assertTrue(lowIndex >= 0)
        assertTrue(highIndex >= 0)
        assertTrue(lowIndex < highIndex)
    }

    @Test
    fun `recursive hit traversal mirrors recursive paint order in reverse`() {
        val root = ContainerNode(key = "hit-root", stackLayout = true)
        val low = ContainerNode(key = "hit-low").apply {
            position = PositionMode.Relative
            zIndex = 1
        }.applyParent(root)
        val mid = ContainerNode(key = "hit-mid").apply {
            position = PositionMode.Relative
            zIndex = 2
        }.applyParent(root)
        val high = ContainerNode(key = "hit-high").apply {
            position = PositionMode.Relative
            zIndex = 3
        }.applyParent(root)

        var lowClicks = 0
        var midClicks = 0
        var highClicks = 0
        ButtonNode("low", key = "hit-low-btn").apply {
            width = 72
            height = 24
            onClick { lowClicks += 1 }
        }.applyParent(low)
        ButtonNode("mid", key = "hit-mid-btn").apply {
            width = 72
            height = 24
            onClick { midClicks += 1 }
        }.applyParent(mid)
        ButtonNode("high", key = "hit-high-btn").apply {
            width = 72
            height = 24
            onClick { highClicks += 1 }
        }.applyParent(high)

        renderTree(root, width = 240, height = 140)
        assertEquals(listOf(high, mid, low), root.orderedChildrenForHitTestingTraversal())
        assertTrue(dispatchClick(root, MouseClickEvent(10, 10, MouseButton.LEFT)))
        assertEquals(0, lowClicks)
        assertEquals(0, midClicks)
        assertEquals(1, highClicks)
    }

    @Test
    fun `fixed root participation remains authoritative when fixed matches phase 4 trigger`() {
        val root = ContainerNode(key = "fixed-root", stackLayout = true)
        val contextA = ContainerNode(key = "fixed-context-a").apply {
            position = PositionMode.Relative
            zIndex = 1
            margin = Insets(top = 30, right = 0, bottom = 0, left = 50)
        }.applyParent(root)
        val contextB = ContainerNode(key = "fixed-context-b").apply {
            position = PositionMode.Relative
            zIndex = 2
        }.applyParent(root)

        var fixedClicks = 0
        var normalClicks = 0
        val fixed = ButtonNode("fixed", key = "fixed-node").apply {
            width = 72
            height = 24
            position = PositionMode.Fixed
            zIndex = 9_999
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "8px",
                StyleProperty.TOP to "9px"
            )
            onClick { fixedClicks += 1 }
        }.applyParent(contextA)
        ButtonNode("normal", key = "fixed-normal").apply {
            width = 72
            height = 24
            onClick { normalClicks += 1 }
        }.applyParent(contextB)

        renderTree(root, width = 260, height = 180)

        assertSame(contextA, fixed.parent)
        assertEquals(8, fixed.bounds.x)
        assertEquals(9, fixed.bounds.y)
        assertTrue(dispatchClick(root, MouseClickEvent(10, 11, MouseButton.LEFT)))
        assertEquals(1, fixedClicks)
        assertEquals(0, normalClicks)
    }

    @Test
    fun `non-context nodes preserve ordinary parent-local behavior`() {
        val root = ContainerNode(key = "nonctx-root", stackLayout = true)
        val first = ContainerNode(key = "nonctx-first").applyParent(root)
        val second = ContainerNode(key = "nonctx-second").applyParent(root)
        val firstA = ContainerNode(key = "nonctx-first-a").applyParent(first)
        val firstB = ContainerNode(key = "nonctx-first-b").applyParent(first)

        assertEquals(listOf(first, second), root.orderedChildrenForPaintTraversal())
        assertEquals(listOf(second, first), root.orderedChildrenForHitTestingTraversal())
        assertEquals(listOf(firstA, firstB), first.orderedChildrenForPaintTraversal())
        assertEquals(listOf(firstB, firstA), first.orderedChildrenForHitTestingTraversal())
    }

    private fun renderTree(root: ContainerNode, width: Int, height: Int) {
        DomTree(root).render(ctx, width, height)
    }

    private fun styleDeclarations(vararg entries: Pair<StyleProperty, String>): StyleDeclarations {
        return StyleDeclarations().apply {
            entries.forEach { (property, literal) ->
                set(property, StyleExpression.Literal(literal))
            }
        }
    }
}
