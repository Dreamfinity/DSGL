package org.dreamfinity.dsgl.core.components.modal

import org.dreamfinity.dsgl.core.components.modal.internal.ModalRuntime
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.event.FocusManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ModalRuntimeTests {
    @AfterTest
    fun cleanup() {
        FocusManager.clearFocus()
    }

    @Test
    fun restoresPreviousFocusWhenTopModalCloses() {
        val hostKey = "tests.modal.host.restore"
        val contentRoot = buildRoot(hostKey, includeM1 = false, includeM2 = false)
        FocusManager.requestFocus(requireNodeByKey(contentRoot, "content.input"))
        FocusManager.retainFocus(contentRoot)

        val modal1 = ModalSpec(key = "m1") { _ -> }
        ModalRuntime.onBuild(hostKey, listOf(modal1))
        val withModal = buildRoot(hostKey, includeM1 = true, includeM2 = false)
        FocusManager.retainFocus(withModal)
        ModalRuntime.onCommit(hostKey, listOf(modal1))
        assertEquals("m1.input", FocusManager.focusedNode()?.key)

        ModalRuntime.onBuild(hostKey, emptyList())
        val withoutModal = buildRoot(hostKey, includeM1 = false, includeM2 = false)
        FocusManager.retainFocus(withoutModal)
        ModalRuntime.onCommit(hostKey, emptyList())
        assertEquals("content.input", FocusManager.focusedNode()?.key)
    }

    @Test
    fun focusesNewestTopmostAndRestoresUnderlyingModalFocusOnPop() {
        val hostKey = "tests.modal.host.stack"
        val modal1 = ModalSpec(key = "m1") { _ -> }
        val modal2 = ModalSpec(key = "m2") { _ -> }

        val withM1 = buildRoot(hostKey, includeM1 = true, includeM2 = false)
        FocusManager.retainFocus(withM1)
        ModalRuntime.onBuild(hostKey, listOf(modal1))
        ModalRuntime.onCommit(hostKey, listOf(modal1))
        assertEquals("m1.input", FocusManager.focusedNode()?.key)

        ModalRuntime.onBuild(hostKey, listOf(modal1, modal2))
        val withM1M2 = buildRoot(hostKey, includeM1 = true, includeM2 = true)
        FocusManager.retainFocus(withM1M2)
        ModalRuntime.onCommit(hostKey, listOf(modal1, modal2))
        assertEquals("m2.input", FocusManager.focusedNode()?.key)

        ModalRuntime.onBuild(hostKey, listOf(modal1))
        val backToM1 = buildRoot(hostKey, includeM1 = true, includeM2 = false)
        FocusManager.retainFocus(backToM1)
        ModalRuntime.onCommit(hostKey, listOf(modal1))
        assertEquals("m1.input", FocusManager.focusedNode()?.key)
    }

    private fun buildRoot(hostKey: String, includeM1: Boolean, includeM2: Boolean): DOMNode {
        val root = ContainerNode(key = "root")
        ContainerNode(key = "content").applyParent(root).apply {
            FocusableNode(key = "content.input").applyParent(this)
        }
        if (includeM1) {
            ContainerNode(key = ModalRuntime.dialogKey(hostKey, "m1")).applyParent(root).apply {
                FocusableNode(key = "m1.input").applyParent(this)
            }
        }
        if (includeM2) {
            ContainerNode(key = ModalRuntime.dialogKey(hostKey, "m2")).applyParent(root).apply {
                FocusableNode(key = "m2.input").applyParent(this)
            }
        }
        return root
    }

    private fun requireNodeByKey(root: DOMNode, key: Any): DOMNode {
        if (root.key == key) return root
        root.children.forEach { child ->
            val found = runCatching { requireNodeByKey(child, key) }.getOrNull()
            if (found != null) {
                return found
            }
        }
        error("Missing node key=$key")
    }

    private class FocusableNode(key: Any) : DOMNode(key) {
        override val focusable: Boolean = true
    }
}
