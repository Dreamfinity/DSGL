package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.dispatchClick
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.CssUnit
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
import kotlin.test.assertTrue

class PositionedLayoutStaticBaselineTests {
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
    fun `default position remains static`() {
        val root = ContainerNode(key = "root")
        val child = ContainerNode(key = "child").apply {
            width = 40
            height = 18
        }
        child.applyParent(root)

        renderTree(root)

        assertEquals(PositionMode.Static, child.position)
        val computed = child.appliedComputedStyleSnapshot()
        assertNotNull(computed)
        assertEquals(PositionMode.Static, computed.position)
    }

    @Test
    fun `explicit static matches default layout behavior`() {
        val defaultRoot = ContainerNode(key = "default-root")
        val defaultChild = ContainerNode(key = "default-child").apply {
            width = 40
            height = 18
        }
        defaultChild.applyParent(defaultRoot)

        val explicitRoot = ContainerNode(key = "explicit-root")
        val explicitChild = ContainerNode(key = "explicit-child").apply {
            width = 40
            height = 18
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "static"
            )
        }
        explicitChild.applyParent(explicitRoot)

        renderTree(defaultRoot)
        renderTree(explicitRoot)

        assertEquals(defaultChild.bounds, explicitChild.bounds)
    }

    @Test
    fun `static ignores offsets for visible and hit geometry`() {
        val baselineRoot = ContainerNode(key = "baseline-root")
        val baselineChild = ContainerNode(key = "baseline-child").apply {
            width = 40
            height = 18
        }
        baselineChild.applyParent(baselineRoot)

        val offsetRoot = ContainerNode(key = "offset-root")
        val offsetChild = ContainerNode(key = "offset-child").apply {
            width = 40
            height = 18
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "static",
                StyleProperty.LEFT to "40px",
                StyleProperty.TOP to "30px",
                StyleProperty.RIGHT to "8px",
                StyleProperty.BOTTOM to "6px"
            )
        }
        offsetChild.applyParent(offsetRoot)

        renderTree(baselineRoot)
        renderTree(offsetRoot)

        assertEquals(baselineChild.bounds, offsetChild.bounds)
        val computed = offsetChild.appliedComputedStyleSnapshot()
        assertNotNull(computed)
        assertEquals(40f, computed.left?.value)
        assertEquals(CssUnit.Px, computed.left?.unit)
        assertEquals(30f, computed.top?.value)
        assertEquals(CssUnit.Px, computed.top?.unit)
        val centerX = offsetChild.bounds.x + offsetChild.bounds.width / 2
        val centerY = offsetChild.bounds.y + offsetChild.bounds.height / 2
        assertTrue(offsetChild.containsGlobalPoint(centerX, centerY))
        assertFalse(offsetChild.containsGlobalPoint(centerX + 40, centerY + 30))
    }

    @Test
    fun `static node remains in normal flow even with offsets declared`() {
        val root = ContainerNode(key = "flow-root")
        val first = ContainerNode(key = "first").apply {
            width = 50
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "static",
                StyleProperty.LEFT to "60px",
                StyleProperty.TOP to "40px"
            )
        }
        val second = ContainerNode(key = "second").apply {
            width = 30
            height = 12
        }
        first.applyParent(root)
        second.applyParent(root)

        renderTree(root)

        assertEquals(first.bounds.y + first.bounds.height, second.bounds.y)
    }

    @Test
    fun `static z-index does not reorder static paint or hit order`() {
        val root = ContainerNode(key = "click-root").apply {
            bounds = Rect(0, 0, 120, 80)
        }
        var underClicks = 0
        var overClicks = 0

        val under = ButtonNode("under", key = "under").apply {
            bounds = Rect(10, 10, 80, 24)
            zIndex = 100
            onClick { underClicks += 1 }
        }
        under.applyParent(root)

        val over = ButtonNode("over", key = "over").apply {
            bounds = Rect(10, 10, 80, 24)
            zIndex = -100
            onClick { overClicks += 1 }
        }
        over.applyParent(root)

        assertEquals(listOf(under, over), root.orderedChildrenForPaintTraversal())
        val click = MouseClickEvent(mouseX = 12, mouseY = 12, mouseButton = MouseButton.LEFT)
        assertTrue(dispatchClick(root, click))
        assertEquals(1, overClicks)
        assertEquals(0, underClicks)
    }

    @Test
    fun `inspector static offset overrides keep static geometry unchanged`() {
        val root = ContainerNode(key = "inspector-root")
        val child = ContainerNode(key = "inspector-child").apply {
            width = 44
            height = 16
        }
        child.applyParent(root)
        val tree = DomTree(root)
        tree.render(ctx, 200, 120)
        val baselineBounds = child.bounds

        StyleEngine.setInspectorOverrideLiteral(child, StyleProperty.POSITION, "static").getOrThrow()
        StyleEngine.setInspectorOverrideLiteral(child, StyleProperty.LEFT, "36px").getOrThrow()
        StyleEngine.setInspectorOverrideLiteral(child, StyleProperty.TOP, "14px").getOrThrow()

        tree.render(ctx, 200, 120)

        assertEquals(baselineBounds, child.bounds)
    }

    @Test
    fun `sticky style value remains runtime-inactive`() {
        val root = ContainerNode(key = "sticky-inactive-root")
        val sticky = ContainerNode(key = "sticky-inactive-node").apply {
            width = 50
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "sticky",
                StyleProperty.LEFT to "60px",
                StyleProperty.TOP to "30px"
            )
            zIndex = 100
        }
        val follower = ContainerNode(key = "sticky-inactive-follower").apply {
            width = 30
            height = 12
        }
        sticky.applyParent(root)
        follower.applyParent(root)

        renderTree(root)

        assertEquals(0, sticky.bounds.x)
        assertEquals(0, sticky.bounds.y)
        assertEquals(sticky.bounds.y + sticky.bounds.height, follower.bounds.y)
        assertFalse(sticky.participatesInPositionedOrderingModel())
        assertEquals(listOf(sticky, follower), root.orderedChildrenForPaintTraversal())
    }

    private fun renderTree(root: ContainerNode) {
        DomTree(root).render(ctx, 240, 160)
    }

    private fun styleDeclarations(vararg entries: Pair<StyleProperty, String>): StyleDeclarations {
        return StyleDeclarations().apply {
            entries.forEach { (property, literal) ->
                set(property, StyleExpression.Literal(literal))
            }
        }
    }
}
