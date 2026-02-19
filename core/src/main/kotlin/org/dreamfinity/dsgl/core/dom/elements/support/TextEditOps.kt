package org.dreamfinity.dsgl.core.dom.elements.support

import org.dreamfinity.dsgl.core.dom.elements.TextEditState

internal object TextEditOps {
    fun isPrintable(ch: Char): Boolean {
        return ch >= ' ' && ch.code != 127
    }

    fun dragIdentity(key: Any?, node: Any): Any {
        return key ?: node
    }

    fun selectedText(source: String, editState: TextEditState): String {
        if (!editState.hasSelection()) return ""
        val start = editState.selectionStart().coerceIn(0, source.length)
        val end = editState.selectionEnd().coerceIn(0, source.length)
        if (end <= start) return ""
        return source.substring(start, end)
    }

    fun selectAll(editState: TextEditState, length: Int) {
        if (length <= 0) {
            editState.clearSelection()
            editState.caretIndex = 0
            return
        }
        editState.selectionAnchor = 0
        editState.caretIndex = length
    }

    fun selectionOrCaretBounds(editState: TextEditState): Pair<Int, Int> {
        val hasSelection = editState.hasSelection()
        val start = if (hasSelection) editState.selectionStart() else editState.caretIndex
        val end = if (hasSelection) editState.selectionEnd() else editState.caretIndex
        return start to end
    }

    fun moveCaretWithSelection(editState: TextEditState, next: Int, textLength: Int, extend: Boolean) {
        if (extend) {
            if (editState.selectionAnchor == null) {
                editState.selectionAnchor = editState.caretIndex
            }
        } else {
            editState.clearSelection()
        }
        editState.caretIndex = next.coerceIn(0, textLength)
        if (!editState.hasSelection()) {
            editState.clearSelection()
        }
    }

    fun replaceRange(source: String, start: Int, end: Int, insert: String): String {
        val safeStart = start.coerceIn(0, source.length)
        val safeEnd = end.coerceIn(safeStart, source.length)
        return source.substring(0, safeStart) + insert + source.substring(safeEnd)
    }
}
