package org.dreamfinity.dsgl.core.inspector.internal

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.ScrollSessionSnapshot
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.TextInputNode
import org.dreamfinity.dsgl.core.dom.layout.Border
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.dsl.*
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.inspector.*
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanel
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanelDragSession
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanelState
import org.dreamfinity.dsgl.core.select.SelectRuntime
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.TextWrap

internal class SystemInspectorOverlayNode(
    private val controller: InspectorController,
    private val overlayPanel: OverlayPanel,
    key: Any? = "dsgl-system-inspector",
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-inspector"
    override val focusable: Boolean = true

    internal constructor(
        controller: InspectorController,
        key: Any? = "dsgl-system-inspector",
    ) : this(
        controller = controller,
        overlayPanel =
            OverlayPanel(
                ownerId = "standalone-system-inspector",
                panelState = OverlayPanelState(),
                dragSession = OverlayPanelDragSession(),
            ),
        key = key,
    )

    private var inspectedRoot: DOMNode? = null
    private var inspectedLayoutRevision: Long = 0L
    private var cursorX: Int = 0
    private var cursorY: Int = 0
    private var lastViewportWidth: Int = 1
    private var lastViewportHeight: Int = 1
    private var persistedBodyScrollSession: ScrollSessionSnapshot? = null
    private var overlayPanelDragUpdatedByDomInput: Boolean = false
    private val panelNode: DOMNode = overlayPanel.node().applyParent(this)
    private var minimizedChipDragSession: MinimizedChipDragSession? = null

    private data class MinimizedChipDragSession(
        val startPointerX: Int,
        val startPointerY: Int,
        val startRect: Rect,
        var currentPointerX: Int,
        var currentPointerY: Int,
        var moved: Boolean = false,
    )

    init {
        EventBus.run {
            this@SystemInspectorOverlayNode.addEventListener(Events.MOUSEDOWN) { event: MouseDownEvent ->
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
        }
    }

    private fun handleOverlayPanelMouseDown(event: MouseDownEvent): Boolean {
        if (event.mouseButton != MouseButton.LEFT) return false
        val bodyRect = controller.overlayContentRect()
        val pointerInsideBody =
            bodyRect.width > 0 &&
                bodyRect.height > 0 &&
                bodyRect.contains(event.mouseX, event.mouseY)
        if (pointerInsideBody) return false
        val handled =
            overlayPanel.handleMouseDown(
                mouseX = event.mouseX,
                mouseY = event.mouseY,
                button = event.mouseButton,
                includeCloseButton = false,
            )
        if (handled) {
            controller.onOverlayPanelPointerCaptureChanged(true)
        }
        return handled
    }

    private fun handleOverlayPanelDrag(mouseX: Int, mouseY: Int): Boolean {
        val handled =
            overlayPanel.handleMouseMove(
                mouseX = mouseX,
                mouseY = mouseY,
                viewportWidth = lastViewportWidth,
                viewportHeight = lastViewportHeight,
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
        val handled =
            overlayPanel.handleMouseUp(
                mouseX = event.mouseX,
                mouseY = event.mouseY,
                button = event.mouseButton,
                viewportWidth = lastViewportWidth,
                viewportHeight = lastViewportHeight,
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

    override fun measure(ctx: UiMeasureContext): Size =
        Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))

    override fun render(
        ctx: UiMeasureContext,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
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
            controller.onNativeDomDropdownSnapshots(emptyList())
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
        return null
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
            height = (bottom - top).coerceAtLeast(0),
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
        controller.onNativeDomDropdownSnapshots(emptyList())
        panelNode.render(ctx, 0, 0, 0, 0)
        val scope = UiScope(this)

        renderHighlights(scope, ctx)
        renderMinimizedChip(scope, ctx, snapshot)
    }

    private fun renderExpanded(ctx: UiMeasureContext, snapshot: InspectorDomSnapshot, viewportRect: Rect) {
        val panelRect = snapshot.panelRect
        val bodyRect =
            snapshot.bodyRect
                ?: overlayPanel.bodyRect()
                ?: Rect(
                    panelRect.x + 6,
                    panelRect.y + 58,
                    panelRect.width - 12,
                    (panelRect.height - 64).coerceAtLeast(24),
                )
        val scope = UiScope(this)

        renderHighlights(scope, ctx)
        renderPanelOccluder(scope, ctx, panelRect)
        panelNode.render(ctx, viewportRect.x, viewportRect.y, viewportRect.width, viewportRect.height)

        renderExpandedChrome(scope, ctx, panelRect)

        val body =
            scope.div({
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

        y =
            renderBodyInfoLines(
                scope = bodyScope,
                ctx = ctx,
                infoLines = snapshot.infoLines,
                contentX = contentX,
                contentW = contentW,
                startY = y,
                lineHeightPx = lineHeightPx,
            )

        y =
            renderParentRow(
                scope = bodyScope,
                ctx = ctx,
                parentLabel = snapshot.parentLabel,
                contentX = contentX,
                contentW = contentW,
                startY = y,
                rowHeightPx = rowHeightPx,
            )
        y =
            renderChildRows(
                scope = bodyScope,
                ctx = ctx,
                childLabels = snapshot.childLabels,
                contentX = contentX,
                contentW = contentW,
                startY = y,
                rowHeightPx = rowHeightPx,
            )
        renderStyleEditorHeading(
            scope = bodyScope,
            ctx = ctx,
            contentX = contentX,
            contentW = contentW,
            y = y,
            lineHeightPx = lineHeightPx,
        )

        val styleRows = controller.overlayStyleEditorRows()
        renderStyleEditorRows(bodyScope, body, ctx, bodyScrollY, styleRows)
        y += snapshot.styleEditorHeight

        y =
            renderComputedStyleLines(
                scope = bodyScope,
                ctx = ctx,
                styleLines = snapshot.styleLines,
                contentX = contentX,
                contentW = contentW,
                startY = y,
                lineHeightPx = lineHeightPx,
            )
        controller.onNativeDomDropdownSnapshots(emptyList())
        body.restoreScrollSessionSnapshot(persistedBodyScrollSession)
        val bodyState = body.scrollContainerState()
        persistedBodyScrollSession = body.captureScrollSessionSnapshot()
        val bodyScrollbarVisual = body.debugScrollbarVisualState().vertical
        controller.onNativeDomBodyScrollState(
            scrollY = bodyState.scrollY,
            trackRect = bodyScrollbarVisual?.trackRect,
            thumbRect = bodyScrollbarVisual?.thumbRect,
        )
        renderTooltip(
            scope,
            ctx,
            "dsgl-system-inspector-variable-tooltip",
            controller.overlayVariableTooltip(),
            0xEE141A22.toInt(),
            0xCC60758F.toInt(),
        )
        renderTooltip(
            scope,
            ctx,
            "dsgl-system-inspector-cursor-tooltip",
            controller.overlayCursorTooltip(),
            0xDD11151A.toInt(),
            0xCC3F4A57.toInt(),
        )
    }

    private fun renderMinimizedChip(scope: UiScope, ctx: UiMeasureContext, snapshot: InspectorDomSnapshot) {
        val chip =
            scope.div({
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
        var lineY =
            snapshot.panelRect.y + ((snapshot.panelRect.height - compactLineHeight * snapshot.minimizedLines.size) / 2)
        snapshot.minimizedLines.forEachIndexed { index, line ->
            val lineNode =
                scope.text(props = {
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
                    compactLineHeight,
                ),
            )
            lineY += compactLineHeight
        }
    }

    private fun renderExpandedChrome(scope: UiScope, ctx: UiMeasureContext, panelRect: Rect) {
        val pickRect =
            controller.overlayPickToggleBounds()
                ?: Rect(panelRect.x + panelRect.width - 264, panelRect.y + 8, 160, 36)
        val minimizeRect =
            controller.overlayMinimizeBounds()
                ?: Rect(panelRect.x + panelRect.width - 96, panelRect.y + 8, 86, 36)
        renderPickToggleButton(scope, ctx, pickRect)
        renderMinimizeButton(scope, ctx, minimizeRect)
    }

    private fun renderPickToggleButton(scope: UiScope, ctx: UiMeasureContext, rect: Rect) {
        val pickButton =
            scope.button("Select Element", {
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
        val minimizeButton =
            scope.button("Minimize", {
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
        lineHeightPx: Int,
    ): Int {
        var y = startY
        infoLines.forEachIndexed { index, line ->
            val lineNode =
                scope.text(props = {
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
        rowHeightPx: Int,
    ): Int {
        var y = startY
        parentLabel?.let { label ->
            val parentButton =
                scope.button(label, {
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
        rowHeightPx: Int,
    ): Int {
        var y = startY
        childLabels.forEachIndexed { index, label ->
            val childButton =
                scope.button(label, {
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
        lineHeightPx: Int,
    ) {
        val styleEditorHeader =
            scope.text(props = {
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
        lineHeightPx: Int,
    ): Int {
        var y = startY
        styleLines.forEachIndexed { index, line ->
            val lineNode =
                scope.text(props = {
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
        val occluder =
            scope.div({
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
            renderHighlightRect(
                scope,
                ctx,
                "dsgl-system-inspector-selected-margin-fill",
                highlight.marginRect,
                0x44F3B33D,
                null,
            )
            renderHighlightRect(
                scope,
                ctx,
                "dsgl-system-inspector-selected-padding-fill",
                highlight.paddingRect,
                0x4426A69A,
                null,
            )
            renderHighlightRect(
                scope,
                ctx,
                "dsgl-system-inspector-selected-content-fill",
                highlight.contentRect,
                0x444285F4,
                null,
            )
            renderHighlightRect(
                scope,
                ctx,
                "dsgl-system-inspector-selected-margin-outline",
                highlight.marginRect,
                null,
                0x99F3B33D.toInt(),
            )
            renderHighlightRect(
                scope,
                ctx,
                "dsgl-system-inspector-selected-border-outline",
                highlight.borderRect,
                null,
                0xCCFF9800.toInt(),
            )
            renderHighlightRect(
                scope,
                ctx,
                "dsgl-system-inspector-selected-padding-outline",
                highlight.paddingRect,
                null,
                0x9926A69A.toInt(),
            )
            renderHighlightRect(
                scope,
                ctx,
                "dsgl-system-inspector-selected-content-outline",
                highlight.contentRect,
                null,
                0x994285F4.toInt(),
            )
            highlight.parentContentRect?.let { parentRect ->
                renderHighlightRect(
                    scope,
                    ctx,
                    "dsgl-system-inspector-selected-parent-outline",
                    parentRect,
                    null,
                    0x66FF5252,
                )
            }
        }
        controller.overlayHoveredHighlight()?.let { highlight ->
            renderHighlightRect(
                scope,
                ctx,
                "dsgl-system-inspector-hovered-content-fill",
                highlight.contentRect,
                0x3A47A0FF,
                null,
            )
            renderHighlightRect(
                scope,
                ctx,
                "dsgl-system-inspector-hovered-border-outline",
                highlight.borderRect,
                null,
                0xCC47A0FF.toInt(),
            )
        }
    }

    private fun renderHighlightRect(
        scope: UiScope,
        ctx: UiMeasureContext,
        key: String,
        rect: Rect,
        fillColor: Int?,
        borderColor: Int?,
    ) {
        if (rect.width <= 0 || rect.height <= 0) return
        val layer =
            scope.div({
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
        rows: List<InspectorStyleEditorRowSnapshot>,
    ) {
        rows.forEachIndexed { index, row ->
            val rowRect = translateRectY(row.rowRect, -bodyScrollY)
            renderStyleEditorRowContainer(scope, ctx, rowRect, index)
            renderStyleEditorRowLabel(scope, ctx, rowRect, row, index)
            renderStyleEditorRowResetButton(scope, ctx, bodyScrollY, row, index)

            when (row.editorKind) {
                InspectorEditorKind.EnumSelect,
                InspectorEditorKind.FontSelect,
                -> {
                    renderStyleEditorSelectButton(scope, ctx, bodyScrollY, row, index)
                }

                InspectorEditorKind.StringInput -> {
                    renderStyleEditorStringInput(scope, parentNode, ctx, bodyScrollY, row)
                    renderStyleEditorColorPreview(scope, ctx, bodyScrollY, row, index)
                }

                InspectorEditorKind.NumericInput -> {
                    renderStyleEditorNumericControls(scope, parentNode, ctx, bodyScrollY, row, index)
                }
            }
        }

        renderStyleEditorFooterActions(scope, ctx, bodyScrollY)
    }

    private fun renderStyleEditorRowContainer(
        scope: UiScope,
        ctx: UiMeasureContext,
        rowRect: Rect,
        index: Int,
    ) {
        val rowNode =
            scope.div({
                key = "dsgl-system-inspector-editor-row-$index"
                style = {
                    display = Display.Block
                }
            })
        rowNode.backgroundColor = 0x1B293746
        rowNode.border = Border.all(1, 0x553F4A57)
        renderNode(ctx, rowNode, rowRect)
    }

    private fun renderStyleEditorRowLabel(
        scope: UiScope,
        ctx: UiMeasureContext,
        rowRect: Rect,
        row: InspectorStyleEditorRowSnapshot,
        index: Int,
    ) {
        val labelNode =
            scope.text(props = {
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
            Rect(
                rowRect.x + 8,
                rowRect.y + 5,
                (row.controlRect.x - row.rowRect.x - 14).coerceAtLeast(40),
                rowRect.height - 10,
            ),
        )
    }

    private fun renderStyleEditorRowResetButton(
        scope: UiScope,
        ctx: UiMeasureContext,
        bodyScrollY: Int,
        row: InspectorStyleEditorRowSnapshot,
        index: Int,
    ) {
        val resetButton =
            scope.button("x", {
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
    }

    private fun renderStyleEditorSelectButton(
        scope: UiScope,
        ctx: UiMeasureContext,
        bodyScrollY: Int,
        row: InspectorStyleEditorRowSnapshot,
        index: Int,
    ) {
        val selector =
            buildSystemOwnedSelectControl(
                scope = scope,
                key = "dsgl-system-inspector-editor-select-$index",
                selectedValue = row.controlValue,
                options = controller.resolveDropdownOptionsForProperty(row.property, unitSelect = false),
                hovered = row.controlHovered,
            ) { selected ->
                controller.onSelectValueOptionPressed(row.property, selected)
            }
        renderNode(ctx, selector, translateRectY(row.controlRect, -bodyScrollY))
    }

    private fun renderStyleEditorStringInput(
        scope: UiScope,
        parentNode: DOMNode,
        ctx: UiMeasureContext,
        bodyScrollY: Int,
        row: InspectorStyleEditorRowSnapshot,
    ) {
        val input =
            TextInputNode(
                text = row.controlValue.replace("|", ""),
                key = "dsgl-system-inspector-editor-input-${row.property.key}",
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
    }

    private fun renderStyleEditorColorPreview(
        scope: UiScope,
        ctx: UiMeasureContext,
        bodyScrollY: Int,
        row: InspectorStyleEditorRowSnapshot,
        index: Int,
    ) {
        row.colorPreviewRect?.let { previewRect ->
            val shiftedPreviewRect = translateRectY(previewRect, -bodyScrollY)
            val preview =
                scope.button("", {
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

    private fun renderStyleEditorNumericControls(
        scope: UiScope,
        parentNode: DOMNode,
        ctx: UiMeasureContext,
        bodyScrollY: Int,
        row: InspectorStyleEditorRowSnapshot,
        index: Int,
    ) {
        row.decrementRect?.let { rect ->
            val dec =
                scope.button("-", {
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
            val input =
                TextInputNode(
                    text = row.controlValue.replace("|", ""),
                    key = "dsgl-system-inspector-editor-numeric-input-${row.property.key}",
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
            val inc =
                scope.button("+", {
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
            val unitValue = row.unitValue ?: "px"
            val unit =
                buildSystemOwnedSelectControl(
                    scope = scope,
                    key = "dsgl-system-inspector-editor-unit-$index",
                    selectedValue = unitValue,
                    options = controller.resolveDropdownOptionsForProperty(row.property, unitSelect = true),
                    hovered = false,
                ) { selected ->
                    controller.onSelectUnitOptionPressed(row.property, selected)
                }
            renderNode(ctx, unit, translateRectY(rect, -bodyScrollY))
        }
    }

    private fun buildSystemOwnedSelectControl(
        scope: UiScope,
        key: String,
        selectedValue: String,
        options: List<String>,
        hovered: Boolean,
        onSelected: (String) -> Unit,
    ): DOMNode {
        val open = SelectRuntime.host.isOpenFor(key)
        val selectNode =
            scope.select(
                props = {
                    this.key = key
                    ownerScope = OverlayOwnerScope.System
                    value = selectedValue
                    onInput = { onSelected(it.value) }
                },
            ) {
                options.forEach { option ->
                    option(id = option, label = option)
                }
            }
        selectNode.backgroundColor =
            if (open) {
                0x334D5D70
            } else if (hovered) {
                0x2A425164
            } else {
                0x22313D4B
            }
        selectNode.border = Border.all(1, if (open) 0xFFA8C6E6.toInt() else 0x77607084)
        selectNode.textColor = 0xFFE6EDF6.toInt()
        selectNode.fontSize = 18
        selectNode.placeholderColor = 0xFFE6EDF6.toInt()
        return selectNode
    }

    private fun renderStyleEditorFooterActions(scope: UiScope, ctx: UiMeasureContext, bodyScrollY: Int) {
        val resetRect = controller.overlayStyleEditorResetRect()
        if (resetRect.width > 0 && resetRect.height > 0) {
            val resetButton =
                scope.button("Reset node", {
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
            val clearButton =
                scope.button("Clear all", {
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

    private fun startMinimizedChipDrag(panelRect: Rect, mouseX: Int, mouseY: Int) {
        minimizedChipDragSession =
            MinimizedChipDragSession(
                startPointerX = mouseX,
                startPointerY = mouseY,
                startRect = panelRect,
                currentPointerX = mouseX,
                currentPointerY = mouseY,
                moved = false,
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
            viewportHeight = lastViewportHeight,
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
        borderColor: Int,
    ) {
        if (tooltip == null) return
        val box =
            scope.div({
                key = "$keyPrefix-box"
                style = {
                    display = Display.Block
                }
            })
        box.backgroundColor = backgroundColor
        box.border = Border.all(1, borderColor)
        renderNode(ctx, box, tooltip.rect)

        val textNode =
            scope.text(props = {
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
                (tooltip.rect.height - 8).coerceAtLeast(16),
            ),
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
    }

    private fun findNodeByKey(targetKey: String): DOMNode? = collectNodes(this).firstOrNull { it.key == targetKey }

    private fun collectNodes(root: DOMNode): List<DOMNode> {
        val out = ArrayList<DOMNode>()

        fun walk(node: DOMNode) {
            out += node
            node.children.forEach(::walk)
        }
        walk(root)
        return out
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

    private fun translateRectY(rect: Rect, deltaY: Int): Rect = Rect(rect.x, rect.y + deltaY, rect.width, rect.height)

    private fun renderNode(ctx: UiMeasureContext, node: DOMNode, rect: Rect) {
        if (rect.width <= 0 || rect.height <= 0) {
            node.display = Display.None
            node.render(ctx, 0, 0, 0, 0)
            return
        }
        node.display = Display.Block
        node.render(ctx, rect.x, rect.y, rect.width, rect.height)
    }
}
