package org.dreamfinity.dsgl.core.inspector

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.AffineTransform2D
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.*

enum class InspectorMode {
    Pick,
    Locked
}

enum class InspectorPanelState {
    Expanded,
    Minimized
}

class InspectorController {
    private enum class EditOperation {
        CyclePrev,
        CycleNext,
        Decrement,
        Increment,
        ResetProperty
    }

    private enum class ActionKind {
        Minimize,
        TogglePick,
        Parent,
        Child,
        EditProperty,
        ResetSelectedOverrides,
        ClearAllOverrides
    }

    private data class PanelAction(
        val bounds: Rect,
        val kind: ActionKind,
        val childIndex: Int = -1,
        val property: StyleProperty? = null,
        val editOperation: EditOperation? = null,
        val step: Float = 1f
    )

    private data class SelectionStyleCache(
        val key: Any?,
        val nodeClass: Class<out DOMNode>,
        val layoutVersion: Long,
        val inspection: StyleInspection
    )

    private data class NodeBoxes(
        val margin: Rect,
        val border: Rect,
        val padding: Rect,
        val content: Rect,
        val parentContent: Rect?
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
        MinimizedMove
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
        get() = dragMode != DragMode.None

    val hoveredKey: String?
        get() = hoveredNode?.key?.toString()

    val selectedKey: String?
        get() = selectedKeyToken?.toString()
    val panelScrollOffsetY: Int
        get() = panelScrollY

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
    private var cachedStyle: SelectionStyleCache? = null
    private var styleEditorError: String? = null
    private var expandedRect: Rect = Rect(22, 18, 360, 280)
    private var minimizedPosX: Int = 22
    private var minimizedPosY: Int = 18
    private var dragMode: DragMode = DragMode.None
    private var dragStartMouseX: Int = 0
    private var dragStartMouseY: Int = 0
    private var dragStartRect: Rect = expandedRect
    private var dragStartOffsetX: Int = 0
    private var dragStartOffsetY: Int = 0
    private var dragMoved: Boolean = false
    private var viewportW: Int = 0
    private var viewportH: Int = 0
    private var lastHandledPointerEvent: String = "none"
    private var pointerOverInspectorUi: Boolean = false
    private var hoverPickEnabled: Boolean = true
    private var panelScrollY: Int = 0
    private var panelContentHeight: Int = 0

    private val minPanelWidth: Int = 240
    private val minPanelHeight: Int = 160
    private val minChipWidth: Int = 160
    private val chipHeight: Int = 26
    private val viewportMargin: Int = 2
    private val resizeHandleSize: Int = 8

    fun toggle() {
        active = !active
        if (!active) deactivateInternal()
    }

    fun deactivate() {
        if (!active) return
        active = false
        deactivateInternal()
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
        dragMoved = false
    }

    fun restore() {
        if (!active) return
        expandedRect = expandedRect.copy(x = minimizedPosX, y = minimizedPosY)
        expandedRect = clampExpandedRect(expandedRect, viewportW, viewportH)
        panelState = InspectorPanelState.Expanded
        dragMode = DragMode.None
        dragMoved = false
    }

    fun blocksUnderlyingInput(): Boolean = active && (mode == InspectorMode.Pick || dragMode != DragMode.None)

    fun shouldConsumePointer(mouseX: Int, mouseY: Int): Boolean {
        if (!active) return false
        if (dragMode != DragMode.None) return true
        if (mode == InspectorMode.Pick) return true
        return hitTestUi(mouseX, mouseY)
    }

    fun shouldConsumeWheel(mouseX: Int, mouseY: Int): Boolean {
        if (!active) return false
        if (dragMode != DragMode.None) return true
        if (mode == InspectorMode.Pick) return true
        return hitTestUi(mouseX, mouseY)
    }

    fun shouldConsumeKeyboard(mouseX: Int, mouseY: Int): Boolean {
        if (!active) return false
        if (dragMode != DragMode.None) return true
        if (mode == InspectorMode.Pick) return true
        return hitTestUi(mouseX, mouseY)
    }

    fun markPointerHandled(reason: String) {
        lastHandledPointerEvent = reason
    }

    fun hitTestUi(mouseX: Int, mouseY: Int): Boolean {
        return isInsideInspectorUi(mouseX, mouseY)
    }

    fun onLayoutCommitted(root: DOMNode, layoutVersion: Long) {
        this.root = root
        this.layoutVersion = layoutVersion
        rebindSelection()
        updateHoverGate()
        if (hoverPickEnabled) {
            refreshHover()
        } else {
            clearHoveredState()
        }
    }

    fun onCursorMoved(mouseX: Int, mouseY: Int) {
        this.mouseX = mouseX
        this.mouseY = mouseY
        updateHoverGate()
        if (!hoverPickEnabled) {
            clearHoveredState()
            return
        }
        refreshHover()
    }

    fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean {
        if (!active || delta == 0) return false
        this.mouseX = mouseX
        this.mouseY = mouseY
        if (dragMode != DragMode.None) return true
        if (panelState != InspectorPanelState.Expanded) {
            return hitTestUi(mouseX, mouseY) || mode == InspectorMode.Pick
        }
        if (!contentBounds.contains(mouseX, mouseY)) {
            return hitTestUi(mouseX, mouseY) || mode == InspectorMode.Pick
        }

        val maxScroll = maxOf(0, panelContentHeight - contentBounds.height)
        val steps = (kotlin.math.abs(delta) / 120).coerceAtLeast(1)
        val amount = steps * 18
        val next = if (delta < 0) {
            (panelScrollY + amount).coerceAtMost(maxScroll)
        } else {
            (panelScrollY - amount).coerceAtLeast(0)
        }
        panelScrollY = next
        return true
    }

    fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        if (!active) return false
        if (button != MouseButton.LEFT) {
            return shouldConsumePointer(mouseX, mouseY)
        }
        this.mouseX = mouseX
        this.mouseY = mouseY
        if (panelState == InspectorPanelState.Minimized) {
            if (minimizedBounds.contains(mouseX, mouseY)) {
                startMinimizedMoveDrag(mouseX, mouseY)
                return true
            }
            if (mode == InspectorMode.Pick) {
                selectHovered(lock = true)
                return true
            }
            return false
        }

        if (resolvePanelAction(mouseX, mouseY)) {
            return true
        }
        val resizeMode = resolveResizeDragMode(mouseX, mouseY)
        if (resizeMode != DragMode.None) {
            startExpandedDrag(resizeMode, mouseX, mouseY)
            return true
        }
        if (headerBounds.contains(mouseX, mouseY)) {
            startExpandedDrag(DragMode.Move, mouseX, mouseY)
            return true
        }
        if (panelBounds.contains(mouseX, mouseY)) {
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
        val wasDragging = dragMode != DragMode.None
        val endedMode = dragMode
        val clickLike = !dragMoved
        dragMode = DragMode.None
        dragMoved = false
        if (!wasDragging) return false
        if (endedMode == DragMode.MinimizedMove &&
            clickLike &&
            panelState == InspectorPanelState.Minimized &&
            minimizedBounds.contains(mouseX, mouseY)
        ) {
            restore()
        }
        return true
    }

    fun onCapturedPointerMove(mouseX: Int, mouseY: Int, viewportWidth: Int, viewportHeight: Int) {
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
            DragMode.ResizeBottomRight -> updateExpandedDrag(mouseX, mouseY, viewportWidth, viewportHeight)
            DragMode.None -> Unit
        }
    }

