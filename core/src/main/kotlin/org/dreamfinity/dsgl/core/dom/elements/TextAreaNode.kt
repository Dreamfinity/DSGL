package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
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
import org.dreamfinity.dsgl.core.event.MouseWheelEvent
import org.dreamfinity.dsgl.core.event.postChange
import org.dreamfinity.dsgl.core.event.postInput
import org.dreamfinity.dsgl.core.input.ClipboardBridge
import org.dreamfinity.dsgl.core.render.RenderCommand
import java.util.ArrayDeque

/**
 * Multiline text area node.
 */
class TextAreaNode(
    var text: String = "",
    var placeholder: String = "",
    key: Any? = null
) : DOMNode(key) {
    companion object {
        private data class UndoSnapshot(
            val text: String,
            val caretIndex: Int,
            val selectionAnchor: Int?,
            val scrollY: Int,
            val preferredColumn: Int?
        )

        private data class PersistedState(
            val scrollY: Int,
            val caretIndex: Int,
            val preferredColumn: Int?,
            val selectionAnchor: Int?,
            val undoHistory: List<UndoSnapshot>,
            val redoHistory: List<UndoSnapshot>
        )

        private val persistedByKey: MutableMap<Any, PersistedState> = HashMap()
        private var activeScrollbarDragIdentity: Any? = null
        private var activeSelectionDragIdentity: Any? = null

        fun clearActiveDrag() {
            activeScrollbarDragIdentity = null
            activeSelectionDragIdentity = null
        }
    }

    override val styleType: String = "input"
    override val focusable: Boolean = true
    var textColor: Int = DsglColors.TEXT
    var placeholderColor: Int = 0xFF8A8A8A.toInt()
    var backgroundColor: Int = 0xFF2E2E33.toInt()
    var focusedBackgroundColor: Int = 0xFF3A3A40.toInt()
    var minContentWidth: Int = 200
    var minContentHeight: Int = 60
    var selectionColor: Int = 0x664A90E2
    var caretBlinkPeriodMs: Long = 500L

    private val editState: TextEditState = TextEditState(caretIndex = text.length)
    private val undoStack: ArrayDeque<UndoSnapshot> = ArrayDeque()
    private val redoStack: ArrayDeque<UndoSnapshot> = ArrayDeque()
    private val undoLimit: Int = 32
    private var typingWordUndoOpen: Boolean = false
    private var valueAtFocusStart: String = text
    private var dirtySinceFocus: Boolean = false
    private var preferredColumn: Int? = null
    private var lastLineHeight: Int = 9
    private var lastVisibleHeight: Int = minContentHeight
    private var hasVerticalOverflow: Boolean = false
    private var lastMaxScroll: Int = 0
    private var scrollbarTrackRect: Rect? = null
    private var scrollbarThumbRect: Rect? = null
    private var scrollbarWidth: Int = 6
    private var scrollbarGap: Int = 1
    private var scrollbarTrackColor: Int = 0x55303030
    private var scrollbarThumbColor: Int = 0xAA9AA5B1.toInt()
    private var scrollbarThumbFocusedColor: Int = 0xCCB7C3D1.toInt()
    private var scrollbarDragAnchorY: Int = 0
    private var lastMeasureText: ((String) -> Int)? = null

    init {
        restorePersistedState()
    }

    init {
        EventBus.run {
            this@TextAreaNode.addEventListener(Events.MOUSEDOWN) { event: MouseDownEvent ->
                if (this@TextAreaNode.styleDisabled) return@addEventListener
                if (event.mouseButton != MouseButton.LEFT) return@addEventListener
                if (handleScrollbarMouseDown(event.mouseX, event.mouseY)) {
                    event.cancelled = true
                    return@addEventListener
                }
                if (!this@TextAreaNode.bounds.contains(event.mouseX, event.mouseY)) return@addEventListener
                handleTextPointerDown(event.mouseX, event.mouseY)
            }
            this@TextAreaNode.addEventListener(Events.DRAG) { event: MouseDragEvent ->
                if (this@TextAreaNode.styleDisabled) return@addEventListener
                val currentX = event.lastMouseX + event.dx
                val currentY = event.lastMouseY + event.dy
                when {
                    isActiveScrollbarDragTarget() -> {
                        updateScrollbarFromDrag(currentY)
                        persistState()
                        event.cancelled = true
                    }

                    isActiveSelectionDragTarget() -> {
                        updateSelectionFromPointerDrag(currentX, currentY)
                        persistState()
                        event.cancelled = true
                    }
                }
            }
            this@TextAreaNode.addEventListener(Events.MOUSEUP) { event: MouseUpEvent ->
                if (event.mouseButton != MouseButton.LEFT) return@addEventListener
                var handled = false
                if (isActiveScrollbarDragTarget()) {
                    activeScrollbarDragIdentity = null
                    handled = true
                }
                if (isActiveSelectionDragTarget()) {
                    activeSelectionDragIdentity = null
                    if (!editState.hasSelection()) {
                        editState.clearSelection()
                    }
                    handled = true
                }
                if (handled) {
                    editState.resetBlinkClock()
                    persistState()
                    event.cancelled = true
                }
            }
            this@TextAreaNode.addEventListener(Events.WHEEL) { event: MouseWheelEvent ->
                if (this@TextAreaNode.styleDisabled) return@addEventListener
                val hovered = this@TextAreaNode.hovered(event)
                val focused = FocusManager.isFocused(this@TextAreaNode)
                if (!hovered && !focused) return@addEventListener
                if (event.dWheel > 0) {
                    scrollByPixels(-lastLineHeight * 3)
                } else {
                    scrollByPixels(lastLineHeight * 3)
                }
                event.cancelled = true
            }
            this@TextAreaNode.addEventListener(Events.KEYDOWN) { event: KeyboardKeyDownEvent ->
                if (this@TextAreaNode.styleDisabled) return@addEventListener
                if (!FocusManager.isFocused(this@TextAreaNode)) return@addEventListener
                handleKey(event)
            }
            this@TextAreaNode.addEventListener(Events.FOCUS) { _: FocusGainEvent ->
                valueAtFocusStart = text
                dirtySinceFocus = false
                resetTypingUndoGroup()
                editState.resetBlinkClock()
            }
            this@TextAreaNode.addEventListener(Events.BLUR) { _: FocusLoseEvent ->
                resetTypingUndoGroup()
                commitCurrentValueChange()
                persistState()
            }
        }
    }

    override fun measure(ctx: UiMeasureContext): Size {
        lastMeasureText = { value -> ctx.measureText(value) }
        lastLineHeight = ctx.fontHeight.coerceAtLeast(1)
        val display = if (text.isNotEmpty()) text else placeholder
        val lines = splitLines(display)
        val maxLineWidth = lines.maxOfOrNull { ctx.measureText(it) } ?: 0
        val contentWidth = width ?: maxOf(maxLineWidth, minContentWidth)
        val contentHeight = height ?: maxOf(lines.size * ctx.fontHeight, minContentHeight)
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

        val innerX = contentX()
        val innerY = contentY()
        val innerWidth = contentWidth()
        val innerHeight = contentHeight()
        lastMeasureText = { value -> ctx.measureText(value) }
        lastLineHeight = ctx.fontHeight.coerceAtLeast(1)
        lastVisibleHeight = innerHeight.coerceAtLeast(lastLineHeight)
        editState.clampToLength(text.length)
        clampScroll()

        val showPlaceholder = text.isEmpty() && !focused && placeholder.isNotEmpty()
        val drawText = if (showPlaceholder) placeholder else text
        val color = if (showPlaceholder) placeholderColor else textColor
        val lines = splitLines(drawText)
        val totalContentHeight = lines.size * lastLineHeight
        val maxScroll = (totalContentHeight - innerHeight).coerceAtLeast(0)
        hasVerticalOverflow = !showPlaceholder && maxScroll > 0 && innerHeight > 0
        lastMaxScroll = if (hasVerticalOverflow) maxScroll else 0
        val textInnerWidth = if (hasVerticalOverflow) {
            (innerWidth - scrollbarWidth - scrollbarGap).coerceAtLeast(0)
        } else {
            innerWidth
        }
        val effectiveScroll = if (showPlaceholder) 0 else editState.scrollY.coerceIn(0, maxScroll)
        val firstVisibleLine = (effectiveScroll / lastLineHeight).coerceAtLeast(0)
        val lastVisibleLine = ((effectiveScroll + innerHeight) / lastLineHeight + 1)
            .coerceIn(0, lines.size - 1)

        if (textInnerWidth > 0 && innerHeight > 0) {
            out.add(RenderCommand.PushClip(innerX, innerY, textInnerWidth, innerHeight))
        }

        if (!showPlaceholder && focused && editState.hasSelection()) {
            drawSelection(out, lines, firstVisibleLine, lastVisibleLine, innerX, innerY, effectiveScroll)
        }

        for (lineIndex in firstVisibleLine..lastVisibleLine) {
            val line = lines[lineIndex]
            val lineY = innerY - effectiveScroll + lineIndex * lastLineHeight
            out.add(RenderCommand.DrawText(line, innerX, lineY, color))
        }

        if (!showPlaceholder && focused && !styleDisabled && editState.isCaretVisible(caretBlinkPeriodMs)) {
            val caret = caretLineAndColumn(text, editState.caretIndex)
            val caretLineText = lines[caret.first]
            val caretPrefix = caretLineText.substring(0, caret.second.coerceIn(0, caretLineText.length))
            val caretX = innerX + ctx.measureText(caretPrefix)
            val caretY = innerY - effectiveScroll + caret.first * lastLineHeight
            out.add(RenderCommand.DrawRect(caretX, caretY, 1, lastLineHeight, textColor))
        }

        if (textInnerWidth > 0 && innerHeight > 0) {
            out.add(RenderCommand.PopClip)
        }

        if (hasVerticalOverflow) {
            drawScrollbar(out, innerX, innerY, innerWidth, innerHeight, focused)
        } else {
            scrollbarTrackRect = null
            scrollbarThumbRect = null
        }

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

    fun shouldCaptureScrollbarDrag(mouseX: Int, mouseY: Int): Boolean {
        if (styleDisabled || !hasVerticalOverflow || lastMaxScroll <= 0) return false
        return isInScrollbarTrack(mouseX, mouseY)
    }

    fun shouldCaptureTextSelectionDrag(mouseX: Int, mouseY: Int): Boolean {
        if (styleDisabled) return false
        return bounds.contains(mouseX, mouseY)
    }

    fun shouldCaptureAnyDrag(mouseX: Int, mouseY: Int): Boolean {
        return shouldCaptureScrollbarDrag(mouseX, mouseY) || shouldCaptureTextSelectionDrag(mouseX, mouseY)
    }

    private fun handleKey(event: KeyboardKeyDownEvent) {
        editState.clampToLength(text.length)
        if (handleClipboardShortcut(event)) return

        when (event.keyCode) {
            KeyCodes.BACKSPACE -> deleteBeforeCaret()
            KeyCodes.DELETE -> deleteAfterCaret()
            KeyCodes.ENTER -> {
                resetTypingUndoGroup()
                replaceSelectionWith("\n", recordUndo = true)
            }
            KeyCodes.LEFT -> moveCaretHorizontal(-1, KeyModifiers.shiftDown)
            KeyCodes.RIGHT -> moveCaretHorizontal(1, KeyModifiers.shiftDown)
            KeyCodes.UP -> moveCaretVertical(-1, KeyModifiers.shiftDown)
            KeyCodes.DOWN -> moveCaretVertical(1, KeyModifiers.shiftDown)
            KeyCodes.HOME -> moveCaretToLineBoundary(start = true, extend = KeyModifiers.shiftDown)
            KeyCodes.END -> moveCaretToLineBoundary(start = false, extend = KeyModifiers.shiftDown)
            KeyCodes.PAGE_UP -> moveCaretByPage(-1, KeyModifiers.shiftDown)
            KeyCodes.PAGE_DOWN -> moveCaretByPage(1, KeyModifiers.shiftDown)
            else -> {
                var ch = event.keyChar
                if (!isPrintable(ch)) return
                ch = KeyInput.applyShift(ch, KeyModifiers.shiftDown)
                val recordUndo = shouldRecordTypingUndo(ch)
                replaceSelectionWith(ch.toString(), recordUndo = recordUndo)
            }
        }
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
                if (editState.hasSelection()) {
                    ClipboardBridge.writeText(selectedText())
                }
                editState.resetBlinkClock()
                return true
            }

            KeyCodes.X -> {
                resetTypingUndoGroup()
                if (editState.hasSelection()) {
                    ClipboardBridge.writeText(selectedText())
                    replaceSelectionWith("", recordUndo = true)
                }
                editState.resetBlinkClock()
                persistState()
                return true
            }

            KeyCodes.V -> {
                resetTypingUndoGroup()
                val raw = ClipboardBridge.readText()
                if (raw.isNotEmpty()) {
                    val normalized = raw.replace("\r\n", "\n").replace("\r", "\n")
                    replaceSelectionWith(normalized, recordUndo = true)
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
                selectionAnchor = editState.selectionAnchor,
                scrollY = editState.scrollY,
                preferredColumn = preferredColumn
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
            selectionAnchor = editState.selectionAnchor,
            scrollY = editState.scrollY,
            preferredColumn = preferredColumn
        )
    }

    private fun undoLastEdit(): Boolean {
        val snapshot = undoStack.pollLast() ?: return false
        pushRedoSnapshot(currentSnapshot())
        val previous = text
        text = snapshot.text
        editState.caretIndex = snapshot.caretIndex.coerceIn(0, text.length)
        editState.selectionAnchor = snapshot.selectionAnchor?.coerceIn(0, text.length)
        editState.scrollY = snapshot.scrollY.coerceAtLeast(0)
        preferredColumn = snapshot.preferredColumn
        clampScroll()
        dirtySinceFocus = true
        persistState()
        if (text != previous) {
            postInput(this, text, text)
        }
        return true
    }

    private fun redoLastUndo(): Boolean {
        val snapshot = redoStack.pollLast() ?: return false
        pushUndoSnapshot()
        val previous = text
        text = snapshot.text
        editState.caretIndex = snapshot.caretIndex.coerceIn(0, text.length)
        editState.selectionAnchor = snapshot.selectionAnchor?.coerceIn(0, text.length)
        editState.scrollY = snapshot.scrollY.coerceAtLeast(0)
        preferredColumn = snapshot.preferredColumn
        clampScroll()
        dirtySinceFocus = true
        persistState()
        if (text != previous) {
            postInput(this, text, text)
        }
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

    private fun handleTextPointerDown(mouseX: Int, mouseY: Int) {
        resetTypingUndoGroup()
        val preservedScrollY = editState.scrollY
        val targetCaret = caretIndexFromClick(mouseX, mouseY, preservedScrollY)
        editState.selectionAnchor = targetCaret
        editState.caretIndex = targetCaret
        preferredColumn = null
        if (!FocusManager.isFocused(this)) {
            FocusManager.requestFocus(this)
        }
        editState.scrollY = preservedScrollY.coerceIn(0, maxScrollFor(text))
        ensureCaretVisible()
        editState.resetBlinkClock()
        activeSelectionDragIdentity = dragIdentity()
        persistState()
    }

    private fun updateSelectionFromPointerDrag(mouseX: Int, mouseY: Int) {
        resetTypingUndoGroup()
        if (editState.selectionAnchor == null) {
            editState.selectionAnchor = editState.caretIndex
        }
        editState.caretIndex = caretIndexFromClick(mouseX, mouseY, editState.scrollY)
        preferredColumn = null
        ensureCaretVisible()
        editState.resetBlinkClock()
        persistState()
    }

    private fun moveCaretHorizontal(delta: Int, extend: Boolean) {
        resetTypingUndoGroup()
        if (delta == 0) return
        if (!extend && editState.hasSelection()) {
            editState.caretIndex = if (delta < 0) editState.selectionStart() else editState.selectionEnd()
            editState.clearSelection()
        } else {
            moveCaretTo((editState.caretIndex + delta).coerceIn(0, text.length), extend)
        }
        preferredColumn = null
        ensureCaretVisible()
        editState.resetBlinkClock()
        persistState()
    }

    private fun moveCaretVertical(deltaLines: Int, extend: Boolean) {
        resetTypingUndoGroup()
        if (deltaLines == 0) return
        val starts = lineStarts(text)
        val current = caretLineAndColumn(text, editState.caretIndex, starts)
        val targetLine = (current.first + deltaLines).coerceIn(0, starts.lastIndex)
        val desiredColumn = preferredColumn ?: current.second
        val targetLineEnd = lineEndIndex(starts, targetLine, text.length)
        val nextIndex = (starts[targetLine] + desiredColumn).coerceAtMost(targetLineEnd)
        moveCaretTo(nextIndex, extend)
        preferredColumn = desiredColumn
        ensureCaretVisible()
        editState.resetBlinkClock()
        persistState()
    }

    private fun moveCaretByPage(direction: Int, extend: Boolean) {
        resetTypingUndoGroup()
        val visibleLines = (lastVisibleHeight / lastLineHeight).coerceAtLeast(1)
        val delta = (visibleLines - 1).coerceAtLeast(1) * direction
        moveCaretVertical(delta, extend)
    }

    private fun moveCaretToLineBoundary(start: Boolean, extend: Boolean) {
        resetTypingUndoGroup()
        val starts = lineStarts(text)
        val current = caretLineAndColumn(text, editState.caretIndex, starts)
        val next = if (start) {
            starts[current.first]
        } else {
            lineEndIndex(starts, current.first, text.length)
        }
        moveCaretTo(next, extend)
        preferredColumn = null
        ensureCaretVisible()
        editState.resetBlinkClock()
        persistState()
    }

    private fun moveCaretTo(next: Int, extend: Boolean) {
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
        val start = editState.caretIndex.coerceIn(0, text.length)
        if (start >= text.length || text.isEmpty()) return
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
        val previous = text
        val next = text.substring(0, safeStart) + insert + text.substring(safeEnd)
        if (next == text) return false
        clearRedoHistory()
        if (recordUndo && (safeStart != safeEnd || insert.isNotEmpty())) {
            pushUndoSnapshot()
        }
        text = next
        editState.caretIndex = (safeStart + insert.length).coerceIn(0, text.length)
        editState.clearSelection()
        preferredColumn = null
        onUserTextChanged(previous)
        return true
    }

    private fun onUserTextChanged(previous: String) {
        editState.clampToLength(text.length)
        dirtySinceFocus = true
        ensureCaretVisible()
        editState.resetBlinkClock()
        persistState()
        if (text != previous) {
            postInput(this, text, text)
        }
    }

    private fun commitCurrentValueChange() {
        if (!dirtySinceFocus) return
        if (text == valueAtFocusStart) {
            dirtySinceFocus = false
            return
        }
        postChange(this, text, text)
        valueAtFocusStart = text
        dirtySinceFocus = false
    }

    private fun ensureCaretVisible() {
        val line = caretLineAndColumn(text, editState.caretIndex).first
        val caretTop = line * lastLineHeight
        val caretBottom = caretTop + lastLineHeight
        val visibleHeight = lastVisibleHeight.coerceAtLeast(lastLineHeight)
        if (caretTop < editState.scrollY) {
            editState.scrollY = caretTop
        } else if (caretBottom > editState.scrollY + visibleHeight) {
            editState.scrollY = caretBottom - visibleHeight
        }
        clampScroll()
    }

    private fun scrollByPixels(delta: Int) {
        if (delta == 0) return
        editState.scrollY += delta
        clampScroll()
        persistState()
    }

    private fun clampScroll() {
        val maxScroll = maxScrollFor(text)
        editState.scrollY = editState.scrollY.coerceIn(0, maxScroll)
    }

    private fun maxScrollFor(source: String): Int {
        val visibleHeight = lastVisibleHeight.coerceAtLeast(lastLineHeight)
        val totalHeight = splitLines(source).size * lastLineHeight
        return (totalHeight - visibleHeight).coerceAtLeast(0)
    }

    private fun drawScrollbar(
        out: MutableList<RenderCommand>,
        innerX: Int,
        innerY: Int,
        innerWidth: Int,
        innerHeight: Int,
        focused: Boolean
    ) {
        val trackX = innerX + innerWidth - scrollbarWidth
        val trackWidth = scrollbarWidth.coerceAtLeast(1)
        val trackHeight = innerHeight.coerceAtLeast(1)
        val trackRect = Rect(trackX, innerY, trackWidth, trackHeight)
        scrollbarTrackRect = trackRect
        out.add(RenderCommand.DrawRect(trackRect.x, trackRect.y, trackRect.width, trackRect.height, scrollbarTrackColor))

        val thumbHeight = computeThumbHeight(trackHeight)
        val thumbTravel = (trackHeight - thumbHeight).coerceAtLeast(0)
        val thumbOffset = if (lastMaxScroll <= 0 || thumbTravel <= 0) {
            0
        } else {
            ((editState.scrollY.toDouble() / lastMaxScroll.toDouble()) * thumbTravel.toDouble()).toInt()
        }
        val thumbY = innerY + thumbOffset
        val thumbRect = Rect(trackX, thumbY, trackWidth, thumbHeight)
        scrollbarThumbRect = thumbRect
        val thumbColor = if (focused) scrollbarThumbFocusedColor else scrollbarThumbColor
        out.add(RenderCommand.DrawRect(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height, thumbColor))
    }

    private fun drawSelection(
        out: MutableList<RenderCommand>,
        lines: List<String>,
        firstVisibleLine: Int,
        lastVisibleLine: Int,
        innerX: Int,
        innerY: Int,
        effectiveScroll: Int
    ) {
        if (!editState.hasSelection()) return
        val startIndex = editState.selectionStart().coerceIn(0, text.length)
        val endIndex = editState.selectionEnd().coerceIn(0, text.length)
        if (endIndex <= startIndex) return

        val start = caretLineAndColumn(text, startIndex)
        val end = caretLineAndColumn(text, endIndex)
        val minLine = maxOf(start.first, firstVisibleLine)
        val maxLine = minOf(end.first, lastVisibleLine)
        if (maxLine < minLine) return

        val measure = lastMeasureText ?: { value: String -> value.length * 6 }
        for (lineIndex in minLine..maxLine) {
            val line = lines[lineIndex]
            val startCol = when {
                lineIndex == start.first -> start.second
                else -> 0
            }.coerceIn(0, line.length)
            val endCol = when {
                lineIndex == end.first -> end.second
                else -> line.length
            }.coerceIn(0, line.length)
            if (endCol <= startCol) continue

            val prefixStart = line.substring(0, startCol)
            val prefixEnd = line.substring(0, endCol)
            val x1 = innerX + measure(prefixStart)
            val x2 = innerX + measure(prefixEnd)
            val width = (x2 - x1).coerceAtLeast(1)
            val y = innerY - effectiveScroll + lineIndex * lastLineHeight
            out.add(RenderCommand.DrawRect(x1, y, width, lastLineHeight, selectionColor))
        }
    }

    private fun computeThumbHeight(trackHeight: Int): Int {
        if (lastMaxScroll <= 0) return trackHeight.coerceAtLeast(8)
        val totalContentHeight = (lastVisibleHeight + lastMaxScroll).coerceAtLeast(lastVisibleHeight)
        val ratio = lastVisibleHeight.toDouble() / totalContentHeight.toDouble()
        return (trackHeight * ratio).toInt().coerceIn(8, trackHeight.coerceAtLeast(8))
    }

    private fun handleScrollbarMouseDown(mouseX: Int, mouseY: Int): Boolean {
        if (!shouldCaptureScrollbarDrag(mouseX, mouseY)) return false
        resetTypingUndoGroup()
        FocusManager.requestFocus(this)
        activeScrollbarDragIdentity = dragIdentity()
        val thumb = scrollbarThumbRect
        scrollbarDragAnchorY = if (thumb != null && thumb.contains(mouseX, mouseY)) {
            (mouseY - thumb.y).coerceIn(0, thumb.height.coerceAtLeast(1))
        } else {
            computeThumbHeight(scrollbarTrackRect?.height ?: 0) / 2
        }
        updateScrollbarFromDrag(mouseY)
        editState.resetBlinkClock()
        persistState()
        return true
    }

    private fun updateScrollbarFromDrag(mouseY: Int) {
        val track = scrollbarTrackRect ?: return
        if (lastMaxScroll <= 0) {
            editState.scrollY = 0
            return
        }
        val thumbHeight = scrollbarThumbRect?.height ?: computeThumbHeight(track.height)
        val thumbTravel = (track.height - thumbHeight).coerceAtLeast(0)
        if (thumbTravel <= 0) {
            editState.scrollY = 0
            return
        }
        val desiredTop = (mouseY - track.y - scrollbarDragAnchorY).coerceIn(0, thumbTravel)
        editState.scrollY = ((desiredTop.toDouble() / thumbTravel.toDouble()) * lastMaxScroll.toDouble()).toInt()
            .coerceIn(0, lastMaxScroll)
    }

    private fun caretIndexFromClick(mouseX: Int, mouseY: Int, scrollOffsetY: Int): Int {
        val lines = splitLines(text)
        val starts = lineStarts(text)
        if (lines.isEmpty() || starts.isEmpty()) return 0

        val localY = mouseY - contentY() + scrollOffsetY
        val lineIndex = (localY / lastLineHeight).coerceIn(0, lines.lastIndex)
        val lineText = lines[lineIndex]
        val localX = (mouseX - contentX()).coerceAtLeast(0)
        val column = caretColumnFromX(lineText, localX)
        val lineEnd = lineEndIndex(starts, lineIndex, text.length)
        return (starts[lineIndex] + column).coerceIn(starts[lineIndex], lineEnd)
    }

    private fun caretColumnFromX(lineText: String, localX: Int): Int {
        if (lineText.isEmpty()) return 0
        val measure = lastMeasureText ?: { value: String -> value.length * 6 }
        if (localX <= 0) return 0

        var previousWidth = 0
        var column = 0
        while (column < lineText.length) {
            val nextColumn = column + 1
            val nextWidth = measure(lineText.substring(0, nextColumn))
            val midpoint = previousWidth + ((nextWidth - previousWidth) / 2)
            if (localX < midpoint) {
                return column
            }
            previousWidth = nextWidth
            column = nextColumn
        }
        return lineText.length
    }

    private fun selectedText(): String {
        if (!editState.hasSelection()) return ""
        val start = editState.selectionStart().coerceIn(0, text.length)
        val end = editState.selectionEnd().coerceIn(0, text.length)
        if (end <= start) return ""
        return text.substring(start, end)
    }

    private fun isPrintable(ch: Char): Boolean {
        return ch >= ' ' && ch.code != 127
    }

    private fun dragIdentity(): Any {
        return key ?: this
    }

    private fun isActiveScrollbarDragTarget(): Boolean {
        val active = activeScrollbarDragIdentity ?: return false
        return active == dragIdentity()
    }

    private fun isActiveSelectionDragTarget(): Boolean {
        val active = activeSelectionDragIdentity ?: return false
        return active == dragIdentity()
    }

    private fun isInScrollbarTrack(mouseX: Int, mouseY: Int): Boolean {
        val track = scrollbarTrackRect ?: return false
        return track.contains(mouseX, mouseY)
    }

    private fun persistState() {
        val identity = key ?: return
        persistedByKey[identity] = PersistedState(
            scrollY = editState.scrollY,
            caretIndex = editState.caretIndex,
            preferredColumn = preferredColumn,
            selectionAnchor = editState.selectionAnchor,
            undoHistory = undoStack.toList(),
            redoHistory = redoStack.toList()
        )
    }

    private fun restorePersistedState() {
        val identity = key ?: return
        val persisted = persistedByKey[identity] ?: return
        editState.scrollY = persisted.scrollY.coerceAtLeast(0)
        editState.caretIndex = persisted.caretIndex.coerceIn(0, text.length)
        editState.selectionAnchor = persisted.selectionAnchor?.coerceIn(0, text.length)
        preferredColumn = persisted.preferredColumn
        undoStack.clear()
        persisted.undoHistory.takeLast(undoLimit).forEach { snapshot -> undoStack.addLast(snapshot) }
        redoStack.clear()
        persisted.redoHistory.takeLast(undoLimit).forEach { snapshot -> redoStack.addLast(snapshot) }
    }

    private fun splitLines(source: String): List<String> {
        if (source.isEmpty()) return listOf("")
        val out = ArrayList<String>()
        var start = 0
        for (i in source.indices) {
            if (source[i] == '\n') {
                out.add(source.substring(start, i))
                start = i + 1
            }
        }
        out.add(source.substring(start, source.length))
        return out
    }

    private fun lineStarts(source: String): IntArray {
        if (source.isEmpty()) return intArrayOf(0)
        val starts = ArrayList<Int>()
        starts.add(0)
        for (i in source.indices) {
            if (source[i] == '\n') {
                starts.add(i + 1)
            }
        }
        return starts.toIntArray()
    }

    private fun lineEndIndex(starts: IntArray, line: Int, textLength: Int): Int {
        return if (line + 1 < starts.size) {
            (starts[line + 1] - 1).coerceAtLeast(starts[line])
        } else {
            textLength
        }
    }

    private fun caretLineAndColumn(source: String, caret: Int, starts: IntArray = lineStarts(source)): Pair<Int, Int> {
        if (starts.isEmpty()) return 0 to 0
        val safeCaret = caret.coerceIn(0, source.length)
        var line = 0
        while (line + 1 < starts.size && starts[line + 1] <= safeCaret) {
            line++
        }
        val lineStart = starts[line]
        val lineEnd = lineEndIndex(starts, line, source.length)
        val column = (safeCaret - lineStart).coerceIn(0, (lineEnd - lineStart).coerceAtLeast(0))
        return line to column
    }
}
