package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.support.*
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.input.ClipboardBridge
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.TextWrap

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

        private val persistedByKey: KeyedStateStore<PersistedState> = KeyedStateStore()
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
    private val undoLimit: Int = 32
    private val history: UndoRedoHistory<UndoSnapshot> = UndoRedoHistory(undoLimit)
    private val typingUndoGrouping: WordUndoGrouping = WordUndoGrouping()
    private val changeTracker: TextChangeTracker = TextChangeTracker(text)
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
    private var lastTextLayout: TextLayoutEngine.Layout = TextLayoutEngine.layout("", null, TextWrap.Wrap, 1) { 0 }
    private var lastTextLayoutWidth: Int? = null

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
                changeTracker.onFocus(text)
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
        val contentWidth = width ?: maxOf(
            layoutForText(display, null, ctx.fontHeight, ctx::measureText).maxLineWidth,
            minContentWidth
        )
        val textLayout = layoutForText(
            source = display,
            contentWidth = if (textWrap == TextWrap.Wrap) contentWidth else null,
            fontHeight = ctx.fontHeight,
            measureText = ctx::measureText
        )
        val contentHeight = height ?: maxOf(textLayout.totalHeight, minContentHeight)
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
        var textInnerWidth = innerWidth.coerceAtLeast(0)
        var textLayout = layoutForText(text, textInnerWidth, ctx.fontHeight, ctx::measureText)
        var maxScroll = (textLayout.totalHeight - innerHeight).coerceAtLeast(0)
        hasVerticalOverflow = maxScroll > 0 && innerHeight > 0
        if (hasVerticalOverflow) {
            val constrainedWidth = (innerWidth - scrollbarWidth - scrollbarGap).coerceAtLeast(0)
            if (constrainedWidth != textInnerWidth) {
                textInnerWidth = constrainedWidth
                textLayout = layoutForText(text, textInnerWidth, ctx.fontHeight, ctx::measureText)
                maxScroll = (textLayout.totalHeight - innerHeight).coerceAtLeast(0)
            }
        }
        lastTextLayout = textLayout
        lastTextLayoutWidth = textInnerWidth
        lastMaxScroll = maxScroll
        clampScroll()

        val showPlaceholder = text.isEmpty() && !focused && placeholder.isNotEmpty()
        val drawLayout = if (showPlaceholder) {
            layoutForText(placeholder, textInnerWidth, ctx.fontHeight, ctx::measureText)
        } else {
            textLayout
        }
        val color = if (showPlaceholder) placeholderColor else textColor
        val effectiveScroll = if (showPlaceholder) 0 else editState.scrollY.coerceIn(0, maxScroll)
        val firstVisibleLine = (effectiveScroll / lastLineHeight).coerceIn(0, drawLayout.lines.lastIndex)
        val lastVisibleLine = ((effectiveScroll + innerHeight) / lastLineHeight + 1)
            .coerceIn(0, drawLayout.lines.lastIndex)

        if (textInnerWidth > 0 && innerHeight > 0) {
            out.add(RenderCommand.PushClip(innerX, innerY, textInnerWidth, innerHeight))
        }

        if (!showPlaceholder && focused && editState.hasSelection()) {
            drawSelection(out, textLayout, firstVisibleLine, lastVisibleLine, innerX, innerY, effectiveScroll)
        }

        for (lineIndex in firstVisibleLine..lastVisibleLine) {
            val line = drawLayout.lines[lineIndex]
            val lineY = innerY - effectiveScroll + lineIndex * lastLineHeight
            out.add(RenderCommand.DrawText(line.text, innerX, lineY, color))
        }

        if (!showPlaceholder && focused && !styleDisabled && editState.isCaretVisible(caretBlinkPeriodMs)) {
            val caret = caretLineAndColumn(editState.caretIndex, textLayout)
            val caretLine = textLayout.lines[caret.first]
            val caretPrefix = caretLine.text.substring(0, caret.second.coerceIn(0, caretLine.text.length))
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

    override fun defaultBackgroundColor(): Int = backgroundColor

    override fun applyBackgroundColor(value: Int?) {
        if (value != null) {
            backgroundColor = value
        }
    }

    override fun defaultForegroundColor(): Int = textColor

    override fun applyForegroundColor(value: Int) {
        textColor = value
    }

    internal fun syncEditablePropsFrom(template: TextAreaNode) {
        placeholder = template.placeholder
        textColor = template.textColor
        placeholderColor = template.placeholderColor
        backgroundColor = template.backgroundColor
        focusedBackgroundColor = template.focusedBackgroundColor
        minContentWidth = template.minContentWidth
        minContentHeight = template.minContentHeight
        selectionColor = template.selectionColor
        caretBlinkPeriodMs = template.caretBlinkPeriodMs
        if (text != template.text) {
            text = template.text
            editState.clampToLength(text.length)
            clampScroll()
            editState.resetBlinkClock()
        }
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
        return TextEditShortcutDispatcher.dispatch(
            event,
            TextShortcutCallbacks(
                hasSelection = { editState.hasSelection() },
                selectAll = {
                    TextEditOps.selectAll(editState, text.length)
                },
                copySelection = { ClipboardBridge.writeText(selectedText()) },
                cutSelection = {
                    ClipboardBridge.writeText(selectedText())
                    replaceSelectionWith("", recordUndo = true)
                },
                normalizePaste = { raw -> raw.replace("\r\n", "\n").replace("\r", "\n") },
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
                selectionAnchor = editState.selectionAnchor,
                scrollY = editState.scrollY,
                preferredColumn = preferredColumn
            )
        )
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
        val snapshot = history.undo(currentSnapshot()) ?: return false
        val previous = text
        text = snapshot.text
        editState.caretIndex = snapshot.caretIndex.coerceIn(0, text.length)
        editState.selectionAnchor = snapshot.selectionAnchor?.coerceIn(0, text.length)
        editState.scrollY = snapshot.scrollY.coerceAtLeast(0)
        preferredColumn = snapshot.preferredColumn
        clampScroll()
        changeTracker.markDirty()
        persistState()
        if (text != previous) {
            postInput(this, text, text)
        }
        return true
    }

    private fun redoLastUndo(): Boolean {
        val snapshot = history.redo(currentSnapshot()) ?: return false
        val previous = text
        text = snapshot.text
        editState.caretIndex = snapshot.caretIndex.coerceIn(0, text.length)
        editState.selectionAnchor = snapshot.selectionAnchor?.coerceIn(0, text.length)
        editState.scrollY = snapshot.scrollY.coerceAtLeast(0)
        preferredColumn = snapshot.preferredColumn
        clampScroll()
        changeTracker.markDirty()
        persistState()
        if (text != previous) {
            postInput(this, text, text)
        }
        return true
    }

    private fun shouldRecordTypingUndo(ch: Char): Boolean {
        return typingUndoGrouping.shouldRecord(ch, editState.hasSelection())
    }

    private fun resetTypingUndoGroup() {
        typingUndoGrouping.reset()
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
        val layout = currentTextLayout()
        val current = caretLineAndColumn(editState.caretIndex, layout)
        val targetLine = (current.first + deltaLines).coerceIn(0, layout.lines.lastIndex)
        val desiredColumn = preferredColumn ?: current.second
        val target = layout.lines[targetLine]
        val nextIndex = (target.startIndex + desiredColumn).coerceAtMost(target.endIndexExclusive)
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
        val layout = currentTextLayout()
        val current = caretLineAndColumn(editState.caretIndex, layout)
        val line = layout.lines[current.first]
        val next = if (start) {
            line.startIndex
        } else {
            line.endIndexExclusive
        }
        moveCaretTo(next, extend)
        preferredColumn = null
        ensureCaretVisible()
        editState.resetBlinkClock()
        persistState()
    }

    private fun moveCaretTo(next: Int, extend: Boolean) {
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
        val start = editState.caretIndex.coerceIn(0, text.length)
        if (start >= text.length || text.isEmpty()) return
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
        val previous = text
        val next = TextEditOps.replaceRange(text, safeStart, safeEnd, insert)
        if (next == text) return false
        history.clearRedo()
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
        changeTracker.markDirty()
        ensureCaretVisible()
        editState.resetBlinkClock()
        persistState()
        if (text != previous) {
            postInput(this, text, text)
        }
    }

    private fun commitCurrentValueChange() {
        changeTracker.commitIfNeeded(text) {
            postChange(this, text, text)
        }
    }

    private fun ensureCaretVisible() {
        val line = caretLineAndColumn(editState.caretIndex, currentTextLayout()).first
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
        val layout = if (source == text) currentTextLayout() else {
            layoutForText(source, textContentWrapWidth(), lastLineHeight, currentMeasure())
        }
        val totalHeight = layout.totalHeight
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
        out.add(
            RenderCommand.DrawRect(
                trackRect.x,
                trackRect.y,
                trackRect.width,
                trackRect.height,
                scrollbarTrackColor
            )
        )

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
        layout: TextLayoutEngine.Layout,
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

        val minLine = maxOf(layout.lineForCaret(startIndex), firstVisibleLine)
        val maxLine = minOf(layout.lineForCaret(endIndex), lastVisibleLine)
        if (maxLine < minLine) return

        val measure = lastMeasureText ?: { value: String -> value.length * 6 }
        for (lineIndex in minLine..maxLine) {
            val line = layout.lines[lineIndex]
            val lineStart = line.startIndex
            val lineEnd = line.endIndexExclusive
            val selectionStartInLine = maxOf(startIndex, lineStart)
            val selectionEndInLine = minOf(endIndex, lineEnd)
            val startCol = (selectionStartInLine - lineStart).coerceIn(0, line.text.length)
            val endCol = (selectionEndInLine - lineStart).coerceIn(0, line.text.length)
            if (endCol <= startCol) continue

            val prefixStart = line.text.substring(0, startCol)
            val prefixEnd = line.text.substring(0, endCol)
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
        val layout = currentTextLayout()
        if (layout.lines.isEmpty()) return 0

        val localY = mouseY - contentY() + scrollOffsetY
        val lineIndex = (localY / lastLineHeight).coerceIn(0, layout.lines.lastIndex)
        val line = layout.lines[lineIndex]
        val localX = (mouseX - contentX()).coerceAtLeast(0)
        val column = caretColumnFromX(line.text, localX)
        return (line.startIndex + column).coerceIn(line.startIndex, line.endIndexExclusive)
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
        return TextEditOps.selectedText(text, editState)
    }

    private fun isPrintable(ch: Char): Boolean {
        return TextEditOps.isPrintable(ch)
    }

    private fun dragIdentity(): Any {
        return TextEditOps.dragIdentity(key, this)
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
        persistedByKey.save(
            key,
            PersistedState(
                scrollY = editState.scrollY,
                caretIndex = editState.caretIndex,
                preferredColumn = preferredColumn,
                selectionAnchor = editState.selectionAnchor,
                undoHistory = history.undoHistory(),
                redoHistory = history.redoHistory()
            )
        )
    }

    private fun restorePersistedState() {
        val persisted = persistedByKey.load(key) ?: return
        editState.scrollY = persisted.scrollY.coerceAtLeast(0)
        editState.caretIndex = persisted.caretIndex.coerceIn(0, text.length)
        editState.selectionAnchor = persisted.selectionAnchor?.coerceIn(0, text.length)
        preferredColumn = persisted.preferredColumn
        history.restore(persisted.undoHistory, persisted.redoHistory)
    }

    private fun currentTextLayout(): TextLayoutEngine.Layout {
        val contentWidth = textContentWrapWidth()
        val measure = currentMeasure()
        val expected = layoutForText(text, contentWidth, lastLineHeight, measure)
        if (lastTextLayout != expected || lastTextLayoutWidth != contentWidth) {
            lastTextLayout = expected
            lastTextLayoutWidth = contentWidth
        }
        return lastTextLayout
    }

    private fun textContentWrapWidth(): Int? {
        if (textWrap == TextWrap.NoWrap) return null
        val base = contentWidth()
        if (base <= 0) return 0
        return if (hasVerticalOverflow) {
            (base - scrollbarWidth - scrollbarGap).coerceAtLeast(0)
        } else {
            base
        }
    }

    private fun currentMeasure(): (String) -> Int {
        return lastMeasureText ?: { value: String -> value.length * 6 }
    }

    private fun layoutForText(
        source: String,
        contentWidth: Int?,
        fontHeight: Int,
        measureText: (String) -> Int
    ): TextLayoutEngine.Layout {
        val wrapMode = if (this@TextAreaNode.textWrap == TextWrap.Wrap) TextWrap.Wrap else TextWrap.NoWrap
        val maxWidth = if (wrapMode == TextWrap.Wrap) contentWidth else null
        return TextLayoutEngine.layout(
            text = source,
            maxWidth = maxWidth,
            wrap = wrapMode,
            fontHeight = fontHeight.coerceAtLeast(1),
            measureText = measureText
        )
    }

    private fun caretLineAndColumn(caret: Int, layout: TextLayoutEngine.Layout): Pair<Int, Int> {
        val safeCaret = caret.coerceIn(0, text.length)
        val lineIndex = layout.lineForCaret(safeCaret).coerceIn(0, layout.lines.lastIndex)
        val line = layout.lines[lineIndex]
        val column = (safeCaret - line.startIndex).coerceIn(0, line.text.length)
        return lineIndex to column
    }
}
