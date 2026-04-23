package org.dreamfinity.dsgl.core.ref

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.hooks.HookHotReloadRemountException
import org.dreamfinity.dsgl.core.hooks.HookRenderSessionMode
import org.dreamfinity.dsgl.core.hooks.HookUsageException
import org.dreamfinity.dsgl.core.hooks.ref.Ref
import org.dreamfinity.dsgl.core.hooks.ref.useRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class UseRefHookRuntimeTests {
    @Test
    fun `delegated useRef persists for same component instance across rerenders`() {
        val window = RefProbeWindow()

        renderWithHookSession(window)
        val firstRef = window.lastRef
        assertNotNull(firstRef)
        firstRef.current = "persist"

        renderWithHookSession(window)
        val secondRef = window.lastRef
        assertNotNull(secondRef)
        assertSame(firstRef, secondRef)
        assertEquals("persist", secondRef.current)
    }

    @Test
    fun `useRef disappears when not visited and reappears fresh`() {
        val window = RefProbeWindow()

        renderWithHookSession(window)
        val firstRef = window.lastRef
        assertNotNull(firstRef)
        firstRef.current = "stale"

        window.showRef = false
        renderWithHookSession(window)
        assertEquals(null, window.lastRef)

        window.showRef = true
        renderWithHookSession(window)
        val reappearedRef = window.lastRef
        assertNotNull(reappearedRef)
        assertNotSame(firstRef, reappearedRef)
        assertEquals(null, reappearedRef.current)
    }

    @Test
    fun `conditional same-name incompatible useRef branch fails in normal runtime`() {
        val window = ConditionalRefTypeWindow()

        window.useBetaBranch = false
        renderWithHookSession(window)

        window.useBetaBranch = true
        val error =
            assertFailsWith<HookUsageException> {
                renderWithHookSession(window)
            }

        assertTrue(error.message?.contains("Hook signature mismatch") == true)
        assertTrue(error.message?.contains("inputRef") == true)
    }

    @Test
    fun `hot reload mismatch remounts conditional useRef branch and resets ref state`() {
        val window = ConditionalRefTypeWindow()

        window.useBetaBranch = false
        renderWithHookSession(window)
        window.alphaRefSnapshot?.current = Alpha("alpha")

        window.useBetaBranch = true
        val attempts = renderWithHotReloadRecovery(window)
        assertEquals(2, attempts)

        val betaRef = window.betaRefSnapshot
        assertNotNull(betaRef)
        assertEquals(null, betaRef.current)
    }

    @Test
    fun `direct assignment useRef fails loudly at render end`() {
        val window = DirectUseRefWindow()

        window.beginRenderBuild()
        window.render()
        val error =
            assertFailsWith<HookUsageException> {
                window.endRenderBuild()
            }

        assertEquals(error.message?.contains("Storage-backed hook 'useRef'"), true)
        assertEquals(error.message?.contains("delegated property syntax"), true)
    }

    @Test
    fun `useRef outside active render fails loudly`() {
        val window = RefProbeWindow()

        val error =
            assertFailsWith<HookUsageException> {
                window.useRef<String>()
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

    private class RefProbeWindow : DsglWindow() {
        var showRef: Boolean = true
        var lastRef: Ref<String>? = null

        override fun render(): DomTree {
            if (showRef) {
                val itemRef by useRef<String>()
                lastRef = itemRef
            } else {
                lastRef = null
            }
            return DomTree(ContainerNode(key = "ref.probe.root"))
        }
    }

    private class ConditionalRefTypeWindow : DsglWindow() {
        var useBetaBranch: Boolean = false
        var alphaRefSnapshot: Ref<Alpha>? = null
        var betaRefSnapshot: Ref<Beta>? = null

        override fun render(): DomTree {
            if (useBetaBranch) {
                val inputRef by useRef<Beta>()
                betaRefSnapshot = inputRef
                alphaRefSnapshot = null
            } else {
                val inputRef by useRef<Alpha>()
                alphaRefSnapshot = inputRef
                betaRefSnapshot = null
            }
            return DomTree(ContainerNode(key = "ref.conditional.root"))
        }
    }

    private class DirectUseRefWindow : DsglWindow() {
        override fun render(): DomTree {
            val unused = useRef<String>()
            if (unused.hashCode() == Int.MIN_VALUE) {
                error("unreachable")
            }
            return DomTree(ContainerNode(key = "ref.invalid.root"))
        }
    }

    private data class Alpha(
        val value: String,
    )

    private data class Beta(
        val value: String,
    )
}
