package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.FocusGainEvent
import org.dreamfinity.dsgl.core.event.Events
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyInput
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.KeyboardKeyDownEvent
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.MouseDownEvent
import org.dreamfinity.dsgl.core.event.MouseDragEvent
import org.dreamfinity.dsgl.core.event.MouseUpEvent
import org.dreamfinity.dsgl.core.event.MouseWheelEvent
import org.dreamfinity.dsgl.core.event.FocusLoseEvent
import org.dreamfinity.dsgl.core.event.postChange
import org.dreamfinity.dsgl.core.event.postInput
import org.dreamfinity.dsgl.core.render.RenderCommand

/**
 * Multiline text area node.
 */
class TextAreaNode(
    var text: String = "",
    var placeholder: String = "",
    key: Any? = null
) : DOMNode(key) {
    companion object {
        private data class PersistedState(
            val scrollY: Int,
            val caretIndex: Int,
            val preferredColumn: Int?
        )

        private var activeScrollbarDragIdentity: Any? = null
        private val persistedByKey: MutableMap<Any, PersistedState> = HashMap()

        fun clearActiveDrag() {
            activeScrollbarDragIdentity = null
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
    private var valueAtFocusStart: String = text
    private var dirtySinceFocus: Boolean = false
    private var scrollY: Int = 0
    private var caretIndex: Int = text.length
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
                }
            }
            this@TextAreaNode.addEventListener(Events.CLICK) { event: MouseClickEvent ->
                if (this@TextAreaNode.styleDisabled) return@addEventListener
                if (isInScrollbarTrack(event.mouseX, event.mouseY)) return@addEventListener
                moveCaretToClickPosition(event.mouseX, event.mouseY)
            }
            this@TextAreaNode.addEventListener(Events.DRAG) { event: MouseDragEvent ->
                if (this@TextAreaNode.styleDisabled) return@addEventListener
                if (!isActiveScrollbarDragTarget()) return@addEventListener
                val currentY = event.lastMouseY + event.dy
                updateScrollbarFromDrag(currentY)
                persistState()
                event.cancelled = true
            }
            this@TextAreaNode.addEventListener(Events.MOUSEUP) { event: MouseUpEvent ->
                if (event.mouseButton != MouseButton.LEFT) return@addEventListener
                if (!isActiveScrollbarDragTarget()) return@addEventListener
                clearActiveDrag()
                persistState()
                event.cancelled = true
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
            }
            this@TextAreaNode.addEventListener(Events.BLUR) { _: FocusLoseEvent ->
                commitCurrentValueChange()
                persistState()
            }
        }
    }

    private fun handleKey(event: KeyboardKeyDownEvent) {
        val previous = text
        var handled = true
        when (event.keyCode) {
            KeyCodes.BACKSPACE -> removePreviousChar()
            KeyCodes.DELETE -> removeNextChar()
            KeyCodes.ENTER -> insertTextAtCaret("\n")
            KeyCodes.LEFT -> moveCaretHorizontal(-1)
            KeyCodes.RIGHT -> moveCaretHorizontal(1)
            KeyCodes.UP -> moveCaretVertical(-1)
            KeyCodes.DOWN -> moveCaretVertical(1)
            KeyCodes.HOME -> moveCaretToLineBoundary(start = true)
            KeyCodes.END -> moveCaretToLineBoundary(start = false)
            KeyCodes.PAGE_UP -> moveCaretByPage(-1)
            KeyCodes.PAGE_DOWN -> moveCaretByPage(1)
            else -> {
                var ch = event.keyChar
                if (!isPrintable(ch)) {
                    handled = false
                } else {
                    ch = KeyInput.applyShift(ch, KeyModifiers.shiftDown)
                    insertTextAtCaret(ch.toString())
                }
            }
        }
        if (!handled) return

        if (text != previous) {
            onUserTextChanged(previous)
        } else {
            ensureCaretVisible()
            persistState()
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

    private fun isPrintable(ch: Char): Boolean {
        return ch >= ' ' && ch.code != 127
    }

    override fun measure(ctx: UiMeasureContext): Size {
        lastMeasureText = { value -> ctx.measureText(value) }
        lastLineHeight = ctx.fontHeight.coerceAtLeast(1)
        val display = text.ifEmpty { placeholder }
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
        val effectiveScroll = if (showPlaceholder) 0 else scrollY.coerceIn(0, maxScroll)
        val firstVisibleLine = (effectiveScroll / lastLineHeight).coerceAtLeast(0)
        val lastVisibleLine = ((effectiveScroll + innerHeight) / lastLineHeight + 1)
            .coerceIn(0, lines.size - 1)

        if (textInnerWidth > 0 && innerHeight > 0) {
            out.add(RenderCommand.PushClip(innerX, innerY, textInnerWidth, innerHeight))
        }

        for (lineIndex in firstVisibleLine..lastVisibleLine) {
            val line = lines[lineIndex]
            val lineY = innerY - effectiveScroll + lineIndex * lastLineHeight
            out.add(RenderCommand.DrawText(line, innerX, lineY, color))
        }

        if (!showPlaceholder && focused && !styleDisabled) {
            val caret = caretLineAndColumn(text, caretIndex)
            val caretLineText = lines[caret.first]
            val caretPrefix = caretLineText.substring(0, caret.second.coerceIn(0, caretLineText.length))
            val caretX = innerX + ctx.measureText(caretPrefix)
            val caretY = innerY - scrollY + caret.first * lastLineHeight
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

    private fun onUserTextChanged(previous: String) {
        caretIndex = caretIndex.coerceIn(0, text.length)
        dirtySinceFocus = true
        ensureCaretVisible()
        persistState()
        if (text != previous) {
            postInput(this, text, text)
        }
    }

    private fun insertTextAtCaret(value: String) {
        val index = caretIndex.coerceIn(0, text.length)
        text = text.substring(0, index) + value + text.substring(index)
        caretIndex = index + value.length
        preferredColumn = null
    }

    private fun removePreviousChar() {
        if (caretIndex <= 0 || text.isEmpty()) return
        val index = caretIndex.coerceIn(0, text.length)
        text = text.removeRange(index - 1, index)
        caretIndex = (index - 1).coerceAtLeast(0)
        preferredColumn = null
    }

    private fun removeNextChar() {
        val index = caretIndex.coerceIn(0, text.length)
        if (index >= text.length || text.isEmpty()) return
        text = text.removeRange(index, index + 1)
        caretIndex = index
        preferredColumn = null
    }

    private fun moveCaretHorizontal(delta: Int) {
        if (delta == 0) return
        caretIndex = (caretIndex + delta).coerceIn(0, text.length)
        preferredColumn = null
    }

    private fun moveCaretVertical(deltaLines: Int) {
        if (deltaLines == 0) return
        val starts = lineStarts(text)
        val current = caretLineAndColumn(text, caretIndex, starts)
        val targetLine = (current.first + deltaLines).coerceIn(0, starts.lastIndex)
        val desiredColumn = preferredColumn ?: current.second
        val targetLineEnd = lineEndIndex(starts, targetLine, text.length)
        caretIndex = (starts[targetLine] + desiredColumn).coerceAtMost(targetLineEnd)
        preferredColumn = desiredColumn
    }

    private fun moveCaretByPage(direction: Int) {
        val visibleLines = (lastVisibleHeight / lastLineHeight).coerceAtLeast(1)
        val delta = (visibleLines - 1).coerceAtLeast(1) * direction
        moveCaretVertical(delta)
    }

    private fun moveCaretToLineBoundary(start: Boolean) {
        val starts = lineStarts(text)
        val current = caretLineAndColumn(text, caretIndex, starts)
        caretIndex = if (start) {
            starts[current.first]
        } else {
            lineEndIndex(starts, current.first, text.length)
        }
        preferredColumn = null
    }

    private fun moveCaretToClickPosition(mouseX: Int, mouseY: Int) {
        val preservedScrollY = scrollY
        val targetCaret = caretIndexFromClick(mouseX, mouseY, preservedScrollY)
        caretIndex = targetCaret
        preferredColumn = null
        val wasFocused = FocusManager.isFocused(this)
        if (!wasFocused) {
            FocusManager.requestFocus(this)
        }
        scrollY = preservedScrollY.coerceIn(0, maxScrollFor(text))
        ensureCaretVisible()
        persistState()
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

    private fun ensureCaretVisible() {
        val line = caretLineAndColumn(text, caretIndex).first
        val caretTop = line * lastLineHeight
        val caretBottom = caretTop + lastLineHeight
        val visibleHeight = lastVisibleHeight.coerceAtLeast(lastLineHeight)
        if (caretTop < scrollY) {
            scrollY = caretTop
        } else if (caretBottom > scrollY + visibleHeight) {
            scrollY = caretBottom - visibleHeight
        }
        clampScroll()
    }

    private fun scrollByPixels(delta: Int) {
        if (delta == 0) return
        scrollY += delta
        clampScroll()
        persistState()
    }

    private fun clampScroll() {
        val maxScroll = maxScrollFor(text)
        scrollY = scrollY.coerceIn(0, maxScroll)
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
            ((scrollY.toDouble() / lastMaxScroll.toDouble()) * thumbTravel.toDouble()).toInt()
        }
        val thumbY = innerY + thumbOffset
        val thumbRect = Rect(trackX, thumbY, trackWidth, thumbHeight)
        scrollbarThumbRect = thumbRect
        val thumbColor = if (focused) scrollbarThumbFocusedColor else scrollbarThumbColor
        out.add(RenderCommand.DrawRect(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height, thumbColor))
    }

    private fun computeThumbHeight(trackHeight: Int): Int {
        if (lastMaxScroll <= 0) return trackHeight.coerceAtLeast(8)
        val totalContentHeight = (lastVisibleHeight + lastMaxScroll).coerceAtLeast(lastVisibleHeight)
        val ratio = lastVisibleHeight.toDouble() / totalContentHeight.toDouble()
        return (trackHeight * ratio).toInt().coerceIn(8, trackHeight.coerceAtLeast(8))
    }

    fun shouldCaptureScrollbarDrag(mouseX: Int, mouseY: Int): Boolean {
        if (styleDisabled || !hasVerticalOverflow || lastMaxScroll <= 0) return false
        return isInScrollbarTrack(mouseX, mouseY)
    }

    private fun handleScrollbarMouseDown(mouseX: Int, mouseY: Int): Boolean {
        if (!shouldCaptureScrollbarDrag(mouseX, mouseY)) return false
        FocusManager.requestFocus(this)
        activeScrollbarDragIdentity = dragIdentity()
        val thumb = scrollbarThumbRect
        scrollbarDragAnchorY = if (thumb != null && thumb.contains(mouseX, mouseY)) {
            (mouseY - thumb.y).coerceIn(0, thumb.height.coerceAtLeast(1))
        } else {
            computeThumbHeight(scrollbarTrackRect?.height ?: 0) / 2
        }
        updateScrollbarFromDrag(mouseY)
        persistState()
        return true
    }

    private fun updateScrollbarFromDrag(mouseY: Int) {
        val track = scrollbarTrackRect ?: return
        if (lastMaxScroll <= 0) {
            scrollY = 0
            return
        }
        val thumbHeight = scrollbarThumbRect?.height ?: computeThumbHeight(track.height)
        val thumbTravel = (track.height - thumbHeight).coerceAtLeast(0)
        if (thumbTravel <= 0) {
            scrollY = 0
            return
        }
        val desiredTop = (mouseY - track.y - scrollbarDragAnchorY).coerceIn(0, thumbTravel)
        scrollY = ((desiredTop.toDouble() / thumbTravel.toDouble()) * lastMaxScroll.toDouble()).toInt()
            .coerceIn(0, lastMaxScroll)
    }

    private fun dragIdentity(): Any {
        return key ?: this
    }

    private fun isActiveScrollbarDragTarget(): Boolean {
        val active = activeScrollbarDragIdentity ?: return false
        return active == dragIdentity()
    }

    private fun isInScrollbarTrack(mouseX: Int, mouseY: Int): Boolean {
        val track = scrollbarTrackRect ?: return false
        return track.contains(mouseX, mouseY)
    }

    private fun persistState() {
        val identity = key ?: return
        persistedByKey[identity] = PersistedState(scrollY, caretIndex, preferredColumn)
    }

    private fun restorePersistedState() {
        val identity = key ?: return
        val persisted = persistedByKey[identity] ?: return
        scrollY = persisted.scrollY.coerceAtLeast(0)
        caretIndex = persisted.caretIndex.coerceIn(0, text.length)
        preferredColumn = persisted.preferredColumn
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
