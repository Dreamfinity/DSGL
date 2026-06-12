package org.dreamfinity.dsgl.core.dom.elements.support

import java.util.ArrayDeque

/**
 * Undo/redo stack pair with a fixed snapshot capacity.
 */
internal class UndoRedoHistory<S>(
    private val limit: Int,
) {
    private val undoStack: ArrayDeque<S> = ArrayDeque()
    private val redoStack: ArrayDeque<S> = ArrayDeque()

    fun pushUndo(snapshot: S) {
        pushWithLimit(undoStack, snapshot)
    }

    fun clearRedo() {
        if (redoStack.isNotEmpty()) {
            redoStack.clear()
        }
    }

    fun undo(currentSnapshot: S): S? {
        val previous = undoStack.pollLast() ?: return null
        pushWithLimit(redoStack, currentSnapshot)
        return previous
    }

    fun redo(currentSnapshot: S): S? {
        val next = redoStack.pollLast() ?: return null
        pushWithLimit(undoStack, currentSnapshot)
        return next
    }

    fun undoHistory(): List<S> = undoStack.toList()

    fun redoHistory(): List<S> = redoStack.toList()

    fun restore(undoHistory: List<S>, redoHistory: List<S>) {
        undoStack.clear()
        redoStack.clear()
        undoHistory.takeLast(limit).forEach { snapshot -> pushWithLimit(undoStack, snapshot) }
        redoHistory.takeLast(limit).forEach { snapshot -> pushWithLimit(redoStack, snapshot) }
    }

    private fun pushWithLimit(stack: ArrayDeque<S>, snapshot: S) {
        if (stack.size >= limit) {
            stack.removeFirst()
        }
        stack.addLast(snapshot)
    }
}
