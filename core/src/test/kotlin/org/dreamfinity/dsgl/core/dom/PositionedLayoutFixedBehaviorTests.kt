package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.dispatchClick
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.PositionMode
import org.dreamfinity.dsgl.core.style.StyleDeclarations
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PositionedLayoutFixedBehaviorTests {
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
    fun `fixed element is removed from normal flow`() {
        val root = ContainerNode(key = "fixed-flow-root")
        val fixed = ContainerNode(key = "fixed-flow-fixed").apply {
            width = 40
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "8px",
                StyleProperty.TOP to "10px"
            )
        }
        val follower = ContainerNode(key = "fixed-flow-follower").apply {
            width = 30
            height = 12
        }
        fixed.applyParent(root)
        follower.applyParent(root)

        renderTree(root, width = 220, height = 140)

        assertEquals(0, follower.bounds.y)
        assertEquals(8, fixed.bounds.x)
        assertEquals(10, fixed.bounds.y)
    }

    @Test
    fun `fixed anchors to current root viewport`() {
        val root = ContainerNode(key = "fixed-root-anchor")
        val child = ContainerNode(key = "fixed-child").apply {
            width = 24
            height = 10
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "20px",
                StyleProperty.TOP to "14px"
            )
        }
        child.applyParent(root)

        renderTree(root, width = 260, height = 180)

        assertEquals(20, child.bounds.x)
        assertEquals(14, child.bounds.y)
        assertTrue(child.containsGlobalPoint(22, 16))
        assertFalse(child.containsGlobalPoint(2, 2))
    }

    @Test
    fun `fixed ignores ordinary content scroll`() {
        val root = ContainerNode(key = "fixed-scroll-root").apply {
            overflowY = Overflow.Scroll
        }
        val spacer = ContainerNode(key = "fixed-scroll-spacer").apply {
            width = 100
            height = 280
        }
        val fixed = ContainerNode(key = "fixed-scroll-fixed").apply {
            width = 20
            height = 10
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "6px",
                StyleProperty.TOP to "5px"
            )
        }
        spacer.applyParent(root)
        fixed.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 180, 90)
        assertTrue(root.scrollContainerState().maxScrollY > 0)
        val initialBounds = fixed.bounds

        root.setScrollOffsets(0, 60)
        tree.render(ctx, 180, 90)

        assertEquals(initialBounds, fixed.bounds)
        assertTrue(fixed.containsGlobalPoint(8, 7))
    }

    @Test
    fun `fixed is not anchored to nearest positioned ancestor`() {
        val root = ContainerNode(key = "fixed-ancestor-root")
        val ancestor = ContainerNode(key = "fixed-ancestor").apply {
            width = 120
            height = 80
            margin = org.dreamfinity.dsgl.core.dom.layout.Insets(top = 40, right = 0, bottom = 0, left = 60)
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "relative"
            )
        }
        val child = ContainerNode(key = "fixed-inside-relative").apply {
            width = 20
            height = 10
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "9px",
                StyleProperty.TOP to "11px"
            )
        }
        child.applyParent(ancestor)
        ancestor.applyParent(root)

        renderTree(root, width = 260, height = 180)

        assertTrue(child.containsGlobalPoint(10, 12))
        assertFalse(child.containsGlobalPoint(70, 52))
    }

    @Test
    fun `fixed hit-testing uses final fixed rect`() {
        val root = ContainerNode(key = "fixed-hit-root")
        var clicks = 0
        val button = ButtonNode("fixed", key = "fixed-hit-button").apply {
            width = 40
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "30px",
                StyleProperty.TOP to "18px"
            )
            onClick { clicks += 1 }
        }
        button.applyParent(root)

        renderTree(root, width = 240, height = 160)

        assertFalse(dispatchClick(root, MouseClickEvent(2, 2, MouseButton.LEFT)))
        assertTrue(dispatchClick(root, MouseClickEvent(32, 20, MouseButton.LEFT)))
        assertEquals(1, clicks)
    }

    @Test
    fun `fixed overlap obeys z-index for paint and hit order`() {
        val root = ContainerNode(key = "fixed-z-root")
        var lowerClicks = 0
        var upperClicks = 0

        val lower = ButtonNode("lower", key = "fixed-lower").apply {
            width = 60
            height = 24
            zIndex = 1
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "10px",
                StyleProperty.TOP to "8px"
            )
            onClick { lowerClicks += 1 }
        }
        val upper = ButtonNode("upper", key = "fixed-upper").apply {
            width = 60
            height = 24
            zIndex = 4
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "10px",
                StyleProperty.TOP to "8px"
            )
            onClick { upperClicks += 1 }
        }
        lower.applyParent(root)
        upper.applyParent(root)

        renderTree(root, width = 240, height = 160)

        assertEquals(listOf(lower, upper), root.orderedChildrenForPaintTraversal())
        assertTrue(dispatchClick(root, MouseClickEvent(12, 10, MouseButton.LEFT)))
        assertEquals(0, lowerClicks)
        assertEquals(1, upperClicks)
    }

    @Test
    fun `fixed uses left over right and top over bottom`() {
        val root = ContainerNode(key = "fixed-precedence-root")
        val child = ContainerNode(key = "fixed-precedence-child").apply {
            width = 20
            height = 10
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "25px",
                StyleProperty.RIGHT to "7px",
                StyleProperty.TOP to "12px",
                StyleProperty.BOTTOM to "3px"
            )
        }
        child.applyParent(root)

        renderTree(root, width = 240, height = 160)

        assertEquals(25, child.bounds.x)
        assertEquals(12, child.bounds.y)
    }

    @Test
    fun `static relative and absolute behavior remains intact`() {
        val root = ContainerNode(key = "fixed-nr-root")
        val staticChild = ContainerNode(key = "fixed-nr-static").apply {
            width = 30
            height = 12
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "static",
                StyleProperty.LEFT to "50px",
                StyleProperty.TOP to "20px"
            )
        }
        val relativeChild = ContainerNode(key = "fixed-nr-relative").apply {
            width = 30
            height = 12
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "relative",
                StyleProperty.LEFT to "18px",
                StyleProperty.TOP to "7px"
            )
        }
        val absoluteChild = ContainerNode(key = "fixed-nr-absolute").apply {
            width = 20
            height = 10
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "absolute",
                StyleProperty.LEFT to "40px",
                StyleProperty.TOP to "22px"
            )
        }
        val follower = ContainerNode(key = "fixed-nr-follower").apply {
            width = 22
            height = 10
        }
        staticChild.applyParent(root)
        relativeChild.applyParent(root)
        absoluteChild.applyParent(root)
        follower.applyParent(root)

        renderTree(root, width = 260, height = 180)

        assertTrue(staticChild.containsGlobalPoint(staticChild.bounds.x + 2, staticChild.bounds.y + 2))
        assertFalse(staticChild.containsGlobalPoint(staticChild.bounds.x + 52, staticChild.bounds.y + 22))
        assertFalse(relativeChild.containsGlobalPoint(relativeChild.bounds.x + 2, relativeChild.bounds.y + 2))
        assertTrue(relativeChild.containsGlobalPoint(relativeChild.bounds.x + 20, relativeChild.bounds.y + 9))
        assertTrue(absoluteChild.containsGlobalPoint(42, 24))
        assertFalse(absoluteChild.containsGlobalPoint(2, 2))
        assertEquals(relativeChild.bounds.y + relativeChild.bounds.height, follower.bounds.y)
    }

    @Test
    fun `inspector overrides keep static relative absolute and fixed behavior`() {
        val root = ContainerNode(key = "fixed-inspector-root").apply {
            overflowY = Overflow.Scroll
        }
        val spacer = ContainerNode(key = "fixed-inspector-spacer").apply {
            width = 80
            height = 220
        }
        val child = ContainerNode(key = "fixed-inspector-child").apply {
            width = 20
            height = 10
        }
        child.applyParent(root)
        spacer.applyParent(root)
        val tree = DomTree(root)
        tree.render(ctx, 220, 90)

        val baselineBounds = child.bounds

        StyleEngine.setInspectorOverrideLiteral(child, StyleProperty.POSITION, "static").getOrThrow()
        StyleEngine.setInspectorOverrideLiteral(child, StyleProperty.LEFT, "15px").getOrThrow()
        StyleEngine.setInspectorOverrideLiteral(child, StyleProperty.TOP, "9px").getOrThrow()
        tree.render(ctx, 220, 90)
        assertEquals(baselineBounds, child.bounds)

        StyleEngine.setInspectorOverrideLiteral(child, StyleProperty.POSITION, "relative").getOrThrow()
        tree.render(ctx, 220, 90)
        assertFalse(child.containsGlobalPoint(child.bounds.x + 2, child.bounds.y + 2))
        assertTrue(child.containsGlobalPoint(child.bounds.x + 17, child.bounds.y + 11))

        StyleEngine.setInspectorOverrideLiteral(child, StyleProperty.POSITION, "absolute").getOrThrow()
        tree.render(ctx, 220, 90)
        assertEquals(15, child.bounds.x)
        assertEquals(9, child.bounds.y)

        StyleEngine.setInspectorOverrideLiteral(child, StyleProperty.POSITION, "fixed").getOrThrow()
        tree.render(ctx, 220, 90)
        val fixedBeforeScroll = child.bounds
        root.setScrollOffsets(0, 60)
        tree.render(ctx, 220, 90)
        assertEquals(fixedBeforeScroll, child.bounds)
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
