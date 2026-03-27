package org.dreamfinity.dsgl.core

interface EffectScope {
    fun onDispose(cleanup: () -> Unit)
}

fun DsglWindow.useEffect(vararg deps: Any?, effect: EffectScope.() -> Unit) {
    val runtime = hookRuntime()
    runtime.registerEffectOnDependencyChange(deps.toList()) { registerCleanup ->
        val scope = object : EffectScope {
            override fun onDispose(cleanup: () -> Unit) {
                registerCleanup(cleanup)
            }
        }
        effect(scope)
    }
}

fun DsglWindow.useEffectEveryCommit(effect: EffectScope.() -> Unit) {
    val runtime = hookRuntime()
    runtime.registerEffectEveryCommit { registerCleanup ->
        val scope = object : EffectScope {
            override fun onDispose(cleanup: () -> Unit) {
                registerCleanup(cleanup)
            }
        }
        effect(scope)
    }
}
