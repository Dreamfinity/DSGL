package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.hooks.HookUsageException
import org.dreamfinity.dsgl.core.host.DsglWindowHost
import org.dreamfinity.dsgl.core.host.Viewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UseStateHookRuntimeTests {
    @Test
    fun `delegated useState persists for same component instance across rerenders`() {
        val window = StateProbeWindow()

        window.pendingMutation = StateProbeWindow.Mutation.Assign(7)
        renderWithHookSession(window)
        assertEquals(7, window.lastSeen)

        renderWithHookSession(window)
        assertEquals(7, window.lastSeen)
    }

    @Test
    fun `useState disappears when not visited and reappears fresh`() {
        val window = StateProbeWindow()

        window.pendingMutation = StateProbeWindow.Mutation.Assign(9)
        renderWithHookSession(window)
        assertEquals(9, window.lastSeen)

        window.showState = false
        renderWithHookSession(window)
        assertEquals(null, window.lastSeen)

        window.showState = true
        renderWithHookSession(window)
        assertEquals(0, window.lastSeen)
    }

    @Test
    fun `delegated assignment uses the hook invalidation pipeline`() {
        val window = StateProbeWindow()
        val host = RecordingHost(window)
        window.attachHost(host)

        window.pendingMutation = StateProbeWindow.Mutation.Assign(3)
        renderWithHookSession(window)
        assertEquals(3, window.lastSeen)
        assertEquals(1, host.rebuildRequests)

        window.pendingMutation = StateProbeWindow.Mutation.Assign(3)
        renderWithHookSession(window)
        assertEquals(3, window.lastSeen)
        assertEquals(1, host.rebuildRequests)

        window.pendingMutation = StateProbeWindow.Mutation.Assign(4)
        renderWithHookSession(window)
        assertEquals(4, window.lastSeen)
        assertEquals(2, host.rebuildRequests)
    }

    @Test
    fun `direct assignment useState fails loudly at render end`() {
        val window = DirectUseStateWindow()

        window.beginRenderBuild()
        window.render()
        val error = assertFailsWith<HookUsageException> {
            window.endRenderBuild()
        }

        assertEquals(error.message?.contains("Storage-backed hook 'useState'"), true)
        assertEquals(error.message?.contains("delegated property syntax"), true)
    }

    @Test
    fun `useState outside active render fails loudly`() {
        val window = StateProbeWindow()

        val error = assertFailsWith<HookUsageException> {
            window.useState(0)
        }

        assertEquals(error.message?.contains("outside active component render"), true)
    }

    private fun renderWithHookSession(window: DsglWindow): DomTree {
        window.beginRenderBuild()
        return try {
            window.render()
        } finally {
            window.endRenderBuild()
        }
    }

    private class StateProbeWindow : DsglWindow() {
        sealed interface Mutation {
            data object None : Mutation
            data class Assign(val value: Int) : Mutation
        }

        var showState: Boolean = true
        var pendingMutation: Mutation = Mutation.None
        var lastSeen: Int? = null

        override fun render(): DomTree {
            if (showState) {
                var count by useState(0)
                when (val mutation = pendingMutation) {
                    is Mutation.Assign -> count = mutation.value
                    Mutation.None -> Unit
                }
                pendingMutation = Mutation.None
                lastSeen = count
            } else {
                pendingMutation = Mutation.None
                lastSeen = null
            }
            return DomTree(ContainerNode(key = "state.probe.root"))
        }
    }

    private class DirectUseStateWindow : DsglWindow() {
        override fun render(): DomTree {
            val unused = useState(0)
            if (unused.hashCode() == Int.MIN_VALUE) {
                error("unreachable")
            }
            return DomTree(ContainerNode(key = "state.invalid.root"))
        }
    }

    private class RecordingHost(
        override val window: DsglWindow
    ) : DsglWindowHost {
        var rebuildRequests: Int = 0

        override fun requestRebuild(reason: String?) {
            rebuildRequests += 1
        }

        override fun requestRedraw() {
        }

        override fun getViewport(): Viewport {
            return Viewport(width = 0, height = 0)
        }
    }
}
