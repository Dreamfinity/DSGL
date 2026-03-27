package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.hooks.HookEntryKind
import kotlin.reflect.KProperty

internal class HookStateCell<T>(
    initial: T,
    private val onChange: () -> Unit
) {
    private var stored: T = initial

    fun read(): T = stored

    fun write(next: T) {
        if (next != stored) {
            stored = next
            onChange()
        }
    }
}

class StateHookDelegate<T> internal constructor(
    private val window: DsglWindow,
    private val initial: T
) {
    private val runtime = window.hookRuntime()
    private val bindingToken = runtime.registerStorageBackedHookCandidate("useState")
    private var boundCell: HookStateCell<T>? = null

    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): StateHookDelegate<T> {
        runtime.markStorageBackedHookBound(bindingToken)
        val resolved = runtime.resolveNamedTypedEntry(
            kind = HookEntryKind.State,
            delegateName = property.name,
            expectedRawType = HookStateCell::class.java
        ) {
            HookStateCell(initial) {
                window.onHookStateChanged()
            }
        }
        boundCell = resolved.value
        return this
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return requireBoundCell().read()
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        requireBoundCell().write(value)
    }

    private fun requireBoundCell(): HookStateCell<T> {
        val resolved = boundCell
        if (resolved != null) {
            return resolved
        }
        runtime.failStorageBackedHookWithoutDelegate("useState")
    }
}

fun <T> DsglWindow.useState(initial: T): StateHookDelegate<T> {
    return StateHookDelegate(window = this, initial = initial)
}
