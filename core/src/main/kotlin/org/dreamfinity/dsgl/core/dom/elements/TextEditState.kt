package org.dreamfinity.dsgl.core.dom.elements

/**
 * Shared editable text state used by text-like controls.
 */
data class TextEditState(
    var caretIndex: Int = 0,
    var selectionAnchor: Int? = null,
    var scrollY: Int = 0,
    var lastInteractionAtMs: Long = System.currentTimeMillis()
) {
    fun clampToLength(length: Int) {
        caretIndex = caretIndex.coerceIn(0, length)
        selectionAnchor = selectionAnchor?.coerceIn(0, length)
    }

    fun clearSelection() {
        selectionAnchor = null
    }

    fun hasSelection(): Boolean {
        val anchor = selectionAnchor ?: return false
        return anchor != caretIndex
    }

    fun selectionStart(): Int {
        val anchor = selectionAnchor ?: caretIndex
        return minOf(anchor, caretIndex)
    }

    fun selectionEnd(): Int {
        val anchor = selectionAnchor ?: caretIndex
        return maxOf(anchor, caretIndex)
    }

    fun resetBlinkClock(now: Long = System.currentTimeMillis()) {
        lastInteractionAtMs = now
    }

    fun isCaretVisible(blinkPeriodMs: Long = 500L, now: Long = System.currentTimeMillis()): Boolean {
        if (blinkPeriodMs <= 0L) return true
        val ticks = ((now - lastInteractionAtMs).coerceAtLeast(0L) / blinkPeriodMs)
        return ticks % 2L == 0L
    }
}
