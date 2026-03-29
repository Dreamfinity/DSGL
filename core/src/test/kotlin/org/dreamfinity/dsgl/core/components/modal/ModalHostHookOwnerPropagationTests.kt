package org.dreamfinity.dsgl.core.components.modal

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.hooks.HookRenderSessionMode
import org.dreamfinity.dsgl.core.hooks.useState
import kotlin.test.Test
import kotlin.test.assertEquals

class ModalHostHookOwnerPropagationTests {
    @Test
    fun `hooks inside modalHost content keep owner-bound UiScope`() {
        val window = ModalHostStateWindow()

        renderWithHookSession(window)
        assertEquals(0, window.lastSeenCount)

        window.pendingMutation = 6
        renderWithHookSession(window)
        assertEquals(6, window.lastSeenCount)

        renderWithHookSession(window)
        assertEquals(6, window.lastSeenCount)
    }

    private fun renderWithHookSession(
        window: DsglWindow,
        mode: HookRenderSessionMode = HookRenderSessionMode.Normal
    ): DomTree {
        window.beginRenderBuild(mode)
        return try {
            window.render()
        } finally {
            window.endRenderBuild()
            window.commitRenderBuild()
        }
    }

    private class ModalHostStateWindow : DsglWindow() {
        var pendingMutation: Int? = null
        var lastSeenCount: Int = -1

        override fun render(): DomTree {
            return ui {
                modalHost(
                    modals = emptyList(),
                    modalKey = "test.modal.host"
                ) {
                    var count by useState(0)
                    pendingMutation?.let { mutation ->
                        count = mutation
                        pendingMutation = null
                    }
                    lastSeenCount = count
                }
            }
        }
    }
}
