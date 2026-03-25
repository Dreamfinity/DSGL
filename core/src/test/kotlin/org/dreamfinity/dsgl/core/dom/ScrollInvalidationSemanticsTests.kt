package org.dreamfinity.dsgl.core.dom

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.style.Overflow

class ScrollInvalidationSemanticsTests {
    @Test
    fun `scroll offset updates mark layout visual and interaction dirty`() {
        val viewport = createScrollableViewport()

        viewport.setScrollOffsets(0, 48)
        val invalidation = viewport.consumeScrollInvalidationRecursively()

        assertTrue(invalidation.layoutDirty)
        assertTrue(invalidation.visualDirty)
        assertTrue(invalidation.interactionDirty)
        assertTrue(invalidation.anyDirty)

        val afterConsume = viewport.consumeScrollInvalidationRecursively()
        assertFalse(afterConsume.anyDirty)
    }

    @Test
    fun `restore snapshot with visual-only delta marks visual interaction without layout`() {
        val viewport = createScrollableViewport()
        val baseline = viewport.captureScrollSessionSnapshot()
        val visualOnly = baseline.copy(
            targetY = baseline.targetY + 36,
            displayedY = baseline.displayedY + 36.0,
            resolvedY = baseline.resolvedY
        )

        viewport.restoreScrollSessionSnapshot(visualOnly)
        val invalidation = viewport.consumeScrollInvalidationRecursively()

        assertFalse(invalidation.layoutDirty)
        assertTrue(invalidation.visualDirty)
        assertTrue(invalidation.interactionDirty)
        assertTrue(invalidation.anyDirty)
    }

    @Test
    fun `root consumption aggregates child scroll invalidation recursively`() {
        val root = ContainerNode(key = "root").apply {
            bounds = Rect(0, 0, 400, 280)
        }
        val viewport = createScrollableViewport().applyParent(root)

        viewport.setScrollOffsets(0, 32)
        val rootInvalidation = root.consumeScrollInvalidationRecursively()

        assertTrue(rootInvalidation.layoutDirty)
        assertTrue(rootInvalidation.visualDirty)
        assertTrue(rootInvalidation.interactionDirty)
        assertFalse(viewport.consumeScrollInvalidationRecursively().anyDirty)
    }

    @Test
    fun `legacy layout-dirty consumer remains compatible`() {
        val viewport = createScrollableViewport()

        viewport.setScrollOffsets(0, 40)
        assertTrue(viewport.consumeScrollLayoutDirtyRecursively())

        val afterLegacyConsume = viewport.consumeScrollInvalidationRecursively()
        assertFalse(afterLegacyConsume.anyDirty)
    }

    private fun createScrollableViewport(): ContainerNode {
        val viewport = ContainerNode(key = "viewport").apply {
            bounds = Rect(0, 0, 180, 90)
            overflowX = Overflow.Hidden
            overflowY = Overflow.Auto
        }
        ContainerNode(key = "content").apply {
            bounds = Rect(0, 0, 180, 420)
        }.applyParent(viewport)
        return viewport
    }
}
