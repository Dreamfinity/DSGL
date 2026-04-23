package org.dreamfinity.dsgl.core.inspector

import org.dreamfinity.dsgl.core.colorpicker.ColorFormatMode
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerState
import org.dreamfinity.dsgl.core.colorpicker.ColorTextCodec
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.colorpicker.internal.InspectorColorPickerHost
import org.dreamfinity.dsgl.core.colorpicker.internal.SystemColorPickerPanelManager
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.TextEditState
import org.dreamfinity.dsgl.core.dom.elements.support.TextEditOps
import org.dreamfinity.dsgl.core.dom.layout.Insets
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyInput
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.collectHoverChain
import org.dreamfinity.dsgl.core.input.ClipboardBridge
import org.dreamfinity.dsgl.core.popup.FloatingPaneDragModel
import org.dreamfinity.dsgl.core.style.*

enum class InspectorMode {
    Pick,
    Locked,
}

enum class InspectorPanelState {
    Expanded,
    Minimized,
}

class InspectorController(
    colorPickerManager: InspectorColorPickerHost = SystemColorPickerPanelManager(),
) {
    private var colorPickerManager: InspectorColorPickerHost = colorPickerManager

    private enum class EditOperation {
        Decrement,
        Increment,
        ResetProperty,
        BeginTextEdit,
        OpenColorPicker,
        ToggleValueSelect,
        SelectValueOption,
        ToggleUnitSelect,
        SelectUnitOption,
    }

    private enum class ActionKind {
        Minimize,
        TogglePick,
        Parent,
        Child,
        EditProperty,
        ResetSelectedOverrides,
        ClearAllOverrides,
    }

    private data class PanelAction(
        val bounds: Rect,
        val kind: ActionKind,
        val childIndex: Int = -1,
        val property: StyleProperty? = null,
        val editOperation: EditOperation? = null,
        val step: Float = 1f,
        val payload: String? = null,
    )

    private data class DropdownLayout(
        val rect: Rect,
        val property: StyleProperty,
        val isUnit: Boolean,
        val totalOptions: Int,
        val visibleRows: Int,
    )

    private data class SelectionStyleCache(
        val key: Any?,
        val nodeClass: Class<out DOMNode>,
        val layoutVersion: Long,
        val inspection: StyleInspection,
    )

    private enum class DragMode {
        None,
        Move,
        ResizeLeft,
        ResizeRight,
        ResizeTop,
        ResizeBottom,
        ResizeTopLeft,
        ResizeTopRight,
        ResizeBottomLeft,
        ResizeBottomRight,
        MinimizedMove,
        ScrollbarThumb,
    }

    var active: Boolean = false
        private set
    var mode: InspectorMode = InspectorMode.Pick
        private set
    var panelState: InspectorPanelState = InspectorPanelState.Expanded
        private set

    val isDraggingPanel: Boolean
        get() = dragMode != DragMode.None
    val panelPosition: Pair<Int, Int>
        get() = minimizedPosX to minimizedPosY

    val isPointerCaptured: Boolean
        get() = dragMode != DragMode.None && dragMode != DragMode.ScrollbarThumb

    val hoveredKey: String?
        get() = hoveredNode?.key?.toString()

    val selectedKey: String?
        get() = selectedKeyToken?.toString()
    val panelScrollOffsetY: Int
        get() = if (nativeDomBodyScrollStateActive) (nativeDomPanelScrollYOverride ?: 0) else panelScrollY
    private var root: DOMNode? = null
    private var layoutVersion: Long = 0L
    private var mouseX: Int = 0
    private var mouseY: Int = 0
    private var hoveredPath: List<DOMNode> = emptyList()
    private var hoveredNode: DOMNode? = null
    private var selectedNode: DOMNode? = null
    private var selectedKeyToken: Any? = null
    private var selectedClass: Class<out DOMNode>? = null
    private var panelBounds: Rect = Rect(0, 0, 0, 0)
    private var minimizedBounds: Rect = Rect(0, 0, 0, 0)
    private var headerBounds: Rect = Rect(0, 0, 0, 0)
    private var contentBounds: Rect = Rect(0, 0, 0, 0)
    private val panelActions: MutableList<PanelAction> = ArrayList()
    private val dropdownLayouts: MutableList<DropdownLayout> = ArrayList()
    private var cachedStyle: SelectionStyleCache? = null
    private var styleEditorError: String? = null
    private var expandedRect: Rect = Rect(22, 18, 760, 680)
    private var minimizedPosX: Int = 22
    private var minimizedPosY: Int = 18
    private var dragMode: DragMode = DragMode.None
    private var dragStartMouseX: Int = 0
    private var dragStartMouseY: Int = 0
    private var dragStartRect: Rect = expandedRect
    private var dragStartOffsetX: Int = 0
    private var dragStartOffsetY: Int = 0
    private var dragMoved: Boolean = false
    private var overlayPanelPointerCapture: Boolean = false
    private var overlayPanelAuthorityEnabled: Boolean = false
    private val paneMoveDrag: FloatingPaneDragModel = FloatingPaneDragModel()
    private var viewportW: Int = 0
    private var viewportH: Int = 0
    private var lastHandledPointerEvent: String = "none"
    private var pointerOverInspectorUi: Boolean = false
    private var hoverPickEnabled: Boolean = true
    private var panelScrollY: Int = 0
    private var panelContentHeight: Int = 0
    private var scrollbarTrackRect: Rect = Rect(0, 0, 0, 0)
    private var scrollbarThumbRect: Rect = Rect(0, 0, 0, 0)
    private var nativeDomBodyScrollStateActive: Boolean = false
    private var nativeDomPanelScrollYOverride: Int? = null
    private var nativeDomLastKnownBodyScrollY: Int = 0
    private var nativeDomScrollbarTrackRectOverride: Rect? = null
    private var nativeDomScrollbarThumbRectOverride: Rect? = null
    private var scrollbarDragOffsetY: Int = 0
    private var hoverDirty: Boolean = true
    private var lastHoverMouseX: Int = Int.MIN_VALUE
    private var lastHoverMouseY: Int = Int.MIN_VALUE
    private var lastHoverLayoutVersion: Long = Long.MIN_VALUE
    private var tooltipNodeRef: DOMNode? = null
    private var tooltipNodeBounds: Rect = Rect(0, 0, 0, 0)
    private var tooltipLabelCache: String = ""
    private val editSession: InspectorEditSession = InspectorEditSession()
    private var activeEditProperty: StyleProperty?
        get() = editSession.activeProperty
        set(value) {
            editSession.activeProperty = value
        }
    private var activeEditBuffer: String
        get() = editSession.activeBuffer
        set(value) {
            editSession.activeBuffer = value
        }
    private var activeEditUnit: CssUnit?
        get() = editSession.activeUnit
        set(value) {
            editSession.activeUnit = value
        }
    private var activeEditIsNumeric: Boolean
        get() = editSession.activeIsNumeric
        set(value) {
            editSession.activeIsNumeric = value
        }
    private val activeEditState: TextEditState
        get() = editSession.textState
    private var openValueSelectProperty: StyleProperty?
        get() = editSession.openValueProperty
        set(value) {
            editSession.openValueProperty = value
        }
    private var openValueSelectScrollIndex: Int
        get() = editSession.openValueScrollIndex
        set(value) {
            editSession.openValueScrollIndex = value
        }
    private var openUnitSelectProperty: StyleProperty?
        get() = editSession.openUnitProperty
        set(value) {
            editSession.openUnitProperty = value
        }
    private var openUnitSelectScrollIndex: Int
        get() = editSession.openUnitScrollIndex
        set(value) {
            editSession.openUnitScrollIndex = value
        }

    private var nativeSelectedHighlight: InspectorHighlightSnapshot? = null
    private var nativeHoveredHighlight: InspectorHighlightSnapshot? = null
    private var nativeCursorTooltip: InspectorTooltipSnapshot? = null
    private var nativeVariableTooltip: InspectorTooltipSnapshot? = null
    private val nativeStyleEditorRows: MutableList<InspectorStyleEditorRowSnapshot> = ArrayList()
    private val nativeDropdowns: MutableList<InspectorDropdownSnapshot> = ArrayList()
    private var nativeStyleEditorResetRect: Rect = Rect(0, 0, 0, 0)
    private var nativeStyleEditorClearRect: Rect = Rect(0, 0, 0, 0)
    private val styleEditorSnapshotBuilder: InspectorStyleEditorSnapshotBuilder =
        InspectorStyleEditorSnapshotBuilder(
            resolveLiteralFromComputed = ::literalFromComputed,
            renderExpressionLabel = ::expressionLabel,
        )

    private val minPanelWidth: Int = 240
    private val minPanelHeight: Int = 160
    private val minChipWidth: Int = 260
    private val chipHeight: Int = 56
    private val viewportMargin: Int = 2
    private val resizeHandleSize: Int = 8
    private val titleFontSizePx: Int = parseLengthPxInt("36px", allowNegative = false)
    private val textFontSizePx: Int = parseLengthPxInt("24px", allowNegative = false)
    private val secondaryFontSizePx: Int = parseLengthPxInt("24px", allowNegative = false)
    private val lineHeightPx: Int = (textFontSizePx + 8).coerceAtLeast(28)
    private val rowHeightPx: Int = (textFontSizePx + 10).coerceAtLeast(32)

    fun toggle() {
        active = !active
        if (!active) deactivateInternal()
    }

    fun deactivate() {
        if (!active) return
        active = false
        deactivateInternal()
    }

    fun installColorPickerHost(host: InspectorColorPickerHost) {
        if (colorPickerManager === host) return
        colorPickerManager.close()
        colorPickerManager = host
    }

    fun toggleMode() {
        if (!active) return
        mode = if (mode == InspectorMode.Pick) InspectorMode.Locked else InspectorMode.Pick
    }

    fun setPickMode(enabled: Boolean) {
        if (!active) return
        mode = if (enabled) InspectorMode.Pick else InspectorMode.Locked
        if (!enabled) {
            clearHoveredState()
        }
    }

    fun cancelPickMode(): Boolean {
        if (!active || mode != InspectorMode.Pick) return false
        mode = InspectorMode.Locked
        clearHoveredState()
        return true
    }

    fun minimize() {
        if (!active) return
        val current = expandedRect
        minimizedPosX = current.x
        minimizedPosY = current.y
        panelState = InspectorPanelState.Minimized
        dragMode = DragMode.None
        overlayPanelPointerCapture = false
        dragMoved = false
    }

    fun restore() {
        if (!active) return
        expandedRect = expandedRect.copy(x = minimizedPosX, y = minimizedPosY)
        expandedRect = clampExpandedRect(expandedRect, viewportW, viewportH)
        panelState = InspectorPanelState.Expanded
        dragMode = DragMode.None
        overlayPanelPointerCapture = false
        dragMoved = false
    }

    fun blocksUnderlyingInput(): Boolean =
        active &&
            (
                mode == InspectorMode.Pick ||
                    dragMode != DragMode.None ||
                    overlayPanelPointerCapture
            )

    fun shouldConsumePointer(mouseX: Int, mouseY: Int): Boolean {
        if (!active) return false
        if (dragMode != DragMode.None || overlayPanelPointerCapture) return true
        if (editSession.textSelectionDragActive) return true
        if (mode == InspectorMode.Pick) return true
        return hitTestUi(mouseX, mouseY)
    }

    fun shouldConsumeWheel(mouseX: Int, mouseY: Int): Boolean {
        if (!active) return false
        if (dragMode != DragMode.None || overlayPanelPointerCapture) return true
        if (mode == InspectorMode.Pick) return true
        return hitTestUi(mouseX, mouseY)
    }

    fun shouldConsumeKeyboard(mouseX: Int, mouseY: Int): Boolean {
        if (!active) return false
        if (dragMode != DragMode.None || overlayPanelPointerCapture) return true
        if (mode == InspectorMode.Pick) return true
        return hitTestUi(mouseX, mouseY)
    }

    fun markPointerHandled(reason: String) {
        lastHandledPointerEvent = reason
    }

    fun hitTestUi(mouseX: Int, mouseY: Int): Boolean = isInsideInspectorUi(mouseX, mouseY)

    fun onLayoutCommitted(root: DOMNode, layoutVersion: Long) {
        val layoutChanged = this.layoutVersion != layoutVersion
        this.root = root
        this.layoutVersion = layoutVersion
        rebindSelection()
        updateHoverGate()
        if (layoutChanged) {
            hoverDirty = true
            tooltipNodeRef = null
        }
        if (hoverPickEnabled) {
            refreshHoverIfNeeded()
        } else {
            clearHoveredState()
        }
    }

    fun onCursorMoved(mouseX: Int, mouseY: Int) {
        if (this.mouseX != mouseX || this.mouseY != mouseY) {
            hoverDirty = true
        }
        this.mouseX = mouseX
        this.mouseY = mouseY
        if (updateActiveTextSelectionFromPointer()) {
            updateHoverGate()
            return
        }
        updateHoverGate()
        if (!hoverPickEnabled) {
            clearHoveredState()
            return
        }
        refreshHoverIfNeeded()
    }

    fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean {
        if (!active || delta == 0) return false
        this.mouseX = mouseX
        this.mouseY = mouseY
        if (dragMode != DragMode.None) return true
        if (KeyModifiers.shiftDown) {
            return false
        }
        if (handleDropdownWheel(mouseX, mouseY, delta)) return true
        if (panelState != InspectorPanelState.Expanded) {
            return hitTestUi(mouseX, mouseY) || mode == InspectorMode.Pick
        }
        if (!contentBounds.contains(mouseX, mouseY)) {
            return hitTestUi(mouseX, mouseY) || mode == InspectorMode.Pick
        }

        val maxScroll = maxOf(0, panelContentHeight - contentBounds.height)
        val steps = (kotlin.math.abs(delta) / 120).coerceAtLeast(1)
        val amount = steps * 18
        val next =
            if (delta < 0) {
                (panelScrollY + amount).coerceAtMost(maxScroll)
            } else {
                (panelScrollY - amount).coerceAtLeast(0)
            }
        panelScrollY = next
        return true
    }

    private fun handleDropdownWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean {
        if (dropdownLayouts.isEmpty()) return false
        val target = dropdownLayouts.lastOrNull { it.rect.contains(mouseX, mouseY) } ?: return false
        val steps = (kotlin.math.abs(delta) / 120).coerceAtLeast(1)
        val maxFirst = (target.totalOptions - target.visibleRows).coerceAtLeast(0)
        if (maxFirst <= 0) return true
        if (target.isUnit) {
            openUnitSelectScrollIndex =
                if (delta < 0) {
                    (openUnitSelectScrollIndex + steps).coerceAtMost(maxFirst)
                } else {
                    (openUnitSelectScrollIndex - steps).coerceAtLeast(0)
                }
        } else {
            openValueSelectScrollIndex =
                if (delta < 0) {
                    (openValueSelectScrollIndex + steps).coerceAtMost(maxFirst)
                } else {
                    (openValueSelectScrollIndex - steps).coerceAtLeast(0)
                }
        }
        return true
    }

    fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean {
        if (!active) return false
        if (activeEditProperty == null) {
            if (keyCode == KeyCodes.ESCAPE) {
                editSession.closeAllDropdowns()
                return true
            }
            return false
        }
        if (handleActiveEditShortcut(keyCode)) {
            return true
        }
        when (keyCode) {
            KeyCodes.ESCAPE -> {
                editSession.clearActiveEdit()
                styleEditorError = null
                stopActiveTextSelectionDrag()
                return true
            }
            KeyCodes.ENTER -> {
                commitActiveTextEdit()
                return true
            }
            KeyCodes.LEFT -> {
                moveActiveCaretLeft(KeyModifiers.shiftDown)
                return true
            }
            KeyCodes.RIGHT -> {
                moveActiveCaretRight(KeyModifiers.shiftDown)
                return true
            }
            KeyCodes.HOME -> {
                moveActiveCaretBoundary(start = true, extend = KeyModifiers.shiftDown)
                return true
            }
            KeyCodes.END -> {
                moveActiveCaretBoundary(start = false, extend = KeyModifiers.shiftDown)
                return true
            }
            KeyCodes.BACKSPACE -> {
                deleteActiveBeforeCaret()
                return true
            }
            KeyCodes.DELETE -> {
                deleteActiveAfterCaret()
                return true
            }
        }
        var ch = keyChar
        if (!TextEditOps.isPrintable(ch)) return false
        ch = KeyInput.applyShift(ch, KeyModifiers.shiftDown)
        replaceActiveSelection(ch.toString())
        return true
    }

    private fun handleActiveEditShortcut(keyCode: Int): Boolean {
        if (!KeyModifiers.shortcutDown) return false
        when (keyCode) {
            KeyCodes.A -> {
                TextEditOps.selectAll(activeEditState, activeEditBuffer.length)
                activeEditState.resetBlinkClock()
                return true
            }
            KeyCodes.C -> {
                if (activeEditState.hasSelection()) {
                    ClipboardBridge.writeText(activeSelectedText())
                }
                activeEditState.resetBlinkClock()
                return true
            }
            KeyCodes.X -> {
                if (activeEditState.hasSelection()) {
                    ClipboardBridge.writeText(activeSelectedText())
                    replaceActiveSelection("")
                }
                activeEditState.resetBlinkClock()
                return true
            }
            KeyCodes.V -> {
                val paste =
                    ClipboardBridge
                        .readText()
                        .replace("\r", "")
                        .replace("\n", "")
                if (paste.isNotEmpty()) {
                    replaceActiveSelection(paste)
                } else {
                    activeEditState.resetBlinkClock()
                }
                return true
            }
            else -> return false
        }
    }

    private fun activeSelectedText(): String {
        activeEditState.clampToLength(activeEditBuffer.length)
        return TextEditOps.selectedText(activeEditBuffer, activeEditState)
    }

    private fun replaceActiveSelection(insert: String) {
        activeEditState.clampToLength(activeEditBuffer.length)
        val (start, end) = TextEditOps.selectionOrCaretBounds(activeEditState)
        val next = TextEditOps.replaceRange(activeEditBuffer, start, end, insert)
        activeEditBuffer = next
        activeEditState.caretIndex = (start + insert.length).coerceIn(0, next.length)
        activeEditState.clearSelection()
        activeEditState.clampToLength(next.length)
        activeEditState.resetBlinkClock()
    }

    private fun deleteActiveBeforeCaret() {
        activeEditState.clampToLength(activeEditBuffer.length)
        if (activeEditState.hasSelection()) {
            replaceActiveSelection("")
            return
        }
        val caret = activeEditState.caretIndex
        if (caret <= 0 || activeEditBuffer.isEmpty()) return
        val next = TextEditOps.replaceRange(activeEditBuffer, caret - 1, caret, "")
        activeEditBuffer = next
        activeEditState.caretIndex = (caret - 1).coerceIn(0, next.length)
        activeEditState.clearSelection()
        activeEditState.resetBlinkClock()
    }

    private fun deleteActiveAfterCaret() {
        activeEditState.clampToLength(activeEditBuffer.length)
        if (activeEditState.hasSelection()) {
            replaceActiveSelection("")
            return
        }
        val caret = activeEditState.caretIndex
        if (caret >= activeEditBuffer.length || activeEditBuffer.isEmpty()) return
        val next = TextEditOps.replaceRange(activeEditBuffer, caret, caret + 1, "")
        activeEditBuffer = next
        activeEditState.caretIndex = caret.coerceIn(0, next.length)
        activeEditState.clearSelection()
        activeEditState.resetBlinkClock()
    }

    private fun moveActiveCaretLeft(extend: Boolean) {
        activeEditState.clampToLength(activeEditBuffer.length)
        if (!extend && activeEditState.hasSelection()) {
            activeEditState.caretIndex = activeEditState.selectionStart().coerceIn(0, activeEditBuffer.length)
            activeEditState.clearSelection()
        } else {
            val next = (activeEditState.caretIndex - 1).coerceAtLeast(0)
            TextEditOps.moveCaretWithSelection(activeEditState, next, activeEditBuffer.length, extend)
        }
        activeEditState.resetBlinkClock()
    }

    private fun moveActiveCaretRight(extend: Boolean) {
        activeEditState.clampToLength(activeEditBuffer.length)
        if (!extend && activeEditState.hasSelection()) {
            activeEditState.caretIndex = activeEditState.selectionEnd().coerceIn(0, activeEditBuffer.length)
            activeEditState.clearSelection()
        } else {
            val next = (activeEditState.caretIndex + 1).coerceAtMost(activeEditBuffer.length)
            TextEditOps.moveCaretWithSelection(activeEditState, next, activeEditBuffer.length, extend)
        }
        activeEditState.resetBlinkClock()
    }

    private fun moveActiveCaretBoundary(start: Boolean, extend: Boolean) {
        activeEditState.clampToLength(activeEditBuffer.length)
        val next = if (start) 0 else activeEditBuffer.length
        TextEditOps.moveCaretWithSelection(activeEditState, next, activeEditBuffer.length, extend)
        activeEditState.resetBlinkClock()
    }

    fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        if (!active) return false
        if (button != MouseButton.LEFT) {
            if (button == MouseButton.RIGHT) {
                mode = InspectorMode.Locked
                return true
            }
            return shouldConsumePointer(mouseX, mouseY)
        }
        this.mouseX = mouseX
        this.mouseY = mouseY
        stopActiveTextSelectionDrag()
        updateHoverGate()
        if (hoverPickEnabled) {
            hoverDirty = true
            refreshHoverIfNeeded()
        } else {
            clearHoveredState()
        }
        if (panelState == InspectorPanelState.Minimized) {
            if (minimizedBounds.contains(mouseX, mouseY)) {
                if (overlayPanelAuthorityEnabled) {
                    return true
                }
                startMinimizedMoveDrag(mouseX, mouseY)
                return true
            }
            if (mode == InspectorMode.Pick) {
                selectHovered(lock = true)
                return true
            }
            return false
        }

        var action = findDropdownOptionAction(mouseX, mouseY)
        if (action == null) {
            action = findPanelAction(mouseX, mouseY)
        }
        if (shouldCommitActiveEdit(action)) {
            commitActiveTextEdit()
        }
        if (!overlayPanelAuthorityEnabled && startScrollbarDrag(mouseX, mouseY)) {
            return true
        }
        if (action != null) {
            performPanelAction(action)
            return true
        }
        if (dropdownLayouts.any { it.rect.contains(mouseX, mouseY) }) {
            editSession.closeAllDropdowns()
            return true
        }
        editSession.closeAllDropdowns()
        if (!overlayPanelAuthorityEnabled) {
            val resizeMode = resolveResizeDragMode(mouseX, mouseY)
            if (resizeMode != DragMode.None) {
                startExpandedDrag(resizeMode, mouseX, mouseY)
                return true
            }
            if (headerBounds.contains(mouseX, mouseY)) {
                startExpandedDrag(DragMode.Move, mouseX, mouseY)
                return true
            }
        }
        val hitPanel = if (panelBounds.width > 0 && panelBounds.height > 0) panelBounds else expandedRect
        if (hitPanel.contains(mouseX, mouseY)) {
            return true
        }
        if (mode == InspectorMode.Pick) {
            selectHovered(lock = true)
            return true
        }
        return false
    }

    fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        if (!active) return false
        if (button != MouseButton.LEFT) return shouldConsumePointer(mouseX, mouseY)
        this.mouseX = mouseX
        this.mouseY = mouseY
        if (editSession.textSelectionDragActive) {
            stopActiveTextSelectionDrag()
            return true
        }
        val wasDragging = dragMode != DragMode.None
        val endedMode = dragMode
        val clickLike = !dragMoved
        dragMode = DragMode.None
        paneMoveDrag.end()
        dragMoved = false
        if (!wasDragging) return false
        if (endedMode == DragMode.ScrollbarThumb) {
            return true
        }
        if (endedMode == DragMode.MinimizedMove &&
            clickLike &&
            panelState == InspectorPanelState.Minimized &&
            minimizedBounds.contains(mouseX, mouseY)
        ) {
            restore()
        }
        return true
    }

    fun onCapturedPointerMove(
        mouseX: Int,
        mouseY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        if (!active || dragMode == DragMode.None) return
        this.mouseX = mouseX
        this.mouseY = mouseY
        updateHoverGate()
        clearHoveredState()
        viewportW = viewportWidth
        viewportH = viewportHeight
        when (dragMode) {
            DragMode.MinimizedMove -> updateMinimizedMoveDrag(mouseX, mouseY, viewportWidth, viewportHeight)
            DragMode.Move,
            DragMode.ResizeLeft,
            DragMode.ResizeRight,
            DragMode.ResizeTop,
            DragMode.ResizeBottom,
            DragMode.ResizeTopLeft,
            DragMode.ResizeTopRight,
            DragMode.ResizeBottomLeft,
            DragMode.ResizeBottomRight,
            -> updateExpandedDrag(mouseX, mouseY, viewportWidth, viewportHeight)

            DragMode.ScrollbarThumb -> updateScrollbarDrag(mouseY)
            DragMode.None -> Unit
        }
    }

    internal fun buildDomSnapshot(viewportWidth: Int, viewportHeight: Int): InspectorDomSnapshot? {
        if (!active || viewportWidth <= 0 || viewportHeight <= 0) {
            resetNativePresentation()
            panelActions.clear()
            dropdownLayouts.clear()
            return null
        }
        viewportW = viewportWidth
        viewportH = viewportHeight
        val currentRoot =
            root ?: run {
                resetNativePresentation()
                return null
            }
        rebindSelection()
        expandedRect = clampExpandedRect(expandedRect, viewportWidth, viewportHeight)
        clampMinimizedPosition(viewportWidth, viewportHeight)
        updateHoverGate()
        if (hoverPickEnabled) {
            refreshHoverIfNeeded()
        } else {
            clearHoveredState()
        }

        return if (panelState == InspectorPanelState.Minimized) {
            buildMinimizedDomSnapshot(viewportWidth, viewportHeight)
        } else {
            buildExpandedDomSnapshot(currentRoot, viewportWidth, viewportHeight)
        }
    }

    private fun buildExpandedDomSnapshot(
        root: DOMNode,
        viewportWidth: Int,
        viewportHeight: Int,
    ): InspectorDomSnapshot {
        val clamped = clampExpandedRect(expandedRect, viewportWidth, viewportHeight)
        expandedRect = clamped
        panelBounds = clamped
        minimizedBounds = Rect(0, 0, 0, 0)
        resetNativePresentation()
        panelActions.clear()
        dropdownLayouts.clear()

        val headerRect =
            Rect(clamped.x + 6, clamped.y + 5, clamped.width - 12, (titleFontSizePx + 16).coerceAtLeast(44))
        headerBounds = headerRect
        val pickOn = mode == InspectorMode.Pick
        val selectedShort =
            selectedNode
                ?.key
                ?.toString()
                ?.take(18) ?: "none"
        val headerText = "Inspector  Pick:${if (pickOn) "ON" else "OFF"}  Sel:$selectedShort"

        val headerButtonHeight = (secondaryFontSizePx + 10).coerceAtLeast(26)
        val headerButtonY = headerRect.y + ((headerRect.height - headerButtonHeight) / 2)
        val minimizeRect = Rect(headerRect.x + headerRect.width - 96, headerButtonY, 86, headerButtonHeight)
        val pickRect = Rect(headerRect.x + headerRect.width - 264, headerButtonY, 160, headerButtonHeight)
        panelActions += PanelAction(pickRect, ActionKind.TogglePick)
        panelActions += PanelAction(minimizeRect, ActionKind.Minimize)

        val bodyTop = headerRect.y + headerRect.height + 6
        val bodyRect =
            Rect(
                clamped.x + 6,
                bodyTop,
                clamped.width - 12,
                (clamped.height - (bodyTop - clamped.y) - 4).coerceAtLeast(24),
            )
        contentBounds = bodyRect

        val infoLines = ArrayList<String>(128)
        var parentLabel: String? = null
        val childLabels = ArrayList<String>(64)
        var styleLines: List<String> = emptyList()

        var y = bodyRect.y
        val maxChars = InspectorPresentationSupport.estimateMaxChars(bodyRect.width - 12, textFontSizePx)
        val buttonLabelMaxChars =
            InspectorPresentationSupport.estimateMaxChars(
                (clamped.width - 32).coerceAtLeast(40),
                secondaryFontSizePx,
            )
        y = appendDomLine(infoLines, y, "F12 toggle, F9 mode, Esc cancel pick", maxChars)
        y =
            appendDomLine(
                infoLines,
                y,
                "Hovered: ${hoveredNode?.let { InspectorPresentationSupport.nodeLabel(it) } ?: "none"}",
                maxChars,
            )
        y =
            appendDomLine(
                infoLines,
                y,
                "Selected: ${selectedNode?.let { InspectorPresentationSupport.nodeLabel(it) } ?: "none"}",
                maxChars,
            )
        y = appendDomLine(infoLines, y, "Inspector handled last: $lastHandledPointerEvent", maxChars)
        y = appendDomLine(infoLines, y, "Pointer over Inspector: $pointerOverInspectorUi", maxChars)
        y = appendDomLine(infoLines, y, "Hover pick enabled: $hoverPickEnabled", maxChars)

        val selected = selectedNode
        captureNativeHighlightsAndTooltips(selected, viewportWidth, viewportHeight)
        if (selected == null) {
            y = appendDomLine(infoLines, y, "Click element in Pick mode to inspect.", maxChars)
            panelContentHeight = (y - bodyRect.y).coerceAtLeast(0)
            panelScrollY = 0
            updateScrollbarGeometry(bodyRect)
            return InspectorDomSnapshot(
                panelState = InspectorPanelState.Expanded,
                panelRect = clamped,
                headerRect = headerRect,
                bodyRect = bodyRect,
                headerText = headerText,
                minimizedLines = emptyList(),
                infoLines = infoLines,
                parentLabel = null,
                childLabels = emptyList(),
                styleEditorHeight = 0,
                styleLines = emptyList(),
            )
        }

        val pathLines =
            InspectorPresentationSupport.wrapPathLines(
                InspectorPresentationSupport.pathToNode(root, selected),
                maxChars,
            )
        pathLines.forEach { line ->
            infoLines += line
            y += lineHeightPx
        }

        val boxes = InspectorGeometrySupport.computeBoxes(selected)
        y =
            appendDomLine(
                infoLines,
                y,
                "Border box: ${InspectorPresentationSupport.rectLabel(boxes.border)}",
                maxChars,
            )
        y =
            appendDomLine(
                infoLines,
                y,
                "Content box: ${InspectorPresentationSupport.rectLabel(boxes.content)}",
                maxChars,
            )
        y =
            appendDomLine(
                infoLines,
                y,
                "Margin box: ${InspectorPresentationSupport.rectLabel(boxes.margin)}",
                maxChars,
            )
        boxes.parentContent?.let {
            y = appendDomLine(infoLines, y, "Parent content: ${InspectorPresentationSupport.rectLabel(it)}", maxChars)
        }
        val localPos =
            selected.parent?.let { parent ->
                val parentContent = InspectorGeometrySupport.contentRect(parent)
                "${selected.bounds.x - parentContent.x},${selected.bounds.y - parentContent.y}"
            } ?: "${selected.bounds.x},${selected.bounds.y}"
        y = appendDomLine(infoLines, y, "Local pos: $localPos", maxChars)
        selected.inspectorScrollOffset()?.let { (sx, sy) ->
            y = appendDomLine(infoLines, y, "Scroll: x=$sx y=$sy", maxChars)
        }

        val parent = selected.parent
        if (parent != null) {
            parentLabel = ellipsize("[Parent] ${InspectorPresentationSupport.nodeLabel(parent)}", buttonLabelMaxChars)
            val row = Rect(clamped.x + 10, y - panelScrollY, clamped.width - 20, rowHeightPx)
            panelActions += PanelAction(row, ActionKind.Parent)
            y += rowHeightPx + 2
        }

        val children = selected.children.filter { it.display != Display.None }
        if (children.isNotEmpty()) {
            y = appendDomLine(infoLines, y, "Children:", maxChars)
            for (index in children.indices) {
                val child = children[index]
                childLabels +=
                    ellipsize("[$index] ${InspectorPresentationSupport.nodeLabel(child)}", buttonLabelMaxChars)
                val row = Rect(clamped.x + 10, y - panelScrollY, clamped.width - 20, rowHeightPx)
                panelActions += PanelAction(row, ActionKind.Child, index)
                y += rowHeightPx + 2
            }
        }

        val inspection = selectionStyle(selected)
        val styleEditorStartY = y + 2
        val styleEditorSnapshots =
            styleEditorSnapshotBuilder.build(
                InspectorStyleEditorSnapshotBuildContext(
                    panelRect = clamped,
                    panelBounds = panelBounds,
                    selected = selected,
                    inspection = inspection,
                    editableProperties = editablePropertiesFor(selected),
                    startY = styleEditorStartY,
                    lineHeightPx = lineHeightPx,
                    rowHeightPx = rowHeightPx,
                    secondaryFontSizePx = secondaryFontSizePx,
                    pointerProjectionScrollY = resolvedNativePointerProjectionScrollY(),
                    mouseX = mouseX,
                    mouseY = mouseY,
                    viewportWidth = viewportW,
                    viewportHeight = viewportH,
                    openValueSelectProperty = openValueSelectProperty,
                    openUnitSelectProperty = openUnitSelectProperty,
                    openValueSelectScrollIndex = openValueSelectScrollIndex,
                    openUnitSelectScrollIndex = openUnitSelectScrollIndex,
                ),
            )
        y = styleEditorSnapshots.endY
        nativeVariableTooltip = styleEditorSnapshots.variableTooltip
        nativeStyleEditorRows.addAll(styleEditorSnapshots.rows)
        nativeDropdowns.addAll(styleEditorSnapshots.dropdowns)
        styleEditorSnapshots.dropdownLayouts.forEach { layout ->
            dropdownLayouts +=
                DropdownLayout(
                    rect = layout.rect,
                    property = layout.property,
                    isUnit = layout.unitSelect,
                    totalOptions = layout.totalOptions,
                    visibleRows = layout.visibleRows,
                )
        }
        styleEditorSnapshots.actionSpecs.forEach { action ->
            panelActions += toPanelAction(action)
        }
        nativeStyleEditorResetRect = styleEditorSnapshots.resetRect
        nativeStyleEditorClearRect = styleEditorSnapshots.clearRect
        openValueSelectScrollIndex = styleEditorSnapshots.openValueSelectScrollIndex
        openUnitSelectScrollIndex = styleEditorSnapshots.openUnitSelectScrollIndex
        val styleEditorHeight = (y - styleEditorStartY).coerceAtLeast(0)

        y = appendDomLine(infoLines, y, "Computed styles:", maxChars)
        styleLines =
            styleRows(inspection).flatMap { line ->
                InspectorPresentationSupport.wrapText(line, maxChars)
            }
        y += styleLines.size * lineHeightPx

        panelContentHeight = (y - bodyRect.y).coerceAtLeast(0)
        val maxScroll = maxOf(0, panelContentHeight - bodyRect.height)
        panelScrollY = panelScrollY.coerceIn(0, maxScroll)
        updateScrollbarGeometry(bodyRect)

        return InspectorDomSnapshot(
            panelState = InspectorPanelState.Expanded,
            panelRect = clamped,
            headerRect = headerRect,
            bodyRect = bodyRect,
            headerText = headerText,
            minimizedLines = emptyList(),
            infoLines = infoLines,
            parentLabel = parentLabel,
            childLabels = childLabels,
            styleEditorHeight = styleEditorHeight,
            styleLines = styleLines,
        )
    }

    private fun buildMinimizedDomSnapshot(viewportWidth: Int, viewportHeight: Int): InspectorDomSnapshot {
        val chipWidth = minimizedWidth().coerceAtLeast(minChipWidth)
        val chipHeight = minimizedHeight()
        clampMinimizedPosition(viewportWidth, viewportHeight)
        val chipRect = Rect(minimizedPosX, minimizedPosY, chipWidth, chipHeight)
        panelBounds = chipRect
        minimizedBounds = chipRect
        headerBounds = Rect(0, 0, 0, 0)
        contentBounds = Rect(0, 0, 0, 0)
        resetNativePresentation()
        panelActions.clear()
        dropdownLayouts.clear()
        panelScrollY = 0
        panelContentHeight = 0
        nativeDomLastKnownBodyScrollY = 0
        scrollbarTrackRect = Rect(0, 0, 0, 0)
        scrollbarThumbRect = Rect(0, 0, 0, 0)

        val badge = if (mode == InspectorMode.Pick) "[Pick]" else "[Locked]"
        val selectedShort =
            selectedNode
                ?.key
                ?.toString()
                ?.let { " $it" } ?: ""
        val maxChars = InspectorPresentationSupport.estimateMaxChars(chipRect.width - 12, secondaryFontSizePx)
        val lines =
            InspectorPresentationSupport.wrapMinimizedLabel(
                "Inspector $badge$selectedShort",
                maxChars,
                maxLines = 2,
            )
        return InspectorDomSnapshot(
            panelState = InspectorPanelState.Minimized,
            panelRect = chipRect,
            headerRect = null,
            bodyRect = null,
            headerText = "Inspector",
            minimizedLines = lines,
            infoLines = emptyList(),
            parentLabel = null,
            childLabels = emptyList(),
            styleEditorHeight = 0,
            styleLines = emptyList(),
        )
    }

    private fun appendDomLine(
        lines: MutableList<String>,
        y: Int,
        text: String,
        maxChars: Int,
    ): Int {
        val wrapped = InspectorPresentationSupport.wrapText(text, maxChars)
        lines += wrapped
        return y + wrapped.size * lineHeightPx
    }

    private fun captureNativeHighlightsAndTooltips(selected: DOMNode?, viewportWidth: Int, viewportHeight: Int) {
        nativeSelectedHighlight =
            selected?.let { node ->
                val boxes = InspectorGeometrySupport.computeHighlightBoxes(node)
                InspectorHighlightSnapshot(
                    marginRect = boxes.margin,
                    borderRect = boxes.border,
                    paddingRect = boxes.padding,
                    contentRect = boxes.content,
                    parentContentRect = boxes.parentContent,
                )
            }
        if (hoverPickEnabled) {
            val hovered = hoveredNode
            nativeHoveredHighlight =
                hovered?.let { node ->
                    val boxes = InspectorGeometrySupport.computeHighlightBoxes(node)
                    InspectorHighlightSnapshot(
                        marginRect = boxes.margin,
                        borderRect = boxes.border,
                        paddingRect = boxes.padding,
                        contentRect = boxes.content,
                        parentContentRect = boxes.parentContent,
                    )
                }
            if (hovered != null) {
                val label = resolveTooltipLabel(hovered)
                val boxW = (label.length * (secondaryFontSizePx / 2) + 18).coerceIn(140, viewportWidth - 8)
                val boxH = (secondaryFontSizePx + 10).coerceAtLeast(26)
                val tooltipRect = resolveTooltipRect(viewportWidth, viewportHeight, boxW, boxH)
                nativeCursorTooltip = InspectorTooltipSnapshot(text = label, rect = tooltipRect)
            }
        }
    }

    private fun toPanelAction(action: InspectorStyleEditorActionSpec): PanelAction {
        fun editAction(operation: EditOperation): PanelAction =
            PanelAction(
                bounds = action.bounds,
                kind = ActionKind.EditProperty,
                property = requireNotNull(action.property),
                editOperation = operation,
                step = action.step,
                payload = action.payload,
            )
        return when (action.type) {
            InspectorStyleEditorActionType.ResetProperty -> editAction(EditOperation.ResetProperty)
            InspectorStyleEditorActionType.ToggleValueSelect -> editAction(EditOperation.ToggleValueSelect)
            InspectorStyleEditorActionType.SelectValueOption -> editAction(EditOperation.SelectValueOption)
            InspectorStyleEditorActionType.OpenColorPicker -> editAction(EditOperation.OpenColorPicker)
            InspectorStyleEditorActionType.Decrement -> editAction(EditOperation.Decrement)
            InspectorStyleEditorActionType.Increment -> editAction(EditOperation.Increment)
            InspectorStyleEditorActionType.ToggleUnitSelect -> editAction(EditOperation.ToggleUnitSelect)
            InspectorStyleEditorActionType.SelectUnitOption -> editAction(EditOperation.SelectUnitOption)
            InspectorStyleEditorActionType.ResetSelectedOverrides ->
                PanelAction(
                    bounds = action.bounds,
                    kind = ActionKind.ResetSelectedOverrides,
                )

            InspectorStyleEditorActionType.ClearAllOverrides ->
                PanelAction(
                    bounds = action.bounds,
                    kind = ActionKind.ClearAllOverrides,
                )
        }
    }

    private fun updateScrollbarGeometry(bodyRect: Rect) {
        if (panelContentHeight <= bodyRect.height || bodyRect.height <= 0) {
            scrollbarTrackRect = Rect(0, 0, 0, 0)
            scrollbarThumbRect = Rect(0, 0, 0, 0)
            return
        }
        val trackWidth = 4
        val track =
            Rect(
                bodyRect.x + bodyRect.width - trackWidth - 2,
                bodyRect.y + 2,
                trackWidth,
                (bodyRect.height - 4).coerceAtLeast(8),
            )
        val maxScroll = (panelContentHeight - bodyRect.height).coerceAtLeast(1)
        val thumbHeight =
            ((track.height.toFloat() * bodyRect.height.toFloat() / panelContentHeight.toFloat()).toInt()).coerceIn(
                10,
                track.height,
            )
        val travel = (track.height - thumbHeight).coerceAtLeast(0)
        val thumbY = track.y + ((panelScrollY.toFloat() / maxScroll.toFloat()) * travel.toFloat()).toInt()
        scrollbarTrackRect = track
        scrollbarThumbRect = Rect(track.x, thumbY, track.width, thumbHeight)
    }

    internal fun overlayPickToggleBounds(): Rect? = panelActions.lastOrNull { it.kind == ActionKind.TogglePick }?.bounds

    internal fun overlayMinimizeBounds(): Rect? = panelActions.lastOrNull { it.kind == ActionKind.Minimize }?.bounds

    internal fun overlayContentRect(): Rect = contentBounds

    internal fun overlayScrollbarThumbRect(): Rect =
        if (nativeDomBodyScrollStateActive) {
            nativeDomScrollbarThumbRectOverride ?: Rect(0, 0, 0, 0)
        } else {
            scrollbarThumbRect
        }

    internal fun overlayScrollbarTrackRect(): Rect =
        if (nativeDomBodyScrollStateActive) {
            nativeDomScrollbarTrackRectOverride ?: Rect(0, 0, 0, 0)
        } else {
            scrollbarTrackRect
        }

    internal fun onNativeDomBodyScrollState(scrollY: Int, trackRect: Rect?, thumbRect: Rect?) {
        nativeDomBodyScrollStateActive = true
        nativeDomPanelScrollYOverride = scrollY.coerceAtLeast(0)
        nativeDomLastKnownBodyScrollY = scrollY.coerceAtLeast(0)
        nativeDomScrollbarTrackRectOverride = trackRect
        nativeDomScrollbarThumbRectOverride = thumbRect
    }

    internal fun onNativeDomExpandedPanelRect(rect: Rect, viewportWidth: Int, viewportHeight: Int) {
        val clamped = clampExpandedRect(rect, viewportWidth, viewportHeight)
        expandedRect = clamped
        panelBounds = clamped
        minimizedBounds = Rect(0, 0, 0, 0)
    }

    internal fun onOverlayPanelRectChanged(rect: Rect, viewportWidth: Int, viewportHeight: Int) {
        onNativeDomExpandedPanelRect(rect, viewportWidth, viewportHeight)
    }

    internal fun onOverlayPanelPointerCaptureChanged(captured: Boolean) {
        overlayPanelPointerCapture = captured
    }

    internal fun setOverlayPanelAuthorityEnabled(enabled: Boolean) {
        overlayPanelAuthorityEnabled = enabled
        if (enabled && dragMode != DragMode.ScrollbarThumb) {
            dragMode = DragMode.None
        }
    }

    internal fun onNativeDomMinimizedPanelPosition(
        x: Int,
        y: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        minimizedPosX = x
        minimizedPosY = y
        clampMinimizedPosition(viewportWidth, viewportHeight)
        minimizedBounds = Rect(minimizedPosX, minimizedPosY, minimizedWidth(), minimizedHeight())
    }

    internal fun overlaySelectedHighlight(): InspectorHighlightSnapshot? = nativeSelectedHighlight

    internal fun overlayHoveredHighlight(): InspectorHighlightSnapshot? = nativeHoveredHighlight

    internal fun overlayCursorTooltip(): InspectorTooltipSnapshot? = nativeCursorTooltip

    internal fun overlayVariableTooltip(): InspectorTooltipSnapshot? = nativeVariableTooltip

    internal fun overlayStyleEditorRows(): List<InspectorStyleEditorRowSnapshot> = nativeStyleEditorRows

    internal fun overlayStyleEditorResetRect(): Rect = nativeStyleEditorResetRect

    internal fun overlayStyleEditorClearRect(): Rect = nativeStyleEditorClearRect

    internal fun overlayStyleEditorDropdowns(): List<InspectorDropdownSnapshot> = nativeDropdowns

    internal fun onNativeDomDropdownSnapshots(dropdowns: List<InspectorDropdownSnapshot>) {
        nativeDropdowns.clear()
        nativeDropdowns.addAll(dropdowns)
    }

    internal fun resolveDropdownOptionsForProperty(property: StyleProperty, unitSelect: Boolean): List<String> {
        if (unitSelect) {
            return InspectorEditorRegistry.unitOptions().map { it.token }
        }
        val selected = selectedNode ?: return emptyList()
        val literal = literalForEdit(selected, property)
        val descriptor =
            InspectorEditorRegistry.describe(
                property = property,
                literal = literal,
                expression = StyleEngine.inspectorOverrideFor(selected, property),
            )
        if (descriptor.kind != InspectorEditorKind.EnumSelect && descriptor.kind != InspectorEditorKind.FontSelect) {
            return emptyList()
        }
        return descriptor.options
    }

    internal fun hasOpenStyleDropdown(): Boolean = openValueSelectProperty != null || openUnitSelectProperty != null

    internal fun closeOpenStyleDropdowns(): Boolean {
        if (!hasOpenStyleDropdown()) return false
        editSession.closeAllDropdowns()
        return true
    }

    internal fun handleOpenStyleDropdownWheel(delta: Int): Boolean {
        if (delta == 0 || KeyModifiers.shiftDown) return false
        val openDropdown = resolveOpenStyleDropdown() ?: return false
        val maxRows = 8
        val visibleRows = minOf(maxRows, openDropdown.optionCount)
        val maxFirst = (openDropdown.optionCount - visibleRows).coerceAtLeast(0)
        if (maxFirst <= 0) return true

        val steps = (kotlin.math.abs(delta) / 120).coerceAtLeast(1)
        val current = if (openDropdown.unitSelect) openUnitSelectScrollIndex else openValueSelectScrollIndex
        val next =
            if (delta < 0) {
                (current + steps).coerceAtMost(maxFirst)
            } else {
                (current - steps).coerceAtLeast(0)
            }
        if (next == current) return true

        if (openDropdown.unitSelect) {
            openUnitSelectScrollIndex = next
        } else {
            openValueSelectScrollIndex = next
        }
        return true
    }

    internal fun debugActiveEditBuffer(): String? = activeEditProperty?.let { activeEditBuffer }

    internal fun debugActiveEditCaret(): Int? =
        activeEditProperty?.let {
            activeEditState.clampToLength(activeEditBuffer.length)
            activeEditState.caretIndex
        }

    internal fun debugActiveEditSelectionRange(): Pair<Int, Int>? =
        activeEditProperty?.let {
            activeEditState.clampToLength(activeEditBuffer.length)
            activeEditState.selectionStart() to activeEditState.selectionEnd()
        }

    internal fun onPickTogglePressed() {
        setPickMode(mode != InspectorMode.Pick)
    }

    internal fun onPanelMinimizeTogglePressed() {
        if (panelState == InspectorPanelState.Minimized) {
            restore()
        } else {
            minimize()
        }
    }

    internal fun onSelectParentPressed() {
        selectParent()
        mode = InspectorMode.Locked
    }

    internal fun onSelectChildPressed(index: Int) {
        selectChild(index)
        mode = InspectorMode.Locked
    }

    internal fun onResetPropertyPressed(property: StyleProperty): Boolean {
        val selected = selectedNode ?: return false
        applyStyleEdit(
            selected = selected,
            property = property,
            operation = EditOperation.ResetProperty,
            step = 1f,
            payload = null,
            actionBounds = Rect(0, 0, 0, 0),
        )
        mode = InspectorMode.Locked
        return true
    }

    internal fun onToggleValueSelectPressed(property: StyleProperty): Boolean {
        val selected = selectedNode ?: return false
        applyStyleEdit(
            selected = selected,
            property = property,
            operation = EditOperation.ToggleValueSelect,
            step = 1f,
            payload = null,
            actionBounds = Rect(0, 0, 0, 0),
        )
        mode = InspectorMode.Locked
        return true
    }

    internal fun onSelectValueOptionPressed(property: StyleProperty, option: String): Boolean {
        val selected = selectedNode ?: return false
        applyStyleEdit(
            selected = selected,
            property = property,
            operation = EditOperation.SelectValueOption,
            step = 1f,
            payload = option,
            actionBounds = Rect(0, 0, 0, 0),
        )
        mode = InspectorMode.Locked
        return true
    }

    internal fun onNumericIncrementPressed(property: StyleProperty): Boolean {
        val selected = selectedNode ?: return false
        applyStyleEdit(
            selected = selected,
            property = property,
            operation = EditOperation.Increment,
            step = StylePropertyRegistry.descriptor(property).numericStep,
            payload = null,
            actionBounds = Rect(0, 0, 0, 0),
        )
        mode = InspectorMode.Locked
        return true
    }

    internal fun onNumericDecrementPressed(property: StyleProperty): Boolean {
        val selected = selectedNode ?: return false
        applyStyleEdit(
            selected = selected,
            property = property,
            operation = EditOperation.Decrement,
            step = StylePropertyRegistry.descriptor(property).numericStep,
            payload = null,
            actionBounds = Rect(0, 0, 0, 0),
        )
        mode = InspectorMode.Locked
        return true
    }

    internal fun onToggleUnitSelectPressed(property: StyleProperty): Boolean {
        val selected = selectedNode ?: return false
        applyStyleEdit(
            selected = selected,
            property = property,
            operation = EditOperation.ToggleUnitSelect,
            step = 1f,
            payload = null,
            actionBounds = Rect(0, 0, 0, 0),
        )
        mode = InspectorMode.Locked
        return true
    }

    internal fun onSelectUnitOptionPressed(property: StyleProperty, option: String): Boolean {
        val selected = selectedNode ?: return false
        applyStyleEdit(
            selected = selected,
            property = property,
            operation = EditOperation.SelectUnitOption,
            step = 1f,
            payload = option,
            actionBounds = Rect(0, 0, 0, 0),
        )
        mode = InspectorMode.Locked
        return true
    }

    internal fun onOpenColorPickerPressed(property: StyleProperty, anchorRect: Rect): Boolean {
        val selected = selectedNode ?: return false
        openColorPicker(selected, property, anchorRect)
        mode = InspectorMode.Locked
        return true
    }

    internal fun onResetSelectedOverridesPressed() {
        selectedNode?.let { selected ->
            StyleEngine.clearInspectorOverride(selected)
            styleEditorError = null
            cachedStyle = null
        }
    }

    internal fun onClearAllOverridesPressed() {
        StyleEngine.clearAllInspectorOverrides()
        styleEditorError = null
        cachedStyle = null
    }

    private data class OpenStyleDropdown(
        val unitSelect: Boolean,
        val optionCount: Int,
    )

    private fun resolveOpenStyleDropdown(): OpenStyleDropdown? {
        val unitProperty = openUnitSelectProperty
        if (unitProperty != null) {
            val optionCount = InspectorEditorRegistry.unitOptions().size
            if (optionCount <= 0) return null
            return OpenStyleDropdown(unitSelect = true, optionCount = optionCount)
        }

        val valueProperty = openValueSelectProperty ?: return null
        val selected = selectedNode ?: return null
        val literal = literalForEdit(selected, valueProperty)
        val editor =
            InspectorEditorRegistry.describe(
                property = valueProperty,
                literal = literal,
                expression = StyleEngine.inspectorOverrideFor(selected, valueProperty),
            )
        val optionCount = editor.options.size
        if (optionCount <= 0) return null
        return OpenStyleDropdown(unitSelect = false, optionCount = optionCount)
    }

    internal fun overlayApplyLiteralOverride(property: StyleProperty, literal: String): Boolean {
        val selected = selectedNode ?: return false
        val normalized = literal.trim()
        return runCatching {
            StyleEngine.setInspectorOverrideLiteral(selected, property, normalized).getOrThrow()
            cachedStyle = null
            styleEditorError = null
            true
        }.getOrElse { error ->
            styleEditorError = error.message?.take(96) ?: "Failed to apply style override."
            false
        }
    }

    internal fun overlayApplyNumericOverride(
        property: StyleProperty,
        numericLiteral: String,
        unitToken: String?,
    ): Boolean {
        val selected = selectedNode ?: return false
        val numberText = numericLiteral.trim()
        if (numberText.isEmpty() || numberText == "-" || numberText == "." || numberText == "-.") {
            return true
        }
        return runCatching {
            val formatted = InspectorEditorRegistry.formatNumericLiteral(property, numberText, unitToken)
            StyleEngine.setInspectorOverrideLiteral(selected, property, formatted).getOrThrow()
            cachedStyle = null
            styleEditorError = null
            true
        }.getOrElse { error ->
            styleEditorError = error.message?.take(96) ?: "Failed to apply style override."
            false
        }
    }

    private fun resetNativePresentation() {
        nativeSelectedHighlight = null
        nativeHoveredHighlight = null
        nativeCursorTooltip = null
        nativeVariableTooltip = null
        nativeStyleEditorRows.clear()
        nativeDropdowns.clear()
        nativeStyleEditorResetRect = Rect(0, 0, 0, 0)
        nativeStyleEditorClearRect = Rect(0, 0, 0, 0)
        nativeDomBodyScrollStateActive = false
        nativeDomPanelScrollYOverride = null
        nativeDomScrollbarTrackRectOverride = null
        nativeDomScrollbarThumbRectOverride = null
    }

    private fun resolvedNativePointerProjectionScrollY(): Int {
        val current = nativeDomPanelScrollYOverride
        return when {
            current != null -> current.coerceAtLeast(0)
            nativeDomLastKnownBodyScrollY > 0 -> nativeDomLastKnownBodyScrollY
            else -> panelScrollY.coerceAtLeast(0)
        }
    }

    private fun selectHovered(lock: Boolean) {
        val hovered = hoveredNode ?: return
        selectedNode = hovered
        selectedKeyToken = hovered.key
        selectedClass = hovered.javaClass
        cachedStyle = null
        styleEditorError = null
        if (lock) {
            mode = InspectorMode.Locked
        }
    }

    private fun deactivateInternal() {
        mode = InspectorMode.Pick
        panelState = InspectorPanelState.Expanded
        hoveredPath = emptyList()
        hoveredNode = null
        hoverDirty = true
        tooltipNodeRef = null
        tooltipLabelCache = ""
        panelActions.clear()
        dropdownLayouts.clear()
        panelBounds = Rect(0, 0, 0, 0)
        minimizedBounds = Rect(0, 0, 0, 0)
        headerBounds = Rect(0, 0, 0, 0)
        contentBounds = Rect(0, 0, 0, 0)
        pointerOverInspectorUi = false
        hoverPickEnabled = true
        styleEditorError = null
        dragMode = DragMode.None
        overlayPanelPointerCapture = false
        overlayPanelAuthorityEnabled = false
        dragMoved = false
        panelScrollY = 0
        panelContentHeight = 0
        nativeDomLastKnownBodyScrollY = 0
        scrollbarTrackRect = Rect(0, 0, 0, 0)
        scrollbarThumbRect = Rect(0, 0, 0, 0)
        scrollbarDragOffsetY = 0
        editSession.resetAll()
        resetNativePresentation()
        colorPickerManager.close()
    }

    private fun rebindSelection() {
        val currentRoot =
            root ?: run {
                selectedNode = null
                selectedKeyToken = null
                selectedClass = null
                cachedStyle = null
                return
            }
        val key = selectedKeyToken
        val klass = selectedClass
        if (key == null || klass == null) {
            if (selectedNode != null && !containsReference(currentRoot, selectedNode!!)) {
                selectedNode = null
                cachedStyle = null
            }
            return
        }
        val rebound = findByKeyAndClass(currentRoot, key, klass)
        if (rebound == null) {
            selectedNode = null
            selectedKeyToken = null
            selectedClass = null
            cachedStyle = null
            styleEditorError = null
            return
        }
        if (selectedNode !== rebound) {
            selectedNode = rebound
            cachedStyle = null
            styleEditorError = null
        }
    }

    private fun refreshHoverIfNeeded() {
        if (!hoverDirty &&
            lastHoverMouseX == mouseX &&
            lastHoverMouseY == mouseY &&
            lastHoverLayoutVersion == layoutVersion
        ) {
            return
        }
        val currentRoot =
            root ?: run {
                hoveredPath = emptyList()
                hoveredNode = null
                hoverDirty = false
                return
            }
        hoveredPath = collectHoverChain(currentRoot, mouseX, mouseY)
        hoveredNode = resolveInspectorHoverCandidate(hoveredPath)
        lastHoverMouseX = mouseX
        lastHoverMouseY = mouseY
        lastHoverLayoutVersion = layoutVersion
        hoverDirty = false
    }

    private fun resolveInspectorHoverCandidate(path: List<DOMNode>): DOMNode? =
        path.lastOrNull { node -> shouldInspectorPickNode(node) }

    private fun shouldInspectorPickNode(node: DOMNode): Boolean = node.display != Display.None

    private fun resolveTooltipLabel(node: DOMNode): String {
        val bounds = node.bounds
        if (tooltipNodeRef === node && tooltipNodeBounds == bounds && tooltipLabelCache.isNotEmpty()) {
            return tooltipLabelCache
        }
        tooltipNodeRef = node
        tooltipNodeBounds = bounds
        tooltipLabelCache =
            "${InspectorPresentationSupport.nodeLabel(node)} ${bounds.width}x${bounds.height} @ ${bounds.x},${bounds.y}"
        return tooltipLabelCache
    }

    private fun selectionStyle(node: DOMNode): StyleInspection {
        val key = node.key
        val klass = node.javaClass
        val cached = cachedStyle
        if (cached != null &&
            cached.key == key &&
            cached.nodeClass == klass &&
            cached.layoutVersion == layoutVersion
        ) {
            return cached.inspection
        }
        val inspection = StyleEngine.inspect(node)
        cachedStyle =
            SelectionStyleCache(
                key = key,
                nodeClass = klass,
                layoutVersion = layoutVersion,
                inspection = inspection,
            )
        return inspection
    }

    private fun styleRows(inspection: StyleInspection): List<String> {
        val computed = inspection.computed
        val rows = ArrayList<String>(StyleProperty.entries.size + 8)
        for (property in StyleProperty.entries) {
            val value = literalFromComputed(computed, property)
            val source = inspection.propertySources[property]
            val sourceLabel = source?.source ?: "default"
            rows += "${property.key}: $value <- $sourceLabel"
        }
        if (inspection.matchedRules.isNotEmpty()) {
            rows += "matched rules:"
            inspection.matchedRules.forEach { row ->
                rows += "  $row"
            }
        }
        return rows
    }

    private fun findPanelAction(mouseX: Int, mouseY: Int): PanelAction? =
        panelActions.lastOrNull { action ->
            isPanelActionVisibleHit(action, mouseX, mouseY)
        }

    private fun isPanelActionVisibleHit(action: PanelAction, mouseX: Int, mouseY: Int): Boolean {
        if (!action.bounds.contains(mouseX, mouseY)) return false
        if (action.kind == ActionKind.Minimize || action.kind == ActionKind.TogglePick) {
            return true
        }
        return contentBounds.contains(mouseX, mouseY)
    }

    private fun findDropdownOptionAction(mouseX: Int, mouseY: Int): PanelAction? {
        val layout = dropdownLayouts.lastOrNull { it.rect.contains(mouseX, mouseY) } ?: return null
        val expectedOp = if (layout.isUnit) EditOperation.SelectUnitOption else EditOperation.SelectValueOption
        return panelActions.lastOrNull { action ->
            action.kind == ActionKind.EditProperty &&
                action.property == layout.property &&
                action.editOperation == expectedOp &&
                action.bounds.contains(mouseX, mouseY)
        }
    }

    internal fun overlayColorPickerActionBounds(property: StyleProperty): Rect? =
        panelActions
            .lastOrNull {
                it.kind == ActionKind.EditProperty &&
                    it.property == property &&
                    it.editOperation == EditOperation.OpenColorPicker
            }?.bounds

    internal fun debugOpenColorPickerForSelection(property: StyleProperty, anchorRect: Rect): Boolean {
        val selected = selectedNode ?: return false
        openColorPicker(selected, property, anchorRect)
        return true
    }

    internal fun overlayPanelRect(): Rect? {
        if (!active) return null
        return currentInspectorRect()
    }

    internal fun overlayExpandedPanelRect(): Rect? {
        if (!active || panelState != InspectorPanelState.Expanded) return null
        return expandedRect
    }

    private fun performPanelAction(action: PanelAction) {
        when (action.kind) {
            ActionKind.Minimize -> {
                minimize()
                return
            }

            ActionKind.TogglePick -> {
                setPickMode(mode != InspectorMode.Pick)
                return
            }

            ActionKind.Parent -> {
                selectParent()
                mode = InspectorMode.Locked
            }

            ActionKind.Child -> {
                selectChild(action.childIndex)
                mode = InspectorMode.Locked
            }

            ActionKind.EditProperty -> {
                val selected = selectedNode
                val property = action.property
                val operation = action.editOperation
                if (selected != null && property != null && operation != null) {
                    applyStyleEdit(selected, property, operation, action.step, action.payload, action.bounds)
                }
                mode = InspectorMode.Locked
            }

            ActionKind.ResetSelectedOverrides -> {
                selectedNode?.let { selected ->
                    StyleEngine.clearInspectorOverride(selected)
                    styleEditorError = null
                    cachedStyle = null
                }
            }

            ActionKind.ClearAllOverrides -> {
                StyleEngine.clearAllInspectorOverrides()
                styleEditorError = null
                cachedStyle = null
            }
        }
    }

    private fun selectParent() {
        val parent = selectedNode?.parent ?: return
        selectedNode = parent
        selectedKeyToken = parent.key
        selectedClass = parent.javaClass
        cachedStyle = null
        styleEditorError = null
    }

    private fun selectChild(index: Int) {
        val selected = selectedNode ?: return
        val children = selected.children.filter { it.display != Display.None }
        if (index !in children.indices) return
        val child = children[index]
        selectedNode = child
        selectedKeyToken = child.key
        selectedClass = child.javaClass
        cachedStyle = null
        styleEditorError = null
    }

    private fun ellipsize(raw: String, maxChars: Int): String {
        if (maxChars <= 1) return raw.take(1)
        if (raw.length <= maxChars) return raw
        val keep = (maxChars - 3).coerceAtLeast(0)
        return raw.take(keep) + "..."
    }

    private fun editablePropertiesFor(selected: DOMNode): List<StyleProperty> {
        val all = StylePropertyRegistry.all.map { it.property }
        val isTextLike = selected.styleType == "text" || selected.styleType.contains("text")
        if (!isTextLike) return all

        val priority =
            listOf(
                StyleProperty.FOREGROUND_COLOR,
                StyleProperty.FONT_ID,
                StyleProperty.FONT_SIZE,
                StyleProperty.TEXT_WRAP,
                StyleProperty.ALIGN,
            )
        val ordered = ArrayList<StyleProperty>(all.size)
        priority.forEach { property ->
            if (property in all) ordered += property
        }
        all.forEach { property ->
            if (property !in ordered) ordered += property
        }
        return ordered
    }

    private fun applyStyleEdit(
        selected: DOMNode,
        property: StyleProperty,
        operation: EditOperation,
        step: Float,
        payload: String?,
        actionBounds: Rect,
    ) {
        runCatching {
            when (operation) {
                EditOperation.ResetProperty -> StyleEngine.clearInspectorOverride(selected, property)
                EditOperation.Decrement -> {
                    val next = adjustNumericLiteral(selected, property, -step)
                    StyleEngine.setInspectorOverrideLiteral(selected, property, next).getOrThrow()
                }

                EditOperation.Increment -> {
                    val next = adjustNumericLiteral(selected, property, step)
                    StyleEngine.setInspectorOverrideLiteral(selected, property, next).getOrThrow()
                }

                EditOperation.BeginTextEdit -> beginTextEdit(selected, property, actionBounds)
                EditOperation.OpenColorPicker -> openColorPicker(selected, property, actionBounds)
                EditOperation.ToggleValueSelect -> {
                    val wasOpen = openValueSelectProperty == property
                    openValueSelectProperty = if (wasOpen) null else property
                    openValueSelectScrollIndex = 0
                    openUnitSelectProperty = null
                    openUnitSelectScrollIndex = 0
                    editSession.clearActiveEdit()
                }

                EditOperation.SelectValueOption -> {
                    val option = payload ?: error("Missing option payload.")
                    StyleEngine.setInspectorOverrideLiteral(selected, property, option).getOrThrow()
                    openValueSelectProperty = null
                    openValueSelectScrollIndex = 0
                    editSession.clearActiveEdit()
                }

                EditOperation.ToggleUnitSelect -> {
                    val wasOpen = openUnitSelectProperty == property
                    openUnitSelectProperty = if (wasOpen) null else property
                    openUnitSelectScrollIndex = 0
                    openValueSelectProperty = null
                    openValueSelectScrollIndex = 0
                }

                EditOperation.SelectUnitOption -> {
                    val current = literalForEdit(selected, property)
                    val parsed = InspectorEditorRegistry.parseNumericLiteral(property, current)
                    val numberText = parsed?.numberText ?: "0"
                    val nextLiteral =
                        InspectorEditorRegistry.formatNumericLiteral(
                            property = property,
                            numberText = numberText,
                            unitToken = payload,
                        )
                    StyleEngine.setInspectorOverrideLiteral(selected, property, nextLiteral).getOrThrow()
                    openUnitSelectProperty = null
                    openUnitSelectScrollIndex = 0
                }
            }
            cachedStyle = null
            styleEditorError = null
        }.onFailure { error ->
            styleEditorError = error.message?.take(96) ?: "Failed to apply style override."
        }
    }

    private fun beginTextEdit(selected: DOMNode, property: StyleProperty, actionBounds: Rect) {
        if (activeEditProperty != property) {
            val current = literalForEdit(selected, property)
            val descriptor =
                InspectorEditorRegistry.describe(
                    property = property,
                    literal = current,
                    expression = StyleEngine.inspectorOverrideFor(selected, property),
                )
            if (descriptor.kind == InspectorEditorKind.NumericInput) {
                val parsed = InspectorEditorRegistry.parseNumericLiteral(property, current)
                editSession.begin(
                    property = property,
                    initialBuffer = parsed?.numberText ?: "0",
                    initialUnit = parsed?.unit ?: InspectorEditorRegistry.defaultNumericUnit(property),
                    isNumeric = true,
                )
            } else {
                editSession.begin(
                    property = property,
                    initialBuffer = current,
                    initialUnit = null,
                    isNumeric = false,
                )
            }
        } else {
            activeEditState.clampToLength(activeEditBuffer.length)
        }
        val caret = caretIndexFromPointer(activeEditBuffer, actionBounds)
        TextEditOps.moveCaretWithSelection(activeEditState, caret, activeEditBuffer.length, KeyModifiers.shiftDown)
        activeEditState.resetBlinkClock()
        editSession.textSelectionDragActive = true
        editSession.textSelectionDragProperty = property
        editSession.textSelectionDragRect = actionBounds
        editSession.closeAllDropdowns()
    }

    private fun updateActiveTextSelectionFromPointer(): Boolean {
        if (!editSession.textSelectionDragActive) return false
        val property =
            editSession.textSelectionDragProperty ?: run {
                stopActiveTextSelectionDrag()
                return false
            }
        if (activeEditProperty != property) {
            stopActiveTextSelectionDrag()
            return false
        }
        val pointerRect = resolveActiveEditPointerRect(property) ?: editSession.textSelectionDragRect
        val caret = caretIndexFromPointer(activeEditBuffer, pointerRect)
        TextEditOps.moveCaretWithSelection(activeEditState, caret, activeEditBuffer.length, extend = true)
        activeEditState.resetBlinkClock()
        return true
    }

    private fun resolveActiveEditPointerRect(property: StyleProperty): Rect? {
        val row = nativeStyleEditorRows.lastOrNull { it.property == property } ?: return null
        return row.inputRect ?: row.controlRect
    }

    private fun stopActiveTextSelectionDrag() {
        editSession.textSelectionDragActive = false
        editSession.textSelectionDragProperty = null
        editSession.textSelectionDragRect = Rect(0, 0, 0, 0)
    }

    private fun caretIndexFromPointer(text: String, bounds: Rect): Int {
        val textAreaStart = bounds.x + 8
        val textAreaWidth = (bounds.width - 12).coerceAtLeast(1)
        val charWidth = (secondaryFontSizePx * 0.56f).toInt().coerceAtLeast(6)
        val local = (mouseX - textAreaStart).coerceIn(0, textAreaWidth)
        val rawIndex = ((local + charWidth / 2) / charWidth)
        return rawIndex.coerceIn(0, text.length)
    }

    private fun openColorPicker(selected: DOMNode, property: StyleProperty, anchorRect: Rect) {
        val literal = literalForEdit(selected, property)
        val parsedByStyle = runCatching { RgbaColor.fromArgbInt(parseColor(literal)) }.getOrNull()
        val parsedByCodec = ColorTextCodec.parse(literal)
        val initialColor = (parsedByStyle ?: parsedByCodec?.color ?: RgbaColor.WHITE).normalized()
        val initialMode = parsedByCodec?.detectedMode ?: ColorFormatMode.HEX
        colorPickerManager.open(
            anchorRect = anchorRect,
            title = "Edit ${property.key}",
            state =
                ColorPickerState(
                    color = initialColor,
                    previous = initialColor,
                    mode = initialMode,
                    alphaEnabled = true,
                    closeOnSelect = false,
                ),
            closeOnOutsideClick = false,
            onPreview = { color ->
                applyInspectorColorLiteral(selected, property, color)
            },
            onChange = { color ->
                applyInspectorColorLiteral(selected, property, color)
            },
            onCommit = { color ->
                applyInspectorColorLiteral(selected, property, color)
            },
        )
    }

    private fun applyInspectorColorLiteral(selected: DOMNode, property: StyleProperty, color: RgbaColor) {
        runCatching {
            val argb =
                color
                    .toArgbInt()
                    .toUInt()
                    .toString(16)
                    .uppercase()
                    .padStart(8, '0')
            StyleEngine.setInspectorOverrideLiteral(selected, property, "#$argb").getOrThrow()
            cachedStyle = null
            styleEditorError = null
        }.onFailure { error ->
            styleEditorError = error.message?.take(96) ?: "Failed to apply style override."
        }
    }

    private fun shouldCommitActiveEdit(action: PanelAction?): Boolean {
        val editing = activeEditProperty ?: return false
        if (action == null) return true
        if (action.kind != ActionKind.EditProperty) return true
        if (action.property != editing) return true
        return action.editOperation != EditOperation.BeginTextEdit
    }

    private fun commitActiveTextEdit() {
        val selected = selectedNode ?: return
        val property = activeEditProperty ?: return
        runCatching {
            val literal =
                if (activeEditIsNumeric) {
                    InspectorEditorRegistry.formatNumericLiteral(
                        property = property,
                        numberText = activeEditBuffer,
                        unitToken = activeEditUnit?.token,
                    )
                } else {
                    activeEditBuffer.trim()
                }
            val normalized =
                if (literal.isEmpty()) {
                    if (activeEditIsNumeric) InspectorEditorRegistry.defaultNumericLiteral(property) else ""
                } else {
                    literal
                }
            StyleEngine.setInspectorOverrideLiteral(selected, property, normalized).getOrThrow()
            cachedStyle = null
            styleEditorError = null
        }.onFailure { error ->
            styleEditorError = error.message?.take(96) ?: "Failed to apply style override."
        }
        editSession.clearActiveEdit()
    }

    private fun literalForEdit(selected: DOMNode, property: StyleProperty): String {
        val override = StyleEngine.inspectorOverrideFor(selected, property)
        if (override != null) return expressionLabel(override)
        return literalFromComputed(selectionStyle(selected).computed, property)
    }

    private fun adjustNumericLiteral(selected: DOMNode, property: StyleProperty, delta: Float): String {
        val descriptor = StylePropertyRegistry.descriptor(property)
        val current = literalForEdit(selected, property)
        return when (descriptor.valueType) {
            StyleEditorValueType.IntNumber -> {
                val base = runCatching { parseIntLike(current) }.getOrElse { 0 }
                val next = (base + delta.toInt()).coerceAtLeast(descriptor.minInt)
                next.toString()
            }

            StyleEditorValueType.LengthPx -> {
                val base =
                    runCatching {
                        parseLengthPxInt(
                            raw = current,
                            allowNegative = false,
                        )
                    }.getOrElse { descriptor.minInt }
                val next = (base + delta.toInt()).coerceAtLeast(descriptor.minInt)
                pxLiteral(next)
            }

            StyleEditorValueType.LineHeight -> {
                val normalized = current.trim().lowercase()
                if (normalized == "normal") {
                    CssLength(delta.coerceAtLeast(0f), CssUnit.Px).toCssLiteral()
                } else {
                    val base =
                        runCatching { parseCssLength(current, allowUnitlessZero = true) }
                            .getOrElse { CssLength.ZERO_PX }
                    val next = (base.value + delta).coerceAtLeast(0f)
                    CssLength(next, base.unit).toCssLiteral()
                }
            }

            StyleEditorValueType.OptionalIntNumber -> {
                val base = parseOptionalInt(current) ?: descriptor.minInt
                val next = (base + delta.toInt()).coerceAtLeast(descriptor.minInt)
                next.toString()
            }

            StyleEditorValueType.OptionalLengthPx -> {
                val base =
                    parseOptionalLengthPxInt(
                        raw = current,
                        allowNegative = false,
                    ) ?: descriptor.minInt
                val next = (base + delta.toInt()).coerceAtLeast(descriptor.minInt)
                pxLiteral(next)
            }

            StyleEditorValueType.Spacing -> {
                val currentInsets = parseSpacingShorthand(current)
                val next = (currentInsets.top + delta.toInt()).coerceAtLeast(0)
                next.toString()
            }

            StyleEditorValueType.SpacingLengthPx -> {
                val allowNegative = property == StyleProperty.MARGIN
                val currentInsets =
                    parseSpacingShorthand(
                        raw = current,
                        allowNegative = allowNegative,
                    )
                val rawNext = currentInsets.top + delta.toInt()
                val next = if (allowNegative) rawNext else rawNext.coerceAtLeast(0)
                pxLiteral(next)
            }

            StyleEditorValueType.FloatNumber -> {
                val base = runCatching { parseFloatLike(current) }.getOrElse { descriptor.minFloat }
                val next = (base + delta).coerceAtLeast(descriptor.minFloat)
                formatFloatLiteral(next)
            }

            else -> error("Property '${property.key}' is not numeric.")
        }
    }

    private fun expressionLabel(expression: StyleExpression): String =
        when (expression) {
            is StyleExpression.Literal -> expression.value
            is StyleExpression.VariableRef -> "var(${expression.name})"
        }

    private fun literalFromComputed(style: ComputedStyle, property: StyleProperty): String =
        when (property) {
            StyleProperty.MARGIN -> spacingLiteral(style.margin)
            StyleProperty.PADDING -> spacingLiteral(style.padding)
            StyleProperty.BACKGROUND_COLOR -> style.backgroundColor?.let(::colorLabel) ?: "none"
            StyleProperty.BACKGROUND_IMAGE -> style.backgroundImage ?: "none"
            StyleProperty.BORDER_COLOR -> colorLabel(style.borderColor)
            StyleProperty.BORDER_WIDTH -> style.borderWidth.toCssLiteral()
            StyleProperty.BORDER_RADIUS -> style.borderRadius.toCssLiteral()
            StyleProperty.FOREGROUND_COLOR -> colorLabel(style.foregroundColor)
            StyleProperty.FONT_ID -> style.fontId ?: "minecraft"
            StyleProperty.FONT_SIZE ->
                style.fontSizeValue?.toCssLiteral() ?: (
                    style.fontSize?.let(::pxLiteral)
                        ?: "auto"
                )
            StyleProperty.LINE_HEIGHT ->
                when (val lineHeightValue = style.lineHeight) {
                    is LineHeightValue.Length -> lineHeightValue.value.toCssLiteral()
                    LineHeightValue.Normal -> "normal"
                }

            StyleProperty.FONT_WEIGHT ->
                style.fontWeight.name
                    .lowercase()
            StyleProperty.FONT_STYLE ->
                style.fontStyle.name
                    .lowercase()
            StyleProperty.TEXT_DECORATION ->
                when (style.textDecoration) {
                    TextDecoration.None -> "none"
                    TextDecoration.Underline -> "underline"
                    TextDecoration.Strikethrough -> "strikethrough"
                    TextDecoration.UnderlineStrikethrough -> "underline-strikethrough"
                }

            StyleProperty.OBFUSCATED -> style.obfuscated.toString()
            StyleProperty.WIDTH -> style.width?.toCssLiteral() ?: "auto"
            StyleProperty.HEIGHT -> style.height?.toCssLiteral() ?: "auto"
            StyleProperty.MIN_WIDTH -> style.minWidth?.toCssLiteral() ?: "auto"
            StyleProperty.MIN_HEIGHT -> style.minHeight?.toCssLiteral() ?: "auto"
            StyleProperty.MAX_WIDTH -> style.maxWidth?.toCssLiteral() ?: "auto"
            StyleProperty.MAX_HEIGHT -> style.maxHeight?.toCssLiteral() ?: "auto"
            StyleProperty.ALIGN ->
                style.align.name
                    .lowercase()
            StyleProperty.DISPLAY ->
                style.display.name
                    .lowercase()
            StyleProperty.POSITION ->
                style.position.name
                    .lowercase()
            StyleProperty.LEFT -> style.left?.toCssLiteral() ?: "auto"
            StyleProperty.TOP -> style.top?.toCssLiteral() ?: "auto"
            StyleProperty.RIGHT -> style.right?.toCssLiteral() ?: "auto"
            StyleProperty.BOTTOM -> style.bottom?.toCssLiteral() ?: "auto"
            StyleProperty.Z_INDEX -> style.zIndex.toString()
            StyleProperty.OVERFLOW ->
                if (style.overflowX == style.overflowY) {
                    style.overflowX.name
                        .lowercase()
                } else {
                    "${style.overflowX.name.lowercase()} ${style.overflowY.name.lowercase()}"
                }
            StyleProperty.OVERFLOW_X ->
                style.overflowX.name
                    .lowercase()
            StyleProperty.OVERFLOW_Y ->
                style.overflowY.name
                    .lowercase()
            StyleProperty.FLEX_DIRECTION ->
                style.flexDirection.name
                    .lowercase()
            StyleProperty.JUSTIFY_CONTENT ->
                style.justifyContent.name
                    .replace(Regex("([a-z])([A-Z])"), "$1-$2")
                    .lowercase()

            StyleProperty.ALIGN_ITEMS ->
                style.alignItems.name
                    .lowercase()
            StyleProperty.JUSTIFY_ITEMS ->
                style.justifyItems.name
                    .lowercase()
            StyleProperty.GAP -> style.gap.toCssLiteral()
            StyleProperty.FLEX_GROW -> formatFloatLiteral(style.flexGrow)
            StyleProperty.FLEX_SHRINK -> formatFloatLiteral(style.flexShrink)
            StyleProperty.FLEX_BASIS -> style.flexBasis?.toCssLiteral() ?: "auto"
            StyleProperty.GRID_COLUMNS -> style.gridColumns.toString()
            StyleProperty.GRID_ROWS -> style.gridRows?.toString() ?: "auto"
            StyleProperty.GRID_AUTO_FLOW ->
                style.gridAutoFlow.name
                    .lowercase()
            StyleProperty.GRID_COLUMN_SPAN -> style.gridColumnSpan.toString()
            StyleProperty.GRID_ROW_SPAN -> style.gridRowSpan.toString()
            StyleProperty.TEXT_WRAP -> if (style.textWrap == TextWrap.Wrap) "wrap" else "nowrap"
            StyleProperty.TEXT_FORMATTING ->
                when (style.textFormatting) {
                    TextFormatting.None -> "none"
                    TextFormatting.Minecraft -> "minecraft"
                }

            StyleProperty.TRANSFORM ->
                buildString {
                    append("translate(")
                    append(style.transform.translateX)
                    append(",")
                    append(style.transform.translateY)
                    append(") scale(")
                    append(style.transform.scaleX)
                    append(",")
                    append(style.transform.scaleY)
                    append(") rotate(")
                    append(style.transform.rotateDeg)
                    append("deg)")
                }

            StyleProperty.TRANSFORM_ORIGIN -> "${style.transformOrigin.originX} ${style.transformOrigin.originY}"
            StyleProperty.OPACITY -> formatFloatLiteral(style.opacity)
        }

    private fun spacingLiteral(value: LengthInsets): String =
        "${value.top.toCssLiteral()} ${value.right.toCssLiteral()} ${value.bottom.toCssLiteral()} ${value.left.toCssLiteral()}"

    private fun pxLiteral(value: Int): String = "${value}px"

    private fun formatFloatLiteral(value: Float): String {
        val rounded = ((value * 100f).toInt()) / 100f
        return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    }

    private fun startMinimizedMoveDrag(mouseX: Int, mouseY: Int) {
        dragMode = DragMode.MinimizedMove
        dragStartMouseX = mouseX
        dragStartMouseY = mouseY
        dragStartOffsetX = mouseX - minimizedPosX
        dragStartOffsetY = mouseY - minimizedPosY
        dragMoved = false
    }

    private fun startScrollbarDrag(mouseX: Int, mouseY: Int): Boolean {
        if (panelState != InspectorPanelState.Expanded) return false
        if (scrollbarTrackRect.width <= 0 || scrollbarTrackRect.height <= 0) return false
        if (!scrollbarTrackRect.contains(mouseX, mouseY)) return false
        if (scrollbarThumbRect.contains(mouseX, mouseY)) {
            dragMode = DragMode.ScrollbarThumb
            scrollbarDragOffsetY = (mouseY - scrollbarThumbRect.y).coerceIn(0, scrollbarThumbRect.height)
            dragMoved = false
            return true
        }
        val targetThumbCenterY = mouseY
        scrollbarDragOffsetY = (scrollbarThumbRect.height / 2).coerceAtLeast(1)
        updateScrollbarFromThumbTop(targetThumbCenterY - scrollbarDragOffsetY)
        dragMode = DragMode.ScrollbarThumb
        dragMoved = true
        return true
    }

    private fun updateScrollbarDrag(mouseY: Int) {
        if (scrollbarTrackRect.height <= 0 || scrollbarThumbRect.height <= 0) return
        updateScrollbarFromThumbTop(mouseY - scrollbarDragOffsetY)
        dragMoved = true
    }

    private fun updateScrollbarFromThumbTop(rawThumbTop: Int) {
        val maxScroll = (panelContentHeight - contentBounds.height).coerceAtLeast(0)
        if (maxScroll <= 0) {
            panelScrollY = 0
            return
        }
        val travel = (scrollbarTrackRect.height - scrollbarThumbRect.height).coerceAtLeast(0)
        if (travel <= 0) {
            panelScrollY = 0
            return
        }
        val minTop = scrollbarTrackRect.y
        val maxTop = scrollbarTrackRect.y + travel
        val thumbTop = rawThumbTop.coerceIn(minTop, maxTop)
        val progress = (thumbTop - minTop).toFloat() / travel.toFloat()
        panelScrollY = (progress * maxScroll.toFloat()).toInt().coerceIn(0, maxScroll)
    }

    private fun startExpandedDrag(mode: DragMode, mouseX: Int, mouseY: Int) {
        dragMode = mode
        dragStartMouseX = mouseX
        dragStartMouseY = mouseY
        dragStartRect = expandedRect
        dragStartOffsetX = mouseX - expandedRect.x
        dragStartOffsetY = mouseY - expandedRect.y
        if (mode == DragMode.Move) {
            paneMoveDrag.begin(mouseX, mouseY, expandedRect)
        } else {
            paneMoveDrag.end()
        }
        dragMoved = false
    }

    private fun updateMinimizedMoveDrag(
        mouseX: Int,
        mouseY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        val nextX = mouseX - dragStartOffsetX
        val nextY = mouseY - dragStartOffsetY
        if (!dragMoved &&
            (kotlin.math.abs(nextX - minimizedPosX) >= 2 || kotlin.math.abs(nextY - minimizedPosY) >= 2)
        ) {
            dragMoved = true
        }
        minimizedPosX = nextX
        minimizedPosY = nextY
        clampMinimizedPosition(viewportWidth, viewportHeight)
    }

    private fun updateExpandedDrag(
        mouseX: Int,
        mouseY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        val dx = mouseX - dragStartMouseX
        val dy = mouseY - dragStartMouseY
        var left = dragStartRect.x
        var top = dragStartRect.y
        var right = dragStartRect.x + dragStartRect.width
        var bottom = dragStartRect.y + dragStartRect.height

        when (dragMode) {
            DragMode.Move -> {
                val movedRect =
                    paneMoveDrag.update(
                        mouseX = mouseX,
                        mouseY = mouseY,
                        viewportWidth = viewportWidth,
                        viewportHeight = viewportHeight,
                        clamp = ::clampExpandedRect,
                    )
                expandedRect = movedRect
                if (paneMoveDrag.moved) {
                    dragMoved = true
                }
                return
            }

            DragMode.ResizeLeft, DragMode.ResizeTopLeft, DragMode.ResizeBottomLeft -> left += dx
            DragMode.ResizeRight, DragMode.ResizeTopRight, DragMode.ResizeBottomRight -> right += dx
            else -> Unit
        }
        when (dragMode) {
            DragMode.ResizeTop, DragMode.ResizeTopLeft, DragMode.ResizeTopRight -> top += dy
            DragMode.ResizeBottom, DragMode.ResizeBottomLeft, DragMode.ResizeBottomRight -> bottom += dy
            else -> Unit
        }

        if (right - left < minPanelWidth) {
            when (dragMode) {
                DragMode.ResizeLeft, DragMode.ResizeTopLeft, DragMode.ResizeBottomLeft -> left = right - minPanelWidth
                DragMode.ResizeRight, DragMode.ResizeTopRight, DragMode.ResizeBottomRight ->
                    right =
                        left + minPanelWidth

                else -> Unit
            }
        }
        if (bottom - top < minPanelHeight) {
            when (dragMode) {
                DragMode.ResizeTop, DragMode.ResizeTopLeft, DragMode.ResizeTopRight -> top = bottom - minPanelHeight
                DragMode.ResizeBottom, DragMode.ResizeBottomLeft, DragMode.ResizeBottomRight ->
                    bottom =
                        top + minPanelHeight

                else -> Unit
            }
        }

        val resized =
            clampExpandedRect(
                Rect(
                    left,
                    top,
                    (right - left).coerceAtLeast(minPanelWidth),
                    (bottom - top).coerceAtLeast(minPanelHeight),
                ),
                viewportWidth,
                viewportHeight,
            )
        if (!dragMoved &&
            (
                kotlin.math.abs(resized.width - dragStartRect.width) >= 2 ||
                    kotlin.math.abs(resized.height - dragStartRect.height) >= 2 ||
                    kotlin.math.abs(resized.x - dragStartRect.x) >= 2 ||
                    kotlin.math.abs(resized.y - dragStartRect.y) >= 2
            )
        ) {
            dragMoved = true
        }
        expandedRect = resized
    }

    private fun resolveResizeDragMode(mouseX: Int, mouseY: Int): DragMode {
        val rect = expandedRect
        if (!rect.contains(mouseX, mouseY)) return DragMode.None
        val leftZone = mouseX <= rect.x + resizeHandleSize
        val rightZone = mouseX >= rect.x + rect.width - resizeHandleSize
        val topZone = mouseY <= rect.y + resizeHandleSize
        val bottomZone = mouseY >= rect.y + rect.height - resizeHandleSize

        if (leftZone && topZone) return DragMode.ResizeTopLeft
        if (rightZone && topZone) return DragMode.ResizeTopRight
        if (leftZone && bottomZone) return DragMode.ResizeBottomLeft
        if (rightZone && bottomZone) return DragMode.ResizeBottomRight
        if (leftZone) return DragMode.ResizeLeft
        if (rightZone) return DragMode.ResizeRight
        if (topZone) return DragMode.ResizeTop
        if (bottomZone) return DragMode.ResizeBottom
        return DragMode.None
    }

    private fun updateHoverGate() {
        pointerOverInspectorUi = hitTestUi(mouseX, mouseY)
        hoverPickEnabled = active && mode == InspectorMode.Pick && dragMode == DragMode.None && !pointerOverInspectorUi
    }

    private fun clearHoveredState() {
        hoveredPath = emptyList()
        hoveredNode = null
        hoverDirty = false
        tooltipNodeRef = null
        tooltipLabelCache = ""
    }

    private fun resolveTooltipRect(
        viewportWidth: Int,
        viewportHeight: Int,
        boxWidth: Int,
        boxHeight: Int,
    ): Rect {
        val inspectorRect = currentInspectorRect()
        val candidates =
            listOf(
                mouseX + 12 to mouseY + 12,
                mouseX + 12 to mouseY - boxHeight - 12,
                mouseX - boxWidth - 12 to mouseY + 12,
                mouseX - boxWidth - 12 to mouseY - boxHeight - 12,
                mouseX + 16 to mouseY - boxHeight / 2,
            )
        candidates.forEach { (rawX, rawY) ->
            val candidate = clampTooltipRect(rawX, rawY, boxWidth, boxHeight, viewportWidth, viewportHeight)
            if (!rectIntersects(candidate, inspectorRect)) {
                return candidate
            }
        }
        return clampTooltipRect(mouseX + 12, mouseY + 12, boxWidth, boxHeight, viewportWidth, viewportHeight)
    }

    private fun currentInspectorRect(): Rect {
        if (panelBounds.width > 0 && panelBounds.height > 0) {
            return panelBounds
        }
        return when (panelState) {
            InspectorPanelState.Expanded -> expandedRect
            InspectorPanelState.Minimized -> Rect(minimizedPosX, minimizedPosY, minimizedWidth(), minimizedHeight())
        }
    }

    private fun clampTooltipRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ): Rect {
        val clampedX = x.coerceIn(2, (viewportWidth - width - 2).coerceAtLeast(2))
        val clampedY = y.coerceIn(2, (viewportHeight - height - 2).coerceAtLeast(2))
        return Rect(clampedX, clampedY, width, height)
    }

    private fun rectIntersects(a: Rect, b: Rect): Boolean {
        if (a.width <= 0 || a.height <= 0 || b.width <= 0 || b.height <= 0) return false
        return a.x < b.x + b.width &&
            a.x + a.width > b.x &&
            a.y < b.y + b.height &&
            a.y + a.height > b.y
    }

    private fun isInsideInspectorUi(mouseX: Int, mouseY: Int): Boolean {
        if (dropdownLayouts.any { it.rect.contains(mouseX, mouseY) }) {
            return true
        }
        if (panelActions.any { action -> isPanelActionVisibleHit(action, mouseX, mouseY) }) {
            return true
        }
        val bounds =
            when (panelState) {
                InspectorPanelState.Expanded -> expandedRect
                InspectorPanelState.Minimized -> Rect(minimizedPosX, minimizedPosY, minimizedWidth(), minimizedHeight())
            }
        return bounds.contains(mouseX, mouseY)
    }

    private fun clampExpandedRect(rect: Rect, viewportWidth: Int, viewportHeight: Int): Rect {
        val safeViewportW = viewportWidth.coerceAtLeast(minPanelWidth + viewportMargin * 2)
        val safeViewportH = viewportHeight.coerceAtLeast(minPanelHeight + viewportMargin * 2)
        val maxW = (safeViewportW - viewportMargin * 2).coerceAtLeast(minPanelWidth)
        val maxH = (safeViewportH - viewportMargin * 2).coerceAtLeast(minPanelHeight)
        val width = rect.width.coerceIn(minPanelWidth, maxW)
        val height = rect.height.coerceIn(minPanelHeight, maxH)
        val maxX = (safeViewportW - width - viewportMargin).coerceAtLeast(viewportMargin)
        val maxY = (safeViewportH - height - viewportMargin).coerceAtLeast(viewportMargin)
        val x = rect.x.coerceIn(viewportMargin, maxX)
        val y = rect.y.coerceIn(viewportMargin, maxY)
        return Rect(x, y, width, height)
    }

    private fun clampMinimizedPosition(viewportWidth: Int, viewportHeight: Int) {
        val safeViewportW = viewportWidth.coerceAtLeast(minimizedWidth() + viewportMargin * 2)
        val safeViewportH = viewportHeight.coerceAtLeast(minimizedHeight() + viewportMargin * 2)
        val maxX = (safeViewportW - minimizedWidth() - viewportMargin).coerceAtLeast(viewportMargin)
        val maxY = (safeViewportH - minimizedHeight() - viewportMargin).coerceAtLeast(viewportMargin)
        minimizedPosX = minimizedPosX.coerceIn(viewportMargin, maxX)
        minimizedPosY = minimizedPosY.coerceIn(viewportMargin, maxY)
    }

    private fun minimizedWidth(): Int = minChipWidth

    private fun minimizedHeight(): Int = chipHeight

    private fun containsReference(root: DOMNode, target: DOMNode): Boolean {
        if (root === target) return true
        root.children.forEach { child ->
            if (containsReference(child, target)) return true
        }
        return false
    }

    private fun findByKeyAndClass(node: DOMNode, key: Any, klass: Class<out DOMNode>): DOMNode? {
        if (node.key == key && node.javaClass == klass) return node
        node.children.forEach { child ->
            val found = findByKeyAndClass(child, key, klass)
            if (found != null) return found
        }
        return null
    }

    private fun nodeLabel(node: DOMNode): String {
        val key = node.key?.toString() ?: "<no-key>"
        return "${node.styleType}[$key]"
    }

    private fun pathToken(node: DOMNode): String {
        val key = node.key?.toString() ?: "?"
        return "${node.styleType}:$key"
    }

    private fun rectLabel(rect: Rect): String = "${rect.x},${rect.y},${rect.width}x${rect.height}"

    private fun spacingLabel(value: Insets): String = "${value.top}/${value.right}/${value.bottom}/${value.left}"

    private fun colorLabel(color: Int): String {
        val hex =
            color
                .toUInt()
                .toString(16)
                .uppercase()
                .padStart(8, '0')
        return "#$hex"
    }
}
