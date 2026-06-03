package org.dreamfinity.dsgl.core.overlay.input

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.RangeInputNode
import org.dreamfinity.dsgl.core.dom.elements.TextInputNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.FocusManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PointerCaptureSessionTests {
    @AfterTest
    fun cleanup() {
        FocusManager.clearFocus()
    }

    @Test
    fun `restores keyed capture target after root subtree replacement`() {
        val firstRoot = ContainerNode(key = "root")
        val firstTarget = CapturingNode(key = "target").applyParent(firstRoot)
        val session = PointerCaptureSession()
        session.capture(firstTarget)

        val secondRoot = ContainerNode(key = "root")
        val secondTarget = CapturingNode(key = "target").applyParent(secondRoot)

        session.restore(secondRoot, pointerPressed = true)

        assertSame(secondTarget, session.target)
        assertFalse(firstTarget.cancelled)
    }

    @Test
    fun `keeps unkeyed capture target while pointer is still pressed`() {
        val root = ContainerNode(key = "root")
        val target = CapturingNode().applyParent(root)
        val session = PointerCaptureSession()
        session.capture(target)

        session.restore(ContainerNode(key = "other-root"), pointerPressed = true)

        assertSame(target, session.target)
        assertFalse(target.cancelled)
    }

    @Test
    fun `releases unkeyed capture target when detached after pointer is no longer pressed`() {
        val root = ContainerNode(key = "root")
        val target = CapturingNode().applyParent(root)
        val session = PointerCaptureSession()
        session.capture(target)

        session.restore(ContainerNode(key = "other-root"), pointerPressed = false)

        assertFalse(session.hasCapture)
        assertTrue(target.cancelled)
    }

    @Test
    fun `focus moving into captured subtree does not cancel capture`() {
        val root = ContainerNode(key = "root")
        val target = CapturingNode(key = "target").applyParent(root)
        val child = TextInputNode(text = "", key = "child").applyParent(target)
        val previous = TextInputNode(text = "", key = "previous").applyParent(root)
        val session = PointerCaptureSession()

        FocusManager.requestFocus(previous)
        session.capture(target)
        FocusManager.requestFocus(child)

        assertFalse(session.hasFocusChanged())
    }

    @Test
    fun `capture target resolution includes control-owned drag nodes`() {
        val root = ContainerNode(key = "root").apply { bounds = Rect(0, 0, 200, 80) }
        val range =
            RangeInputNode(value = 0L, min = 0L, max = 100L, key = "range")
                .apply {
                    bounds = Rect(20, 20, 120, 12)
                }.applyParent(root)

        val resolved = PointerCaptureSession.resolveCaptureTarget(range, 24, 24)

        assertSame(range, resolved)
    }

    private class CapturingNode(
        key: Any? = null,
    ) : DOMNode(key) {
        var cancelled: Boolean = false

        override fun cancelPointerCapture() {
            cancelled = true
        }
    }
}
