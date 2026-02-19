package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.Events
import org.dreamfinity.dsgl.core.event.FocusGainEvent
import org.dreamfinity.dsgl.core.event.FocusLoseEvent
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyInput
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.KeyboardKeyDownEvent
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseDownEvent
import org.dreamfinity.dsgl.core.event.MouseDragEvent
import org.dreamfinity.dsgl.core.event.MouseUpEvent
import org.dreamfinity.dsgl.core.event.postChange
import org.dreamfinity.dsgl.core.event.postInput
import org.dreamfinity.dsgl.core.input.ClipboardBridge
import org.dreamfinity.dsgl.core.render.RenderCommand
import java.util.ArrayDeque

/**
 * Base class for single-line text inputs.
 */
open class SingleLineInputNode(
    text: String = "",
    var placeholder: String = "",
    key: Any? = null
) : DOMNode(key) {
    companion object {
        private data class UndoSnapshot(
            val text: String,
            val caretIndex: Int,
            val selectionAnchor: Int?
        )

        private data class PersistedState(
            val caretIndex: Int,
            val selectionAnchor: Int?,
            val undoHistory: List<UndoSnapshot>,
            val redoHistory: List<UndoSnapshot>
        )

        private val persistedByKey: MutableMap<Any, PersistedState> = HashMap()
        private var activeSelectionDragIdentity: Any? = null

        fun clearActiveDrag() {
            activeSelectionDragIdentity = null

        }
    }

    override val styleType: String = "input"
    private val initialText: String = text

    override val focusable: Boolean = true
    var text: String = initialText
    var allowedChars: String? = null
    var minLength: Int? = null
    var maxLength: Int? = null
    var textColor: Int = DsglColors.TEXT
    var placeholderColor: Int = 0xFF8A8A8A.toInt()
    var backgroundColor: Int = 0xFF2E2E33.toInt()
    var focusedBackgroundColor: Int = 0xFF3A3A40.toInt()
    var minContentWidth: Int = 80
    var selectionColor: Int = 0x664A90E2
    var caretBlinkPeriodMs: Long = 500L

    private val editState: TextEditState = TextEditState(caretIndex = this.text.length)
    private val undoStack: ArrayDeque<UndoSnapshot> = ArrayDeque()
    private val redoStack: ArrayDeque<UndoSnapshot> = ArrayDeque()
    private val undoLimit: Int = 32
    private var typingWordUndoOpen: Boolean = false
    private var valueAtFocusStart: String = this.text
    private var dirtySinceFocus: Boolean = false
    private var lastMeasureText: ((String) -> Int)? = null

    init {
        restorePersistedState()
    }

    init {
        EventBus.run {
            this@SingleLineInputNode.addEventListener(Events.MOUSEDOWN) { event: MouseDownEvent ->
                if (this@SingleLineInputNode.styleDisabled) return@addEventListener
                if (event.mouseButton != MouseButton.LEFT) return@addEventListener
                if (!this@SingleLineInputNode.bounds.contains(event.mouseX, event.mouseY)) return@addEventListener
                handlePointerDown(event.mouseX)
            }
            this@SingleLineInputNode.addEventListener(Events.DRAG) { event: MouseDragEvent ->
                if (this@SingleLineInputNode.styleDisabled) return@addEventListener
                if (!isActiveSelectionDragTarget()) return@addEventListener
                val currentX = event.lastMouseX + event.dx
                updateSelectionFromPointerDrag(currentX)
            }
            this@SingleLineInputNode.addEventListener(Events.MOUSEUP) { event: MouseUpEvent ->
                if (event.mouseButton != MouseButton.LEFT) return@addEventListener
                if (!isActiveSelectionDragTarget()) return@addEventListener
                clearActiveDrag()
                if (!editState.hasSelection()) {
                    editState.clearSelection()
                }
                editState.resetBlinkClock()
                persistState()
            }
            this@SingleLineInputNode.addEventListener(Events.KEYDOWN) { event: KeyboardKeyDownEvent ->
                if (this@SingleLineInputNode.styleDisabled) return@addEventListener
                if (!FocusManager.isFocused(this@SingleLineInputNode)) return@addEventListener
                handleKey(event)
            }
            this@SingleLineInputNode.addEventListener(Events.FOCUS) { _: FocusGainEvent ->
                valueAtFocusStart = currentEventValue()
                dirtySinceFocus = false
                editState.resetBlinkClock()
            }
            this@SingleLineInputNode.addEventListener(Events.BLUR) { _: FocusLoseEvent ->
                commitCurrentValueChange()
                persistState()
            }
        }
    }

    protected open fun displayText(): String = this.text
    protected open fun currentEventValue(): String = this.text
    protected open fun currentParsedValue(): Any? = this.text
    protected open fun allowClipboardCopy(): Boolean = true
    protected open fun allowClipboardCut(): Boolean = true
    protected open fun allowClipboardPaste(): Boolean = true

    protected open fun sanitizePastedText(raw: String): String {
        return raw.replace("\r", "").replace("\n", "")
    }

    protected open fun handleKey(event: KeyboardKeyDownEvent) {
        editState.clampToLength(text.length)
        if (handleClipboardShortcut(event)) return

        when (event.keyCode) {
            KeyCodes.ENTER -> {
                commitCurrentValueChange()
                editState.resetBlinkClock()
                persistState()
            }

            KeyCodes.LEFT -> moveCaretLeft(KeyModifiers.shiftDown)
            KeyCodes.RIGHT -> moveCaretRight(KeyModifiers.shiftDown)
            KeyCodes.HOME -> moveCaretToBoundary(start = true, extend = KeyModifiers.shiftDown)
            KeyCodes.END -> moveCaretToBoundary(start = false, extend = KeyModifiers.shiftDown)
            KeyCodes.BACKSPACE -> deleteBeforeCaret()
            KeyCodes.DELETE -> deleteAfterCaret()
            else -> {
                var ch = event.keyChar
                if (!isPrintable(ch)) return
                ch = KeyInput.applyShift(ch, KeyModifiers.shiftDown)
                if (allowedChars != null && !allowedChars!!.contains(ch)) return
                val recordUndo = shouldRecordTypingUndo(ch)
                replaceSelectionWith(ch.toString(), recordUndo = recordUndo)
            }
        }
    }

    protected fun isPrintable(ch: Char): Boolean {
        return ch >= ' ' && ch.code != 127
    }

    protected open fun canAcceptText(next: String): Boolean {
        if (maxLength != null && next.length > maxLength!!) return false
        return true
    }

    protected open fun applyText(next: String) {
        text = next
    }

    protected fun commitCurrentValueChange() {
        val current = currentEventValue()
        if (!dirtySinceFocus) return
        if (current == valueAtFocusStart) {
            dirtySinceFocus = false
            return
        }
        postChange(this, current, currentParsedValue())
        valueAtFocusStart = current
        dirtySinceFocus = false
    }

    protected fun notifyUserValueChanged(previousValue: String) {
        val current = currentEventValue()
        if (current == previousValue) return
        dirtySinceFocus = true
        postInput(this, current, currentParsedValue())
    }

    override fun measure(ctx: UiMeasureContext): Size {
        lastMeasureText = { value -> ctx.measureText(value) }
        val display = if (text.isNotEmpty()) displayText() else placeholder
        val contentWidth = width ?: maxOf(ctx.measureText(display), minContentWidth)
        val contentHeight = height ?: ctx.fontHeight
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        val focused = FocusManager.isFocused(this)
        val bg = if (focused && !styleDisabled) focusedBackgroundColor else backgroundColor
        out.add(RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, bg))
        addBackgroundImageCommand(out)
        addBorderCommands(out)

        val showPlaceholder = text.isEmpty() && !focused && placeholder.isNotEmpty()
        val drawText = if (showPlaceholder) placeholder else displayText()
        val innerX = contentX()
        val innerY = contentY()
        val innerWidth = contentWidth()
        val innerHeight = contentHeight()
        val textY = innerY + (innerHeight - ctx.fontHeight) / 2
        lastMeasureText = { value -> ctx.measureText(value) }
        editState.clampToLength(text.length)

        if (innerWidth > 0 && innerHeight > 0) {
            out.add(RenderCommand.PushClip(innerX, innerY, innerWidth, innerHeight))
        }

        if (!showPlaceholder && focused && editState.hasSelection()) {
            val start = editState.selectionStart().coerceIn(0, drawText.length)
            val end = editState.selectionEnd().coerceIn(0, drawText.length)
            if (end > start) {
                val startX = innerX + ctx.measureText(drawText.substring(0, start))
                val endX = innerX + ctx.measureText(drawText.substring(0, end))
                val width = (endX - startX).coerceAtLeast(1)
                out.add(RenderCommand.DrawRect(startX, textY, width, ctx.fontHeight, selectionColor))
            }
        }

        if (drawText.isNotEmpty()) {
            val color = if (showPlaceholder) placeholderColor else textColor
            out.add(RenderCommand.DrawText(drawText, innerX, textY, color))
        }

        if (!showPlaceholder && focused && !styleDisabled && editState.isCaretVisible(caretBlinkPeriodMs)) {
            val caretBaseText = drawText.substring(0, editState.caretIndex.coerceIn(0, drawText.length))
            val caretX = innerX + ctx.measureText(caretBaseText)
            out.add(RenderCommand.DrawRect(caretX, textY, 1, ctx.fontHeight, textColor))
        }

        if (innerWidth > 0 && innerHeight > 0) {
            out.add(RenderCommand.PopClip)
        }

    }

    fun shouldCaptureTextSelectionDrag(mouseX: Int, mouseY: Int): Boolean {
        if (styleDisabled) return false
        return bounds.contains(mouseX, mouseY)
    }

    private fun handlePointerDown(mouseX: Int) {
        resetTypingUndoGroup()
        val target = caretIndexFromMouseX(mouseX)
        editState.selectionAnchor = target
        editState.caretIndex = target
        editState.clampToLength(text.length)
        editState.resetBlinkClock()
        FocusManager.requestFocus(this)
        activeSelectionDragIdentity = dragIdentity()
        persistState()
    }

    private fun updateSelectionFromPointerDrag(mouseX: Int) {
        resetTypingUndoGroup()
        if (editState.selectionAnchor == null) {
            editState.selectionAnchor = editState.caretIndex
        }
        editState.caretIndex = caretIndexFromMouseX(mouseX)
        editState.clampToLength(text.length)
        editState.resetBlinkClock()
        persistState()
    }

    private fun moveCaretLeft(extend: Boolean) {
        resetTypingUndoGroup()
        if (!extend && editState.hasSelection()) {
            editState.caretIndex = editState.selectionStart()
            editState.clearSelection()
        } else {
            val next = (editState.caretIndex - 1).coerceAtLeast(0)
            moveCaret(next, extend)
        }
        editState.resetBlinkClock()
        persistState()
    }

    private fun moveCaretRight(extend: Boolean) {
        resetTypingUndoGroup()
        if (!extend && editState.hasSelection()) {
            editState.caretIndex = editState.selectionEnd()
            editState.clearSelection()
        } else {
            val next = (editState.caretIndex + 1).coerceAtMost(text.length)
            moveCaret(next, extend)
        }
        editState.resetBlinkClock()
        persistState()
    }

    private fun moveCaretToBoundary(start: Boolean, extend: Boolean) {
        resetTypingUndoGroup()
        val next = if (start) 0 else text.length
        moveCaret(next, extend)
        editState.resetBlinkClock()
        persistState()
    }

    private fun moveCaret(next: Int, extend: Boolean) {
        if (extend) {
            if (editState.selectionAnchor == null) {
                editState.selectionAnchor = editState.caretIndex
            }
        } else {
            editState.clearSelection()
        }
        editState.caretIndex = next.coerceIn(0, text.length)
        if (!editState.hasSelection()) {
            editState.clearSelection()
        }
    }

    private fun deleteBeforeCaret() {
        resetTypingUndoGroup()
        if (replaceSelectionWith("", recordUndo = true)) return
        if (editState.caretIndex <= 0 || text.isEmpty()) return
        val start = (editState.caretIndex - 1).coerceAtLeast(0)
        val end = editState.caretIndex.coerceIn(0, text.length)
        replaceRange(start, end, "", recordUndo = true)
    }

    private fun deleteAfterCaret() {
        resetTypingUndoGroup()
        if (replaceSelectionWith("", recordUndo = true)) return
        if (editState.caretIndex >= text.length || text.isEmpty()) return
        val start = editState.caretIndex.coerceIn(0, text.length)
        val end = (start + 1).coerceAtMost(text.length)
        replaceRange(start, end, "", recordUndo = true)
    }

    private fun replaceSelectionWith(insert: String, recordUndo: Boolean = false): Boolean {
        val hasSelection = editState.hasSelection()
        if (!hasSelection && insert.isEmpty()) return false
        val start = if (hasSelection) editState.selectionStart() else editState.caretIndex
        val end = if (hasSelection) editState.selectionEnd() else editState.caretIndex
        return replaceRange(start, end, insert, recordUndo)
    }

    private fun replaceRange(start: Int, end: Int, insert: String, recordUndo: Boolean = false): Boolean {
        val safeStart = start.coerceIn(0, text.length)
        val safeEnd = end.coerceIn(safeStart, text.length)
        val previous = currentEventValue()
        val next = text.substring(0, safeStart) + insert + text.substring(safeEnd)
        if (!canAcceptText(next)) return false
        if (next == text) return false
        clearRedoHistory()
        if (recordUndo && (safeStart != safeEnd || insert.isNotEmpty())) {
            pushUndoSnapshot()
        }
        applyText(next)
        editState.caretIndex = (safeStart + insert.length).coerceIn(0, text.length)
        editState.clearSelection()
        editState.resetBlinkClock()
        persistState()
        notifyUserValueChanged(previous)
        return true
    }

    private fun handleClipboardShortcut(event: KeyboardKeyDownEvent): Boolean {
        if (!KeyModifiers.shortcutDown) return false
        when (event.keyCode) {
            KeyCodes.A -> {
                resetTypingUndoGroup()
                if (text.isEmpty()) {
                    editState.clearSelection()
                    editState.caretIndex = 0
                } else {
                    editState.selectionAnchor = 0
                    editState.caretIndex = text.length
                }
                editState.resetBlinkClock()
                persistState()
                return true
            }

            KeyCodes.C -> {
                resetTypingUndoGroup()
                if (allowClipboardCopy() && editState.hasSelection()) {
                    ClipboardBridge.writeText(selectedText())
                }
                editState.resetBlinkClock()
                return true
            }

            KeyCodes.X -> {
                resetTypingUndoGroup()
                if (allowClipboardCut() && editState.hasSelection()) {
                    ClipboardBridge.writeText(selectedText())
                    replaceSelectionWith("", recordUndo = true)
                }
                editState.resetBlinkClock()
                persistState()
                return true
            }

            KeyCodes.V -> {
                resetTypingUndoGroup()
                if (!allowClipboardPaste()) return true
                val paste = sanitizePastedText(ClipboardBridge.readText())
                if (paste.isNotEmpty()) {
                    replaceSelectionWith(paste, recordUndo = true)
                }
                editState.resetBlinkClock()
                persistState()
                return true
            }

            KeyCodes.Z -> {
                resetTypingUndoGroup()
                if (KeyModifiers.shiftDown) {
                    redoLastUndo()
                } else {
                    undoLastEdit()
                }
                editState.resetBlinkClock()
                persistState()
                return true
            }

            else -> return false
        }
    }

    private fun pushUndoSnapshot() {
        if (undoStack.size >= undoLimit) {
            undoStack.removeFirst()
        }
        undoStack.addLast(
            UndoSnapshot(
                text = text,
                caretIndex = editState.caretIndex,
                selectionAnchor = editState.selectionAnchor
            )
        )
    }

    private fun pushRedoSnapshot(snapshot: UndoSnapshot) {
        if (redoStack.size >= undoLimit) {
            redoStack.removeFirst()
        }
        redoStack.addLast(snapshot)
    }

    private fun clearRedoHistory() {
        if (redoStack.isNotEmpty()) {
            redoStack.clear()
        }
    }

    private fun currentSnapshot(): UndoSnapshot {
        return UndoSnapshot(
            text = text,
            caretIndex = editState.caretIndex,
            selectionAnchor = editState.selectionAnchor
        )
    }

    private fun undoLastEdit(): Boolean {
        val snapshot = undoStack.pollLast() ?: return false
        pushRedoSnapshot(currentSnapshot())
        val previous = currentEventValue()
        applyText(snapshot.text)
        editState.caretIndex = snapshot.caretIndex.coerceIn(0, text.length)
        editState.selectionAnchor = snapshot.selectionAnchor?.coerceIn(0, text.length)
        editState.clampToLength(text.length)
        notifyUserValueChanged(previous)
        return true
    }

    private fun redoLastUndo(): Boolean {
        val snapshot = redoStack.pollLast() ?: return false
        pushUndoSnapshot()
        val previous = currentEventValue()
        applyText(snapshot.text)
        editState.caretIndex = snapshot.caretIndex.coerceIn(0, text.length)
        editState.selectionAnchor = snapshot.selectionAnchor?.coerceIn(0, text.length)
        editState.clampToLength(text.length)
        notifyUserValueChanged(previous)
        return true
    }

    private fun shouldRecordTypingUndo(ch: Char): Boolean {
        val wordChar = ch.isLetterOrDigit() || ch == '_'
        if (editState.hasSelection()) {
            typingWordUndoOpen = wordChar
            return true
        }
        if (!wordChar) {
            typingWordUndoOpen = false
            return false
        }
        if (!typingWordUndoOpen) {
            typingWordUndoOpen = true
            return true
        }
        return false
    }

    private fun resetTypingUndoGroup() {
        typingWordUndoOpen = false
    }

    private fun selectedText(): String {
        if (!editState.hasSelection()) return ""
        val start = editState.selectionStart().coerceIn(0, text.length)
        val end = editState.selectionEnd().coerceIn(0, text.length)
        if (end <= start) return ""
        return text.substring(start, end)
    }

    private fun caretIndexFromMouseX(mouseX: Int): Int {
        val rendered = displayText()
        val localX = (mouseX - contentX()).coerceAtLeast(0)
        val measure = lastMeasureText ?: { value: String -> value.length * 6 }
        if (rendered.isEmpty() || localX <= 0) return 0

        var previousWidth = 0
        var index = 0
        while (index < rendered.length) {
            val nextIndex = index + 1
            val nextWidth = measure(rendered.substring(0, nextIndex))
            val midpoint = previousWidth + ((nextWidth - previousWidth) / 2)
            if (localX < midpoint) {
                return index
            }
            previousWidth = nextWidth
            index = nextIndex
        }
        return rendered.length
    }

    private fun dragIdentity(): Any {
        return key ?: this
    }

    private fun isActiveSelectionDragTarget(): Boolean {
        val active = activeSelectionDragIdentity ?: return false
        return active == dragIdentity()
    }

    private fun persistState() {
        val identity = key ?: return
        persistedByKey[identity] = PersistedState(
            caretIndex = editState.caretIndex,
            selectionAnchor = editState.selectionAnchor,
            undoHistory = undoStack.toList(),
            redoHistory = redoStack.toList()
        )
    }

    private fun restorePersistedState() {
        val identity = key ?: return
        val persisted = persistedByKey[identity] ?: return
        editState.caretIndex = persisted.caretIndex.coerceIn(0, text.length)
        editState.selectionAnchor = persisted.selectionAnchor?.coerceIn(0, text.length)
        undoStack.clear()
        persisted.undoHistory.takeLast(undoLimit).forEach { snapshot -> undoStack.addLast(snapshot) }
        redoStack.clear()
        persisted.redoHistory.takeLast(undoLimit).forEach { snapshot -> redoStack.addLast(snapshot) }
    }

    override fun defaultBackgroundColor(): Int? = backgroundColor

    override fun applyBackgroundColor(value: Int?) {
        if (value != null) {
            backgroundColor = value
        }
    }

    override fun defaultForegroundColor(): Int = textColor

    override fun applyForegroundColor(value: Int) {
        textColor = value
    }
}
