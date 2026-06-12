@file:Suppress("MatchingDeclarationName")

package org.dreamfinity.dsgl.core.hooks

import org.dreamfinity.dsgl.core.DsglWindow
import org.dreamfinity.dsgl.core.dsl.UiScope

interface EffectScope {
    fun onDispose(cleanup: () -> Unit)
}

fun UiScope.useEffect(vararg deps: Any?, effect: EffectScope.() -> Unit) {
    requireHookOwnerWindow().useEffect(*deps, effect = effect)
}

fun UiScope.useEffectEveryCommit(effect: EffectScope.() -> Unit) {
    requireHookOwnerWindow().useEffectEveryCommit(effect = effect)
}

internal fun DsglWindow.useEffect(vararg deps: Any?, effect: EffectScope.() -> Unit) {
    val runtime = hookRuntime()
    runtime.registerEffectOnDependencyChange(deps.toList()) { registerCleanup ->
        val scope =
            object : EffectScope {
                override fun onDispose(cleanup: () -> Unit) {
                    registerCleanup(cleanup)
                }
            }
        effect(scope)
    }
}

internal fun DsglWindow.useEffectEveryCommit(effect: EffectScope.() -> Unit) {
    val runtime = hookRuntime()
    runtime.registerEffectEveryCommit { registerCleanup ->
        val scope =
            object : EffectScope {
                override fun onDispose(cleanup: () -> Unit) {
                    registerCleanup(cleanup)
                }
            }
        effect(scope)
    }
}
