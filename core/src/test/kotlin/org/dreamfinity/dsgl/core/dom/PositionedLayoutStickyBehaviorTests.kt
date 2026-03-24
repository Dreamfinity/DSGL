package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.dispatchClick
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Overflow
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

class PositionedLayoutStickyBehaviorTests {
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
    fun `sticky top sticks visually and keeps normal flow slot`() {
        val root = ContainerNode(key = "sticky-top-root").apply {
            overflowY = Overflow.Scroll
        }
        val sticky = ContainerNode(key = "sticky-top-node").apply {
            width = 100
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "sticky",
                StyleProperty.TOP to "0px"
            )
        }
        val follower = ContainerNode(key = "sticky-top-follower").apply {
            width = 100
            height = 280
        }
        sticky.applyParent(root)
        follower.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 200, 100)
        assertEquals(0, visibleRect(sticky).y)

        root.setScrollOffsets(0, 40)
        tree.render(ctx, 200, 100)

        assertEquals(0, visibleRect(sticky).y)
        assertEquals(-40, sticky.bounds.y)
        assertEquals(sticky.bounds.y + sticky.bounds.height, follower.bounds.y)
    }

    @Test
    fun `sticky bottom-only mode is deterministic`() {
        val root = ContainerNode(key = "sticky-bottom-root").apply {
            overflowY = Overflow.Scroll
        }
        val topSpacer = ContainerNode(key = "sticky-bottom-spacer-top").apply {
            width = 100
            height = 140
        }
        val sticky = ContainerNode(key = "sticky-bottom-node").apply {
            width = 100
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "sticky",
                StyleProperty.BOTTOM to "0px"
            )
        }
        val bottomSpacer = ContainerNode(key = "sticky-bottom-spacer-bottom").apply {
            width = 100
            height = 180
        }
        topSpacer.applyParent(root)
        sticky.applyParent(root)
        bottomSpacer.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 200, 100)
        assertEquals(80, visibleRect(sticky).y)

        root.setScrollOffsets(0, 100)
        tree.render(ctx, 200, 100)
        assertEquals(40, visibleRect(sticky).y)
    }

    @Test
    fun `sticky both top and bottom uses top precedence`() {
        val root = ContainerNode(key = "sticky-both-root").apply {
            overflowY = Overflow.Scroll
        }
        val sticky = ContainerNode(key = "sticky-both-node").apply {
            width = 80
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "sticky",
                StyleProperty.TOP to "10px",
                StyleProperty.BOTTOM to "0px"
            )
        }
        val follower = ContainerNode(key = "sticky-both-follower").apply {
            width = 80
            height = 220
        }
        sticky.applyParent(root)
        follower.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 200, 100)

        assertEquals(10, visibleRect(sticky).y)
    }

    @Test
    fun `sticky left sticks visually on horizontal axis and keeps normal flow slot`() {
        val root = ContainerNode(key = "sticky-left-root").apply {
            display = org.dreamfinity.dsgl.core.style.Display.Flex
            overflowX = Overflow.Scroll
        }
        val sticky = ContainerNode(key = "sticky-left-node").apply {
            width = 40
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "sticky",
                StyleProperty.LEFT to "0px"
            )
        }
        val filler = ContainerNode(key = "sticky-left-filler").apply {
            width = 260
            height = 20
        }
        sticky.applyParent(root)
        filler.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 100, 80)
        assertEquals(0, visibleRect(sticky).x)

        root.setScrollOffsets(120, 0)
        tree.render(ctx, 100, 80)

        assertEquals(0, visibleRect(sticky).x)
        assertEquals(-120, sticky.bounds.x)
        assertEquals(sticky.bounds.x + sticky.bounds.width, filler.bounds.x)
    }

    @Test
    fun `sticky right-only mode is deterministic on horizontal axis`() {
        val root = ContainerNode(key = "sticky-right-root").apply {
            display = org.dreamfinity.dsgl.core.style.Display.Flex
            overflowX = Overflow.Scroll
        }
        val spacer = ContainerNode(key = "sticky-right-spacer").apply {
            width = 140
            height = 20
        }
        val sticky = ContainerNode(key = "sticky-right-node").apply {
            width = 20
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "sticky",
                StyleProperty.RIGHT to "0px"
            )
        }
        val tail = ContainerNode(key = "sticky-right-tail").apply {
            width = 120
            height = 20
        }
        spacer.applyParent(root)
        sticky.applyParent(root)
        tail.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 100, 80)
        assertEquals(80, visibleRect(sticky).x)

        root.setScrollOffsets(100, 0)
        tree.render(ctx, 100, 80)
        assertEquals(40, visibleRect(sticky).x)
    }

    @Test
    fun `sticky both left and right uses left precedence`() {
        val root = ContainerNode(key = "sticky-horizontal-both-root").apply {
            display = org.dreamfinity.dsgl.core.style.Display.Flex
            overflowX = Overflow.Scroll
        }
        val sticky = ContainerNode(key = "sticky-horizontal-both-node").apply {
            width = 20
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "sticky",
                StyleProperty.LEFT to "7px",
                StyleProperty.RIGHT to "0px"
            )
        }
        val filler = ContainerNode(key = "sticky-horizontal-both-filler").apply {
            width = 200
            height = 20
        }
        sticky.applyParent(root)
        filler.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 100, 80)

        assertEquals(7, visibleRect(sticky).x)
    }

    @Test
    fun `sticky combines horizontal and vertical offsets from independent axis rules`() {
        val root = ContainerNode(key = "sticky-xy-root").apply {
            overflowX = Overflow.Scroll
            overflowY = Overflow.Scroll
        }
        val sticky = ContainerNode(key = "sticky-xy-node").apply {
            width = 80
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "sticky",
                StyleProperty.LEFT to "0px",
                StyleProperty.TOP to "0px"
            )
        }
        val filler = ContainerNode(key = "sticky-xy-filler").apply {
            width = 400
            height = 400
        }
        sticky.applyParent(root)
        filler.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 120, 100)
        root.setScrollOffsets(50, 60)
        tree.render(ctx, 120, 100)

        assertEquals(-50, sticky.bounds.x)
        assertEquals(-60, sticky.bounds.y)
        assertEquals(0, visibleRect(sticky).x)
        assertEquals(0, visibleRect(sticky).y)
    }

    @Test
    fun `sticky with no horizontal inset stays inactive on horizontal axis`() {
        val root = ContainerNode(key = "sticky-horizontal-inactive-root").apply {
            display = org.dreamfinity.dsgl.core.style.Display.Flex
            overflowX = Overflow.Scroll
        }
        val sticky = ContainerNode(key = "sticky-horizontal-inactive-node").apply {
            width = 40
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "sticky"
            )
        }
        val filler = ContainerNode(key = "sticky-horizontal-inactive-filler").apply {
            width = 260
            height = 20
        }
        sticky.applyParent(root)
        filler.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 100, 80)
        root.setScrollOffsets(70, 0)
        tree.render(ctx, 100, 80)

        assertEquals(sticky.bounds.x, visibleRect(sticky).x)
    }

    @Test
    fun `sticky with no inset stays inactive on vertical axis`() {
        val root = ContainerNode(key = "sticky-inactive-root").apply {
            overflowY = Overflow.Scroll
        }
        val sticky = ContainerNode(key = "sticky-inactive-node").apply {
            width = 90
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "sticky"
            )
        }
        val filler = ContainerNode(key = "sticky-inactive-filler").apply {
            width = 90
            height = 260
        }
        sticky.applyParent(root)
        filler.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 200, 100)
        root.setScrollOffsets(0, 50)
        tree.render(ctx, 200, 100)

        assertEquals(sticky.bounds.y, visibleRect(sticky).y)
    }

    @Test
    fun `sticky used geometry resolves from shared path without render-owned refresh`() {
        val root = ContainerNode(key = "sticky-refresh-root").apply {
            overflowY = Overflow.Scroll
        }
        val sticky = ContainerNode(key = "sticky-refresh-node").apply {
            width = 100
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "sticky",
                StyleProperty.TOP to "0px"
            )
        }
        val filler = ContainerNode(key = "sticky-refresh-filler").apply {
            width = 100
            height = 260
        }
        sticky.applyParent(root)
        filler.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 200, 100)

        root.setScrollOffsets(0, 60)
        sticky.render(
            ctx = ctx,
            x = sticky.bounds.x,
            y = sticky.bounds.y - 60,
            width = sticky.bounds.width,
            height = sticky.bounds.height
        )

        assertEquals(0, visibleRect(sticky).y)
    }

    @Test
    fun `sticky render and interaction use same final geometry`() {
        val root = ContainerNode(key = "sticky-click-root").apply {
            overflowY = Overflow.Scroll
        }
        var clicks = 0
        val sticky = ButtonNode("sticky", key = "sticky-click-node").apply {
            width = 100
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "sticky",
                StyleProperty.TOP to "0px"
            )
            onClick { clicks += 1 }
        }
        val filler = ContainerNode(key = "sticky-click-filler").apply {
            width = 100
            height = 260
        }
        sticky.applyParent(root)
        filler.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 200, 100)
        root.setScrollOffsets(0, 60)
        tree.render(ctx, 200, 100)

        val rect = visibleRect(sticky)
        assertTrue(dispatchClick(root, MouseClickEvent(rect.x + 4, rect.y + 4, MouseButton.LEFT)))
        assertEquals(1, clicks)
    }

    @Test
    fun `sticky render interaction and inspector stay aligned for combined axis movement`() {
        val root = ContainerNode(key = "sticky-xy-consistency-root").apply {
            overflowX = Overflow.Scroll
            overflowY = Overflow.Scroll
        }
        var clicks = 0
        val sticky = ButtonNode("sticky", key = "sticky-xy-consistency-node").apply {
            width = 80
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "sticky",
                StyleProperty.LEFT to "0px",
                StyleProperty.TOP to "0px"
            )
            onClick { clicks += 1 }
        }
        val spacer = ContainerNode(key = "sticky-xy-consistency-spacer").apply {
            width = 20
            height = 120
        }
        val filler = ContainerNode(key = "sticky-xy-consistency-filler").apply {
            width = 420
            height = 420
        }
        sticky.applyParent(root)
        spacer.applyParent(root)
        filler.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 140, 110)
        root.setScrollOffsets(70, 60)
        tree.render(ctx, 140, 110)

        val rect = visibleRect(sticky)
        assertTrue(dispatchClick(root, MouseClickEvent(rect.x + 4, rect.y + 4, MouseButton.LEFT)))
        assertEquals(1, clicks)

        val inspector = InspectorController().also { it.toggle() }
        inspector.onLayoutCommitted(root, 302L)
        inspector.onNativeDomExpandedPanelRect(Rect(280, 20, 320, 220), 800, 600)
        inspector.onCursorMoved(rect.x + 5, rect.y + 5)
        inspector.buildDomSnapshot(800, 600)

        assertEquals(sticky.key?.toString(), inspector.hoveredKey)
        val highlight = inspector.debugHoveredHighlight()
        assertNotNull(highlight)
        assertEquals(rect, highlight.borderRect)
    }

    @Test
    fun `inspector picks and highlights sticky at final used geometry`() {
        val root = ContainerNode(key = "sticky-inspector-root").apply {
            overflowY = Overflow.Scroll
        }
        val sticky = ButtonNode("sticky", key = "sticky-inspector-node").apply {
            width = 100
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "sticky",
                StyleProperty.TOP to "0px"
            )
        }
        val filler = ContainerNode(key = "sticky-inspector-filler").apply {
            width = 40
            height = 240
        }
        sticky.applyParent(root)
        filler.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 200, 100)
        root.setScrollOffsets(0, 60)
        tree.render(ctx, 200, 100)

        val inspector = InspectorController().also { it.toggle() }
        inspector.onLayoutCommitted(root, 301L)
        inspector.onNativeDomExpandedPanelRect(Rect(260, 20, 320, 220), 800, 600)

        val geometry = UsedInteractionGeometryResolver.resolveNodeGeometry(sticky)
        val visibleRect = geometry.visibleBorderRect ?: geometry.usedBorderRect
        inspector.onCursorMoved(visibleRect.x + 70, visibleRect.y + 3)
        inspector.buildDomSnapshot(800, 600)

        assertEquals(sticky.key?.toString(), inspector.hoveredKey)
        val highlight = inspector.debugHoveredHighlight()
        assertNotNull(highlight)
        assertEquals(visibleRect, highlight.borderRect)
    }

    @Test
    fun `sticky horizontal movement is clamped by direct-parent containing block`() {
        val root = ContainerNode(key = "sticky-clamp-x-root").apply {
            display = org.dreamfinity.dsgl.core.style.Display.Flex
            overflowX = Overflow.Scroll
        }
        val leftSpacer = ContainerNode(key = "sticky-clamp-x-left-spacer").apply {
            width = 200
            height = 80
        }
        val section = ContainerNode(key = "sticky-clamp-x-section").apply {
            width = 60
            height = 80
        }
        val sticky = ContainerNode(key = "sticky-clamp-x-node").apply {
            width = 20
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "sticky",
                StyleProperty.LEFT to "0px"
            )
        }
        val sectionFiller = ContainerNode(key = "sticky-clamp-x-section-filler").apply {
            width = 120
            height = 40
        }
        val rightSpacer = ContainerNode(key = "sticky-clamp-x-right-spacer").apply {
            width = 220
            height = 80
        }
        leftSpacer.applyParent(root)
        section.applyParent(root)
        rightSpacer.applyParent(root)
        sticky.applyParent(section)
        sectionFiller.applyParent(section)

        val tree = DomTree(root)
        tree.render(ctx, 100, 100)
        root.setScrollOffsets(260, 0)
        tree.render(ctx, 100, 100)

        val expectedClampX = section.bounds.x + section.bounds.width - sticky.bounds.width
        assertEquals(expectedClampX, usedRect(sticky).x)
    }

    @Test
    fun `sticky movement is clamped by direct-parent containing block`() {
        val root = ContainerNode(key = "sticky-clamp-root").apply {
            overflowY = Overflow.Scroll
        }
        val topSpacer = ContainerNode(key = "sticky-clamp-top-spacer").apply {
            width = 120
            height = 60
        }
        val section = ContainerNode(key = "sticky-clamp-section").apply {
            width = 120
            height = 120
        }
        val sticky = ContainerNode(key = "sticky-clamp-node").apply {
            width = 120
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "sticky",
                StyleProperty.TOP to "0px"
            )
        }
        val sectionFiller = ContainerNode(key = "sticky-clamp-section-filler").apply {
            width = 120
            height = 220
        }
        val bottomSpacer = ContainerNode(key = "sticky-clamp-bottom-spacer").apply {
            width = 120
            height = 260
        }
        topSpacer.applyParent(root)
        section.applyParent(root)
        sticky.applyParent(section)
        sectionFiller.applyParent(section)
        bottomSpacer.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 220, 100)
        root.setScrollOffsets(0, 200)
        tree.render(ctx, 220, 100)

        val expectedClampY = section.bounds.y + section.bounds.height - sticky.bounds.height
        assertEquals(expectedClampY, usedRect(sticky).y)
    }

    @Test
    fun `non-sticky positioned modes remain unchanged with sticky enabled`() {
        val root = ContainerNode(key = "sticky-regression-root").apply {
            overflowY = Overflow.Scroll
        }
        val staticNode = ContainerNode(key = "sticky-regression-static").apply {
            width = 30
            height = 12
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "static",
                StyleProperty.LEFT to "50px",
                StyleProperty.TOP to "20px"
            )
        }
        val relativeNode = ContainerNode(key = "sticky-regression-relative").apply {
            width = 30
            height = 12
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "relative",
                StyleProperty.LEFT to "18px",
                StyleProperty.TOP to "7px"
            )
        }
        val absoluteNode = ContainerNode(key = "sticky-regression-absolute").apply {
            width = 20
            height = 10
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "absolute",
                StyleProperty.LEFT to "40px",
                StyleProperty.TOP to "22px"
            )
        }
        val fixedNode = ContainerNode(key = "sticky-regression-fixed").apply {
            width = 20
            height = 10
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "fixed",
                StyleProperty.LEFT to "15px",
                StyleProperty.TOP to "9px"
            )
        }
        val stickyNode = ContainerNode(key = "sticky-regression-sticky").apply {
            width = 80
            height = 20
            inlineStyleDeclarations = styleDeclarations(
                StyleProperty.POSITION to "sticky",
                StyleProperty.TOP to "0px"
            )
        }
        val filler = ContainerNode(key = "sticky-regression-filler").apply {
            width = 120
            height = 260
        }

        staticNode.applyParent(root)
        relativeNode.applyParent(root)
        absoluteNode.applyParent(root)
        fixedNode.applyParent(root)
        stickyNode.applyParent(root)
        filler.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 220, 100)

        assertTrue(staticNode.containsGlobalPoint(staticNode.bounds.x + 2, staticNode.bounds.y + 2))
        assertFalse(staticNode.containsGlobalPoint(staticNode.bounds.x + 52, staticNode.bounds.y + 22))
        assertFalse(relativeNode.containsGlobalPoint(relativeNode.bounds.x + 2, relativeNode.bounds.y + 2))
        assertTrue(relativeNode.containsGlobalPoint(relativeNode.bounds.x + 20, relativeNode.bounds.y + 9))
        assertTrue(absoluteNode.containsGlobalPoint(absoluteNode.bounds.x + 2, absoluteNode.bounds.y + 2))
        assertFalse(absoluteNode.containsGlobalPoint(2, 2))

        val fixedBeforeScroll = fixedNode.bounds

        root.setScrollOffsets(0, 60)
        tree.render(ctx, 220, 100)

        assertEquals(fixedBeforeScroll, fixedNode.bounds)
    }

    private fun usedRect(node: ContainerNode): Rect {
        return UsedInteractionGeometryResolver.resolveNodeGeometry(node).usedBorderRect
    }

    private fun visibleRect(node: ContainerNode): Rect {
        val geometry = UsedInteractionGeometryResolver.resolveNodeGeometry(node)
        return geometry.visibleBorderRect ?: geometry.usedBorderRect
    }

    private fun visibleRect(node: ButtonNode): Rect {
        val geometry = UsedInteractionGeometryResolver.resolveNodeGeometry(node)
        return geometry.visibleBorderRect ?: geometry.usedBorderRect
    }

    private fun styleDeclarations(vararg entries: Pair<StyleProperty, String>): StyleDeclarations {
        return StyleDeclarations().apply {
            entries.forEach { (property, literal) ->
                set(property, StyleExpression.Literal(literal))
            }
        }
    }
}
