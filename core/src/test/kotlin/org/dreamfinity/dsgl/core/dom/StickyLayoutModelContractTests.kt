package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.style.CssLength
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StickyLayoutModelContractTests {
    @Test
    fun `sticky scroll-container rule chooses nearest vertical scroll-container ancestor`() {
        val root = ContainerNode(key = "sticky-root")
        val farScroll = ContainerNode(key = "sticky-scroll-far").apply {
            overflowY = Overflow.Auto
        }.applyParent(root)
        val nearScroll = ContainerNode(key = "sticky-scroll-near").apply {
            overflowY = Overflow.Hidden
        }.applyParent(farScroll)
        val parent = ContainerNode(key = "sticky-parent").applyParent(nearScroll)
        val node = ContainerNode(key = "sticky-node").applyParent(parent)

        assertSame(nearScroll, node.stickyReferenceScrollContainerVertical())
    }

    @Test
    fun `sticky scroll-container rule falls back to root when no vertical scroll-container ancestor exists`() {
        val root = ContainerNode(key = "sticky-root-fallback")
        val a = ContainerNode(key = "sticky-a").applyParent(root)
        val b = ContainerNode(key = "sticky-b").applyParent(a)
        val node = ContainerNode(key = "sticky-node").applyParent(b)

        assertSame(root, node.stickyReferenceScrollContainerVertical())
    }

    @Test
    fun `sticky containing-block rule is parent slot owner and is independent from sticky scroll-container rule`() {
        val root = ContainerNode(key = "sticky-container-root")
        val scrollAncestor = ContainerNode(key = "sticky-scroll-ancestor").apply {
            overflowY = Overflow.Scroll
        }.applyParent(root)
        val flowParent = ContainerNode(key = "sticky-flow-parent").applyParent(scrollAncestor)
        val node = ContainerNode(key = "sticky-child").applyParent(flowParent)

        assertSame(scrollAncestor, node.stickyReferenceScrollContainerVertical())
        assertSame(flowParent, node.stickyContainingBlockForPositioning())
    }

    @Test
    fun `sticky vertical inset rule is explicit and deterministic`() {
        val top = CssLength.px(14)
        val bottom = CssLength.px(9)

        val topOnly = StickyLayoutModel.resolveVerticalInsets(top = top, bottom = null)
        assertEquals(StickyLayoutModel.StickyInsetAxisMode.Top, topOnly.mode)
        assertEquals(StyleProperty.TOP, topOnly.sourceProperty)
        assertEquals(top, topOnly.value)
        assertTrue(topOnly.active)

        val bottomOnly = StickyLayoutModel.resolveVerticalInsets(top = null, bottom = bottom)
        assertEquals(StickyLayoutModel.StickyInsetAxisMode.Bottom, bottomOnly.mode)
        assertEquals(StyleProperty.BOTTOM, bottomOnly.sourceProperty)
        assertEquals(bottom, bottomOnly.value)
        assertTrue(bottomOnly.active)

        val both = StickyLayoutModel.resolveVerticalInsets(top = top, bottom = bottom)
        assertEquals(StickyLayoutModel.StickyInsetAxisMode.Top, both.mode)
        assertEquals(StyleProperty.TOP, both.sourceProperty)
        assertEquals(top, both.value)
        assertTrue(both.active)

        val none = StickyLayoutModel.resolveVerticalInsets(top = null, bottom = null)
        assertEquals(StickyLayoutModel.StickyInsetAxisMode.Inactive, none.mode)
        assertEquals(null, none.sourceProperty)
        assertEquals(null, none.value)
        assertEquals(false, none.active)
    }

    @Test
    fun `sticky integration point is explicit for future used-geometry activation`() {
        val root = ContainerNode(key = "sticky-integration-root")
        val node = ContainerNode(key = "sticky-integration-node").applyParent(root)

        assertEquals(
            StickyLayoutModel.PositionedGeometryIntegrationPoint.ContainerRenderContainedChild,
            node.stickyPositionedGeometryIntegrationPoint()
        )
    }
}

