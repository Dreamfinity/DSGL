package org.dreamfinity.dsgl.core.ref

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.hooks.HookUsageException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

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
    fun `direct assignment useRef fails loudly at render end`() {
        val window = DirectUseRefWindow()

        window.beginRenderBuild()
        window.render()
        val error = assertFailsWith<HookUsageException> {
            window.endRenderBuild()
        }

        assertEquals(error.message?.contains("Storage-backed hook 'useRef'"), true)
        assertEquals(error.message?.contains("delegated property syntax"), true)
    }

    @Test
    fun `useRef outside active render fails loudly`() {
        val window = RefProbeWindow()

        val error = assertFailsWith<HookUsageException> {
            window.useRef<String>()
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

    private class DirectUseRefWindow : DsglWindow() {
        override fun render(): DomTree {
            val unused = useRef<String>()
            if (unused.hashCode() == Int.MIN_VALUE) {
                error("unreachable")
            }
            return DomTree(ContainerNode(key = "ref.invalid.root"))
        }
    }
}
