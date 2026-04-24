package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.hooks.HookHotReloadRemountException
import org.dreamfinity.dsgl.core.hooks.HookRenderSessionMode
import org.dreamfinity.dsgl.core.hooks.HookUsageException
import org.dreamfinity.dsgl.core.hooks.useEffect
import org.dreamfinity.dsgl.core.hooks.useEffectEveryCommit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class UseEffectHookRuntimeTests {
    @Test
    fun `useEffect runs only after successful commit`() {
        val window = EffectProbeWindow()

        renderWithHookSession(window, commit = false)
        assertEquals(emptyList(), window.events)

        renderWithHookSession(window, commit = true)
        assertEquals(listOf("run:1"), window.events)
    }

    @Test
    fun `useEffect cleanup runs before rerun when deps change`() {
        val window = EffectProbeWindow()

        renderWithHookSession(window, commit = true)
        window.dep = 2
        renderWithHookSession(window, commit = true)

        assertEquals(listOf("run:1", "cleanup:1", "run:2"), window.events)
    }

    @Test
    fun `useEffect cleanup runs on disappearance and reappears fresh`() {
        val window = EffectProbeWindow()

        renderWithHookSession(window, commit = true)
        window.showEffect = false
        renderWithHookSession(window, commit = true)
        window.showEffect = true
        renderWithHookSession(window, commit = true)

        assertEquals(listOf("run:1", "cleanup:1", "run:1"), window.events)
    }

    @Test
    fun `useEffectEveryCommit reruns every successful commit`() {
        val window = EveryCommitEffectWindow()

        renderWithHookSession(window, commit = true)
        renderWithHookSession(window, commit = true)

        assertEquals(listOf("run", "cleanup", "run"), window.events)
    }

    @Test
    fun `aborted render attempt does not run or cleanup effects`() {
        val window = EffectProbeWindow()

        renderWithHookSession(window, commit = true)
        window.dep = 2
        renderWithHookSession(window, commit = false)
        assertEquals(listOf("run:1"), window.events)

        renderWithHookSession(window, commit = true)
        assertEquals(listOf("run:1", "cleanup:1", "run:2"), window.events)
    }

    @Test
    fun `render failure attempt does not execute effect`() {
        val window = FailingEffectWindow()

        val error =
            assertFailsWith<IllegalStateException> {
                renderWithHookSession(window, commit = false)
            }
        assertTrue(error.message?.contains("forced render failure") == true)
        assertEquals(emptyList(), window.events)
    }

    @Test
    fun `normal runtime incompatible effect signature fails loudly`() {
        val window = ConditionalEffectModeWindow()

        window.everyCommitBranch = false
        renderWithHookSession(window, commit = true)

        window.everyCommitBranch = true
        val error =
            assertFailsWith<HookUsageException> {
                renderWithHookSession(window, commit = true)
            }

        assertTrue(error.message?.contains("Hook signature mismatch") == true)
        assertTrue(error.message?.contains("useEffect#0") == true)
    }

    @Test
    fun `hot reload incompatible effect signature remounts and cleans up old effect`() {
        val window = ConditionalEffectModeWindow()

        window.everyCommitBranch = false
        renderWithHookSession(window, commit = true)
        assertEquals(listOf("run:deps"), window.events)

        window.everyCommitBranch = true
        val attempts = renderWithHotReloadRecovery(window)
        assertEquals(2, attempts)
        assertEquals(listOf("run:deps", "cleanup:deps", "run:every"), window.events)
    }

    @Test
    fun `disposing hook runtime runs effect cleanup`() {
        val window = EffectProbeWindow()

        renderWithHookSession(window, commit = true)
        window.disposeHookRuntime()

        assertEquals(listOf("run:1", "cleanup:1"), window.events)
    }

    private fun renderWithHookSession(window: DsglWindow, mode: HookRenderSessionMode = HookRenderSessionMode.Normal, commit: Boolean): DomTree {
        window.beginRenderBuild(mode)
        var renderSucceeded = false
        return try {
            window.render().also {
                renderSucceeded = true
            }
        } finally {
            window.endRenderBuild()
            if (commit && renderSucceeded) {
                window.commitRenderBuild()
            } else {
                window.discardRenderBuild()
            }
        }
    }

    private fun renderWithHotReloadRecovery(window: DsglWindow, maxAttempts: Int = 8): Int {
        var attempt = 0
        var lastWarning: HookHotReloadRemountException? = null
        while (attempt < maxAttempts) {
            attempt += 1
            window.beginRenderBuild(HookRenderSessionMode.HotReload)
            try {
                window.render()
                window.endRenderBuild()
                window.commitRenderBuild()
                return attempt
            } catch (warning: HookHotReloadRemountException) {
                lastWarning = warning
                window.endRenderBuild()
                window.discardRenderBuild()
                assertTrue(warning.message?.contains("Hot-reload remount/reset") == true)
            }
        }
        fail("Expected hot-reload recovery to succeed. last=${lastWarning?.message}")
    }

    private class EffectProbeWindow : DsglWindow() {
        var dep: Int = 1
        var showEffect: Boolean = true
        val events: MutableList<String> = arrayListOf()

        override fun render(): DomTree {
            if (showEffect) {
                useEffect(dep) {
                    val captured = dep
                    events += "run:$captured"
                    onDispose { events += "cleanup:$captured" }
                }
            }
            return DomTree(ContainerNode(key = "effect.probe.root"))
        }
    }

    private class EveryCommitEffectWindow : DsglWindow() {
        val events: MutableList<String> = arrayListOf()

        override fun render(): DomTree {
            useEffectEveryCommit {
                events += "run"
                onDispose { events += "cleanup" }
            }
            return DomTree(ContainerNode(key = "effect.every.commit.root"))
        }
    }

    private class ConditionalEffectModeWindow : DsglWindow() {
        var everyCommitBranch: Boolean = false
        val events: MutableList<String> = arrayListOf()

        override fun render(): DomTree {
            if (everyCommitBranch) {
                useEffectEveryCommit {
                    events += "run:every"
                    onDispose { events += "cleanup:every" }
                }
            } else {
                useEffect("dep") {
                    events += "run:deps"
                    onDispose { events += "cleanup:deps" }
                }
            }
            return DomTree(ContainerNode(key = "effect.mode.root"))
        }
    }

    private class FailingEffectWindow : DsglWindow() {
        val events: MutableList<String> = arrayListOf()

        override fun render(): DomTree {
            useEffect("x") {
                events += "run"
                onDispose { events += "cleanup" }
            }
            error("forced render failure")
        }
    }
}
