package org.dreamfinity.dsgl.core.hooks

import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.UiScope
import kotlin.ExperimentalStdlibApi
import kotlin.reflect.KProperty
import kotlin.reflect.typeOf

internal class MemoHookCell<T>(
    deps: List<Any?>,
    value: T
) {
    private var depsSnapshot: List<Any?> = deps
    private var cachedValue: T = value

    fun sync(nextDeps: List<Any?>, compute: () -> T) {
        if (depsSnapshot == nextDeps) {
            return
        }
        depsSnapshot = nextDeps
        cachedValue = compute()
    }

    fun read(): T = cachedValue
}

class MemoHookDelegate<T> @PublishedApi internal constructor(
    private val window: DsglWindow,
    private val hookName: String,
    private val deps: List<Any?>,
    private val compute: () -> T,
    private val signature: HookSignature
) {
    private val runtime = window.hookRuntime()
    private val bindingToken = runtime.registerStorageBackedHookCandidate(hookName)
    private var boundCell: MemoHookCell<T>? = null

    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): MemoHookDelegate<T> {
        runtime.markStorageBackedHookBound(bindingToken)
        val resolved = runtime.resolveNamedTypedEntry(
            kind = HookEntryKind.Memo,
            delegateName = property.name,
            signature = signature,
            expectedRawType = MemoHookCell::class.java
        ) {
            MemoHookCell(deps = deps, value = compute())
        }
        resolved.value.sync(nextDeps = deps, compute = compute)
        boundCell = resolved.value
        return this
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val resolved = boundCell
        if (resolved != null) {
            return resolved.read()
        }
        runtime.failStorageBackedHookWithoutDelegate(hookName)
    }
}

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T> UiScope.useMemo(vararg deps: Any?, noinline compute: () -> T): MemoHookDelegate<T> {
    return createMemoHookDelegate(
        window = requireHookOwnerWindow(),
        hookName = "useMemo",
        deps = deps.toList(),
        compute = compute,
        signature = HookSignatures.memo(typeOf<T>())
    )
}

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T> UiScope.useMemo(noinline compute: () -> T): MemoHookDelegate<T> {
    return createMemoHookDelegate(
        window = requireHookOwnerWindow(),
        hookName = "useMemo",
        deps = emptyList(),
        compute = compute,
        signature = HookSignatures.memo(typeOf<T>())
    )
}

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified F : Any> UiScope.useCallback(vararg deps: Any?, noinline factory: () -> F): MemoHookDelegate<F> {
    return createMemoHookDelegate(
        window = requireHookOwnerWindow(),
        hookName = "useCallback",
        deps = deps.toList(),
        compute = factory,
        signature = HookSignatures.memo(typeOf<F>())
    )
}

@PublishedApi
@OptIn(ExperimentalStdlibApi::class)
internal inline fun <reified T> DsglWindow.useMemo(vararg deps: Any?, noinline compute: () -> T): MemoHookDelegate<T> {
    return createMemoHookDelegate(
        window = this,
        hookName = "useMemo",
        deps = deps.toList(),
        compute = compute,
        signature = HookSignatures.memo(typeOf<T>())
    )
}

@PublishedApi
@OptIn(ExperimentalStdlibApi::class)
internal inline fun <reified T> DsglWindow.useMemo(noinline compute: () -> T): MemoHookDelegate<T> {
    return createMemoHookDelegate(
        window = this,
        hookName = "useMemo",
        deps = emptyList(),
        compute = compute,
        signature = HookSignatures.memo(typeOf<T>())
    )
}

@PublishedApi
@OptIn(ExperimentalStdlibApi::class)
internal inline fun <reified F : Any> DsglWindow.useCallback(
    vararg deps: Any?,
    noinline factory: () -> F
): MemoHookDelegate<F> {
    return createMemoHookDelegate(
        window = this,
        hookName = "useCallback",
        deps = deps.toList(),
        compute = factory,
        signature = HookSignatures.memo(typeOf<F>())
    )
}

@PublishedApi
internal fun <T> createMemoHookDelegate(
    window: DsglWindow,
    hookName: String,
    deps: List<Any?>,
    compute: () -> T,
    signature: HookSignature
): MemoHookDelegate<T> {
    return MemoHookDelegate(
        window = window,
        hookName = hookName,
        deps = deps,
        compute = compute,
        signature = signature
    )
}
