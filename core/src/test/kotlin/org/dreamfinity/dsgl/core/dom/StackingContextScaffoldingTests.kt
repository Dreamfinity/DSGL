package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.dispatchClick
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleDeclarations
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.PositionMode
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StackingContextScaffoldingTests {
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
    fun `root stacking context identity is explicit and root-scoped`() {
        val rootA = ContainerNode(key = "stacking-root-a")
        val rootB = ContainerNode(key = "stacking-root-b")
        val childA = ContainerNode(key = "stacking-child-a").applyParent(rootA)
        val childB = ContainerNode(key = "stacking-child-b").applyParent(rootB)

        val idRootA = rootA.rootStackingContextIdentityForPositioning()
        val idChildA = childA.rootStackingContextIdentityForPositioning()
        val idRootB = rootB.rootStackingContextIdentityForPositioning()
        val idChildB = childB.rootStackingContextIdentityForPositioning()

        assertEquals(idRootA, idChildA)
        assertEquals(idRootB, idChildB)
        assertTrue(idRootA != idRootB)
        assertSame(rootA, idRootA.rootNode)
        assertSame(rootB, idRootB.rootNode)
    }

    @Test
    fun `stacking context scaffold represents participants and future promotion hints`() {
        val root = ContainerNode(key = "stacking-scaffold-root")
        val owner = ContainerNode(key = "stacking-scaffold-owner").applyParent(root)
        val normalChild = ContainerNode(key = "stacking-normal").applyParent(owner)
        val fixedChild = ContainerNode(key = "stacking-fixed").apply {
            position = PositionMode.Fixed
            zIndex = 9999
        }.applyParent(owner)

        val context = owner.stackingContextScaffoldForTraversalOwner()
        val rootId = owner.rootStackingContextIdentityForPositioning()

        assertEquals(rootId, context.id)
        assertSame(owner, context.ownerNode)
        assertSame(root, context.rootNode)
        assertEquals(2, context.participants.size)

        val normalParticipant = context.participants.firstOrNull { it.node === normalChild }
        assertNotNull(normalParticipant)
        assertEquals(PositionedLayoutModel.StackingParticipantKind.LocalNode, normalParticipant.kind)
        assertFalse(normalParticipant.createsChildContextHint)
        assertNull(normalParticipant.rootContextPromotionTarget)

        val fixedParticipant = context.participants.firstOrNull { it.node === fixedChild }
        assertNotNull(fixedParticipant)
        assertEquals(PositionedLayoutModel.StackingParticipantKind.ChildContext, fixedParticipant.kind)
        assertTrue(fixedParticipant.createsChildContextHint)
        assertEquals(rootId, fixedParticipant.rootContextPromotionTarget)
    }

    @Test
    fun `paint traversal behavior remains parent-local after scaffolding`() {
        val root = ContainerNode(key = "stacking-paint-root", stackLayout = true)
        val early = ContainerNode(key = "stacking-paint-early").applyParent(root)
        val later = ContainerNode(key = "stacking-paint-later").applyParent(root)
        ContainerNode(key = "stacking-paint-fixed").apply {
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "4px",
                StyleProperty.TOP to "4px"
            )
            zIndex = 9999
        }.applyParent(early)

        assertEquals(listOf(early, later), root.orderedChildrenForPaintTraversal())
        val scaffold = root.stackingContextScaffoldForTraversalOwner()
        assertEquals(listOf(early, later), scaffold.participants.map { it.node })
    }

    @Test
    fun `hit traversal behavior remains parent-local after scaffolding`() {
        val root = ContainerNode(key = "stacking-hit-root", stackLayout = true)
        val earlySubtree = ContainerNode(key = "stacking-hit-early").applyParent(root)
        val laterSubtree = ContainerNode(key = "stacking-hit-later").applyParent(root)

        var fixedClicks = 0
        var laterClicks = 0

        ButtonNode("fixed", key = "stacking-hit-fixed").apply {
            width = 72
            height = 24
            zIndex = 9999
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "8px",
                StyleProperty.TOP to "8px"
            )
            onClick { fixedClicks += 1 }
        }.applyParent(earlySubtree)
        ButtonNode("later", key = "stacking-hit-later-node").apply {
            width = 72
            height = 24
            onClick { laterClicks += 1 }
        }.applyParent(laterSubtree)

        renderTree(root, width = 220, height = 140)
        assertEquals(listOf(laterSubtree, earlySubtree), root.orderedChildrenForHitTestingTraversal())

        assertTrue(dispatchClick(root, MouseClickEvent(10, 10, MouseButton.LEFT)))
        assertEquals(0, fixedClicks)
        assertEquals(1, laterClicks)
    }

    @Test
    fun `phase 1 nested fixed characterization remains unchanged after scaffolding`() {
        val root = ContainerNode(key = "stacking-phase1-root", stackLayout = true)
        val earlySubtree = ContainerNode(key = "stacking-phase1-early").apply {
            width = 120
            height = 60
        }
        val laterSubtree = ContainerNode(key = "stacking-phase1-later").apply {
            width = 120
            height = 60
        }

        var fixedClicks = 0
        var laterClicks = 0

        val fixed = ButtonNode("fixed", key = "stacking-phase1-fixed").apply {
            width = 72
            height = 24
            zIndex = 9999
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "8px",
                StyleProperty.TOP to "8px"
            )
            onClick { fixedClicks += 1 }
        }
        val later = ButtonNode("later", key = "stacking-phase1-later-node").apply {
            width = 72
            height = 24
            onClick { laterClicks += 1 }
        }

        fixed.applyParent(earlySubtree)
        later.applyParent(laterSubtree)
        earlySubtree.applyParent(root)
        laterSubtree.applyParent(root)

        renderTree(root, width = 220, height = 140)
        assertEquals(8, fixed.bounds.x)
        assertEquals(8, fixed.bounds.y)
        assertTrue(dispatchClick(root, MouseClickEvent(10, 10, MouseButton.LEFT)))
        assertEquals(0, fixedClicks)
        assertEquals(1, laterClicks)
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
