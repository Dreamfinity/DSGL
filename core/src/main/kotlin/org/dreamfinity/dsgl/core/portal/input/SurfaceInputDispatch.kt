package org.dreamfinity.dsgl.core.portal.input

internal inline fun dispatchManualThenDomFallback(
    manualDispatch: () -> Boolean,
    domFallbackDispatch: () -> Boolean,
): Boolean {
    if (manualDispatch()) {
        return true
    }
    return domFallbackDispatch()
}
