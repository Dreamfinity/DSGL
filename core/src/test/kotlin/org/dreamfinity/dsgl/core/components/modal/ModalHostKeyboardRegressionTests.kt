package org.dreamfinity.dsgl.core.components.modal

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyboardKeyDownEvent
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.dsl.div
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.dsl.ui
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ModalHostKeyboardRegressionTests {
    private val trees: MutableList<DomTree> = ArrayList()
    private val measureContext = object : UiMeasureContext {
        override fun measureText(text: String): Int = text.length * 6
        override fun measureText(text: String, fontId: String?, fontSize: Int?): Int = text.length * 6
        override val fontHeight: Int = 9
        override fun fontHeight(fontId: String?, fontSize: Int?): Int = 9
        override fun paint(commands: List<RenderCommand>) = Unit
    }

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

    @Test
    fun `modal host fills root viewport bounds`() {
        val tree = buildTree("tests.modal.host.layout.viewport", emptyList())
        trees += tree

        tree.render(measureContext, 1920, 1080)

        val host = tree.root.children.firstOrNull()
        assertNotNull(host)
        assertEquals(0, host.bounds.x)
        assertEquals(0, host.bounds.y)
        assertEquals(1920, host.bounds.width)
        assertEquals(1080, host.bounds.height)

        val content = host.children.firstOrNull()
        assertNotNull(content)
        assertEquals(1920, content.bounds.width)
        assertEquals(1080, content.bounds.height)
    }

    private fun buildTree(hostKey: String, modals: List<ModalSpec>): DomTree {
        return ui {
            modalHost(modals = modals, modalKey = hostKey) {
                div({ key = "$hostKey.content" })
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
