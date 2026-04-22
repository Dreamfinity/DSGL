package org.dreamfinity.dsgl.core.overlay.input

internal inline fun dispatchManualThenDomFallback(
    manualDispatch: () -> Boolean,
    domFallbackDispatch: () -> Boolean
): Boolean {
    if (manualDispatch()) {
        return true
    }
    return domFallbackDispatch()
}
