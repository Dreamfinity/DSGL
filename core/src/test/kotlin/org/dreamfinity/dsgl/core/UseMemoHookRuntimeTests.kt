package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.hooks.HookHotReloadRemountException
import org.dreamfinity.dsgl.core.hooks.HookRenderSessionMode
import org.dreamfinity.dsgl.core.hooks.HookUsageException
import org.dreamfinity.dsgl.core.hooks.useCallback
import org.dreamfinity.dsgl.core.hooks.useMemo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class UseMemoHookRuntimeTests {
    @Test
    fun `useMemo without deps computes once per mounted hook instance`() {
        val window = NoDepsMemoWindow()

        renderWithHookSession(window)
        assertEquals(1, window.computeCalls)
        assertEquals(1, window.lastSeen)

        renderWithHookSession(window)
        assertEquals(1, window.computeCalls)
        assertEquals(1, window.lastSeen)
    }

    @Test
    fun `useMemo recomputes when ordered deps change`() {
        val window = DepMemoWindow()

        window.deps = listOf("items", 1)
        renderWithHookSession(window)
        assertEquals(1, window.computeCalls)
        assertEquals("memo(items,1)#1", window.lastSeen)

        window.deps = listOf("items", 1)
        renderWithHookSession(window)
        assertEquals(1, window.computeCalls)
        assertEquals("memo(items,1)#1", window.lastSeen)

        window.deps = listOf("items", 2)
        renderWithHookSession(window)
        assertEquals(2, window.computeCalls)
        assertEquals("memo(items,2)#2", window.lastSeen)
    }

    @Test
    fun `useMemo disappears when not visited and reappears fresh`() {
        val window = ConditionalMemoWindow()

        renderWithHookSession(window)
        assertEquals(1, window.computeCalls)
        assertEquals("memo#1", window.lastSeen)

        window.showMemo = false
        renderWithHookSession(window)
        assertEquals(null, window.lastSeen)

        window.showMemo = true
        renderWithHookSession(window)
        assertEquals(2, window.computeCalls)
        assertEquals("memo#2", window.lastSeen)
    }

    @Test
    fun `useMemo type mismatch fails loudly in normal runtime`() {
        val window = ConditionalMemoTypeWindow()

        window.useStringBranch = false
        renderWithHookSession(window)

        window.useStringBranch = true
        val error = assertFailsWith<HookUsageException> {
            renderWithHookSession(window)
        }

        assertTrue(error.message?.contains("Hook signature mismatch") == true)
        assertTrue(error.message?.contains("memoValue") == true)
    }

    @Test
    fun `useMemo hot reload mismatch remounts and resets memo state`() {
        val window = ConditionalMemoTypeWindow()

        window.useStringBranch = false
        renderWithHookSession(window)
        assertEquals(42, window.lastSeen)

        window.useStringBranch = true
        val attempts = renderWithHotReloadRecovery(window)
        assertEquals(2, attempts)
        assertEquals("fresh", window.lastSeen)
    }

    @Test
    fun `useMemo remount for new window instance initializes fresh`() {
        GlobalMemoCounter.calls = 0
        val first = GlobalMemoWindow()
        val second = GlobalMemoWindow()

        renderWithHookSession(first)
        renderWithHookSession(first)
        assertEquals(1, GlobalMemoCounter.calls)
        assertEquals(1, first.lastSeen)

        renderWithHookSession(second)
        assertEquals(2, GlobalMemoCounter.calls)
        assertEquals(2, second.lastSeen)
    }

    @Test
    fun `useCallback keeps stable function identity until deps change`() {
        val window = CallbackWindow()

        window.dep = 5
        renderWithHookSession(window)
        val first = window.lastCallback
        assertNotNull(first)
        assertEquals(5, first())

        window.dep = 5
        renderWithHookSession(window)
        val second = window.lastCallback
        assertNotNull(second)
        assertSame(first, second)
        assertEquals(5, second())
    }

    @Test
    fun `useCallback creates new function identity when deps change`() {
        val window = CallbackWindow()

        window.dep = 1
        renderWithHookSession(window)
        val first = window.lastCallback
        assertNotNull(first)
        assertEquals(1, first())

        window.dep = 2
        renderWithHookSession(window)
        val second = window.lastCallback
        assertNotNull(second)
        assertNotSame(first, second)
        assertEquals(2, second())
    }

    @Test
    fun `direct assignment useMemo fails loudly at render end`() {
        val window = DirectUseMemoWindow()

        window.beginRenderBuild()
        window.render()
        val error = assertFailsWith<HookUsageException> {
            window.endRenderBuild()
        }

        assertTrue(error.message?.contains("Storage-backed hook 'useMemo'") == true)
        assertTrue(error.message?.contains("delegated property syntax") == true)
    }

    @Test
    fun `direct assignment useCallback fails loudly at render end`() {
        val window = DirectUseCallbackWindow()

        window.beginRenderBuild()
        window.render()
        val error = assertFailsWith<HookUsageException> {
            window.endRenderBuild()
        }

        assertTrue(error.message?.contains("Storage-backed hook 'useCallback'") == true)
        assertTrue(error.message?.contains("delegated property syntax") == true)
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
        fail("Expected hot-reload remount recovery to succeed. last=${lastWarning?.message}")
    }

    private class NoDepsMemoWindow : DsglWindow() {
        var computeCalls: Int = 0
        var lastSeen: Int? = null

        override fun render(): DomTree {
            val memo by useMemo {
                computeCalls += 1
                computeCalls
            }
            lastSeen = memo
            return DomTree(ContainerNode(key = "memo.no.deps.root"))
        }
    }

    private class DepMemoWindow : DsglWindow() {
        var deps: List<Any?> = emptyList()
        var computeCalls: Int = 0
        var lastSeen: String? = null

        override fun render(): DomTree {
            val first = deps.getOrNull(0)
            val second = deps.getOrNull(1)
            val memo by useMemo(first, second) {
                computeCalls += 1
                "memo($first,$second)#$computeCalls"
            }
            lastSeen = memo
            return DomTree(ContainerNode(key = "memo.deps.root"))
        }
    }

    private class ConditionalMemoWindow : DsglWindow() {
        var showMemo: Boolean = true
        var computeCalls: Int = 0
        var lastSeen: String? = null

        override fun render(): DomTree {
            if (showMemo) {
                val memo by useMemo {
                    computeCalls += 1
                    "memo#$computeCalls"
                }
                lastSeen = memo
            } else {
                lastSeen = null
            }
            return DomTree(ContainerNode(key = "memo.conditional.root"))
        }
    }

    private class ConditionalMemoTypeWindow : DsglWindow() {
        var useStringBranch: Boolean = false
        var lastSeen: Any? = null

        override fun render(): DomTree {
            if (useStringBranch) {
                val memoValue by useMemo { "fresh" }
                lastSeen = memoValue
            } else {
                val memoValue by useMemo { 42 }
                lastSeen = memoValue
            }
            return DomTree(ContainerNode(key = "memo.type.root"))
        }
    }

    private class CallbackWindow : DsglWindow() {
        var dep: Int = 0
        var lastCallback: (() -> Int)? = null

        override fun render(): DomTree {
            val callback by useCallback(dep) {
                val captured = dep
                { captured }
            }
            lastCallback = callback
            return DomTree(ContainerNode(key = "callback.root"))
        }
    }

    private class DirectUseMemoWindow : DsglWindow() {
        override fun render(): DomTree {
            val unused = useMemo { 1 }
            if (unused.hashCode() == Int.MIN_VALUE) {
                error("unreachable")
            }
            return DomTree(ContainerNode(key = "memo.invalid.root"))
        }
    }

    private class DirectUseCallbackWindow : DsglWindow() {
        override fun render(): DomTree {
            val unused = useCallback { { 1 } }
            if (unused.hashCode() == Int.MIN_VALUE) {
                error("unreachable")
            }
            return DomTree(ContainerNode(key = "callback.invalid.root"))
        }
    }

    private class GlobalMemoWindow : DsglWindow() {
        var lastSeen: Int? = null

        override fun render(): DomTree {
            val memo by useMemo {
                GlobalMemoCounter.calls += 1
                GlobalMemoCounter.calls
            }
            lastSeen = memo
            return DomTree(ContainerNode(key = "memo.global.root"))
        }
    }

    private object GlobalMemoCounter {
        var calls: Int = 0
    }
}
