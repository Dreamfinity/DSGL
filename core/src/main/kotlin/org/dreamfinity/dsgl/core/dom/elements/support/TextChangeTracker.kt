package org.dreamfinity.dsgl.core.dom.elements.support

/**
 * Tracks "dirty since focus" state and commit baseline for change events.
 */
internal class TextChangeTracker(
    initialValue: String
) {
    private var valueAtFocusStart: String = initialValue
    private var dirtySinceFocus: Boolean = false

    fun onFocus(currentValue: String) {
        valueAtFocusStart = currentValue
        dirtySinceFocus = false
    }

    fun markDirty() {
        dirtySinceFocus = true
    }

    fun markInputChange(previousValue: String, currentValue: String): Boolean {
        if (currentValue == previousValue) return false
        dirtySinceFocus = true
        return true
    }

    fun commitIfNeeded(currentValue: String, onCommit: () -> Unit): Boolean {
        if (!dirtySinceFocus) return false
        if (currentValue == valueAtFocusStart) {
            dirtySinceFocus = false
            return false
        }
        onCommit()
        valueAtFocusStart = currentValue
        dirtySinceFocus = false
        return true
    }
}

