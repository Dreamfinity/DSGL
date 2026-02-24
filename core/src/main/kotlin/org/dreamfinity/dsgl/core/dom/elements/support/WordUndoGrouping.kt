package org.dreamfinity.dsgl.core.dom.elements.support

/**
 * Groups typing undo snapshots by word to avoid per-character history entries.
 */
internal class WordUndoGrouping {
    private var wordUndoOpen: Boolean = false

    fun shouldRecord(ch: Char, hasSelection: Boolean): Boolean {
        val wordChar = ch.isLetterOrDigit() || ch == '_'
        if (hasSelection) {
            wordUndoOpen = wordChar
            return true
        }
        if (!wordChar) {
            wordUndoOpen = false
            return false
        }
        if (!wordUndoOpen) {
            wordUndoOpen = true
            return true
        }
        return false
    }

    fun reset() {
        wordUndoOpen = false
    }
}
