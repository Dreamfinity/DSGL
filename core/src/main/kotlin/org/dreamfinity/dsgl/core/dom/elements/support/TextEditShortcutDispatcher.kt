package org.dreamfinity.dsgl.core.dom.elements.support

import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.KeyboardKeyDownEvent
import org.dreamfinity.dsgl.core.input.ClipboardBridge

internal enum class TextShortcutAction {
    SELECT_ALL,
    COPY,
    CUT,
    PASTE,
    UNDO,
    REDO
}

internal data class TextShortcutCallbacks(
    val canCopy: Boolean = true,
    val canCut: Boolean = true,
    val canPaste: Boolean = true,
    val hasSelection: () -> Boolean,
    val selectAll: () -> Unit,
    val copySelection: () -> Unit,
    val cutSelection: () -> Unit,
    val normalizePaste: (String) -> String,
    val pasteSelection: (String) -> Unit,
    val undo: () -> Unit,
    val redo: () -> Unit,
    val beforeHandled: () -> Unit,
    val afterHandled: (TextShortcutAction) -> Unit
)

internal object TextEditShortcutDispatcher {
    fun dispatch(event: KeyboardKeyDownEvent, callbacks: TextShortcutCallbacks): Boolean {
        if (!KeyModifiers.shortcutDown) return false
        when (event.keyCode) {
            KeyCodes.A -> {
                callbacks.beforeHandled()
                callbacks.selectAll()
                callbacks.afterHandled(TextShortcutAction.SELECT_ALL)
                return true
            }

            KeyCodes.C -> {
                callbacks.beforeHandled()
                if (callbacks.canCopy && callbacks.hasSelection()) {
                    callbacks.copySelection()
                }
                callbacks.afterHandled(TextShortcutAction.COPY)
                return true
            }

            KeyCodes.X -> {
                callbacks.beforeHandled()
                if (callbacks.canCut && callbacks.hasSelection()) {
                    callbacks.cutSelection()
                }
                callbacks.afterHandled(TextShortcutAction.CUT)
                return true
            }

            KeyCodes.V -> {
                callbacks.beforeHandled()
                if (!callbacks.canPaste) return true
                val paste = callbacks.normalizePaste(ClipboardBridge.readText())
                if (paste.isNotEmpty()) {
                    callbacks.pasteSelection(paste)
                }
                callbacks.afterHandled(TextShortcutAction.PASTE)
                return true
            }

            KeyCodes.Z -> {
                callbacks.beforeHandled()
                if (KeyModifiers.shiftDown) {
                    callbacks.redo()
                    callbacks.afterHandled(TextShortcutAction.REDO)
                } else {
                    callbacks.undo()
                    callbacks.afterHandled(TextShortcutAction.UNDO)
                }
                return true
            }

            else -> return false
        }
    }
}
