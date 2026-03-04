package org.dreamfinity.dsgl.core.components.modal

import org.dreamfinity.dsgl.core.ComponentProps
import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.ui
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyboardKeyDownEvent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse

class ModalHostKeyboardRegressionTests {
    private val trees: MutableList<DomTree> = ArrayList()

    @AfterTest
    fun cleanup() {
        FocusManager.clearFocus()
        EventBus.run {
            trees.forEach { tree ->
                tree.root.clearListenersDeep()
            }
        }
        trees.clear()
    }

    @Test
    fun `escape is not cancelled after static modal closes`() {
        val hostKey = "tests.modal.host.keyboard.regression"
        val current = buildTree(hostKey, emptyList())
        trees += current

        val withStatic = buildTree(hostKey, listOf(staticModal()))
        trees += withStatic
        current.reconcileWith(withStatic)

        val closed = buildTree(hostKey, emptyList())
        trees += closed
        current.reconcileWith(closed)

        FocusManager.clearFocus()
        val event = KeyboardKeyDownEvent('\u0000', KeyCodes.ESCAPE)
        EventBus.post(event)

        assertFalse(event.cancelled)
    }

    private fun buildTree(hostKey: String, modals: List<ModalSpec>): DomTree {
        return ui {
            modalHost(modals = modals, key = hostKey) {
                div(ComponentProps(key = "$hostKey.content"))
            }
        }
    }

    private fun staticModal(): ModalSpec {
        return ModalSpec(
            key = "modal.static",
            backdrop = BackdropMode.Static,
            keyboard = false
        ) { _ -> }
    }
}
