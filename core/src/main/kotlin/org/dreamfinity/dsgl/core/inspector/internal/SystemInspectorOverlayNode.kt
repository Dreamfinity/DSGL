package org.dreamfinity.dsgl.core.inspector.internal

import org.dreamfinity.dsgl.core.dsl.UiScope
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.ScrollSessionSnapshot
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.TextInputNode
import org.dreamfinity.dsgl.core.dom.layout.Border
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.dsl.button
import org.dreamfinity.dsgl.core.dsl.div
import org.dreamfinity.dsgl.core.dsl.text
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.InspectorDomSnapshot
import org.dreamfinity.dsgl.core.inspector.InspectorDropdownOptionSnapshot
import org.dreamfinity.dsgl.core.inspector.InspectorDropdownSnapshot
import org.dreamfinity.dsgl.core.inspector.InspectorStyleEditorRowSnapshot
import org.dreamfinity.dsgl.core.inspector.InspectorEditorKind
import org.dreamfinity.dsgl.core.inspector.InspectorPanelState
import org.dreamfinity.dsgl.core.inspector.InspectorTooltipSnapshot
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanelDragSession
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanel
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanelState
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.StyleProperty
import org.dreamfinity.dsgl.core.style.TextWrap
import java.util.LinkedHashMap

internal class SystemInspectorOverlayNode(
    private val controller: InspectorController,
    private val overlayPanel: OverlayPanel,
    key: Any? = "dsgl-system-inspector"
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-inspector"
    override val focusable: Boolean = true

    internal constructor(
        controller: InspectorController,
        key: Any? = "dsgl-system-inspector"
    ) : this(
        controller = controller,
        overlayPanel = OverlayPanel(
            ownerId = "standalone-system-inspector",
            panelState = OverlayPanelState(),
            dragSession = OverlayPanelDragSession()
        ),
        key = key
    )

    private var inspectedRoot: DOMNode? = null
    private var inspectedLayoutRevision: Long = 0L
    private var cursorX: Int = 0
    private var cursorY: Int = 0
    private var lastViewportWidth: Int = 1
    private var lastViewportHeight: Int = 1
    private var persistedBodyScrollSession: ScrollSessionSnapshot? = null
    private val persistedDropdownScrollSession: MutableMap<String, ScrollSessionSnapshot> = LinkedHashMap()
    private var activeDomDropdown: ActiveDomDropdown? = null
    private var overlayPanelDragUpdatedByDomInput: Boolean = false
    private val panelNode: DOMNode = overlayPanel.node().applyParent(this)
    private var minimizedChipDragSession: MinimizedChipDragSession? = null

    private data class ActiveDomDropdown(
        val property: StyleProperty,
        val unitSelect: Boolean
    )

    private data class MinimizedChipDragSession(
        val startPointerX: Int,
        val startPointerY: Int,
        val startRect: Rect,
        var currentPointerX: Int,
        var currentPointerY: Int,
        var moved: Boolean = false
    )

    init {
        EventBus.run {
            this@SystemInspectorOverlayNode.addEventListener(Events.MOUSEDOWN) { event: MouseDownEvent ->
                if (
                    event.mouseButton == MouseButton.LEFT &&
                    activeDomDropdown != null &&
                    shouldDismissOpenDropdownOnPointerDown(event.target)
                ) {
                    closeActiveDomDropdown()
                }
                if (handleOverlayPanelMouseDown(event)) {
                    event.cancelled = true
                    return@addEventListener
                }
                val domOwnedTarget = isDomOwnedInteractionTarget(event.target)
                if (domOwnedTarget) return@addEventListener
                if (controller.handleMouseDown(event.mouseX, event.mouseY, event.mouseButton)) {
                    event.cancelled = true
                }
            }
            this@SystemInspectorOverlayNode.addEventListener(Events.MOUSEUP) { event: MouseUpEvent ->
                if (handleOverlayPanelMouseUp(event)) {
                    event.cancelled = true
                    return@addEventListener
                }
                val routeToController = controller.isPointerCaptured || !isDomOwnedInteractionTarget(event.target)
                if (!routeToController) return@addEventListener
                if (controller.handleMouseUp(event.mouseX, event.mouseY, event.mouseButton)) {
                    event.cancelled = true
                }
            }
            this@SystemInspectorOverlayNode.addEventListener(Events.DRAG) { event: MouseDragEvent ->
                val nextMouseX = event.lastMouseX + event.dx
                val nextMouseY = event.lastMouseY + event.dy
                if (handleOverlayPanelDrag(nextMouseX, nextMouseY)) {
                    event.cancelled = true
                    return@addEventListener
                }
                if (isDomOwnedInteractionTarget(event.target)) return@addEventListener
                if (!controller.isPointerCaptured) return@addEventListener
                controller.onCapturedPointerMove(nextMouseX, nextMouseY, bounds.width, bounds.height)
                event.cancelled = true
            }
            this@SystemInspectorOverlayNode.addEventListener(Events.WHEEL) { event: MouseWheelEvent ->
                if (handleActiveDomDropdownWheel(event.dWheel)) {
                    event.cancelled = true
                }
            }
        }
    }

    private fun handleOverlayPanelMouseDown(event: MouseDownEvent): Boolean {
        if (event.mouseButton != MouseButton.LEFT) return false
        val bodyRect = controller.overlayContentRect()
        val pointerInsideBody = bodyRect.width > 0 &&
            bodyRect.height > 0 &&
            bodyRect.contains(event.mouseX, event.mouseY)
        if (pointerInsideBody) return false
        val handled = overlayPanel.handleMouseDown(
            mouseX = event.mouseX,
            mouseY = event.mouseY,
            button = event.mouseButton,
            includeCloseButton = false
        )
        if (handled) {
            controller.onOverlayPanelPointerCaptureChanged(true)
        }
        return handled
    }

    private fun handleOverlayPanelDrag(mouseX: Int, mouseY: Int): Boolean {
        val handled = overlayPanel.handleMouseMove(
            mouseX = mouseX,
            mouseY = mouseY,
            viewportWidth = lastViewportWidth,
            viewportHeight = lastViewportHeight
        ) { rect ->
            controller.onOverlayPanelRectChanged(rect, lastViewportWidth, lastViewportHeight)
        }
        if (handled) {
            overlayPanelDragUpdatedByDomInput = true
            controller.onOverlayPanelPointerCaptureChanged(true)
        }
        return handled
    }

    private fun handleOverlayPanelMouseUp(event: MouseUpEvent): Boolean {
        val handled = overlayPanel.handleMouseUp(
            mouseX = event.mouseX,
            mouseY = event.mouseY,
            button = event.mouseButton,
            viewportWidth = lastViewportWidth,
            viewportHeight = lastViewportHeight
        ) { rect ->
            controller.onOverlayPanelRectChanged(rect, lastViewportWidth, lastViewportHeight)
        }
        if (handled) {
            overlayPanelDragUpdatedByDomInput = false
            controller.onOverlayPanelPointerCaptureChanged(false)
        }
        return handled
    }

    internal fun consumeOverlayPanelDomDragUpdate(): Boolean {
        val consumed = overlayPanelDragUpdatedByDomInput
        overlayPanelDragUpdatedByDomInput = false
        return consumed
    }

    fun bindInspectedTree(root: DOMNode?, layoutRevision: Long) {
        inspectedRoot = root
        inspectedLayoutRevision = layoutRevision
    }

    @Suppress("UNUSED_PARAMETER")
    fun updateCursor(mouseX: Int, mouseY: Int, pointerCaptured: Boolean) {
        cursorX = mouseX
        cursorY = mouseY
        updateMinimizedChipDragPointer(mouseX, mouseY)
    }

    fun syncInputBounds(viewportWidth: Int, viewportHeight: Int) {
        val viewportRect = Rect(0, 0, viewportWidth.coerceAtLeast(0), viewportHeight.coerceAtLeast(0))
        bounds = resolveInputBounds(viewportRect, controller.overlayPanelRect())
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        val viewportRect = Rect(x, y, width, height)
        bounds = resolveInputBounds(viewportRect, controller.overlayPanelRect())
        inspectedRoot?.let { root ->
            controller.onLayoutCommitted(root, inspectedLayoutRevision)
        }
        controller.onCursorMoved(cursorX, cursorY)
        lastViewportWidth = viewportRect.width.coerceAtLeast(1)
        lastViewportHeight = viewportRect.height.coerceAtLeast(1)
        applyMinimizedChipDragFrame()
        val retainInspectorFocus = shouldRetainInspectorSubtreeFocus()

        val snapshot = controller.buildDomSnapshot(viewportRect.width, viewportRect.height)
        if (snapshot == null) {
            clearTree()
            panelNode.render(ctx, 0, 0, 0, 0)
            children.remove(panelNode)
            panelNode.parent = null
            persistedBodyScrollSession = null
            persistedDropdownScrollSession.clear()
            closeActiveDomDropdown()
            clearMinimizedChipDragSession()
            controller.onNativeDomBodyScrollState(0, null, null)
            controller.onOverlayPanelPointerCaptureChanged(false)
            return
        }
        bounds = resolveInputBounds(viewportRect, snapshot.panelRect)

        capturePersistedScrollStateFromCurrentTree()
        clearTree()
        when (snapshot.panelState) {
            InspectorPanelState.Minimized -> renderMinimized(ctx, snapshot)
            InspectorPanelState.Expanded -> renderExpanded(ctx, snapshot, viewportRect)
        }
        if (retainInspectorFocus) {
            FocusManager.retainFocus(this, updateRootReference = false)
        }
    }

    private fun resolveInputBounds(viewportRect: Rect, panelRect: Rect?): Rect {
        if (controller.blocksUnderlyingInput() || overlayPanel.isDragging() || minimizedChipDragSession != null) {
            return viewportRect
        }
        val base = panelRect ?: viewportRect
        val dropdownBounds = resolveRenderedDropdownInputBounds()
        if (dropdownBounds == null) {
            return base
        }
        return unionRect(base, dropdownBounds)
    }

    private fun resolveRenderedDropdownInputBounds(): Rect? {
        val dropdowns = controller.overlayStyleEditorDropdowns()
        if (dropdowns.isNotEmpty()) {
            return dropdowns
                .map { it.popupRect }
                .reduce(::unionRect)
        }
        return if (activeDomDropdown != null) {
            Rect(0, 0, lastViewportWidth.coerceAtLeast(1), lastViewportHeight.coerceAtLeast(1))
        } else {
            null
        }
    }

    private fun unionRect(a: Rect, b: Rect): Rect {
        val left = minOf(a.x, b.x)
        val top = minOf(a.y, b.y)
        val right = maxOf(a.x + a.width, b.x + b.width)
        val bottom = maxOf(a.y + a.height, b.y + b.height)
        return Rect(
            x = left,
            y = top,
            width = (right - left).coerceAtLeast(0),
            height = (bottom - top).coerceAtLeast(0)
        )
    }

    private fun clearTree() {
        EventBus.run {
            children.filter { child -> child !== panelNode }.forEach { child ->
                child.clearListenersDeep()
                child.parent = null
            }
        }
        children.retainAll(listOf(panelNode))
        if (panelNode.parent !== this) {
            panelNode.applyParent(this)
        }
        markRenderCommandsDirty()
    }

    private fun renderMinimized(ctx: UiMeasureContext, snapshot: InspectorDomSnapshot) {
        closeActiveDomDropdown()
        panelNode.render(ctx, 0, 0, 0, 0)
        val scope = UiScope(this)

        renderHighlights(scope, ctx)
        renderMinimizedChip(scope, ctx, snapshot)
    }

    private fun renderExpanded(ctx: UiMeasureContext, snapshot: InspectorDomSnapshot, viewportRect: Rect) {
        val viewportWidth = viewportRect.width
        val viewportHeight = viewportRect.height
        val panelRect = snapshot.panelRect
        val bodyRect = snapshot.bodyRect
            ?: overlayPanel.bodyRect()
            ?: Rect(panelRect.x + 6, panelRect.y + 58, panelRect.width - 12, (panelRect.height - 64).coerceAtLeast(24))
        val scope = UiScope(this)

        renderHighlights(scope, ctx)
        renderPanelOccluder(scope, ctx, panelRect)
        panelNode.render(ctx, viewportRect.x, viewportRect.y, viewportRect.width, viewportRect.height)

        renderExpandedChrome(scope, ctx, panelRect)

        val body = scope.div({
            key = "dsgl-system-inspector-body"
            style = {
                display = Display.Block
            }
        })
        body.backgroundColor = 0x18212C39
        body.overflow = Overflow.Hidden
        body.overflowX = Overflow.Hidden
        body.overflowY = Overflow.Auto
        val bodyScope = UiScope(body)
        renderNode(ctx, body, bodyRect)

        val lineHeightPx = 32
        val rowHeightPx = 34
        val contentX = bodyRect.x + 4
        val contentW = (bodyRect.width - 10).coerceAtLeast(1)
        val bodyScrollY = persistedBodyScrollSession?.resolvedY?.coerceAtLeast(0) ?: 0
        var y = bodyRect.y + 2 - bodyScrollY

        y = renderBodyInfoLines(
            scope = bodyScope,
            ctx = ctx,
            infoLines = snapshot.infoLines,
            contentX = contentX,
            contentW = contentW,
            startY = y,
            lineHeightPx = lineHeightPx
        )

        y = renderParentRow(
            scope = bodyScope,
            ctx = ctx,
            parentLabel = snapshot.parentLabel,
            contentX = contentX,
            contentW = contentW,
            startY = y,
            rowHeightPx = rowHeightPx
        )
        y = renderChildRows(
            scope = bodyScope,
            ctx = ctx,
            childLabels = snapshot.childLabels,
            contentX = contentX,
            contentW = contentW,
            startY = y,
            rowHeightPx = rowHeightPx
        )
        renderStyleEditorHeading(
            scope = bodyScope,
            ctx = ctx,
            contentX = contentX,
            contentW = contentW,
            y = y,
            lineHeightPx = lineHeightPx
        )

        val styleRows = controller.overlayStyleEditorRows()
        reconcileActiveDomDropdown(styleRows)
        renderStyleEditorRows(bodyScope, body, ctx, bodyScrollY, styleRows)
        y += snapshot.styleEditorHeight

        y = renderComputedStyleLines(
            scope = bodyScope,
            ctx = ctx,
            styleLines = snapshot.styleLines,
            contentX = contentX,
            contentW = contentW,
            startY = y,
            lineHeightPx = lineHeightPx
        )

        renderDropdowns(scope, ctx, styleRows, bodyScrollY, viewportWidth, viewportHeight)
        body.restoreScrollSessionSnapshot(persistedBodyScrollSession)
        val bodyState = body.scrollContainerState()
        persistedBodyScrollSession = body.captureScrollSessionSnapshot()
        val bodyScrollbarVisual = body.debugScrollbarVisualState().vertical
        controller.onNativeDomBodyScrollState(
            scrollY = bodyState.scrollY,
            trackRect = bodyScrollbarVisual?.trackRect,
            thumbRect = bodyScrollbarVisual?.thumbRect
        )
        renderTooltip(scope, ctx, "dsgl-system-inspector-variable-tooltip", controller.overlayVariableTooltip(), 0xEE141A22.toInt(), 0xCC60758F.toInt())
        renderTooltip(scope, ctx, "dsgl-system-inspector-cursor-tooltip", controller.overlayCursorTooltip(), 0xDD11151A.toInt(), 0xCC3F4A57.toInt())
    }

    private fun renderMinimizedChip(
        scope: UiScope,
        ctx: UiMeasureContext,
        snapshot: InspectorDomSnapshot
    ) {
        val chip = scope.div({
            key = "dsgl-system-inspector-chip"
            style = {
                display = Display.Block
            }
        })
        chip.backgroundColor = 0xDD1A202A.toInt()
        chip.border = Border.all(1, 0xCC4F6076.toInt())
        chip.onMouseDown = { event ->
            if (event.mouseButton == MouseButton.LEFT) {
                startMinimizedChipDrag(snapshot.panelRect, event.mouseX, event.mouseY)
                event.cancelled = true
            }
        }
        chip.onMouseDrag = { event ->
            val currentX = event.lastMouseX + event.dx
            val currentY = event.lastMouseY + event.dy
            updateMinimizedChipDragPointer(currentX, currentY)
            event.cancelled = true
        }
        chip.onMouseUp = { event ->
            if (event.mouseButton == MouseButton.LEFT) {
                endMinimizedChipDrag(event.mouseX, event.mouseY)
                event.cancelled = true
            }
        }
        renderNode(ctx, chip, snapshot.panelRect)

        val compactLineHeight = 20
        var lineY = snapshot.panelRect.y + ((snapshot.panelRect.height - compactLineHeight * snapshot.minimizedLines.size) / 2)
        snapshot.minimizedLines.forEachIndexed { index, line ->
            val lineNode = scope.text(props = {
                key = "dsgl-system-inspector-chip-line-$index"
                value = line
                style = {
                    textWrap = TextWrap.NoWrap
                }
            })
            lineNode.color = 0xFFE6EDF6.toInt()
            lineNode.fontSize = 14
            renderNode(
                ctx,
                lineNode,
                Rect(
                    snapshot.panelRect.x + 8,
                    lineY,
                    (snapshot.panelRect.width - 16).coerceAtLeast(1),
                    compactLineHeight
                )
            )
            lineY += compactLineHeight
        }
    }

    private fun renderExpandedChrome(
        scope: UiScope,
        ctx: UiMeasureContext,
        panelRect: Rect
    ) {
        val pickRect = controller.overlayPickToggleBounds()
            ?: Rect(panelRect.x + panelRect.width - 264, panelRect.y + 8, 160, 36)
        val minimizeRect = controller.overlayMinimizeBounds()
            ?: Rect(panelRect.x + panelRect.width - 96, panelRect.y + 8, 86, 36)
        renderPickToggleButton(scope, ctx, pickRect)
        renderMinimizeButton(scope, ctx, minimizeRect)
    }

    private fun renderPickToggleButton(scope: UiScope, ctx: UiMeasureContext, rect: Rect) {
        val pickButton = scope.button("Select Element", {
            key = "dsgl-system-inspector-pick-toggle"
        })
        pickButton.backgroundColor = 0x3346596E
        pickButton.border = Border.all(1, 0x775E738C)
        pickButton.textColor = 0xFFE6EDF6.toInt()
        pickButton.fontSize = 18
        pickButton.onClick {
            controller.onPickTogglePressed()
        }
        renderNode(ctx, pickButton, rect)
    }

    private fun renderMinimizeButton(scope: UiScope, ctx: UiMeasureContext, rect: Rect) {
        val minimizeButton = scope.button("Minimize", {
            key = "dsgl-system-inspector-minimize"
        })
        minimizeButton.backgroundColor = 0x3346596E
        minimizeButton.border = Border.all(1, 0x775E738C)
        minimizeButton.textColor = 0xFFE6EDF6.toInt()
        minimizeButton.fontSize = 18
        minimizeButton.onClick {
            controller.onPanelMinimizeTogglePressed()
        }
        renderNode(ctx, minimizeButton, rect)
    }

    private fun renderBodyInfoLines(
        scope: UiScope,
        ctx: UiMeasureContext,
        infoLines: List<String>,
        contentX: Int,
        contentW: Int,
        startY: Int,
        lineHeightPx: Int
    ): Int {
        var y = startY
        infoLines.forEachIndexed { index, line ->
            val lineNode = scope.text(props = {
                key = "dsgl-system-inspector-info-line-$index"
                value = line
                style = {
                    textWrap = TextWrap.NoWrap
                }
            })
            lineNode.color = 0xFFDCE5EF.toInt()
            lineNode.fontSize = 24
            renderNode(
                ctx,
                lineNode,
                Rect(contentX, y, contentW, lineHeightPx),
            )
            y += lineHeightPx
        }
        return y
    }

    private fun renderParentRow(
        scope: UiScope,
        ctx: UiMeasureContext,
        parentLabel: String?,
        contentX: Int,
        contentW: Int,
        startY: Int,
        rowHeightPx: Int
    ): Int {
        var y = startY
        parentLabel?.let { label ->
            val parentButton = scope.button(label, {
                key = "dsgl-system-inspector-parent-row"
            })
            parentButton.backgroundColor = 0x1E263241
            parentButton.border = Border.all(1, 0x55394654)
            parentButton.textColor = 0xFFDCE5EF.toInt()
            parentButton.fontSize = 22
            parentButton.onClick {
                controller.onSelectParentPressed()
            }
            renderNode(
                ctx,
                parentButton,
                Rect(contentX, y, contentW, rowHeightPx),
            )
            y += rowHeightPx + 2
        }
        return y
    }

    private fun renderChildRows(
        scope: UiScope,
        ctx: UiMeasureContext,
        childLabels: List<String>,
        contentX: Int,
        contentW: Int,
        startY: Int,
        rowHeightPx: Int
    ): Int {
        var y = startY
        childLabels.forEachIndexed { index, label ->
            val childButton = scope.button(label, {
                key = "dsgl-system-inspector-child-row-$index"
            })
            childButton.backgroundColor = 0x1E263241
            childButton.border = Border.all(1, 0x55394654)
            childButton.textColor = 0xFFDCE5EF.toInt()
            childButton.fontSize = 22
            childButton.onClick {
                controller.onSelectChildPressed(index)
            }
            renderNode(
                ctx,
                childButton,
                Rect(contentX, y, contentW, rowHeightPx),
            )
            y += rowHeightPx + 2
        }
        return y
    }

    private fun renderStyleEditorHeading(
        scope: UiScope,
        ctx: UiMeasureContext,
        contentX: Int,
        contentW: Int,
        y: Int,
        lineHeightPx: Int
    ) {
        val styleEditorHeader = scope.text(props = {
            key = "dsgl-system-inspector-editor-header"
            value = "Style editor (live overrides):"
            style = {
                textWrap = TextWrap.NoWrap
            }
        })
        styleEditorHeader.color = 0xFFDCE5EF.toInt()
        styleEditorHeader.fontSize = 24
        renderNode(
            ctx,
            styleEditorHeader,
            Rect(contentX, y, contentW, lineHeightPx),
        )
    }

    private fun renderComputedStyleLines(
        scope: UiScope,
        ctx: UiMeasureContext,
        styleLines: List<String>,
        contentX: Int,
        contentW: Int,
        startY: Int,
        lineHeightPx: Int
    ): Int {
        var y = startY
        styleLines.forEachIndexed { index, line ->
            val lineNode = scope.text(props = {
                key = "dsgl-system-inspector-style-line-$index"
                value = line
                style = {
                    textWrap = TextWrap.NoWrap
                }
            })
            lineNode.color = 0xFFDCE5EF.toInt()
            lineNode.fontSize = 24
            renderNode(
                ctx,
                lineNode,
                Rect(contentX, y, contentW, lineHeightPx),
            )
            y += lineHeightPx
        }
        return y
    }

    private fun renderPanelOccluder(scope: UiScope, ctx: UiMeasureContext, panelRect: Rect) {
        val occluder = scope.div({
            key = "dsgl-system-inspector-panel-occluder"
            style = {
                display = Display.Block
            }
        })
        occluder.backgroundColor = 0xFF141820.toInt()
        occluder.border = Border.NONE
        renderNode(ctx, occluder, panelRect)
    }

    private fun renderHighlights(scope: UiScope, ctx: UiMeasureContext) {
        controller.overlaySelectedHighlight()?.let { highlight ->
            renderHighlightRect(scope, ctx, "dsgl-system-inspector-selected-margin-fill", highlight.marginRect, 0x44F3B33D, null)
            renderHighlightRect(scope, ctx, "dsgl-system-inspector-selected-padding-fill", highlight.paddingRect, 0x4426A69A, null)
            renderHighlightRect(scope, ctx, "dsgl-system-inspector-selected-content-fill", highlight.contentRect, 0x444285F4, null)
            renderHighlightRect(scope, ctx, "dsgl-system-inspector-selected-margin-outline", highlight.marginRect, null, 0x99F3B33D.toInt())
            renderHighlightRect(scope, ctx, "dsgl-system-inspector-selected-border-outline", highlight.borderRect, null, 0xCCFF9800.toInt())
            renderHighlightRect(scope, ctx, "dsgl-system-inspector-selected-padding-outline", highlight.paddingRect, null, 0x9926A69A.toInt())
            renderHighlightRect(scope, ctx, "dsgl-system-inspector-selected-content-outline", highlight.contentRect, null, 0x994285F4.toInt())
            highlight.parentContentRect?.let { parentRect ->
                renderHighlightRect(scope, ctx, "dsgl-system-inspector-selected-parent-outline", parentRect, null, 0x66FF5252)
            }
        }
        controller.overlayHoveredHighlight()?.let { highlight ->
            renderHighlightRect(scope, ctx, "dsgl-system-inspector-hovered-content-fill", highlight.contentRect, 0x3A47A0FF, null)
            renderHighlightRect(scope, ctx, "dsgl-system-inspector-hovered-border-outline", highlight.borderRect, null, 0xCC47A0FF.toInt())
        }
    }

    private fun renderHighlightRect(
        scope: UiScope,
        ctx: UiMeasureContext,
        key: String,
        rect: Rect,
        fillColor: Int?,
        borderColor: Int?
    ) {
        if (rect.width <= 0 || rect.height <= 0) return
        val layer = scope.div({
            this.key = key
            style = {
                display = Display.Block
            }
        })
        layer.backgroundColor = fillColor ?: 0
        layer.border = if (borderColor != null) Border.all(1, borderColor) else Border.NONE
        renderNode(ctx, layer, rect)
    }

    private fun renderStyleEditorRows(
        scope: UiScope,
        parentNode: DOMNode,
        ctx: UiMeasureContext,
        bodyScrollY: Int,
        rows: List<InspectorStyleEditorRowSnapshot>
    ) {
        rows.forEachIndexed { index, row ->
            val rowRect = translateRectY(row.rowRect, -bodyScrollY)
            val rowNode = scope.div({
                key = "dsgl-system-inspector-editor-row-$index"
                style = {
                    display = Display.Block
                }
            })
            rowNode.backgroundColor = 0x1B293746
            rowNode.border = Border.all(1, 0x553F4A57)
            renderNode(ctx, rowNode, rowRect)

            val labelNode = scope.text(props = {
                key = "dsgl-system-inspector-editor-label-$index"
                value = row.labelText
                style = {
                    textWrap = TextWrap.Wrap
                }
            })
            labelNode.color = 0xFFDCE5EF.toInt()
            labelNode.fontSize = 18
            renderNode(
                ctx,
                labelNode,
                Rect(rowRect.x + 8, rowRect.y + 5, (row.controlRect.x - row.rowRect.x - 14).coerceAtLeast(40), rowRect.height - 10),
            )

            val resetButton = scope.button("x", {
                key = "dsgl-system-inspector-editor-reset-$index"
            })
            resetButton.backgroundColor = 0x3346596E
            resetButton.border = Border.all(1, 0x775E738C)
            resetButton.textColor = 0xFFDCE5EF.toInt()
            resetButton.fontSize = 18
            resetButton.onClick {
                controller.onResetPropertyPressed(row.property)
            }
            renderNode(ctx, resetButton, translateRectY(row.resetRect, -bodyScrollY))

            when (row.editorKind) {
                InspectorEditorKind.EnumSelect,
                InspectorEditorKind.FontSelect -> {
                    val valueOpen = isDomDropdownOpen(row.property, unitSelect = false)
                    val selector = scope.button(row.controlValue, {
                        key = "dsgl-system-inspector-editor-select-$index"
                    })
                    selector.backgroundColor = if (valueOpen) 0x334D5D70 else if (row.controlHovered) 0x2A425164 else 0x22313D4B
                    selector.border = Border.all(1, if (valueOpen) 0xFFA8C6E6.toInt() else 0x77607084)
                    selector.textColor = 0xFFE6EDF6.toInt()
                    selector.fontSize = 18
                    selector.onClick {
                        toggleDomDropdown(row.property, unitSelect = false)
                    }
                    renderNode(ctx, selector, translateRectY(row.controlRect, -bodyScrollY))
                }

                InspectorEditorKind.StringInput -> {
                    val input = TextInputNode(
                        text = row.controlValue.replace("|", ""),
                        key = "dsgl-system-inspector-editor-input-${row.property.key}"
                    )
                    input.backgroundColor = if (row.inputActive) 0x334D5D70 else 0x22313D4B
                    input.focusedBackgroundColor = input.backgroundColor
                    input.border = Border.all(1, if (row.inputActive) 0xFFA8C6E6.toInt() else 0x77607084)
                    input.textColor = 0xFFE6EDF6.toInt()
                    input.placeholderColor = 0xAA9AAFC6.toInt()
                    input.fontSize = 18
                    input.onInput = {
                        controller.overlayApplyLiteralOverride(row.property, it.value)
                    }
                    input.onValueChange = {
                        controller.overlayApplyLiteralOverride(row.property, it.value)
                    }
                    input.applyParent(parentNode)
                    renderNode(ctx, input, translateRectY(row.controlRect, -bodyScrollY))

                    row.colorPreviewRect?.let { previewRect ->
                        val shiftedPreviewRect = translateRectY(previewRect, -bodyScrollY)
                        val preview = scope.button("", {
                            key = "dsgl-system-inspector-editor-color-preview-$index"
                        })
                        preview.backgroundColor = row.colorPreviewColor ?: 0x663F4A57
                        preview.border = Border.all(1, 0xCC9BB2C9.toInt())
                        preview.onClick {
                            controller.onOpenColorPickerPressed(row.property, shiftedPreviewRect)
                        }
                        renderNode(ctx, preview, shiftedPreviewRect)
                    }
                }

                InspectorEditorKind.NumericInput -> {
                    row.decrementRect?.let { rect ->
                        val dec = scope.button("-", {
                            key = "dsgl-system-inspector-editor-dec-$index"
                        })
                        dec.backgroundColor = 0x3346596E
                        dec.border = Border.all(1, 0x775E738C)
                        dec.textColor = 0xFFDCE5EF.toInt()
                        dec.fontSize = 18
                        dec.onClick {
                            controller.onNumericDecrementPressed(row.property)
                        }
                        renderNode(ctx, dec, translateRectY(rect, -bodyScrollY))
                    }
                    row.inputRect?.let { rect ->
                        val input = TextInputNode(
                            text = row.controlValue.replace("|", ""),
                            key = "dsgl-system-inspector-editor-numeric-input-${row.property.key}"
                        )
                        input.allowedChars = "-0123456789."
                        input.backgroundColor = if (row.inputActive) 0x334D5D70 else 0x22313D4B
                        input.focusedBackgroundColor = input.backgroundColor
                        input.border = Border.all(1, if (row.inputActive) 0xFFA8C6E6.toInt() else 0x77607084)
                        input.textColor = 0xFFE6EDF6.toInt()
                        input.placeholderColor = 0xAA9AAFC6.toInt()
                        input.fontSize = 18
                        input.onInput = {
                            controller.overlayApplyNumericOverride(row.property, it.value, row.unitValue)
                        }
                        input.onValueChange = {
                            controller.overlayApplyNumericOverride(row.property, it.value, row.unitValue)
                        }
                        input.applyParent(parentNode)
                        renderNode(ctx, input, translateRectY(rect, -bodyScrollY))
                    }

                    row.incrementRect?.let { rect ->
                        val inc = scope.button("+", {
                            key = "dsgl-system-inspector-editor-inc-$index"
                        })
                        inc.backgroundColor = 0x3346596E
                        inc.border = Border.all(1, 0x775E738C)
                        inc.textColor = 0xFFDCE5EF.toInt()
                        inc.fontSize = 18
                        inc.onClick {
                            controller.onNumericIncrementPressed(row.property)
                        }
                        renderNode(ctx, inc, translateRectY(rect, -bodyScrollY))
                    }
                    row.unitRect?.let { rect ->
                        val unitOpen = isDomDropdownOpen(row.property, unitSelect = true)
                        val unit = scope.button(row.unitValue ?: "px", {
                            key = "dsgl-system-inspector-editor-unit-$index"
                        })
                        unit.backgroundColor = if (unitOpen) 0x334D5D70 else 0x22313D4B
                        unit.border = Border.all(1, if (unitOpen) 0xFFA8C6E6.toInt() else 0x77607084)
                        unit.textColor = 0xFFE6EDF6.toInt()
                        unit.fontSize = 18
                        unit.onClick {
                            toggleDomDropdown(row.property, unitSelect = true)
                        }
                        renderNode(ctx, unit, translateRectY(rect, -bodyScrollY))
                    }
                }
            }
        }

        val resetRect = controller.overlayStyleEditorResetRect()
        if (resetRect.width > 0 && resetRect.height > 0) {
            val resetButton = scope.button("Reset node", {
                key = "dsgl-system-inspector-reset-node"
            })
            resetButton.backgroundColor = 0x2A465968
            resetButton.border = Border.all(1, 0x775E738C)
            resetButton.textColor = 0xFFDCE5EF.toInt()
            resetButton.fontSize = 18
            resetButton.onClick {
                controller.onResetSelectedOverridesPressed()
            }
            renderNode(ctx, resetButton, translateRectY(resetRect, -bodyScrollY))
        }

        val clearRect = controller.overlayStyleEditorClearRect()
        if (clearRect.width > 0 && clearRect.height > 0) {
            val clearButton = scope.button("Clear all", {
                key = "dsgl-system-inspector-clear-all"
            })
            clearButton.backgroundColor = 0x2A4E3F56
            clearButton.border = Border.all(1, 0x777A5C84)
            clearButton.textColor = 0xFFDCE5EF.toInt()
            clearButton.fontSize = 18
            clearButton.onClick {
                controller.onClearAllOverridesPressed()
            }
            renderNode(ctx, clearButton, translateRectY(clearRect, -bodyScrollY))
        }
    }

    private fun renderDropdowns(
        scope: UiScope,
        ctx: UiMeasureContext,
        rows: List<InspectorStyleEditorRowSnapshot>,
        bodyScrollY: Int,
        viewportWidth: Int,
        viewportHeight: Int
    ) {
        val dropdown = resolveDomDropdownSnapshot(rows, bodyScrollY, viewportWidth, viewportHeight)
        if (dropdown == null) {
            controller.onNativeDomDropdownSnapshots(emptyList())
            return
        }

        val dropdownKey = dropdownScrollKey(dropdown.property, dropdown.unitSelect)
        val persistedDropdownSession = persistedDropdownScrollSession[dropdownKey]
        val persistedDropdownY = persistedDropdownSession?.resolvedY?.coerceAtLeast(0) ?: 0
        val popup = scope.div({
            key = dropdownKey
            style = {
                display = Display.Block
            }
        })
        popup.backgroundColor = 0xEE202A36.toInt()
        popup.border = Border.all(1, 0xCC596A80.toInt())
        popup.overflowY = Overflow.Auto
        renderNode(ctx, popup, dropdown.popupRect)

        dropdown.options.forEachIndexed { optionIndex, option ->
            val optionRect = Rect(
                option.rect.x,
                option.rect.y - persistedDropdownY,
                option.rect.width,
                option.rect.height
            )
            val hovered = optionRect.contains(cursorX, cursorY)
            val button = scope.button(option.text, {
                key = "$dropdownKey-option-$optionIndex"
            })
            button.backgroundColor = if (hovered) 0x2D4C6279 else 0x22313D4B
            button.border = Border.all(1, if (hovered) 0xCC95B3D3.toInt() else 0x664F6076)
            button.textColor = if (hovered) 0xFFFFFFFF.toInt() else 0xFFE6EDF6.toInt()
            button.fontSize = 18
            button.onClick {
                if (dropdown.unitSelect) {
                    controller.onSelectUnitOptionPressed(dropdown.property, option.value)
                } else {
                    controller.onSelectValueOptionPressed(dropdown.property, option.value)
                }
                closeActiveDomDropdown()
            }
            renderNode(ctx, button, optionRect)
        }

        dropdown.footerText?.let { footer ->
            val footerNode = scope.text(props = {
                key = "$dropdownKey-footer"
                value = footer
                style = {
                    textWrap = TextWrap.NoWrap
                }
            })
            footerNode.color = 0xFF8EA6BF.toInt()
            footerNode.fontSize = 18
            renderNode(
                ctx,
                footerNode,
                Rect(
                    dropdown.popupRect.x + 6,
                    dropdown.popupRect.y + dropdown.popupRect.height - 22 - persistedDropdownY,
                    (dropdown.popupRect.width - 12).coerceAtLeast(20),
                    20
                )
            )
        }

        popup.restoreScrollSessionSnapshot(persistedDropdownSession)
        popup.scrollContainerState()
        persistedDropdownScrollSession[dropdownKey] = popup.captureScrollSessionSnapshot()
        controller.onNativeDomDropdownSnapshots(listOf(dropdown))
    }

    private fun resolveDomDropdownSnapshot(
        rows: List<InspectorStyleEditorRowSnapshot>,
        bodyScrollY: Int,
        viewportWidth: Int,
        viewportHeight: Int
    ): InspectorDropdownSnapshot? {
        val activeDropdown = activeDomDropdown ?: return null
        val row = rows.firstOrNull { it.property == activeDropdown.property } ?: run {
            closeActiveDomDropdown()
            return null
        }
        if (activeDropdown.unitSelect && row.unitRect == null) {
            closeActiveDomDropdown()
            return null
        }

        val options = controller.resolveDropdownOptionsForProperty(activeDropdown.property, activeDropdown.unitSelect)
        if (options.isEmpty()) {
            closeActiveDomDropdown()
            return null
        }

        val triggerRect = if (activeDropdown.unitSelect) {
            row.unitRect ?: row.controlRect
        } else {
            row.controlRect
        }
        val visibleTriggerRect = translateRectY(triggerRect, -bodyScrollY)

        val maxChars = options.maxOfOrNull { it.length } ?: 0
        val estimatedTextWidth = (maxChars * 8 + 22).coerceAtLeast(120)
        val popupWidth = maxOf(triggerRect.width, estimatedTextWidth).coerceAtLeast(120)
        val optionHeight = 24
        val maxVisibleRows = 8
        val visibleRows = minOf(maxVisibleRows, options.size)
        val footerText = if (options.size > visibleRows) "Scroll for more" else null
        val footerHeight = if (footerText != null) 22 else 0
        val popupHeight = (visibleRows * optionHeight + footerHeight + 4).coerceAtLeast(optionHeight + 4)

        val rawX = if (activeDropdown.unitSelect) {
            visibleTriggerRect.x + visibleTriggerRect.width - popupWidth
        } else {
            visibleTriggerRect.x
        }
        val rawY = visibleTriggerRect.y + visibleTriggerRect.height + 2
        val clampedX = rawX.coerceIn(2, (viewportWidth - popupWidth - 2).coerceAtLeast(2))
        val clampedY = rawY.coerceIn(2, (viewportHeight - popupHeight - 2).coerceAtLeast(2))
        val popupRect = Rect(clampedX, clampedY, popupWidth, popupHeight)

        val optionWidth = (popupRect.width - 4).coerceAtLeast(20)
        val optionSnapshots = options.mapIndexed { index, option ->
            InspectorDropdownOptionSnapshot(
                rect = Rect(
                    popupRect.x + 2,
                    popupRect.y + 2 + index * optionHeight,
                    optionWidth,
                    optionHeight
                ),
                text = option,
                value = option,
                hovered = false
            )
        }

        return InspectorDropdownSnapshot(
            popupRect = popupRect,
            property = activeDropdown.property,
            unitSelect = activeDropdown.unitSelect,
            options = optionSnapshots,
            footerText = footerText
        )
    }

    private fun handleActiveDomDropdownWheel(delta: Int): Boolean {
        if (delta == 0 || KeyModifiers.shiftDown) return false
        val activeDropdown = activeDomDropdown ?: return false
        val dropdownKey = dropdownScrollKey(activeDropdown.property, activeDropdown.unitSelect)
        val current = persistedDropdownScrollSession[dropdownKey]
        val baseResolved = current?.resolvedY?.coerceAtLeast(0) ?: 0
        val steps = (kotlin.math.abs(delta) / 120).coerceAtLeast(1)
        val amount = steps * 18
        val nextResolved = if (delta < 0) {
            baseResolved + amount
        } else {
            (baseResolved - amount).coerceAtLeast(0)
        }
        val nextTarget = if (delta < 0) {
            (current?.targetY?.coerceAtLeast(0) ?: baseResolved) + amount
        } else {
            ((current?.targetY?.coerceAtLeast(0) ?: baseResolved) - amount).coerceAtLeast(0)
        }
        persistedDropdownScrollSession[dropdownKey] = ScrollSessionSnapshot(
            targetX = current?.targetX?.coerceAtLeast(0) ?: 0,
            targetY = nextTarget,
            displayedX = current?.displayedX?.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0,
            displayedY = nextResolved.toDouble(),
            resolvedX = current?.resolvedX?.coerceAtLeast(0) ?: 0,
            resolvedY = nextResolved,
            dragSession = current?.dragSession
        )
        return true
    }

    private fun reconcileActiveDomDropdown(rows: List<InspectorStyleEditorRowSnapshot>) {
        val activeDropdown = activeDomDropdown ?: return
        val row = rows.firstOrNull { it.property == activeDropdown.property } ?: run {
            closeActiveDomDropdown()
            return
        }
        if (activeDropdown.unitSelect && row.unitRect == null) {
            closeActiveDomDropdown()
            return
        }
        val options = controller.resolveDropdownOptionsForProperty(activeDropdown.property, activeDropdown.unitSelect)
        if (options.isEmpty()) {
            closeActiveDomDropdown()
        }
    }

    private fun isDomDropdownOpen(property: StyleProperty, unitSelect: Boolean): Boolean {
        val activeDropdown = activeDomDropdown ?: return false
        return activeDropdown.property == property && activeDropdown.unitSelect == unitSelect
    }

    private fun toggleDomDropdown(property: StyleProperty, unitSelect: Boolean) {
        val activeDropdown = activeDomDropdown
        if (activeDropdown != null && activeDropdown.property == property && activeDropdown.unitSelect == unitSelect) {
            closeActiveDomDropdown()
            return
        }
        activeDomDropdown = ActiveDomDropdown(property = property, unitSelect = unitSelect)
    }

    private fun closeActiveDomDropdown() {
        if (activeDomDropdown == null) return
        activeDomDropdown = null
        controller.onNativeDomDropdownSnapshots(emptyList())
    }

    private fun shouldDismissOpenDropdownOnPointerDown(target: DOMNode?): Boolean {
        var current = target
        while (current != null && current !== this) {
            val key = current.key?.toString() ?: ""
            if (key.startsWith("dsgl-system-inspector-dropdown-")) {
                return false
            }
            if (
                key.startsWith("dsgl-system-inspector-editor-select-") ||
                key.startsWith("dsgl-system-inspector-editor-unit-")
            ) {
                return false
            }
            current = current.parent
        }
        return true
    }

    private fun startMinimizedChipDrag(panelRect: Rect, mouseX: Int, mouseY: Int) {
        minimizedChipDragSession = MinimizedChipDragSession(
            startPointerX = mouseX,
            startPointerY = mouseY,
            startRect = panelRect,
            currentPointerX = mouseX,
            currentPointerY = mouseY,
            moved = false
        )
        controller.onOverlayPanelPointerCaptureChanged(true)
    }

    private fun updateMinimizedChipDragPointer(mouseX: Int, mouseY: Int) {
        val session = minimizedChipDragSession ?: return
        session.currentPointerX = mouseX
        session.currentPointerY = mouseY
    }

    private fun applyMinimizedChipDragFrame() {
        val session = minimizedChipDragSession ?: return
        val dx = session.currentPointerX - session.startPointerX
        val dy = session.currentPointerY - session.startPointerY
        if (!session.moved && (kotlin.math.abs(dx) >= 2 || kotlin.math.abs(dy) >= 2)) {
            session.moved = true
        }
        controller.onNativeDomMinimizedPanelPosition(
            x = session.startRect.x + dx,
            y = session.startRect.y + dy,
            viewportWidth = lastViewportWidth,
            viewportHeight = lastViewportHeight
        )
    }

    private fun continueMinimizedChipDrag(mouseX: Int, mouseY: Int) {
        updateMinimizedChipDragPointer(mouseX, mouseY)
        applyMinimizedChipDragFrame()
    }

    private fun endMinimizedChipDrag(mouseX: Int, mouseY: Int) {
        val session = minimizedChipDragSession ?: return
        continueMinimizedChipDrag(mouseX, mouseY)
        if (!session.moved) {
            controller.restore()
        }
        minimizedChipDragSession = null
        controller.onOverlayPanelPointerCaptureChanged(false)
    }

    private fun clearMinimizedChipDragSession() {
        minimizedChipDragSession = null
        controller.onOverlayPanelPointerCaptureChanged(false)
    }
    private fun renderTooltip(
        scope: UiScope,
        ctx: UiMeasureContext,
        keyPrefix: String,
        tooltip: InspectorTooltipSnapshot?,
        backgroundColor: Int,
        borderColor: Int
    ) {
        if (tooltip == null) return
        val box = scope.div({
            key = "$keyPrefix-box"
            style = {
                display = Display.Block
            }
        })
        box.backgroundColor = backgroundColor
        box.border = Border.all(1, borderColor)
        renderNode(ctx, box, tooltip.rect)

        val textNode = scope.text(props = {
            key = "$keyPrefix-text"
            value = tooltip.text
            style = {
                textWrap = TextWrap.NoWrap
            }
        })
        textNode.color = 0xFFE6EDF6.toInt()
        textNode.fontSize = 18
        renderNode(
            ctx,
            textNode,
            Rect(
                tooltip.rect.x + 6,
                tooltip.rect.y + 4,
                (tooltip.rect.width - 10).coerceAtLeast(20),
                (tooltip.rect.height - 8).coerceAtLeast(16)
            )
        )
    }

    private fun isDomOwnedInteractionTarget(target: DOMNode?): Boolean {
        var current = target
        while (current != null && current !== this) {
            when (current.styleType) {
                "input", "textarea", "select", "toggle", "button" -> return true
            }
            val nodeKey = current.key?.toString()
            if (nodeKey?.startsWith("dsgl-system-inspector-") == true) {
                return true
            }
            if (nodeKey?.startsWith("dsgl-overlay-panel-") == true) {
                return true
            }
            current = current.parent
        }
        return current === this && target !== this
    }

    private fun capturePersistedScrollStateFromCurrentTree() {
        findNodeByKey("dsgl-system-inspector-body")?.let { bodyNode ->
            persistedBodyScrollSession = bodyNode.captureScrollSessionSnapshot()
        }
        val nextDropdownScroll = LinkedHashMap<String, ScrollSessionSnapshot>()
        collectNodes(this).forEach { node ->
            val nodeKey = node.key?.toString() ?: return@forEach
            if (!nodeKey.startsWith("dsgl-system-inspector-dropdown-")) return@forEach
            nextDropdownScroll[nodeKey] = node.captureScrollSessionSnapshot()
        }
        persistedDropdownScrollSession.clear()
        persistedDropdownScrollSession.putAll(nextDropdownScroll)
    }

    private fun findNodeByKey(targetKey: String): DOMNode? {
        return collectNodes(this).firstOrNull { it.key == targetKey }
    }

    private fun collectNodes(root: DOMNode): List<DOMNode> {
        val out = ArrayList<DOMNode>()
        fun walk(node: DOMNode) {
            out += node
            node.children.forEach(::walk)
        }
        walk(root)
        return out
    }

    private fun dropdownScrollKey(property: StyleProperty, unitSelect: Boolean): String {
        return "dsgl-system-inspector-dropdown-${property.key}-${if (unitSelect) "unit" else "value"}"
    }

    private fun shouldRetainInspectorSubtreeFocus(): Boolean {
        val focused = FocusManager.focusedNode() ?: return false
        return isSameOrAncestor(this, focused)
    }

    private fun isSameOrAncestor(candidate: DOMNode, node: DOMNode?): Boolean {
        var current = node
        while (current != null) {
            if (current === candidate) return true
            current = current.parent
        }
        return false
    }

    private fun translateRectY(rect: Rect, deltaY: Int): Rect {
        return Rect(rect.x, rect.y + deltaY, rect.width, rect.height)
    }
    private fun renderNode(
        ctx: UiMeasureContext,
        node: DOMNode,
        rect: Rect
    ) {
        if (rect.width <= 0 || rect.height <= 0) {
            node.display = Display.None
            node.render(ctx, 0, 0, 0, 0)
            return
        }
        node.display = Display.Block
        node.render(ctx, rect.x, rect.y, rect.width, rect.height)
    }
}
