package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dsl.UiScope
import org.dreamfinity.dsgl.core.hooks.HookHotReloadRemountException
import org.dreamfinity.dsgl.core.hooks.HookRenderSessionMode
import org.dreamfinity.dsgl.core.hooks.HookUsageException
import org.dreamfinity.dsgl.core.hooks.useState
import org.dreamfinity.dsgl.core.host.DsglWindowHost
import org.dreamfinity.dsgl.core.host.Viewport
import kotlin.test.*

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
    fun `adjacent sibling custom component calls keep independent local state`() {
        val window = SiblingComponentStateWindow()
        window.order = listOf("Left panel", "Right panel")

        window.enqueueIncrement("Left panel")
        renderWithHookSession(window)
        assertEquals(
            linkedMapOf("Left panel" to 1, "Right panel" to 0),
            window.observedCountsSnapshot(),
        )

        window.enqueueIncrement("Right panel")
        renderWithHookSession(window)
        assertEquals(
            linkedMapOf("Left panel" to 1, "Right panel" to 1),
            window.observedCountsSnapshot(),
        )
    }

    @Test
    fun `repeated unkeyed sibling component calls from same invocation site preserve by ordinal`() {
        val window = SiblingComponentStateWindow()
        window.order = listOf("A", "B", "C")

        window.enqueueIncrement("B")
        renderWithHookSession(window)
        assertEquals(
            linkedMapOf("A" to 0, "B" to 1, "C" to 0),
            window.observedCountsSnapshot(),
        )

        renderWithHookSession(window)
        assertEquals(
            linkedMapOf("A" to 0, "B" to 1, "C" to 0),
            window.observedCountsSnapshot(),
        )
    }

    @Test
    fun `reordering unkeyed sibling custom components rebinds state by position`() {
        val window = SiblingComponentStateWindow()
        window.order = listOf("A", "B", "C")

        window.enqueueIncrement("B")
        renderWithHookSession(window)
        assertEquals(
            linkedMapOf("A" to 0, "B" to 1, "C" to 0),
            window.observedCountsSnapshot(),
        )

        window.order = listOf("B", "A", "C")
        renderWithHookSession(window)
        assertEquals(
            linkedMapOf("B" to 0, "A" to 1, "C" to 0),
            window.observedCountsSnapshot(),
        )
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
    fun `conditional same-name incompatible useState branch fails in normal runtime`() {
        val window = ConditionalStateTypeWindow()

        window.useStringBranch = false
        renderWithHookSession(window)

        window.useStringBranch = true
        val error =
            assertFailsWith<HookUsageException> {
                renderWithHookSession(window)
            }

        assertTrue(error.message?.contains("Hook signature mismatch") == true)
        assertTrue(error.message?.contains("counter") == true)
    }

    @Test
    fun `hot reload mismatch remounts conditional useState branch and resets state`() {
        val window = ConditionalStateTypeWindow()

        window.useStringBranch = false
        window.pendingIntMutation = 12
        renderWithHookSession(window)
        assertEquals(12, window.lastSeen)

        window.useStringBranch = true
        val attempts = renderWithHotReloadRecovery(window)
        assertEquals(2, attempts)

        assertEquals("fresh", window.lastSeen)
    }

    @Test
    fun `direct assignment useState fails loudly at render end`() {
        val window = DirectUseStateWindow()

        window.beginRenderBuild()
        window.render()
        val error =
            assertFailsWith<HookUsageException> {
                window.endRenderBuild()
            }

        assertEquals(error.message?.contains("Storage-backed hook 'useState'"), true)
        assertEquals(error.message?.contains("delegated property syntax"), true)
    }

    @Test
    fun `useState outside active render fails loudly`() {
        val window = StateProbeWindow()

        val error =
            assertFailsWith<HookUsageException> {
                window.useState(0)
            }

        assertEquals(error.message?.contains("outside active component render"), true)
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

    private class StateProbeWindow : DsglWindow() {
        sealed interface Mutation {
            data object None : Mutation

            data class Assign(
                val value: Int,
            ) : Mutation
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

    private class ConditionalStateTypeWindow : DsglWindow() {
        var useStringBranch: Boolean = false
        var pendingIntMutation: Int? = null
        var lastSeen: Any? = null

        override fun render(): DomTree {
            if (useStringBranch) {
                var counter by useState("fresh")
                lastSeen = counter
            } else {
                var counter by useState(0)
                val mutation = pendingIntMutation
                if (mutation != null) {
                    counter = mutation
                    pendingIntMutation = null
                }
                lastSeen = counter
            }
            return DomTree(ContainerNode(key = "state.conditional.root"))
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

    private class SiblingComponentStateWindow : DsglWindow() {
        var order: List<String> = listOf("Left panel", "Right panel")
        private val pendingIncrements: MutableSet<String> = linkedSetOf()
        private val observedCounts: LinkedHashMap<String, Int> = linkedMapOf()

        fun enqueueIncrement(label: String) {
            pendingIncrements += label
        }

        fun observedCountsSnapshot(): LinkedHashMap<String, Int> = LinkedHashMap(observedCounts)

        override fun render(): DomTree {
            observedCounts.clear()
            return ui {
                order.forEach { label ->
                    counterCard(label)
                }
            }
        }

        private fun UiScope.counterCard(label: String) {
            var count by useState(0)
            if (pendingIncrements.remove(label)) {
                count += 1
            }
            observedCounts[label] = count
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
