package org.dreamfinity.dsgl.core.hooks.ref

import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.dsl.UiScope
import org.dreamfinity.dsgl.core.hooks.HookEntryKind
import org.dreamfinity.dsgl.core.hooks.HookSignature
import org.dreamfinity.dsgl.core.hooks.HookSignatures
import kotlin.ExperimentalStdlibApi
import kotlin.reflect.KProperty
import kotlin.reflect.typeOf

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
    override var current: T? = null,
) : Ref<T>

fun <T : Any> createRef(initial: T? = null): Ref<T> = RefObject(initial)

class RefHookDelegate<T : Any>
    @PublishedApi
    internal constructor(
        private val window: DsglWindow,
        private val initial: T?,
        private val signature: HookSignature,
    ) {
        private val runtime = window.hookRuntime()
        private val bindingToken = runtime.registerStorageBackedHookCandidate("useRef")
        private var boundRef: Ref<T>? = null

        operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): RefHookDelegate<T> {
            runtime.markStorageBackedHookBound(bindingToken)
            val resolved =
                runtime.resolveNamedTypedEntry(
                    kind = HookEntryKind.Ref,
                    delegateName = property.name,
                    signature = signature,
                    expectedRawType = Ref::class.java,
                ) {
                    RefObject(initial)
                }
            boundRef = resolved.value
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

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T : Any> UiScope.useRef(initial: T? = null): RefHookDelegate<T> =
    createRefHookDelegate(window = requireHookOwnerWindow(), initial = initial)

@PublishedApi
@OptIn(ExperimentalStdlibApi::class)
internal inline fun <reified T : Any> DsglWindow.useRef(initial: T? = null): RefHookDelegate<T> =
    createRefHookDelegate(window = this, initial = initial)

@PublishedApi
@OptIn(ExperimentalStdlibApi::class)
internal inline fun <reified T : Any> createRefHookDelegate(
    window: DsglWindow,
    initial: T? = null,
): RefHookDelegate<T> =
    RefHookDelegate(
        window = window,
        initial = initial,
        signature = HookSignatures.ref(typeOf<T>()),
    )