    fun appendOverlayCommands(
        viewportWidth: Int,
        viewportHeight: Int,
        out: MutableList<RenderCommand>
    ) {
        if (!active || viewportWidth <= 0 || viewportHeight <= 0) {
            panelActions.clear()
            return
        }
        viewportW = viewportWidth
        viewportH = viewportHeight
        val currentRoot = root ?: return
        rebindSelection()
        expandedRect = clampExpandedRect(expandedRect, viewportWidth, viewportHeight)
        clampMinimizedPosition(viewportWidth, viewportHeight)
        updateHoverGate()
        if (hoverPickEnabled) {
            refreshHover()
        } else {
            clearHoveredState()
        }

        if (hoverPickEnabled) {
            hoveredNode?.let { appendHighlightCommands(it, hovered = true, selected = false, out = out) }
        }
        selectedNode?.let { appendHighlightCommands(it, hovered = false, selected = true, out = out) }
        if (panelState == InspectorPanelState.Minimized) {
            appendMinimizedPanel(viewportWidth, viewportHeight, out)
        } else {
            appendPanel(currentRoot, viewportWidth, viewportHeight, out)
        }
        if (hoverPickEnabled) {
            appendCursorTooltip(viewportWidth, viewportHeight, out)
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
        panelActions.clear()
        panelBounds = Rect(0, 0, 0, 0)
        minimizedBounds = Rect(0, 0, 0, 0)
        headerBounds = Rect(0, 0, 0, 0)
        contentBounds = Rect(0, 0, 0, 0)
        pointerOverInspectorUi = false
        hoverPickEnabled = true
        styleEditorError = null
        dragMode = DragMode.None
        dragMoved = false
        panelScrollY = 0
        panelContentHeight = 0
    }

    private fun rebindSelection() {
        val currentRoot = root ?: run {
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

    private fun refreshHover() {
        val currentRoot = root ?: run {
            hoveredPath = emptyList()
            hoveredNode = null
            return
        }
        hoveredPath = collectHoverChain(currentRoot, mouseX, mouseY)
        hoveredNode = hoveredPath.lastOrNull()
    }

    private fun appendHighlightCommands(
        node: DOMNode,
        hovered: Boolean,
        selected: Boolean,
        out: MutableList<RenderCommand>
    ) {
        val boxes = computeBoxes(node)
        if (selected) {
            addFill(out, boxes.margin, 0x22F3B33D)
            addFill(out, boxes.padding, 0x2226A69A)
            addFill(out, boxes.content, 0x224285F4)
            addOutline(out, boxes.margin, 0x99F3B33D.toInt())
            addOutline(out, boxes.border, 0xCCFF9800.toInt())
            addOutline(out, boxes.padding, 0x9926A69A.toInt())
            addOutline(out, boxes.content, 0x994285F4.toInt())
            boxes.parentContent?.let { addOutline(out, it, 0x66FF5252) }
            return
        }
        if (hovered) {
            addOutline(out, boxes.border, 0xCC47A0FF.toInt())
        }
    }

    private fun appendCursorTooltip(
        viewportWidth: Int,
        viewportHeight: Int,
        out: MutableList<RenderCommand>
    ) {
        if (!hoverPickEnabled) return
        val node = hoveredNode ?: return
        val label = "${nodeLabel(node)} ${node.bounds.width}x${node.bounds.height} @ ${node.bounds.x},${node.bounds.y}"
        val boxW = (label.length * 6 + 8).coerceIn(96, viewportWidth - 8)
        val boxH = 14
        val tooltipRect = resolveTooltipRect(viewportWidth, viewportHeight, boxW, boxH)
        addFill(out, tooltipRect, 0xDD11151A.toInt())
        addOutline(out, tooltipRect, 0xCC3F4A57.toInt())
        out += RenderCommand.DrawText(label, tooltipRect.x + 4, tooltipRect.y + 3, 0xFFE6EDF6.toInt())
    }

    private fun appendPanel(
        root: DOMNode,
        viewportWidth: Int,
        viewportHeight: Int,
        out: MutableList<RenderCommand>
    ) {
        val clamped = clampExpandedRect(expandedRect, viewportWidth, viewportHeight)
        expandedRect = clamped
        panelBounds = clamped
        minimizedBounds = Rect(0, 0, 0, 0)
        contentBounds = Rect(0, 0, 0, 0)
        panelActions.clear()

        addFill(out, panelBounds, 0xE0141820.toInt())
        addOutline(out, panelBounds, 0xCC425062.toInt())

        val headerRect = Rect(clamped.x + 6, clamped.y + 5, clamped.width - 12, 14)
        headerBounds = headerRect
        addFill(out, headerRect, 0x222D3846)
        addOutline(out, headerRect, 0x553F4A57)
        val pickOn = mode == InspectorMode.Pick
        val selectedShort = selectedNode?.key?.toString()?.take(18) ?: "none"
        appendPanelLine(
            out,
            headerRect.x + 4,
            headerRect.y + 2,
            "Inspector Pick:${if (pickOn) "ON" else "OFF"} Sel:$selectedShort"
        )
        val pickRect = Rect(headerRect.x + headerRect.width - 120, headerRect.y + 2, 82, 10)
        addFill(out, pickRect, if (pickOn) 0x33599F5D else 0x3346596E)
        addOutline(out, pickRect, if (pickOn) 0x8896D49A.toInt() else 0x775E738C)
        appendPanelLine(out, pickRect.x + 3, pickRect.y + 1, "Select elem")
        panelActions += PanelAction(pickRect, ActionKind.TogglePick)
        val minimizeRect = Rect(headerRect.x + headerRect.width - 32, headerRect.y + 2, 28, 10)
        addFill(out, minimizeRect, 0x3346596E)
        addOutline(out, minimizeRect, 0x775E738C)
        appendPanelLine(out, minimizeRect.x + 5, minimizeRect.y + 1, "Min")
        panelActions += PanelAction(minimizeRect, ActionKind.Minimize)

        val resizeHandle = Rect(clamped.x + clamped.width - 10, clamped.y + clamped.height - 10, 8, 8)
        addFill(out, resizeHandle, 0x55748AA1)
        addOutline(out, resizeHandle, 0xAA90A7BF.toInt())

        val bodyRect = Rect(clamped.x + 6, clamped.y + 24, clamped.width - 12, (clamped.height - 28).coerceAtLeast(24))
        contentBounds = bodyRect
        panelScrollY = panelScrollY.coerceIn(0, maxOf(0, panelContentHeight - bodyRect.height))
        val maxChars = ((bodyRect.width - 12) / 6).coerceAtLeast(8)

        addFill(out, bodyRect, 0x18212C39)
        out += RenderCommand.PushClip(bodyRect.x, bodyRect.y, bodyRect.width, bodyRect.height)

        var y = bodyRect.y
        y = appendPanelLine(out, bodyRect, y, "F8 toggle, F9 mode, Esc cancel pick", maxChars, panelScrollY)
        y = appendPanelLine(out, bodyRect, y, "Hovered: ${hoveredNode?.let { nodeLabel(it) } ?: "none"}", maxChars, panelScrollY)
        y = appendPanelLine(out, bodyRect, y, "Selected: ${selectedNode?.let { nodeLabel(it) } ?: "none"}", maxChars, panelScrollY)
        y = appendPanelLine(out, bodyRect, y, "Inspector handled last: $lastHandledPointerEvent", maxChars, panelScrollY)
        y = appendPanelLine(out, bodyRect, y, "Pointer over Inspector: $pointerOverInspectorUi", maxChars, panelScrollY)
        y = appendPanelLine(out, bodyRect, y, "Hover pick enabled: $hoverPickEnabled", maxChars, panelScrollY)
        y += 2

        val selected = selectedNode
        if (selected == null) {
            y = appendPanelLine(out, bodyRect, y, "Click element in Pick mode to inspect.", maxChars, panelScrollY)
            out += RenderCommand.PopClip
            panelContentHeight = (y - bodyRect.y).coerceAtLeast(0)
            panelScrollY = panelScrollY.coerceIn(0, maxOf(0, panelContentHeight - bodyRect.height))
            appendScrollbarIndicator(out, bodyRect)
            return
        }

        val selectedPath = pathToNode(root, selected)
        wrapPathLines(selectedPath, maxChars).forEach { line ->
            y = appendPanelLine(out, bodyRect, y, line, maxChars, panelScrollY)
        }
        val boxes = computeBoxes(selected)
        y = appendPanelLine(out, bodyRect, y, "Border box: ${rectLabel(boxes.border)}", maxChars, panelScrollY)
        y = appendPanelLine(out, bodyRect, y, "Content box: ${rectLabel(boxes.content)}", maxChars, panelScrollY)
        y = appendPanelLine(out, bodyRect, y, "Margin box: ${rectLabel(boxes.margin)}", maxChars, panelScrollY)
        boxes.parentContent?.let {
            y = appendPanelLine(out, bodyRect, y, "Parent content: ${rectLabel(it)}", maxChars, panelScrollY)
        }
        val localPos = selected.parent?.let { parent ->
            val parentContent = contentRect(parent)
            "${selected.bounds.x - parentContent.x},${selected.bounds.y - parentContent.y}"
        } ?: "${selected.bounds.x},${selected.bounds.y}"
        y = appendPanelLine(out, bodyRect, y, "Local pos: $localPos", maxChars, panelScrollY)
        selected.inspectorScrollOffset()?.let { (sx, sy) ->
            y = appendPanelLine(out, bodyRect, y, "Scroll: x=$sx y=$sy", maxChars, panelScrollY)
        }
        y += 2

        val parent = selected.parent
        if (parent != null) {
            val row = Rect(clamped.x + 8, y - panelScrollY, clamped.width - 16, 10)
            addFill(out, row, 0x222D3846)
            addOutline(out, row, 0x553F4A57)
            appendPanelLine(out, row.x + 3, row.y + 1, "[Parent] ${nodeLabel(parent)}")
            panelActions += PanelAction(row, ActionKind.Parent)
            y += 11
        }

        val children = selected.children.filter { it.display != Display.None }
        if (children.isNotEmpty()) {
            y = appendPanelLine(out, bodyRect, y, "Children:", maxChars, panelScrollY)
            for (index in children.indices) {
                val child = children[index]
                val row = Rect(clamped.x + 10, y - panelScrollY, clamped.width - 20, 10)
                addFill(out, row, 0x1E263241)
                addOutline(out, row, 0x55394654)
                appendPanelLine(out, row.x + 3, row.y + 1, "[$index] ${nodeLabel(child)}")
                panelActions += PanelAction(row, ActionKind.Child, index)
                y += 11
            }
        }

        y += 2
        val inspection = selectionStyle(selected)
        y = appendStyleEditorSection(clamped, bodyRect, selected, inspection, y, out, panelScrollY, maxChars)
        y += 1
        y = appendPanelLine(out, bodyRect, y, "Computed styles:", maxChars, panelScrollY)
        val styleRows = styleRows(inspection)
        for (line in styleRows) {
            y = appendPanelLine(out, bodyRect, y, line, maxChars, panelScrollY, 2)
        }

        out += RenderCommand.PopClip
        panelContentHeight = (y - bodyRect.y).coerceAtLeast(0)
        panelScrollY = panelScrollY.coerceIn(0, maxOf(0, panelContentHeight - bodyRect.height))
        appendScrollbarIndicator(out, bodyRect)
    }

    private fun appendMinimizedPanel(
        viewportWidth: Int,
        viewportHeight: Int,
        out: MutableList<RenderCommand>
    ) {
        val chipWidth = minimizedWidth().coerceAtLeast(minChipWidth)
        val chipHeight = minimizedHeight()
        clampMinimizedPosition(viewportWidth, viewportHeight)
        val chipRect = Rect(minimizedPosX, minimizedPosY, chipWidth, chipHeight)
        panelBounds = chipRect
        minimizedBounds = chipRect
        headerBounds = Rect(0, 0, 0, 0)
        contentBounds = Rect(0, 0, 0, 0)
        panelScrollY = 0
        panelContentHeight = 0
        panelActions.clear()

        addFill(out, chipRect, if (dragMode == DragMode.MinimizedMove) 0xEE1C2430.toInt() else 0xDD1A202A.toInt())
        addOutline(out, chipRect, 0xCC4F6076.toInt())
        val badge = if (mode == InspectorMode.Pick) "[Pick]" else "[Locked]"
        val selectedShort = selectedNode?.key?.toString()?.let { " $it" } ?: ""
        val title = "Inspector $badge$selectedShort"
        val maxChars = ((chipRect.width - 12) / 6).coerceAtLeast(4)
        val lines = wrapMinimizedLabel(title, maxChars, maxLines = 2)
        val startY = if (lines.size == 1) chipRect.y + 8 else chipRect.y + 4
        lines.forEachIndexed { index, line ->
            out += RenderCommand.DrawText(line, chipRect.x + 6, startY + index * 9, 0xFFE6EDF6.toInt())
        }
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
        cachedStyle = SelectionStyleCache(
            key = key,
            nodeClass = klass,
            layoutVersion = layoutVersion,
            inspection = inspection
        )
        return inspection
    }

    private fun styleRows(inspection: StyleInspection): List<String> {
        val computed = inspection.computed
        val rows = ArrayList<String>(24)
        val values = linkedMapOf(
            StyleProperty.DISPLAY to computed.display.name,
            StyleProperty.WIDTH to (computed.width?.toString() ?: "auto"),
            StyleProperty.HEIGHT to (computed.height?.toString() ?: "auto"),
            StyleProperty.MARGIN to spacingLabel(computed.margin),
            StyleProperty.PADDING to spacingLabel(computed.padding),
            StyleProperty.BORDER_WIDTH to computed.borderWidth.toString(),
            StyleProperty.BORDER_COLOR to colorLabel(computed.borderColor),
            StyleProperty.BACKGROUND_COLOR to (computed.backgroundColor?.let(::colorLabel) ?: "none"),
            StyleProperty.FOREGROUND_COLOR to colorLabel(computed.foregroundColor),
            StyleProperty.GAP to computed.gap.toString(),
            StyleProperty.FLEX_DIRECTION to computed.flexDirection.name,
            StyleProperty.JUSTIFY_CONTENT to computed.justifyContent.name,
            StyleProperty.ALIGN_ITEMS to computed.alignItems.name,
            StyleProperty.GRID_COLUMNS to computed.gridColumns.toString(),
            StyleProperty.GRID_ROWS to (computed.gridRows?.toString() ?: "auto"),
            StyleProperty.GRID_COLUMN_SPAN to computed.gridColumnSpan.toString(),
            StyleProperty.GRID_ROW_SPAN to computed.gridRowSpan.toString(),
            StyleProperty.TEXT_WRAP to computed.textWrap.name,
            StyleProperty.TRANSFORM to "tx=${computed.transform.translateX},ty=${computed.transform.translateY},sx=${computed.transform.scaleX},sy=${computed.transform.scaleY},rot=${computed.transform.rotateDeg}",
            StyleProperty.TRANSFORM_ORIGIN to "${computed.transformOrigin.originX} ${computed.transformOrigin.originY}",
            StyleProperty.OPACITY to formatFloatLiteral(computed.opacity)
        )
        values.forEach { (property, value) ->
            val source = inspection.propertySources[property]
            val sourceLabel = source?.source ?: "default"
            rows += "${property.key}: $value <- $sourceLabel"
        }
        if (inspection.matchedRules.isNotEmpty()) {
            rows += "matched rules:"
            inspection.matchedRules.take(4).forEach { row ->
                rows += "  $row"
            }
        }
        return rows
    }

    private fun resolvePanelAction(mouseX: Int, mouseY: Int): Boolean {
        if (!panelBounds.contains(mouseX, mouseY)) return false
        val action = panelActions.lastOrNull { it.bounds.contains(mouseX, mouseY) } ?: return false
        when (action.kind) {
            ActionKind.Minimize -> {
                minimize()
                return true
            }

            ActionKind.TogglePick -> {
                setPickMode(mode != InspectorMode.Pick)
                return true
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
                    applyStyleEdit(selected, property, operation, action.step)
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
        return true
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

    private fun appendStyleEditorSection(
        panelRect: Rect,
        bodyRect: Rect,
        selected: DOMNode,
        inspection: StyleInspection,
        startY: Int,
        out: MutableList<RenderCommand>,
        scrollY: Int,
        maxChars: Int
    ): Int {
        var y = appendPanelLine(out, bodyRect, startY, "Style editor (live overrides):", maxChars, scrollY)

        val rowLeft = panelRect.x + 10
        val rowWidth = panelRect.width - 20
        val properties = editablePropertiesFor(selected)
        for (property in properties) {
            y = appendEditablePropertyRow(
                selected = selected,
                inspection = inspection,
                property = property,
                x = rowLeft,
                y = y,
                width = rowWidth,
                bodyRect = bodyRect,
                scrollY = scrollY,
                maxChars = maxChars,
                out = out
            )
        }

        styleEditorError?.let { error ->
            y = appendPanelLine(
                out,
                bodyRect,
                y,
                "Edit error: ${error.take(58)}",
                maxChars,
                scrollY,
                2,
                0xFFFF6E6E.toInt()
            )
        }

        val resetRect = Rect(rowLeft, y - scrollY, 74, 10)
        val clearRect = Rect(rowLeft + 80, y - scrollY, 96, 10)
        addFill(out, resetRect, 0x2A465968)
        addOutline(out, resetRect, 0x775E738C)
        addFill(out, clearRect, 0x2A4E3F56)
        addOutline(out, clearRect, 0x777A5C84)
        out += RenderCommand.DrawText("Reset node", resetRect.x + 4, resetRect.y + 1, 0xFFDCE5EF.toInt())
        out += RenderCommand.DrawText("Clear all", clearRect.x + 4, clearRect.y + 1, 0xFFDCE5EF.toInt())
        panelActions += PanelAction(resetRect, ActionKind.ResetSelectedOverrides)
        panelActions += PanelAction(clearRect, ActionKind.ClearAllOverrides)
        y += 11

        return y
    }

    private fun appendEditablePropertyRow(
        selected: DOMNode,
        inspection: StyleInspection,
        property: StyleProperty,
        x: Int,
        y: Int,
        width: Int,
        bodyRect: Rect,
        scrollY: Int,
        maxChars: Int,
        out: MutableList<RenderCommand>
    ): Int {
        val descriptor = StylePropertyRegistry.descriptor(property)
        val row = Rect(x, y - scrollY, width, 10)
        addFill(out, row, 0x1B293746)
        addOutline(out, row, 0x553F4A57)

        val overrideExpr = StyleEngine.inspectorOverrideFor(selected, property)
        val effectiveValue = overrideExpr?.let(::expressionLabel) ?: literalFromComputed(inspection.computed, property)
        val sourceTag = if (overrideExpr != null) "ins" else (inspection.propertySources[property]?.source ?: "default")
        val label = "${property.key}: ${effectiveValue.take(20)} <$sourceTag>"
        appendPanelLine(out, bodyRect, y, label.take(56), maxChars, scrollY, 1)

        val buttonsRight = row.x + row.width - 2
        val btnWidth = 10
        val gap = 2
        val resetRect = Rect(buttonsRight - btnWidth, row.y, btnWidth, 10)
        drawActionButton(resetRect, "x", out)
        panelActions += PanelAction(
            bounds = resetRect,
            kind = ActionKind.EditProperty,
            property = property,
            editOperation = EditOperation.ResetProperty
        )

        val cycleOptions = enumOptions(property)
        if (cycleOptions != null) {
            val nextRect = Rect(resetRect.x - gap - btnWidth, row.y, btnWidth, 10)
            val prevRect = Rect(nextRect.x - gap - btnWidth, row.y, btnWidth, 10)
            drawActionButton(prevRect, "<", out)
            drawActionButton(nextRect, ">", out)
            panelActions += PanelAction(
                bounds = prevRect,
                kind = ActionKind.EditProperty,
                property = property,
                editOperation = EditOperation.CyclePrev
            )
            panelActions += PanelAction(
                bounds = nextRect,
                kind = ActionKind.EditProperty,
                property = property,
                editOperation = EditOperation.CycleNext
            )
        } else {
            val step = descriptor.numericStep
            val incRect = Rect(resetRect.x - gap - btnWidth, row.y, btnWidth, 10)
            val decRect = Rect(incRect.x - gap - btnWidth, row.y, btnWidth, 10)
            drawActionButton(decRect, "-", out)
            drawActionButton(incRect, "+", out)
            panelActions += PanelAction(
                bounds = decRect,
                kind = ActionKind.EditProperty,
                property = property,
                editOperation = EditOperation.Decrement,
                step = step
            )
            panelActions += PanelAction(
                bounds = incRect,
                kind = ActionKind.EditProperty,
                property = property,
                editOperation = EditOperation.Increment,
                step = step
            )
        }
        return y + 11
    }

    private fun drawActionButton(rect: Rect, text: String, out: MutableList<RenderCommand>) {
        addFill(out, rect, 0x3346596E)
        addOutline(out, rect, 0x775E738C)
        out += RenderCommand.DrawText(text, rect.x + 3, rect.y + 1, 0xFFDCE5EF.toInt())
    }

    private fun editablePropertiesFor(selected: DOMNode): List<StyleProperty> {
        val all = StylePropertyRegistry.all.map { it.property }
        val isTextLike = selected.styleType == "text" || selected.styleType.contains("text")
        if (!isTextLike) return all

        val priority = listOf(
            StyleProperty.FOREGROUND_COLOR,
            StyleProperty.FONT_SIZE,
            StyleProperty.TEXT_WRAP,
            StyleProperty.ALIGN
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
        step: Float
    ) {
        runCatching {
            when (operation) {
                EditOperation.ResetProperty -> StyleEngine.clearInspectorOverride(selected, property)
                EditOperation.CyclePrev -> {
                    val options = enumOptions(property) ?: error("Property '${property.key}' is not enumerable.")
                    val current = literalForEdit(selected, property)
                    val currentIndex = options.indexOfFirst { it.equals(current, ignoreCase = true) }
                    val nextIndex = if (currentIndex <= 0) options.lastIndex else currentIndex - 1
                    StyleEngine.setInspectorOverrideLiteral(selected, property, options[nextIndex]).getOrThrow()
                }

                EditOperation.CycleNext -> {
                    val options = enumOptions(property) ?: error("Property '${property.key}' is not enumerable.")
                    val current = literalForEdit(selected, property)
                    val currentIndex = options.indexOfFirst { it.equals(current, ignoreCase = true) }
                    val nextIndex = if (currentIndex == -1 || currentIndex == options.lastIndex) 0 else currentIndex + 1
                    StyleEngine.setInspectorOverrideLiteral(selected, property, options[nextIndex]).getOrThrow()
                }

                EditOperation.Decrement -> {
                    val next = adjustNumericLiteral(selected, property, -step)
                    StyleEngine.setInspectorOverrideLiteral(selected, property, next).getOrThrow()
                }

                EditOperation.Increment -> {
                    val next = adjustNumericLiteral(selected, property, step)
                    StyleEngine.setInspectorOverrideLiteral(selected, property, next).getOrThrow()
                }
            }
            cachedStyle = null
            styleEditorError = null
        }.onFailure { error ->
            styleEditorError = error.message?.take(96) ?: "Failed to apply style override."
        }
    }

    private fun enumOptions(property: StyleProperty): List<String>? {
        val descriptor = StylePropertyRegistry.descriptor(property)
        return when (descriptor.valueType) {
            StyleEditorValueType.EnumChoice,
            StyleEditorValueType.ColorHex,
            StyleEditorValueType.StringPreset -> descriptor.enumOptions

            else -> null
        }
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

            StyleEditorValueType.OptionalIntNumber -> {
                val base = parseOptionalInt(current) ?: descriptor.minInt
                val next = (base + delta.toInt()).coerceAtLeast(descriptor.minInt)
                next.toString()
            }

            StyleEditorValueType.Spacing -> {
                val currentInsets = parseSpacingShorthand(current)
                val next = (currentInsets.top + delta.toInt()).coerceAtLeast(0)
                next.toString()
            }

            StyleEditorValueType.FloatNumber -> {
                val base = runCatching { parseFloatLike(current) }.getOrElse { descriptor.minFloat }
                val next = (base + delta).coerceAtLeast(descriptor.minFloat)
                formatFloatLiteral(next)
            }

            else -> error("Property '${property.key}' is not numeric.")
        }
    }

    private fun expressionLabel(expression: StyleExpression): String {
        return when (expression) {
            is StyleExpression.Literal -> expression.value
            is StyleExpression.VariableRef -> "var(${expression.name})"
        }
    }

    private fun literalFromComputed(style: ComputedStyle, property: StyleProperty): String {
        return when (property) {
            StyleProperty.MARGIN -> spacingLiteral(style.margin)
            StyleProperty.PADDING -> spacingLiteral(style.padding)
            StyleProperty.BACKGROUND_COLOR -> style.backgroundColor?.let(::colorLabel) ?: "none"
            StyleProperty.BACKGROUND_IMAGE -> style.backgroundImage ?: "none"
            StyleProperty.BORDER_COLOR -> colorLabel(style.borderColor)
            StyleProperty.BORDER_WIDTH -> style.borderWidth.toString()
            StyleProperty.BORDER_RADIUS -> style.borderRadius.toString()
            StyleProperty.FOREGROUND_COLOR -> colorLabel(style.foregroundColor)
            StyleProperty.FONT_SIZE -> style.fontSize?.toString() ?: "auto"
            StyleProperty.WIDTH -> style.width?.toString() ?: "auto"
            StyleProperty.HEIGHT -> style.height?.toString() ?: "auto"
            StyleProperty.ALIGN -> style.align.name.lowercase()
            StyleProperty.DISPLAY -> style.display.name.lowercase()
            StyleProperty.FLEX_DIRECTION -> style.flexDirection.name.lowercase()
            StyleProperty.JUSTIFY_CONTENT -> style.justifyContent.name
                .replace(Regex("([a-z])([A-Z])"), "$1-$2")
                .lowercase()
            StyleProperty.ALIGN_ITEMS -> style.alignItems.name.lowercase()
            StyleProperty.JUSTIFY_ITEMS -> style.justifyItems.name.lowercase()
            StyleProperty.GAP -> style.gap.toString()
            StyleProperty.FLEX_GROW -> formatFloatLiteral(style.flexGrow)
            StyleProperty.FLEX_SHRINK -> formatFloatLiteral(style.flexShrink)
            StyleProperty.FLEX_BASIS -> style.flexBasis?.toString() ?: "auto"
            StyleProperty.GRID_COLUMNS -> style.gridColumns.toString()
            StyleProperty.GRID_ROWS -> style.gridRows?.toString() ?: "auto"
            StyleProperty.GRID_AUTO_FLOW -> style.gridAutoFlow.name.lowercase()
            StyleProperty.GRID_COLUMN_SPAN -> style.gridColumnSpan.toString()
            StyleProperty.GRID_ROW_SPAN -> style.gridRowSpan.toString()
            StyleProperty.TEXT_WRAP -> if (style.textWrap == TextWrap.Wrap) "wrap" else "nowrap"
            StyleProperty.TRANSFORM -> buildString {
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
    }

    private fun spacingLiteral(value: org.dreamfinity.dsgl.core.dom.layout.Insets): String {
        return "${value.top} ${value.right} ${value.bottom} ${value.left}"
    }

    private fun formatFloatLiteral(value: Float): String {
        val rounded = ((value * 100f).toInt()) / 100f
        return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    }

    private fun appendPanelLine(
        out: MutableList<RenderCommand>,
        x: Int,
        y: Int,
        text: String
    ): Int {
        out += RenderCommand.DrawText(text.take(74), x, y, 0xFFDCE5EF.toInt())
        return y + 10
    }

    private fun appendPanelLine(
        out: MutableList<RenderCommand>,
        bodyRect: Rect,
        y: Int,
        text: String,
        maxChars: Int,
        scrollY: Int,
        leftInset: Int = 0,
        color: Int = 0xFFDCE5EF.toInt()
    ): Int {
        val lines = wrapText(text, maxChars)
        var logicalY = y
        for (line in lines) {
            val drawY = logicalY - scrollY
            out += RenderCommand.DrawText(
                line,
                bodyRect.x + 2 + leftInset,
                drawY,
                color
            )
            logicalY += 10
        }
        return logicalY
    }

    private fun wrapPathLines(path: List<DOMNode>, maxChars: Int): List<String> {
        if (path.isEmpty()) return listOf("Path: <none>")
        val tokens = path.map(::pathToken)
        val lines = ArrayList<String>()
        var current = "Path: "
        tokens.forEachIndexed { index, token ->
            val segment = if (index == 0) token else " > $token"
            if (current.length + segment.length <= maxChars) {
                current += segment
                return@forEachIndexed
            }
            if (current.isNotBlank()) {
                lines += current
            }
            val continued = if (index == 0) token else "> $token"
            val wrapped = wrapText(continued, maxChars - 2)
            if (wrapped.isEmpty()) {
                current = "  "
            } else {
                lines += wrapped.dropLast(1).map { "  $it" }
                current = "  ${wrapped.last()}"
            }
        }
        lines += current
        return lines
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        val limit = maxChars.coerceAtLeast(1)
        if (text.length <= limit) return listOf(text)
        val result = ArrayList<String>()
        var cursor = 0
        while (cursor < text.length) {
            val end = (cursor + limit).coerceAtMost(text.length)
            var cut = end
            if (end < text.length) {
                val ws = text.lastIndexOf(' ', end - 1)
                if (ws >= cursor + 1) {
                    cut = ws
                }
            }
            if (cut <= cursor) {
                cut = end
            }
            result += text.substring(cursor, cut).trimEnd()
            cursor = cut
            while (cursor < text.length && text[cursor] == ' ') cursor++
        }
        return if (result.isEmpty()) listOf("") else result
    }

    private fun appendScrollbarIndicator(out: MutableList<RenderCommand>, bodyRect: Rect) {
        if (panelContentHeight <= bodyRect.height || bodyRect.height <= 0) return
        val trackWidth = 4
        val track = Rect(
            bodyRect.x + bodyRect.width - trackWidth - 2,
            bodyRect.y + 2,
            trackWidth,
            (bodyRect.height - 4).coerceAtLeast(8)
        )
        val maxScroll = (panelContentHeight - bodyRect.height).coerceAtLeast(1)
        val thumbHeight = ((track.height.toFloat() * bodyRect.height.toFloat() / panelContentHeight.toFloat()).toInt())
            .coerceIn(10, track.height)
        val travel = (track.height - thumbHeight).coerceAtLeast(0)
        val thumbY = track.y + ((panelScrollY.toFloat() / maxScroll.toFloat()) * travel.toFloat()).toInt()
        val thumb = Rect(track.x, thumbY, track.width, thumbHeight)
        addFill(out, track, 0x22384A5D)
        addFill(out, thumb, 0x887E97B1.toInt())
        addOutline(out, thumb, 0xCC9BB2C9.toInt())
    }

    private fun pathToNode(root: DOMNode, target: DOMNode): List<DOMNode> {
        val path = ArrayList<DOMNode>(8)
        if (collectPath(root, target, path)) {
            return path
        }
        return listOf(target)
    }

    private fun collectPath(node: DOMNode, target: DOMNode, path: MutableList<DOMNode>): Boolean {
        path += node
        if (node === target) return true
        for (child in node.children) {
            if (collectPath(child, target, path)) {
                return true
            }
        }
        path.removeAt(path.lastIndex)
        return false
    }

    private fun collectHoverChain(root: DOMNode, mouseX: Int, mouseY: Int): List<DOMNode> {
        val out = ArrayList<DOMNode>(8)
        collectHoverChain(root, mouseX, mouseY, AffineTransform2D.IDENTITY, out)
        return out
    }

    private fun collectHoverChain(
        node: DOMNode,
        mouseX: Int,
        mouseY: Int,
        parentTransform: AffineTransform2D,
        out: MutableList<DOMNode>
    ): Boolean {
        if (node.styleDisabled) return false
        if (!node.isHitTestVisible()) return false
        val world = parentTransform.times(node.localTransformMatrix())
        val inverse = world.inverseOrNull() ?: return false
        val local = inverse.transform(mouseX.toFloat(), mouseY.toFloat())
        if (!node.bounds.contains(local.first, local.second)) return false
        out += node
        for (index in node.children.lastIndex downTo 0) {
            val child = node.children[index]
            if (collectHoverChain(child, mouseX, mouseY, world, out)) {
                return true
            }
        }
        return true
    }

    private fun startMinimizedMoveDrag(mouseX: Int, mouseY: Int) {
        dragMode = DragMode.MinimizedMove
        dragStartMouseX = mouseX
        dragStartMouseY = mouseY
        dragStartOffsetX = mouseX - minimizedPosX
        dragStartOffsetY = mouseY - minimizedPosY
        dragMoved = false
    }

    private fun startExpandedDrag(mode: DragMode, mouseX: Int, mouseY: Int) {
        dragMode = mode
        dragStartMouseX = mouseX
        dragStartMouseY = mouseY
        dragStartRect = expandedRect
        dragStartOffsetX = mouseX - expandedRect.x
        dragStartOffsetY = mouseY - expandedRect.y
        dragMoved = false
    }

    private fun updateMinimizedMoveDrag(
        mouseX: Int,
        mouseY: Int,
        viewportWidth: Int,
        viewportHeight: Int
    ) {
        val nextX = mouseX - dragStartOffsetX
        val nextY = mouseY - dragStartOffsetY
        if (!dragMoved && (kotlin.math.abs(nextX - minimizedPosX) >= 2 || kotlin.math.abs(nextY - minimizedPosY) >= 2)) {
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
        viewportHeight: Int
    ) {
        val dx = mouseX - dragStartMouseX
        val dy = mouseY - dragStartMouseY
        var left = dragStartRect.x
        var top = dragStartRect.y
        var right = dragStartRect.x + dragStartRect.width
        var bottom = dragStartRect.y + dragStartRect.height

        when (dragMode) {
            DragMode.Move -> {
                val targetX = mouseX - dragStartOffsetX
                val targetY = mouseY - dragStartOffsetY
                val moved = clampExpandedRect(
                    Rect(targetX, targetY, dragStartRect.width, dragStartRect.height),
                    viewportWidth,
                    viewportHeight
                )
                if (!dragMoved && (kotlin.math.abs(moved.x - dragStartRect.x) >= 2 || kotlin.math.abs(moved.y - dragStartRect.y) >= 2)) {
                    dragMoved = true
                }
                expandedRect = moved
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
                DragMode.ResizeRight, DragMode.ResizeTopRight, DragMode.ResizeBottomRight -> right = left + minPanelWidth
                else -> Unit
            }
        }
        if (bottom - top < minPanelHeight) {
            when (dragMode) {
                DragMode.ResizeTop, DragMode.ResizeTopLeft, DragMode.ResizeTopRight -> top = bottom - minPanelHeight
                DragMode.ResizeBottom, DragMode.ResizeBottomLeft, DragMode.ResizeBottomRight -> bottom = top + minPanelHeight
                else -> Unit
            }
        }

        val resized = clampExpandedRect(
            Rect(left, top, (right - left).coerceAtLeast(minPanelWidth), (bottom - top).coerceAtLeast(minPanelHeight)),
            viewportWidth,
            viewportHeight
        )
        if (!dragMoved && (kotlin.math.abs(resized.width - dragStartRect.width) >= 2 || kotlin.math.abs(resized.height - dragStartRect.height) >= 2 ||
                    kotlin.math.abs(resized.x - dragStartRect.x) >= 2 || kotlin.math.abs(resized.y - dragStartRect.y) >= 2)
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
    }

    private fun resolveTooltipRect(
        viewportWidth: Int,
        viewportHeight: Int,
        boxWidth: Int,
        boxHeight: Int
    ): Rect {
        val inspectorRect = currentInspectorRect()
        val candidates = listOf(
            mouseX + 12 to mouseY + 12,
            mouseX + 12 to mouseY - boxHeight - 12,
            mouseX - boxWidth - 12 to mouseY + 12,
            mouseX - boxWidth - 12 to mouseY - boxHeight - 12,
            mouseX + 16 to mouseY - boxHeight / 2
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
        viewportHeight: Int
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
        val bounds = when (panelState) {
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

    private fun wrapMinimizedLabel(
        text: String,
        maxCharsPerLine: Int,
        maxLines: Int
    ): List<String> {
        val source = text.trim()
        if (source.isEmpty()) return listOf("")
        if (maxCharsPerLine <= 0 || maxLines <= 0) return listOf("")

        val lines = ArrayList<String>(maxLines)
        var cursor = 0
        while (cursor < source.length && lines.size < maxLines) {
            var end = (cursor + maxCharsPerLine).coerceAtMost(source.length)
            if (end < source.length) {
                val breakAt = source.lastIndexOf(' ', end - 1)
                if (breakAt >= cursor + 1) {
                    end = breakAt
                }
            }
            var line = source.substring(cursor, end).trim()
            if (line.isEmpty()) {
                end = (cursor + maxCharsPerLine).coerceAtMost(source.length)
                line = source.substring(cursor, end)
            }
            lines += line
            cursor = end
            while (cursor < source.length && source[cursor] == ' ') cursor++
        }
        if (cursor < source.length && lines.isNotEmpty()) {
            val last = lines.last()
            val keep = (maxCharsPerLine - 3).coerceAtLeast(0)
            val trimmed = last.take(keep).trimEnd()
            lines[lines.lastIndex] = if (trimmed.isEmpty()) "..." else "$trimmed..."
        }
        return lines
    }

    private fun containsReference(root: DOMNode, target: DOMNode): Boolean {
        if (root === target) return true
        root.children.forEach { child ->
            if (containsReference(child, target)) return true
        }
        return false
    }

    private fun findByKeyAndClass(
        node: DOMNode,
        key: Any,
        klass: Class<out DOMNode>
    ): DOMNode? {
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

    private fun rectLabel(rect: Rect): String {
        return "${rect.x},${rect.y},${rect.width}x${rect.height}"
    }

    private fun spacingLabel(value: org.dreamfinity.dsgl.core.dom.layout.Insets): String {
        return "${value.top}/${value.right}/${value.bottom}/${value.left}"
    }

    private fun colorLabel(color: Int): String {
        val hex = color.toUInt().toString(16).uppercase().padStart(8, '0')
        return "#$hex"
    }

    private fun computeBoxes(node: DOMNode): NodeBoxes {
        val borderRect = node.bounds
        val marginRect = Rect(
            x = borderRect.x - node.margin.left,
            y = borderRect.y - node.margin.top,
            width = (borderRect.width + node.margin.horizontal).coerceAtLeast(0),
            height = (borderRect.height + node.margin.vertical).coerceAtLeast(0)
        )
        val paddingRect = Rect(
            x = borderRect.x + node.border.left,
            y = borderRect.y + node.border.top,
            width = (borderRect.width - node.border.horizontal).coerceAtLeast(0),
            height = (borderRect.height - node.border.vertical).coerceAtLeast(0)
        )
        val contentRect = Rect(
            x = paddingRect.x + node.padding.left,
            y = paddingRect.y + node.padding.top,
            width = (paddingRect.width - node.padding.horizontal).coerceAtLeast(0),
            height = (paddingRect.height - node.padding.vertical).coerceAtLeast(0)
        )
        val parentContent = node.parent?.let { parent -> contentRect(parent) }
        return NodeBoxes(
            margin = marginRect,
            border = borderRect,
            padding = paddingRect,
            content = contentRect,
            parentContent = parentContent
        )
    }

    private fun contentRect(node: DOMNode): Rect {
        return Rect(
            x = node.bounds.x + node.border.left + node.padding.left,
            y = node.bounds.y + node.border.top + node.padding.top,
            width = (node.bounds.width - node.border.horizontal - node.padding.horizontal).coerceAtLeast(0),
            height = (node.bounds.height - node.border.vertical - node.padding.vertical).coerceAtLeast(0)
        )
    }

    private fun addFill(out: MutableList<RenderCommand>, rect: Rect, color: Int) {
        if (rect.width <= 0 || rect.height <= 0) return
        out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, rect.height, color)
    }

    private fun addOutline(out: MutableList<RenderCommand>, rect: Rect, color: Int) {
        if (rect.width <= 0 || rect.height <= 0) return
        out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, 1, color)
        out += RenderCommand.DrawRect(rect.x, rect.y + rect.height - 1, rect.width, 1, color)
        out += RenderCommand.DrawRect(rect.x, rect.y, 1, rect.height, color)
        out += RenderCommand.DrawRect(rect.x + rect.width - 1, rect.y, 1, rect.height, color)
    }
}
