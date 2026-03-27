package org.dreamfinity.dsgl.core.hooks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ComponentHookRuntimeTests {
    @Test
    fun `root hook path is stable and reinitialized after disappearance`() {
        val runtime = ComponentHookRuntime()
        val owner = Any()

        render(runtime, owner) {
            val count = resolveNamedEntry(HookEntryKind.State, "count") { 0 }
            assertTrue(count.created)
            assertEquals("count", count.path.toString())
            count.entry.value = 7
        }

        render(runtime, owner) {
            val count = resolveNamedEntry(HookEntryKind.State, "count") { 0 }
            assertFalse(count.created)
            assertEquals(7, count.entry.value)
        }

        render(runtime, owner) {
            resolveNamedEntry(HookEntryKind.State, "other") { 1 }
        }

        render(runtime, owner) {
            val count = resolveNamedEntry(HookEntryKind.State, "count") { 0 }
            assertTrue(count.created)
            assertEquals(0, count.entry.value)
        }
    }

    @Test
    fun `component identity persists by key and resets on remount`() {
        val runtime = ComponentHookRuntime()
        val owner = Any()

        render(runtime, owner) {
            withComponentInstance("counter", key = "alpha") {
                val value = resolveNamedEntry(HookEntryKind.State, "value") { 10 }
                assertTrue(value.created)
                value.entry.value = 42
            }
        }

        render(runtime, owner) {
            withComponentInstance("counter", key = "alpha") {
                val value = resolveNamedEntry(HookEntryKind.State, "value") { 10 }
                assertFalse(value.created)
                assertEquals(42, value.entry.value)
            }
        }

        render(runtime, owner) {
            withComponentInstance("counter", key = "beta") {
                val value = resolveNamedEntry(HookEntryKind.State, "value") { 10 }
                assertTrue(value.created)
                assertEquals(10, value.entry.value)
            }
        }
    }

    @Test
    fun `component disappearance removes context and later reappearance starts fresh`() {
        val runtime = ComponentHookRuntime()
        val owner = Any()

        render(runtime, owner) {
            withComponentInstance("panel") {
                val value = resolveNamedEntry(HookEntryKind.State, "value") { 1 }
                value.entry.value = 9
            }
        }

        render(runtime, owner) {
            // panel not visited, so its context must be removed after this render
            resolveNamedEntry(HookEntryKind.State, "sentinel") { 0 }
        }

        render(runtime, owner) {
            withComponentInstance("panel") {
                val value = resolveNamedEntry(HookEntryKind.State, "value") { 1 }
                assertTrue(value.created)
                assertEquals(1, value.entry.value)
            }
        }
    }

    @Test
    fun `custom hook delegated scope contributes named nested path`() {
        val runtime = ComponentHookRuntime()
        val owner = Any()

        render(runtime, owner) {
            withCustomHookScope("counter") {
                val count = resolveNamedEntry(HookEntryKind.State, "count") { 0 }
                val step = resolveNamedEntry(HookEntryKind.State, "step") { 1 }
                assertEquals("counter.count", count.path.toString())
                assertEquals("counter.step", step.path.toString())
            }
        }
    }

    @Test
    fun `duplicate hook path in same scope fails loudly`() {
        val runtime = ComponentHookRuntime()
        val owner = Any()

        render(runtime, owner) {
            resolveNamedEntry(HookEntryKind.State, "count") { 0 }
            val error = assertFailsWith<HookUsageException> {
                resolveNamedEntry(HookEntryKind.State, "count") { 0 }
            }
            assertTrue(error.message?.contains("Duplicate hook path") == true)
        }
    }

    @Test
    fun `kind mismatch for same path fails loudly`() {
        val runtime = ComponentHookRuntime()
        val owner = Any()

        render(runtime, owner) {
            resolveNamedEntry(HookEntryKind.Ref, "shared") { null }
        }

        render(runtime, owner) {
            val error = assertFailsWith<HookUsageException> {
                resolveNamedEntry(HookEntryKind.State, "shared") { 0 }
            }
            assertTrue(error.message?.contains("Hook kind mismatch") == true)
        }
    }

    @Test
    fun `synthetic unnamed keys are stable within scope and collisions are guarded`() {
        val runtime = ComponentHookRuntime()
        val owner = Any()
        lateinit var firstA: String
        lateinit var secondA: String
        lateinit var firstB: String
        lateinit var secondB: String

        render(runtime, owner) {
            withCustomHookScope("counter") {
                firstA = resolveUnnamedEntry(HookEntryKind.Custom, "useEffect") { Unit }.path.toString()
                secondA = resolveUnnamedEntry(HookEntryKind.Custom, "useEffect") { Unit }.path.toString()
            }
        }

        render(runtime, owner) {
            withCustomHookScope("counter") {
                firstB = resolveUnnamedEntry(HookEntryKind.Custom, "useEffect") { Unit }.path.toString()
                secondB = resolveUnnamedEntry(HookEntryKind.Custom, "useEffect") { Unit }.path.toString()
            }
        }

        assertEquals("counter.useEffect#0", firstA)
        assertEquals("counter.useEffect#1", secondA)
        assertEquals(firstA, firstB)
        assertEquals(secondA, secondB)

        render(runtime, owner) {
            resolveNamedEntry(HookEntryKind.State, "useEffect#0") { 0 }
            val error = assertFailsWith<HookUsageException> {
                resolveUnnamedEntry(HookEntryKind.Custom, "useEffect") { Unit }
            }
            assertTrue(error.message?.contains("Synthetic hook key collision") == true)
        }
    }

    @Test
    fun `invalid custom hook scope nesting fails loudly`() {
        val runtime = ComponentHookRuntime()
        val owner = Any()

        val outsideRender = assertFailsWith<HookUsageException> {
            runtime.enterCustomHookScope("counter")
        }
        assertTrue(outsideRender.message?.contains("outside active component render") == true)

        render(runtime, owner) {
            val noScope = assertFailsWith<HookUsageException> {
                leaveCustomHookScope()
            }
            assertTrue(noScope.message?.contains("no active custom hook scope") == true)
        }
    }

    @Test
    fun `typed resolver reuses existing value across rerenders`() {
        val runtime = ComponentHookRuntime()
        val owner = Any()

        render(runtime, owner) {
            val value = resolveNamedTypedEntry(
                kind = HookEntryKind.State,
                delegateName = "typed"
            ) {
                CounterHolder(0)
            }
            assertTrue(value.created)
            value.value.count = 7
        }

        render(runtime, owner) {
            val value = resolveNamedTypedEntry(
                kind = HookEntryKind.State,
                delegateName = "typed"
            ) {
                CounterHolder(0)
            }
            assertFalse(value.created)
            assertEquals(7, value.value.count)
        }
    }

    @Test
    fun `typed resolver fails loudly on stored value type mismatch`() {
        val runtime = ComponentHookRuntime()
        val owner = Any()

        render(runtime, owner) {
            resolveNamedEntry(HookEntryKind.State, "typedMismatch") { 0 }
        }

        render(runtime, owner) {
            val error = assertFailsWith<HookUsageException> {
                resolveNamedTypedEntry<String>(
                    kind = HookEntryKind.State,
                    delegateName = "typedMismatch"
                ) { "value" }
            }
            assertTrue(error.message?.contains("Hook value type mismatch") == true)
            assertTrue(error.message?.contains("typedMismatch") == true)
        }
    }
    @Test
    fun `state does not leak across different owners`() {
        val runtime = ComponentHookRuntime()
        val firstOwner = Any()
        val secondOwner = Any()

        render(runtime, firstOwner) {
            val count = resolveNamedEntry(HookEntryKind.State, "count") { 0 }
            count.entry.value = 5
        }

        render(runtime, secondOwner) {
            val count = resolveNamedEntry(HookEntryKind.State, "count") { 0 }
            assertTrue(count.created)
            assertEquals(0, count.entry.value)
        }
    }

    private data class CounterHolder(
        var count: Int
    )
    private inline fun render(runtime: ComponentHookRuntime, owner: Any, block: ComponentHookRuntime.() -> Unit) {
        runtime.beginRender(owner)
        try {
            runtime.block()
        } finally {
            runtime.endRender()
        }
    }
}


