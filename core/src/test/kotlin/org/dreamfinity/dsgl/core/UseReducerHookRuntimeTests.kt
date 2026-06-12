package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.hooks.HookHotReloadRemountException
import org.dreamfinity.dsgl.core.hooks.HookRenderSessionMode
import org.dreamfinity.dsgl.core.hooks.HookUsageException
import org.dreamfinity.dsgl.core.hooks.useReducer
import org.dreamfinity.dsgl.core.host.DsglWindowHost
import org.dreamfinity.dsgl.core.host.Viewport
import kotlin.test.*

class UseReducerHookRuntimeTests {
    @Test
    fun `useReducer state persists and repeated dispatches are accumulated`() {
        val window = ReducerProbeWindow()

        renderWithHookSession(window)
        assertEquals(0, window.lastSeen)

        window.lastDispatch?.invoke(ReducerProbeWindow.CounterAction.Add(2))
        window.lastDispatch?.invoke(ReducerProbeWindow.CounterAction.Add(3))
        window.lastDispatch?.invoke(ReducerProbeWindow.CounterAction.Add(-1))

        renderWithHookSession(window)
        assertEquals(4, window.lastSeen)

        renderWithHookSession(window)
        assertEquals(4, window.lastSeen)
    }

    @Test
    fun `dispatch uses same invalidation pipeline as state mutation`() {
        val window = ReducerProbeWindow()
        val host = RecordingHost(window)
        window.attachHost(host)

        renderWithHookSession(window)
        assertEquals(0, host.rebuildRequests)

        window.lastDispatch?.invoke(ReducerProbeWindow.CounterAction.Noop)
        assertEquals(0, host.rebuildRequests)

        window.lastDispatch?.invoke(ReducerProbeWindow.CounterAction.Increment)
        assertEquals(1, host.rebuildRequests)

        window.lastDispatch?.invoke(ReducerProbeWindow.CounterAction.Noop)
        assertEquals(1, host.rebuildRequests)

        window.lastDispatch?.invoke(ReducerProbeWindow.CounterAction.Add(4))
        assertEquals(2, host.rebuildRequests)
    }

    @Test
    fun `useReducer disappears when not visited and reappears fresh`() {
        val window = ReducerProbeWindow()

        renderWithHookSession(window)
        window.lastDispatch?.invoke(ReducerProbeWindow.CounterAction.Add(9))
        renderWithHookSession(window)
        assertEquals(9, window.lastSeen)

        window.showReducer = false
        renderWithHookSession(window)
        assertEquals(null, window.lastSeen)

        window.showReducer = true
        renderWithHookSession(window)
        assertEquals(0, window.lastSeen)
    }

    @Test
    fun `same-path incompatible reducer signature fails loudly in normal runtime`() {
        val window = ConditionalReducerTypeWindow()

        window.useStringBranch = false
        renderWithHookSession(window)

        window.useStringBranch = true
        val error =
            assertFailsWith<HookUsageException> {
                renderWithHookSession(window)
            }

        assertTrue(error.message?.contains("Hook signature mismatch") == true)
        assertTrue(error.message?.contains("useReducer#0") == true)
    }

    @Test
    fun `hot reload mismatch remounts incompatible reducer signature and resets state`() {
        val window = ConditionalReducerTypeWindow()

        window.useStringBranch = false
        renderWithHookSession(window)
        window.lastIntDispatch?.invoke(12)
        renderWithHookSession(window)
        assertEquals(12, window.lastSeen)

        window.useStringBranch = true
        val attempts = renderWithHotReloadRecovery(window)
        assertEquals(2, attempts)
        assertEquals("fresh", window.lastSeen)
    }

    @Test
    fun `useReducer remount for new window instance initializes fresh state`() {
        val first = ReducerProbeWindow()
        val second = ReducerProbeWindow()

        renderWithHookSession(first)
        first.lastDispatch?.invoke(ReducerProbeWindow.CounterAction.Add(5))
        renderWithHookSession(first)
        assertEquals(5, first.lastSeen)

        renderWithHookSession(second)
        assertEquals(0, second.lastSeen)
    }

    @Test
    fun `useReducer outside active render fails loudly`() {
        val window = ReducerProbeWindow()

        val error =
            assertFailsWith<HookUsageException> {
                window.useReducer(0) { old: Int, action: Int ->
                    old + action
                }
            }

        assertTrue(error.message?.contains("outside active component render") == true)
    }

    private fun renderWithHookSession(window: DsglWindow, mode: HookRenderSessionMode = HookRenderSessionMode.Normal): DomTree {
        window.beginRenderBuild(mode)
        return try {
            window.render()
        } finally {
            window.endRenderBuild()
        }
    }

    private fun renderWithHotReloadRecovery(window: DsglWindow, maxAttempts: Int = 8): Int {
        var attempt = 0
        var lastWarning: HookHotReloadRemountException? = null
        while (attempt < maxAttempts) {
            attempt += 1
            try {
                renderWithHookSession(window, HookRenderSessionMode.HotReload)
                return attempt
            } catch (warning: HookHotReloadRemountException) {
                lastWarning = warning
                assertTrue(warning.message?.contains("Hot-reload remount/reset") == true)
            }
        }
        fail("Expected hot-reload recovery to succeed. last=${lastWarning?.message}")
    }

    private class ReducerProbeWindow : DsglWindow() {
        sealed interface CounterAction {
            data object Increment : CounterAction

            data class Add(
                val delta: Int,
            ) : CounterAction

            data object Noop : CounterAction
        }

        var showReducer: Boolean = true
        var lastSeen: Int? = null
        var lastDispatch: ((CounterAction) -> Unit)? = null

        override fun render(): DomTree {
            if (showReducer) {
                val (count, dispatch) = useReducer(0, ::reduceCounter)
                lastSeen = count
                lastDispatch = dispatch
            } else {
                lastSeen = null
                lastDispatch = null
            }
            return DomTree(ContainerNode(key = "reducer.probe.root"))
        }

        private fun reduceCounter(old: Int, action: CounterAction): Int =
            when (action) {
                CounterAction.Increment -> old + 1
                is CounterAction.Add -> old + action.delta
                CounterAction.Noop -> old
            }
    }

    private class ConditionalReducerTypeWindow : DsglWindow() {
        var useStringBranch: Boolean = false
        var lastSeen: Any? = null
        var lastIntDispatch: ((Int) -> Unit)? = null

        override fun render(): DomTree {
            if (useStringBranch) {
                val (state, dispatch) =
                    useReducer("fresh") { old: String, action: String ->
                        old + action
                    }
                lastSeen = state
                lastIntDispatch = null
                if (dispatch.hashCode() == Int.MIN_VALUE) {
                    error("unreachable")
                }
            } else {
                val (state, dispatch) =
                    useReducer(0) { old: Int, action: Int ->
                        old + action
                    }
                lastSeen = state
                lastIntDispatch = dispatch
            }
            return DomTree(ContainerNode(key = "reducer.type.conditional.root"))
        }
    }

    private class RecordingHost(
        override val window: DsglWindow,
    ) : DsglWindowHost {
        var rebuildRequests: Int = 0

        override fun requestRebuild(reason: String?) {
            rebuildRequests += 1
        }

        @Suppress("EmptyFunctionBlock")
        override fun requestRedraw() {
        }

        override fun getViewport(): Viewport = Viewport(width = 0, height = 0)
    }
}
