package org.dreamfinity.dsgl.core.select

import org.dreamfinity.dsgl.core.animation.Easings
import org.dreamfinity.dsgl.core.contextmenu.PopupPlacement
import org.dreamfinity.dsgl.core.contextmenu.PopupPlacementRequest
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand

class SelectEngine(
    private val clock: SelectClock = SystemSelectClock,
    private val measurementCache: SelectMeasurementCache = SelectMeasurementCache()
) : SelectHost {
    private data class PopupState(
        val owner: Any,
        var modelToken: Long,
        var entries: List<SelectEntry>,
        var selectedId: String?,
        var anchorRect: Rect,
        var closeOnSelect: Boolean,
        var onSelect: ((String) -> Unit)?,
        var onClose: (() -> Unit)?,
        var fontId: String?,
        var fontSize: Int?,
        var panelRect: Rect = Rect(0, 0, 1, 1),
        var measurement: SelectMeasurementCache.Measurement? = null,
        var hoveredIndex: Int = -1,
        var highlightedIndex: Int = -1,
        var scrollOffset: Int = 0,
        var scrollbarDragging: Boolean = false,
        var scrollbarDragThumbOffsetY: Int = 0
    )

    data class Snapshot(
        val isOpen: Boolean,
        val owner: Any?,
        val highlightedIndex: Int,
        val hoveredIndex: Int,
        val scrollOffset: Int,
        val animationProgress: Float
    )

    private enum class VisibilityState {
        Hidden,
        Opening,
        Open,
        Closing
    }

    private var popup: PopupState? = null
    private var style: SelectStyle = SelectStyle()
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private var viewportScale: Float = 1f
    private var layoutDirty: Boolean = true
    private var visibilityState: VisibilityState = VisibilityState.Hidden
    private var animationFrom: Float = 0f
    private var animationTo: Float = 1f
    private var animationProgress: Float = 0f
    private var animationDurationMs: Long = 1L
    private var animationStartedAtMs: Long = 0L
    private var typeAheadBuffer: String = ""
    private var typeAheadLastInputMs: Long = 0L
    private var lastMeasureContext: UiMeasureContext? = null

    fun setStyle(next: SelectStyle) {
        if (style == next) return
        style = next
        layoutDirty = true
    }

    fun currentStyle(): SelectStyle = style

    fun measurementComputeCount(): Long = measurementCache.computeCount

    override fun open(request: SelectOpenRequest) {
        if (request.entries.isEmpty()) {
            close(request.owner)
            return
        }
        val current = popup
        if (current != null && current.owner == request.owner) {
            applyRequest(current, request)
            ensureHighlightValid(current)
            layoutDirty = true
            startVisibilityTransition(1f, style.openDurationMs)
            return
        }
        current?.let {
            val onClose = it.onClose
            popup = null
            visibilityState = VisibilityState.Hidden
            animationProgress = 0f
            onClose?.invoke()
        }
        popup = PopupState(
            owner = request.owner,
            modelToken = request.modelToken,
            entries = request.entries,
            selectedId = request.selectedId,
            anchorRect = request.anchorRect,
            closeOnSelect = request.closeOnSelect,
            onSelect = request.onSelect,
            onClose = request.onClose,
            fontId = request.fontId,
            fontSize = request.fontSize
        )
        typeAheadBuffer = ""
        typeAheadLastInputMs = 0L
        ensureHighlightValid(popup!!)
        layoutDirty = true
        startVisibilityTransition(1f, style.openDurationMs)
    }

    fun sync(request: SelectOpenRequest) {
        val current = popup ?: return
        if (current.owner != request.owner) return
        val changed = applyRequest(current, request)
        if (changed) {
            ensureHighlightValid(current)
            layoutDirty = true
        }
    }

    override fun close(owner: Any) {
        val current = popup ?: return
        if (current.owner != owner) return
        startVisibilityTransition(0f, style.closeDurationMs)
    }

    override fun closeAll() {
        val current = popup ?: return
        val onClose = current.onClose
        popup = null
        visibilityState = VisibilityState.Hidden
        animationProgress = 0f
        layoutDirty = true
        typeAheadBuffer = ""
        typeAheadLastInputMs = 0L
        onClose?.invoke()
    }

    override fun isOpenFor(owner: Any): Boolean {
        val current = popup ?: return false
        return current.owner == owner && visibilityState != VisibilityState.Hidden
    }

    override fun isOpen(): Boolean = popup != null && visibilityState != VisibilityState.Hidden

    fun snapshot(): Snapshot {
        val current = popup
        return Snapshot(
            isOpen = current != null && visibilityState != VisibilityState.Hidden,
            owner = current?.owner,
            highlightedIndex = current?.highlightedIndex ?: -1,
            hoveredIndex = current?.hoveredIndex ?: -1,
            scrollOffset = current?.scrollOffset ?: 0,
            animationProgress = animationProgress
        )
    }

    fun debugPanelRect(owner: Any): Rect? {
        val current = popup ?: return null
        if (current.owner != owner) return null
        return current.panelRect
    }

    fun onFrame(
        measureContext: UiMeasureContext,
        viewportWidth: Int,
        viewportHeight: Int,
        viewportScale: Float = 1f
    ) {
        lastMeasureContext = measureContext
        if (this.viewportWidth != viewportWidth || this.viewportHeight != viewportHeight) {
            this.viewportWidth = viewportWidth
            this.viewportHeight = viewportHeight
            layoutDirty = true
        }
        if (this.viewportScale != viewportScale) {
            this.viewportScale = viewportScale
            layoutDirty = true
        }
        updateAnimationState()
        ensureLayout()
    }

    fun appendOverlayCommands(
        measureContext: UiMeasureContext,
        viewportWidth: Int,
        viewportHeight: Int,
        out: MutableList<RenderCommand>
    ) {
        if (!isOpen()) return
        onFrame(measureContext, viewportWidth, viewportHeight, viewportScale)
        val current = popup ?: return
        if (visibilityState == VisibilityState.Hidden) return

        val progress = animationProgress.coerceIn(0f, 1f)
        val panel = current.panelRect
        val fontId = current.fontId ?: style.fontId
        val fontSize = current.fontSize ?: style.fontSize
        val fontHeight = measureContext.fontHeight(fontId, fontSize)

        out += RenderCommand.PushClip(0, 0, viewportWidth.coerceAtLeast(1), viewportHeight.coerceAtLeast(1))
        if (progress < 0.999f) {
            val scale = style.openStartScale + (1f - style.openStartScale) * progress
            val translateY = style.openStartOffsetYPx * (1f - progress)
            out += RenderCommand.PushTransform(
                originX = panel.x + panel.width * 0.5f,
                originY = panel.y.toFloat(),
                translateX = 0f,
                translateY = translateY,
                scaleX = scale,
                scaleY = scale,
                rotateDeg = 0f
            )
            out += RenderCommand.PushOpacity(progress)
        }

        val shadowX = panel.x + 2
        val shadowY = panel.y + 2
        out += RenderCommand.DrawRect(shadowX, shadowY, panel.width, panel.height, style.panelShadowColor)
        out += RenderCommand.DrawRect(panel.x, panel.y, panel.width, panel.height, style.panelBackgroundColor)
        out += RenderCommand.DrawRect(panel.x, panel.y, panel.width, 1, style.panelBorderColor)
        out += RenderCommand.DrawRect(panel.x, panel.y + panel.height - 1, panel.width, 1, style.panelBorderColor)
        out += RenderCommand.DrawRect(panel.x, panel.y, 1, panel.height, style.panelBorderColor)
        out += RenderCommand.DrawRect(panel.x + panel.width - 1, panel.y, 1, panel.height, style.panelBorderColor)

        val clipX = panel.x + 1
        val clipY = panel.y + 1
        val clipW = (panel.width - 2).coerceAtLeast(1)
        val clipH = (panel.height - 2).coerceAtLeast(1)
        out += RenderCommand.PushClip(clipX, clipY, clipW, clipH)
        val maxScroll = maxScroll(current)
        val hasScrollbar = maxScroll > 0
        val contentX = panel.x + style.panelPaddingX
        val contentY = panel.y + style.panelPaddingY
        val contentW = contentWidth(current)
        val contentH = contentHeight(current)
        out += RenderCommand.PushClip(contentX, contentY, contentW, contentH)

        val measurement = current.measurement
        if (measurement != null) {
            measurement.snapshots.indices.forEach { index ->
                val rowRect = entryRect(current, index) ?: return@forEach
                if (rowRect.y + rowRect.height < contentY || rowRect.y > contentY + contentH) {
                    return@forEach
                }
                val snapshot = measurement.snapshots[index]
                if (snapshot.kind == SelectMeasurementCache.KIND_SEPARATOR) {
                    val y = rowRect.y + rowRect.height / 2
                    val x = rowRect.x + style.separatorInsetX
                    val w = (rowRect.width - style.separatorInsetX * 2).coerceAtLeast(1)
                    out += RenderCommand.DrawRect(x, y, w, 1, style.separatorColor)
                    return@forEach
                }

                val isHovered = index == current.hoveredIndex
                val isSelected = snapshot.optionId != null && snapshot.optionId == current.selectedId
                if (isHovered && snapshot.kind == SelectMeasurementCache.KIND_OPTION) {
                    out += RenderCommand.DrawRect(
                        rowRect.x,
                        rowRect.y,
                        rowRect.width,
                        rowRect.height,
                        style.optionHoverBackgroundColor
                    )
                } else if (isSelected && snapshot.kind == SelectMeasurementCache.KIND_OPTION) {
                    out += RenderCommand.DrawRect(
                        rowRect.x,
                        rowRect.y,
                        rowRect.width,
                        rowRect.height,
                        style.optionSelectedBackgroundColor
                    )
                }

                val textY = rowRect.y + ((rowRect.height - fontHeight).coerceAtLeast(0) / 2)
                if (snapshot.kind == SelectMeasurementCache.KIND_OPTION) {
                    val markerX = rowRect.x + style.rowPaddingX
                    if (isSelected) {
                        out += RenderCommand.DrawText(
                            text = style.checkGlyph,
                            x = markerX,
                            y = textY,
                            color = style.markerColor,
                            fontId = fontId,
                            fontSize = fontSize
                        )
                    }
                    val labelX = markerX +
                        style.markerColumnWidth +
                        style.markerGap +
                        snapshot.depth * style.groupIndentX
                    val color = if (snapshot.enabled) style.optionTextColor else style.disabledTextColor
                    out += RenderCommand.DrawText(
                        text = snapshot.label,
                        x = labelX,
                        y = textY,
                        color = color,
                        fontId = fontId,
                        fontSize = fontSize
                    )
                } else if (snapshot.kind == SelectMeasurementCache.KIND_GROUP) {
                    val labelX = rowRect.x + style.rowPaddingX + snapshot.depth * style.groupIndentX
                    out += RenderCommand.DrawText(
                        text = snapshot.label,
                        x = labelX,
                        y = textY,
                        color = style.groupTextColor,
                        fontId = fontId,
                        fontSize = fontSize
                    )
                }
            }
        }

        out += RenderCommand.PopClip

        if (hasScrollbar) {
            val track = scrollbarTrackRect(current)
            if (track != null) {
                out += RenderCommand.DrawRect(
                    track.x,
                    track.y,
                    track.width,
                    track.height,
                    style.scrollbarTrackColor
                )
                val thumb = scrollbarThumbRect(current, track)
                if (thumb != null) {
                    out += RenderCommand.DrawRect(
                        thumb.x,
                        thumb.y,
                        thumb.width,
                        thumb.height,
                        style.scrollbarThumbColor
                    )
                }
            }
        }

        if (progress < 0.999f) {
            out += RenderCommand.PopOpacity
            out += RenderCommand.PopTransform
        }
        out += RenderCommand.PopClip
        out += RenderCommand.PopClip
    }

    fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean {
        val current = popup ?: return false
        if (visibilityState == VisibilityState.Hidden) return false
        ensureLayout()
        if (current.scrollbarDragging) {
            updateScrollbarDrag(current, mouseY)
            current.hoveredIndex = -1
            return true
        }
        val hit = entryAt(current, mouseX, mouseY)
        current.hoveredIndex = hit
        if (hit >= 0) {
            val snapshot = current.measurement?.snapshots?.getOrNull(hit)
            if (snapshot != null && snapshot.kind == SelectMeasurementCache.KIND_OPTION && snapshot.enabled) {
                current.highlightedIndex = hit
            }
        }
        return true
    }

    fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        val current = popup ?: return false
        if (visibilityState == VisibilityState.Hidden) return false
        ensureLayout()
        if (!current.panelRect.contains(mouseX, mouseY)) {
            startVisibilityTransition(0f, style.closeDurationMs)
            return true
        }
        if (button != MouseButton.LEFT && button != MouseButton.RIGHT) {
            return true
        }
        if (button == MouseButton.LEFT && startScrollbarDragIfNeeded(current, mouseX, mouseY)) {
            return true
        }
        val entryIndex = entryAt(current, mouseX, mouseY)
        if (entryIndex < 0) return true
        activate(current, entryIndex)
        return true
    }

    fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        val current = popup ?: return false
        if (visibilityState == VisibilityState.Hidden) return false
        if (button == MouseButton.LEFT && current.scrollbarDragging) {
            current.scrollbarDragging = false
            return true
        }
        return when (button) {
            MouseButton.LEFT, MouseButton.RIGHT, MouseButton.MIDDLE -> true
        }
    }

    fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean {
        val current = popup ?: return false
        if (visibilityState == VisibilityState.Hidden) return false
        ensureLayout()
        if (!current.panelRect.contains(mouseX, mouseY)) {
            return true
        }
        val maxScroll = maxScroll(current)
        if (maxScroll <= 0) return true
        val rowHeight = current.measurement?.rowHeight ?: return true
        val rows = style.wheelStepRows.coerceAtLeast(1)
        val step = rowHeight * rows
        val direction = if (delta > 0) -1 else 1
        current.scrollOffset = (current.scrollOffset + direction * step).coerceIn(0, maxScroll)
        return true
    }

    fun handleKeyDown(keyCode: Int, keyChar: Char = 0.toChar()): Boolean {
        val current = popup ?: return false
        if (visibilityState == VisibilityState.Hidden) return false
        ensureLayout()
        when (keyCode) {
            KeyCodes.ESCAPE -> {
                startVisibilityTransition(0f, style.closeDurationMs)
                return true
            }

            KeyCodes.UP -> {
                moveSelection(current, -1)
                return true
            }

            KeyCodes.DOWN -> {
                moveSelection(current, 1)
                return true
            }

            KeyCodes.ENTER -> {
                val index = normalizeHighlight(current)
                if (index >= 0) {
                    activate(current, index)
                }
                return true
            }

            KeyCodes.SPACE -> {
                val index = normalizeHighlight(current)
                if (index >= 0) {
                    activate(current, index)
                }
                return true
            }
        }
        if (isTypeAheadCharacter(keyChar)) {
            typeAhead(current, keyChar.lowercaseChar())
            return true
        }
        return false
    }

    fun moveHighlight(owner: Any, direction: Int): Boolean {
        val current = popup ?: return false
        if (current.owner != owner || visibilityState == VisibilityState.Hidden) return false
        ensureLayout()
        moveSelection(current, direction)
        return true
    }

    private fun ensureLayout() {
        val current = popup ?: return
        val ctx = lastMeasureContext ?: return
        if (!layoutDirty) return

        val measurement = measurementCache.measure(
            modelToken = current.modelToken,
            entries = current.entries,
            style = style,
            ctx = ctx,
            dpiScale = viewportScale,
            fontId = current.fontId,
            fontSize = current.fontSize
        )
        current.measurement = measurement
        val desiredHeight = measurement.totalContentHeight + style.panelPaddingY * 2
        val maxPanelHeight = (viewportHeight - style.maxPanelHeightPadding * 2).coerceAtLeast(1)
        val panelHeight = desiredHeight.coerceIn(1, maxPanelHeight)
        val overflow = measurement.totalContentHeight > (panelHeight - style.panelPaddingY * 2).coerceAtLeast(1)
        val desiredWidth = maxOf(
            current.anchorRect.width.coerceAtLeast(1),
            measurement.panelWidth + style.panelPaddingX * 2 + if (overflow) scrollbarReservedWidth() else 0,
            style.minPanelWidth
        )
        val maxPanelWidth = (viewportWidth - style.maxPanelWidthPadding * 2).coerceAtLeast(1)
        val panelWidth = desiredWidth.coerceIn(1, maxPanelWidth)

        val belowY = current.anchorRect.y + current.anchorRect.height
        val aboveY = current.anchorRect.y - panelHeight
        val belowSpace = viewportHeight - style.viewportPadding - belowY
        val aboveSpace = current.anchorRect.y - style.viewportPadding
        val preferredY = if (belowSpace < panelHeight && aboveSpace > belowSpace) aboveY else belowY

        val placement = PopupPlacement.resolve(
            PopupPlacementRequest(
                preferredRect = Rect(current.anchorRect.x, preferredY, panelWidth, panelHeight),
                popupSize = Size(panelWidth, panelHeight),
                viewport = Rect(0, 0, viewportWidth.coerceAtLeast(1), viewportHeight.coerceAtLeast(1)),
                padding = style.viewportPadding
            )
        )
        current.panelRect = placement.rect
        val maxScroll = maxScroll(current)
        current.scrollOffset = current.scrollOffset.coerceIn(0, maxScroll)
        ensureEntryVisible(current, normalizeHighlight(current))
        layoutDirty = false
    }

    private fun activate(current: PopupState, entryIndex: Int) {
        val measurement = current.measurement ?: return
        val snapshot = measurement.snapshots.getOrNull(entryIndex) ?: return
        if (snapshot.kind != SelectMeasurementCache.KIND_OPTION || !snapshot.enabled) return
        val optionId = snapshot.optionId ?: return
        current.selectedId = optionId
        current.onSelect?.invoke(optionId)
        if (current.closeOnSelect) {
            startVisibilityTransition(0f, style.closeDurationMs)
        } else {
            current.highlightedIndex = entryIndex
            current.hoveredIndex = entryIndex
        }
    }

    private fun moveSelection(current: PopupState, direction: Int) {
        val measurement = current.measurement ?: return
        if (measurement.snapshots.isEmpty()) return
        val start = if (current.highlightedIndex >= 0) {
            current.highlightedIndex
        } else if (direction >= 0) {
            -1
        } else {
            measurement.snapshots.size
        }
        var index = start
        repeat(measurement.snapshots.size) {
            index += direction
            if (index < 0) {
                index = measurement.snapshots.size - 1
            } else if (index >= measurement.snapshots.size) {
                index = 0
            }
            val snapshot = measurement.snapshots[index]
            if (snapshot.kind == SelectMeasurementCache.KIND_OPTION && snapshot.enabled) {
                current.highlightedIndex = index
                current.hoveredIndex = index
                ensureEntryVisible(current, index)
                return
            }
        }
    }

    private fun normalizeHighlight(current: PopupState): Int {
        val measurement = current.measurement ?: return -1
        val highlighted = current.highlightedIndex
        if (highlighted in measurement.snapshots.indices) {
            val snapshot = measurement.snapshots[highlighted]
            if (snapshot.kind == SelectMeasurementCache.KIND_OPTION && snapshot.enabled) {
                return highlighted
            }
        }
        val selected = current.selectedId
        if (!selected.isNullOrEmpty()) {
            measurement.snapshots.indices.forEach { index ->
                val snapshot = measurement.snapshots[index]
                if (snapshot.kind == SelectMeasurementCache.KIND_OPTION &&
                    snapshot.enabled &&
                    snapshot.optionId == selected
                ) {
                    current.highlightedIndex = index
                    return index
                }
            }
        }
        measurement.snapshots.indices.forEach { index ->
            val snapshot = measurement.snapshots[index]
            if (snapshot.kind == SelectMeasurementCache.KIND_OPTION && snapshot.enabled) {
                current.highlightedIndex = index
                return index
            }
        }
        current.highlightedIndex = -1
        return -1
    }

    private fun ensureHighlightValid(current: PopupState) {
        val measurement = current.measurement
        if (measurement == null) {
            current.highlightedIndex = -1
            current.hoveredIndex = -1
            return
        }
        normalizeHighlight(current)
        if (current.hoveredIndex !in measurement.snapshots.indices) {
            current.hoveredIndex = -1
        }
    }

    private fun entryAt(current: PopupState, mouseX: Int, mouseY: Int): Int {
        val panel = current.panelRect
        val measurement = current.measurement ?: return -1
        if (!panel.contains(mouseX, mouseY)) return -1
        val contentX = panel.x + style.panelPaddingX
        val contentY = panel.y + style.panelPaddingY
        val contentW = contentWidth(current)
        val contentH = contentHeight(current)
        if (contentW <= 0 || contentH <= 0) return -1
        if (mouseX < contentX || mouseX >= contentX + contentW) return -1
        if (mouseY < contentY || mouseY >= contentY + contentH) return -1

        val localY = mouseY - contentY + current.scrollOffset
        measurement.entryOffsets.indices.forEach { index ->
            val top = measurement.entryOffsets[index]
            val bottom = top + measurement.entryHeights[index]
            if (localY >= top && localY < bottom) {
                return index
            }
        }
        return -1
    }

    private fun entryRect(current: PopupState, entryIndex: Int): Rect? {
        val measurement = current.measurement ?: return null
        if (entryIndex !in measurement.entryOffsets.indices) return null
        val rowX = current.panelRect.x + style.panelPaddingX
        val rowY = current.panelRect.y + style.panelPaddingY + measurement.entryOffsets[entryIndex] - current.scrollOffset
        val rowW = contentWidth(current)
        val rowH = measurement.entryHeights[entryIndex]
        return Rect(rowX, rowY, rowW, rowH)
    }

    private fun maxScroll(current: PopupState): Int {
        val measurement = current.measurement ?: return 0
        val visible = (current.panelRect.height - style.panelPaddingY * 2).coerceAtLeast(1)
        return (measurement.totalContentHeight - visible).coerceAtLeast(0)
    }

    private fun ensureEntryVisible(current: PopupState, entryIndex: Int) {
        if (entryIndex < 0) return
        val measurement = current.measurement ?: return
        if (entryIndex !in measurement.entryOffsets.indices) return
        val top = measurement.entryOffsets[entryIndex]
        val bottom = top + measurement.entryHeights[entryIndex]
        val viewportTop = current.scrollOffset
        val viewportBottom = viewportTop + (current.panelRect.height - style.panelPaddingY * 2).coerceAtLeast(1)
        when {
            top < viewportTop -> current.scrollOffset = top
            bottom > viewportBottom -> current.scrollOffset = (bottom - (viewportBottom - viewportTop)).coerceAtLeast(0)
        }
        current.scrollOffset = current.scrollOffset.coerceIn(0, maxScroll(current))
    }

    private fun contentHeight(current: PopupState): Int {
        return (current.panelRect.height - style.panelPaddingY * 2).coerceAtLeast(1)
    }

    private fun contentWidth(current: PopupState): Int {
        val reserved = if (maxScroll(current) > 0) scrollbarReservedWidth() else 0
        return (current.panelRect.width - style.panelPaddingX * 2 - reserved).coerceAtLeast(1)
    }

    private fun scrollbarReservedWidth(): Int {
        return (style.scrollbarGap + style.scrollbarWidth).coerceAtLeast(0)
    }

    private fun scrollbarTrackRect(current: PopupState): Rect? {
        if (maxScroll(current) <= 0) return null
        val trackWidth = style.scrollbarWidth.coerceAtLeast(1)
        val trackHeight = contentHeight(current)
        if (trackHeight <= 0) return null
        val x = current.panelRect.x + current.panelRect.width - style.panelPaddingX - trackWidth
        val y = current.panelRect.y + style.panelPaddingY
        return Rect(x, y, trackWidth, trackHeight)
    }

    private fun scrollbarThumbRect(current: PopupState, trackRect: Rect): Rect? {
        val measurement = current.measurement ?: return null
        val maxScroll = maxScroll(current)
        if (maxScroll <= 0) return null
        val totalContentHeight = measurement.totalContentHeight.coerceAtLeast(1)
        val trackHeight = trackRect.height.coerceAtLeast(1)
        val visibleHeight = contentHeight(current).coerceAtLeast(1)
        val rawThumb = ((visibleHeight.toLong() * trackHeight.toLong()) / totalContentHeight.toLong()).toInt()
        val thumbHeight = rawThumb
            .coerceAtLeast(style.scrollbarMinThumbHeight.coerceAtLeast(1))
            .coerceAtMost(trackHeight)
        val travel = (trackHeight - thumbHeight).coerceAtLeast(0)
        val thumbOffset = if (travel == 0) {
            0
        } else {
            ((current.scrollOffset.toLong() * travel.toLong()) / maxScroll.toLong()).toInt().coerceIn(0, travel)
        }
        return Rect(trackRect.x, trackRect.y + thumbOffset, trackRect.width, thumbHeight)
    }

    private fun startScrollbarDragIfNeeded(current: PopupState, mouseX: Int, mouseY: Int): Boolean {
        val trackRect = scrollbarTrackRect(current) ?: return false
        if (!trackRect.contains(mouseX, mouseY)) return false
        val thumbRect = scrollbarThumbRect(current, trackRect)
        val maxScroll = maxScroll(current)
        if (thumbRect == null || maxScroll <= 0) return false
        current.scrollbarDragging = true
        current.hoveredIndex = -1
        current.scrollbarDragThumbOffsetY = if (thumbRect.contains(mouseX, mouseY)) {
            (mouseY - thumbRect.y).coerceIn(0, thumbRect.height.coerceAtLeast(1) - 1)
        } else {
            (thumbRect.height / 2).coerceAtLeast(0)
        }
        updateScrollbarDrag(current, mouseY)
        return true
    }

    private fun updateScrollbarDrag(current: PopupState, mouseY: Int) {
        val trackRect = scrollbarTrackRect(current) ?: return
        val thumbRect = scrollbarThumbRect(current, trackRect) ?: return
        val maxScroll = maxScroll(current)
        if (maxScroll <= 0) {
            current.scrollOffset = 0
            return
        }
        val thumbTravel = (trackRect.height - thumbRect.height).coerceAtLeast(0)
        if (thumbTravel <= 0) {
            current.scrollOffset = 0
            return
        }
        val desiredTop = (mouseY - trackRect.y - current.scrollbarDragThumbOffsetY).coerceIn(0, thumbTravel)
        current.scrollOffset =
            ((desiredTop.toLong() * maxScroll.toLong()) / thumbTravel.toLong()).toInt().coerceIn(0, maxScroll)
    }

    private fun updateAnimationState() {
        val current = popup ?: return
        if (visibilityState == VisibilityState.Open || visibilityState == VisibilityState.Hidden) {
            animationProgress = if (visibilityState == VisibilityState.Open) 1f else 0f
            return
        }
        val duration = animationDurationMs.coerceAtLeast(1L)
        val elapsed = (clock.nowMs() - animationStartedAtMs).coerceAtLeast(0L)
        val t = (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        val eased = when (visibilityState) {
            VisibilityState.Opening -> Easings.EASE_OUT.map(t)
            VisibilityState.Closing -> Easings.EASE_IN.map(t)
            else -> t
        }
        animationProgress = animationFrom + (animationTo - animationFrom) * eased
        if (t >= 1f) {
            if (animationTo >= 0.999f) {
                visibilityState = VisibilityState.Open
                animationProgress = 1f
            } else {
                val onClose = current.onClose
                popup = null
                visibilityState = VisibilityState.Hidden
                animationProgress = 0f
                layoutDirty = true
                typeAheadBuffer = ""
                typeAheadLastInputMs = 0L
                onClose?.invoke()
            }
        }
    }

    private fun startVisibilityTransition(target: Float, durationMs: Long) {
        val current = popup ?: return
        val from = animationProgress.coerceIn(0f, 1f)
        animationFrom = from
        animationTo = target.coerceIn(0f, 1f)
        animationDurationMs = durationMs.coerceAtLeast(1L)
        animationStartedAtMs = clock.nowMs()
        visibilityState = when {
            animationTo >= 0.999f -> if (from >= 0.999f) VisibilityState.Open else VisibilityState.Opening
            animationTo <= 0.001f -> if (from <= 0.001f) VisibilityState.Hidden else VisibilityState.Closing
            else -> VisibilityState.Opening
        }
        if (visibilityState == VisibilityState.Hidden) {
            val onClose = current.onClose
            popup = null
            animationProgress = 0f
            typeAheadBuffer = ""
            typeAheadLastInputMs = 0L
            onClose?.invoke()
        } else if (visibilityState == VisibilityState.Open) {
            animationProgress = 1f
        }
    }

    private fun applyRequest(current: PopupState, request: SelectOpenRequest): Boolean {
        var changed = false
        if (current.modelToken != request.modelToken) {
            current.modelToken = request.modelToken
            changed = true
        }
        if (current.entries != request.entries) {
            current.entries = request.entries
            changed = true
        }
        if (current.selectedId != request.selectedId) {
            current.selectedId = request.selectedId
            changed = true
        }
        if (current.anchorRect != request.anchorRect) {
            current.anchorRect = request.anchorRect
            changed = true
        }
        if (current.closeOnSelect != request.closeOnSelect) {
            current.closeOnSelect = request.closeOnSelect
            changed = true
        }
        if (current.onSelect !== request.onSelect) {
            current.onSelect = request.onSelect
        }
        if (current.onClose !== request.onClose) {
            current.onClose = request.onClose
        }
        if (current.fontId != request.fontId) {
            current.fontId = request.fontId
            changed = true
        }
        if (current.fontSize != request.fontSize) {
            current.fontSize = request.fontSize
            changed = true
        }
        return changed
    }

    private fun typeAhead(current: PopupState, ch: Char) {
        val measurement = current.measurement ?: return
        val now = clock.nowMs()
        if (now - typeAheadLastInputMs > style.typeAheadResetMs) {
            typeAheadBuffer = ""
        }
        typeAheadLastInputMs = now
        typeAheadBuffer += ch
        if (typeAheadBuffer.isEmpty()) return
        val prefix = typeAheadBuffer
        val count = measurement.snapshots.size
        val start = if (current.highlightedIndex >= 0) current.highlightedIndex + 1 else 0
        for (offset in 0 until count) {
            val index = (start + offset) % count
            val snapshot = measurement.snapshots[index]
            if (snapshot.kind != SelectMeasurementCache.KIND_OPTION || !snapshot.enabled) continue
            if (snapshot.label.startsWith(prefix, ignoreCase = true)) {
                current.highlightedIndex = index
                current.hoveredIndex = index
                ensureEntryVisible(current, index)
                return
            }
        }
    }

    private fun isTypeAheadCharacter(ch: Char): Boolean {
        if (ch.code <= 0) return false
        if (ch.isWhitespace()) return false
        return !Character.isISOControl(ch)
    }
}
