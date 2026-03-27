package org.dreamfinity.dsgl.core.ref

import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.hooks.HookEntryKind
import kotlin.reflect.KProperty

typealias RefCallback<T> = (T?) -> Unit

fun interface RefTarget<T : Any> {
    fun set(value: T?)
}

interface Ref<T : Any> : RefTarget<T> {
    var current: T?

    override fun set(value: T?) {
        current = value
    }
}

class RefObject<T : Any>(
    override var current: T? = null
) : Ref<T>

fun <T : Any> createRef(initial: T? = null): Ref<T> = RefObject(initial)

class RefHookDelegate<T : Any> internal constructor(
    private val window: DsglWindow,
    private val initial: T?
) {
    private val runtime = window.hookRuntime()
    private val bindingToken = runtime.registerStorageBackedHookCandidate("useRef")
    private var boundRef: Ref<T>? = null

    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): RefHookDelegate<T> {
        runtime.markStorageBackedHookBound(bindingToken)
        val resolved = runtime.resolveNamedEntry(
            kind = HookEntryKind.Ref,
            delegateName = property.name
        ) {
            RefObject(initial)
        }
        @Suppress("UNCHECKED_CAST")
        boundRef = resolved.entry.value as Ref<T>
        return this
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Ref<T> {
        val resolved = boundRef
        if (resolved != null) {
            return resolved
        }
        runtime.failStorageBackedHookWithoutDelegate("useRef")
    }
}

fun <T : Any> DsglWindow.useRef(initial: T? = null): RefHookDelegate<T> {
    return RefHookDelegate(window = this, initial = initial)
}
