package org.dreamfinity.dsgl.core.components.modal

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.hooks.HookRenderSessionMode
import org.dreamfinity.dsgl.core.hooks.useState
import kotlin.test.Test
import kotlin.test.assertEquals

class ModalPortalHookOwnerPropagationTests {
    @Test
    fun `hooks inside modalPortal content keep owner-bound UiScope`() {
        val window = ModalPortalStateWindow()

        renderWithHookSession(window)
        assertEquals(0, window.lastSeenCount)

        window.pendingMutation = 6
        renderWithHookSession(window)
        assertEquals(6, window.lastSeenCount)

        renderWithHookSession(window)
        assertEquals(6, window.lastSeenCount)
    }

    private fun renderWithHookSession(window: DsglWindow, mode: HookRenderSessionMode = HookRenderSessionMode.Normal): DomTree {
        window.beginRenderBuild(mode)
        return try {
            window.render()
        } finally {
            window.endRenderBuild()
            window.commitRenderBuild()
        }
    }

    private class ModalPortalStateWindow : DsglWindow() {
        var pendingMutation: Int? = null
        var lastSeenCount: Int = -1

        override fun render(): DomTree =
            ui {
                modalPortal(
                    modals = emptyList(),
                    key = "test.modal.portal",
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
