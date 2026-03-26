package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.support.*
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.input.ClipboardBridge
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.TextWrap

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

        private val persistedByKey: KeyedStateStore<PersistedState> = KeyedStateStore()
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
    private val undoLimit: Int = 32
    private val history: UndoRedoHistory<UndoSnapshot> = UndoRedoHistory(undoLimit)
    private val typingUndoGrouping: WordUndoGrouping = WordUndoGrouping()
    private val changeTracker: TextChangeTracker = TextChangeTracker(this.text)
    private var lastMeasureText: ((String) -> Int)? = null

    init {
        textWrap = TextWrap.NoWrap
        restorePersistedState()
    }

    init {
        EventBus.run {
            this@SingleLineInputNode.addEventListener(Events.MOUSEDOWN) { event: MouseDownEvent ->
                if (this@SingleLineInputNode.styleDisabled) return@addEventListener
                if (event.mouseButton != MouseButton.LEFT) return@addEventListener
                if (!this@SingleLineInputNode.containsGlobalPoint(event.mouseX, event.mouseY)) return@addEventListener
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
                changeTracker.onFocus(currentEventValue())
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
        return TextEditOps.isPrintable(ch)
    }

    protected open fun canAcceptText(next: String): Boolean {
        return !(maxLength != null && next.length > maxLength!!)
    }

    protected open fun applyText(next: String) {
        text = next
    }

    protected fun commitCurrentValueChange() {
        val current = currentEventValue()
        changeTracker.commitIfNeeded(current) {
            postChange(this, current, currentParsedValue())
        }
    }

    protected fun notifyUserValueChanged(previousValue: String) {
        val current = currentEventValue()
        if (!changeTracker.markInputChange(previousValue, current)) return
        postInput(this, current, currentParsedValue())
    }

    internal override fun measureForLayout(ctx: UiMeasureContext, availableOuterWidth: Int?): Size {
        return measureWithConstraint(ctx, availableOuterWidth)
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return measureWithConstraint(ctx, null)
    }

    private fun measureWithConstraint(ctx: UiMeasureContext, availableOuterWidth: Int?): Size {
        val lineHeight = resolveFontSize(ctx)
        lastMeasureText = { value -> measureText(ctx, value) }
        val display = if (text.isNotEmpty()) displayText() else placeholder
        val contentLimit = resolvedContentLimit(availableOuterWidth)
        val naturalWidth = width ?: maxOf(measureText(ctx, display), minContentWidth)
        val contentWidth = contentLimit?.let { minOf(it, naturalWidth) } ?: naturalWidth
        val contentHeight = height ?: lineHeight
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    private fun resolvedContentLimit(availableOuterWidth: Int?): Int? {
        val explicit = width
        val extras = margin.horizontal + padding.horizontal + border.horizontal
        val constrainedByParent = availableOuterWidth?.let { (it - extras).coerceAtLeast(0) }
        return when {
            explicit != null && constrainedByParent != null -> minOf(explicit, constrainedByParent)
            explicit != null -> explicit
            else -> constrainedByParent
        }
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        val lineHeight = resolveFontSize(ctx)
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
        val textY = innerY + (innerHeight - lineHeight) / 2
        lastMeasureText = { value -> measureText(ctx, value) }
        editState.clampToLength(text.length)

        if (innerWidth > 0 && innerHeight > 0) {
            out.add(RenderCommand.PushClip(innerX, innerY, innerWidth, innerHeight))
        }

        if (!showPlaceholder && focused && editState.hasSelection()) {
            val start = editState.selectionStart().coerceIn(0, drawText.length)
            val end = editState.selectionEnd().coerceIn(0, drawText.length)
            if (end > start) {
                val startX = innerX + measureText(ctx, drawText.substring(0, start))
                val endX = innerX + measureText(ctx, drawText.substring(0, end))
                val width = (endX - startX).coerceAtLeast(1)
                out.add(RenderCommand.DrawRect(startX, textY, width, lineHeight, selectionColor))
            }
        }

        if (drawText.isNotEmpty()) {
            val color = if (showPlaceholder) placeholderColor else textColor
            out.add(drawTextCommand(ctx, drawText, innerX, textY, color))
        }

        if (!showPlaceholder && focused && !styleDisabled && editState.isCaretVisible(caretBlinkPeriodMs)) {
            val caretBaseText = drawText.substring(0, editState.caretIndex.coerceIn(0, drawText.length))
            val caretX = innerX + measureText(ctx, caretBaseText)
            out.add(RenderCommand.DrawRect(caretX, textY, 1, lineHeight, textColor))
        }

        if (innerWidth > 0 && innerHeight > 0) {
            out.add(RenderCommand.PopClip)
        }

    }

    fun shouldCaptureTextSelectionDrag(mouseX: Int, mouseY: Int): Boolean {
        if (styleDisabled) return false
        return containsGlobalPoint(mouseX, mouseY)
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
        TextEditOps.moveCaretWithSelection(editState, next, text.length, extend)
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
        val (start, end) = TextEditOps.selectionOrCaretBounds(editState)
        if (start == end && insert.isEmpty()) return false
        return replaceRange(start, end, insert, recordUndo)
    }

    private fun replaceRange(start: Int, end: Int, insert: String, recordUndo: Boolean = false): Boolean {
        val safeStart = start.coerceIn(0, text.length)
        val safeEnd = end.coerceIn(safeStart, text.length)
        val previous = currentEventValue()
        val next = TextEditOps.replaceRange(text, safeStart, safeEnd, insert)
        if (!canAcceptText(next)) return false
        if (next == text) return false
        history.clearRedo()
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
        return TextEditShortcutDispatcher.dispatch(
            event,
            TextShortcutCallbacks(
                canCopy = allowClipboardCopy(),
                canCut = allowClipboardCut(),
                canPaste = allowClipboardPaste(),
                hasSelection = { editState.hasSelection() },
                selectAll = {
                    TextEditOps.selectAll(editState, text.length)
                },
                copySelection = { ClipboardBridge.writeText(selectedText()) },
                cutSelection = {
                    ClipboardBridge.writeText(selectedText())
                    replaceSelectionWith("", recordUndo = true)
                },
                normalizePaste = { raw -> sanitizePastedText(raw) },
                pasteSelection = { paste -> replaceSelectionWith(paste, recordUndo = true) },
                undo = { undoLastEdit() },
                redo = { redoLastUndo() },
                beforeHandled = { resetTypingUndoGroup() },
                afterHandled = { action ->
                    editState.resetBlinkClock()
                    if (action != TextShortcutAction.COPY) {
                        persistState()
                    }
                }
            )
        )
    }

    private fun pushUndoSnapshot() {
        history.pushUndo(
            UndoSnapshot(
                text = text,
                caretIndex = editState.caretIndex,
                selectionAnchor = editState.selectionAnchor
            )
        )
    }

    private fun currentSnapshot(): UndoSnapshot {
        return UndoSnapshot(
            text = text,
            caretIndex = editState.caretIndex,
            selectionAnchor = editState.selectionAnchor
        )
    }

    private fun undoLastEdit(): Boolean {
        val snapshot = history.undo(currentSnapshot()) ?: return false
        val previous = currentEventValue()
        applyText(snapshot.text)
        editState.caretIndex = snapshot.caretIndex.coerceIn(0, text.length)
        editState.selectionAnchor = snapshot.selectionAnchor?.coerceIn(0, text.length)
        editState.clampToLength(text.length)
        notifyUserValueChanged(previous)
        return true
    }

    private fun redoLastUndo(): Boolean {
        val snapshot = history.redo(currentSnapshot()) ?: return false
        val previous = currentEventValue()
        applyText(snapshot.text)
        editState.caretIndex = snapshot.caretIndex.coerceIn(0, text.length)
        editState.selectionAnchor = snapshot.selectionAnchor?.coerceIn(0, text.length)
        editState.clampToLength(text.length)
        notifyUserValueChanged(previous)
        return true
    }

    private fun shouldRecordTypingUndo(ch: Char): Boolean {
        return typingUndoGrouping.shouldRecord(ch, editState.hasSelection())
    }

    private fun resetTypingUndoGroup() {
        typingUndoGrouping.reset()
    }

    private fun selectedText(): String {
        return TextEditOps.selectedText(text, editState)
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
        return TextEditOps.dragIdentity(key, this)
    }

    private fun isActiveSelectionDragTarget(): Boolean {
        val active = activeSelectionDragIdentity ?: return false
        return active == dragIdentity()
    }

    private fun persistState() {
        markRenderCommandsDirty()
        persistedByKey.save(
            key,
            PersistedState(
                caretIndex = editState.caretIndex,
                selectionAnchor = editState.selectionAnchor,
                undoHistory = history.undoHistory(),
                redoHistory = history.redoHistory()
            )
        )
    }

    private fun restorePersistedState() {
        val persisted = persistedByKey.load(key) ?: return
        editState.caretIndex = persisted.caretIndex.coerceIn(0, text.length)
        editState.selectionAnchor = persisted.selectionAnchor?.coerceIn(0, text.length)
        history.restore(persisted.undoHistory, persisted.redoHistory)
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

    internal fun syncEditablePropsFrom(template: SingleLineInputNode) {
        placeholder = template.placeholder
        allowedChars = template.allowedChars
        minLength = template.minLength
        maxLength = template.maxLength
        textColor = template.textColor
        placeholderColor = template.placeholderColor
        backgroundColor = template.backgroundColor
        focusedBackgroundColor = template.focusedBackgroundColor
        minContentWidth = template.minContentWidth
        selectionColor = template.selectionColor
        caretBlinkPeriodMs = template.caretBlinkPeriodMs
        if (text != template.text) {
            text = template.text
            editState.clampToLength(text.length)
            editState.resetBlinkClock()
        }
        markRenderCommandsDirty()
    }

    override fun volatileRenderCommandsSignature(nowMs: Long): Long {
        val focused = FocusManager.isFocused(this) && !styleDisabled
        val caretVisible = focused && editState.isCaretVisible(caretBlinkPeriodMs, nowMs)
        var hash = 17L
        hash = 31L * hash + text.hashCode().toLong()
        hash = 31L * hash + editState.caretIndex.toLong()
        hash = 31L * hash + (editState.selectionAnchor ?: -1).toLong()
        hash = 31L * hash + if (focused) 1L else 0L
        hash = 31L * hash + if (caretVisible) 1L else 0L
        return hash
    }
}
