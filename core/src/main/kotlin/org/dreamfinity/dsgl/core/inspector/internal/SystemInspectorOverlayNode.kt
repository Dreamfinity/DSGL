package org.dreamfinity.dsgl.core.inspector.internal

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.TextInputNode
import org.dreamfinity.dsgl.core.dom.layout.Border
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.InspectorDomSnapshot
import org.dreamfinity.dsgl.core.inspector.InspectorEditorKind
import org.dreamfinity.dsgl.core.inspector.InspectorPanelState
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.TextWrap

internal class SystemInspectorOverlayNode(
    private val controller: InspectorController,
    key: Any? = "dsgl-system-inspector"
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-inspector"
    override val focusable: Boolean = true

    private var inspectedRoot: DOMNode? = null
    private var inspectedLayoutRevision: Long = 0L
    private var cursorX: Int = 0
    private var cursorY: Int = 0
    private var pointerCaptured: Boolean = false

    init {
        EventBus.run {
            this@SystemInspectorOverlayNode.addEventListener(Events.MOUSEDOWN) { event: MouseDownEvent ->
                if (isDomOwnedControlTarget(event.target)) return@addEventListener
                if (controller.handleMouseDown(event.mouseX, event.mouseY, event.mouseButton)) {
                    event.cancelled = true
                }
            }
            this@SystemInspectorOverlayNode.addEventListener(Events.MOUSEUP) { event: MouseUpEvent ->
                val routeToController = controller.isPointerCaptured || !isDomOwnedControlTarget(event.target)
                if (!routeToController) return@addEventListener
                if (controller.handleMouseUp(event.mouseX, event.mouseY, event.mouseButton)) {
                    event.cancelled = true
                }
            }
            this@SystemInspectorOverlayNode.addEventListener(Events.DRAG) { event: MouseDragEvent ->
                if (isDomOwnedControlTarget(event.target)) return@addEventListener
                if (!controller.isPointerCaptured) return@addEventListener
                val nextMouseX = event.lastMouseX + event.dx
                val nextMouseY = event.lastMouseY + event.dy
                controller.onCapturedPointerMove(nextMouseX, nextMouseY, bounds.width, bounds.height)
                event.cancelled = true
            }
            this@SystemInspectorOverlayNode.addEventListener(Events.WHEEL) { event: MouseWheelEvent ->
                if (controller.handleMouseWheel(event.mouseX, event.mouseY, event.dWheel)) {
                    event.cancelled = true
                }
            }
            this@SystemInspectorOverlayNode.addEventListener(Events.KEYDOWN) { event: KeyboardKeyDownEvent ->
                if (isDomOwnedControlTarget(event.target)) return@addEventListener
                if (controller.handleKeyDown(event.keyCode, event.keyChar)) {
                    event.cancelled = true
                }
            }
        }
    }

    fun bindInspectedTree(root: DOMNode?, layoutRevision: Long) {
        inspectedRoot = root
        inspectedLayoutRevision = layoutRevision
    }

    fun updateCursor(mouseX: Int, mouseY: Int, pointerCaptured: Boolean) {
        cursorX = mouseX
        cursorY = mouseY
        this.pointerCaptured = pointerCaptured
    }

    fun syncInputBounds(viewportWidth: Int, viewportHeight: Int) {
        val viewportRect = Rect(0, 0, viewportWidth.coerceAtLeast(0), viewportHeight.coerceAtLeast(0))
        bounds = resolveInputBounds(viewportRect, controller.debugPanelRect())
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun shouldCapturePointerDrag(mouseX: Int, mouseY: Int): Boolean {
        if (controller.isPointerCaptured) return true
        return super.shouldCapturePointerDrag(mouseX, mouseY)
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        val viewportRect = Rect(x, y, width, height)
        bounds = resolveInputBounds(viewportRect, controller.debugPanelRect())
        inspectedRoot?.let { root ->
            controller.onLayoutCommitted(root, inspectedLayoutRevision)
        }
        controller.onCursorMoved(cursorX, cursorY)
        if (pointerCaptured) {
            controller.onCapturedPointerMove(cursorX, cursorY, width, height)
        }

        val snapshot = controller.buildDomSnapshot(viewportRect.width, viewportRect.height)
        if (snapshot == null) {
            clearTree()
            return
        }
        bounds = resolveInputBounds(viewportRect, snapshot.panelRect)

        clearTree()
        when (snapshot.panelState) {
            InspectorPanelState.Minimized -> renderMinimized(ctx, snapshot)
            InspectorPanelState.Expanded -> renderExpanded(ctx, snapshot)
        }
        FocusManager.retainFocus(this, updateRootReference = false)
    }

    private fun resolveInputBounds(viewportRect: Rect, panelRect: Rect?): Rect {
        if (controller.blocksUnderlyingInput() || controller.isPointerCaptured) {
            return viewportRect
        }
        return panelRect ?: viewportRect
    }

    private fun clearTree() {
        EventBus.run {
            children.forEach { child ->
                child.clearListenersDeep()
                child.parent = null
            }
        }
        children.clear()
        markRenderCommandsDirty()
    }

    private fun renderMinimized(ctx: UiMeasureContext, snapshot: InspectorDomSnapshot) {
        val scope = UiScope(this)

        renderHighlights(scope, ctx)
        val chip = scope.div({
            key = "dsgl-system-inspector-chip"
            style = {
                display = Display.Block
            }
        })
        chip.backgroundColor = 0xDD1A202A.toInt()
        chip.border = Border.all(1, 0xCC4F6076.toInt())
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

    private fun renderExpanded(ctx: UiMeasureContext, snapshot: InspectorDomSnapshot) {
        val panelRect = snapshot.panelRect
        val headerRect = snapshot.headerRect ?: Rect(panelRect.x, panelRect.y, panelRect.width, 42)
        val bodyRect = snapshot.bodyRect ?: Rect(panelRect.x, panelRect.y + 42, panelRect.width, panelRect.height - 42)
        val scope = UiScope(this)

        renderHighlights(scope, ctx)

        val panel = scope.div({
            key = "dsgl-system-inspector-panel"
            style = {
                display = Display.Block
            }
        })
        panel.backgroundColor = 0xE0141820.toInt()
        panel.border = Border.all(1, 0xCC425062.toInt())
        renderNode(ctx, panel, panelRect)

        val header = scope.div({
            key = "dsgl-system-inspector-header"
            style = {
                display = Display.Block
            }
        })
        header.backgroundColor = 0x222D3846
        header.border = Border.all(1, 0x553F4A57)
        renderNode(ctx, header, headerRect)

        val pickRect = controller.debugPickToggleBounds()
            ?: Rect(headerRect.x + headerRect.width - 264, headerRect.y + 8, 160, (headerRect.height - 16).coerceAtLeast(22))
        val minimizeRect = controller.debugMinimizeBounds()
            ?: Rect(headerRect.x + headerRect.width - 96, headerRect.y + 8, 86, (headerRect.height - 16).coerceAtLeast(22))

        val titleNode = scope.text(props = {
            key = "dsgl-system-inspector-header-title"
            value = snapshot.headerText
            style = {
                textWrap = TextWrap.NoWrap
            }
        })
        titleNode.color = 0xFFE6EDF6.toInt()
        titleNode.fontSize = 24
        renderNode(
            ctx,
            titleNode,
            Rect(
                headerRect.x + 8,
                headerRect.y + 6,
                (pickRect.x - headerRect.x - 14).coerceAtLeast(40),
                (headerRect.height - 10).coerceAtLeast(24)
            )
        )

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
        renderNode(ctx, pickButton, pickRect)

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
        renderNode(ctx, minimizeButton, minimizeRect)

        val body = scope.div({
            key = "dsgl-system-inspector-body"
            style = {
                display = Display.Block
            }
        })
        body.backgroundColor = 0x18212C39
        body.overflow = Overflow.Hidden
        val bodyScope = UiScope(body)
        renderNode(ctx, body, bodyRect)

        val lineHeightPx = 32
        val rowHeightPx = 34
        val contentX = bodyRect.x + 4
        val contentW = (bodyRect.width - 10).coerceAtLeast(1)
        var y = bodyRect.y + 2 - controller.panelScrollOffsetY.coerceAtLeast(0)

        snapshot.infoLines.forEachIndexed { index, line ->
            val lineNode = bodyScope.text(props = {
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

        snapshot.parentLabel?.let { label ->
            val parentButton = bodyScope.button(label, {
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

        snapshot.childLabels.forEachIndexed { index, label ->
            val childButton = bodyScope.button(label, {
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
        val styleEditorHeader = bodyScope.text(props = {
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

        renderStyleEditorRows(bodyScope, body, ctx)
        y += snapshot.styleEditorHeight

        snapshot.styleLines.forEachIndexed { index, line ->
            val lineNode = bodyScope.text(props = {
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

        val scrollbarTrack = controller.debugScrollbarTrackRect()
        if (scrollbarTrack.width > 0 && scrollbarTrack.height > 0) {
            val trackNode = bodyScope.div({
                key = "dsgl-system-inspector-scrollbar-track"
                style = {
                    display = Display.Block
                }
            })
            trackNode.backgroundColor = 0x22384A5D
            trackNode.border = Border.NONE
            renderNode(ctx, trackNode, scrollbarTrack)
        }

        val scrollbarThumb = controller.debugScrollbarThumbRect()
        if (scrollbarThumb.width > 0 && scrollbarThumb.height > 0) {
            val thumbNode = bodyScope.div({
                key = "dsgl-system-inspector-scrollbar-thumb"
                style = {
                    display = Display.Block
                }
            })
            thumbNode.backgroundColor = 0x887E97B1.toInt()
            thumbNode.border = Border.all(1, 0xCC9BB2C9.toInt())
            renderNode(ctx, thumbNode, scrollbarThumb)
        }

        renderDropdowns(scope, ctx)
        renderTooltip(scope, ctx, "dsgl-system-inspector-variable-tooltip", controller.debugVariableTooltip(), 0xEE141A22.toInt(), 0xCC60758F.toInt())
        renderTooltip(scope, ctx, "dsgl-system-inspector-cursor-tooltip", controller.debugCursorTooltip(), 0xDD11151A.toInt(), 0xCC3F4A57.toInt())
    }


    private fun renderHighlights(scope: UiScope, ctx: UiMeasureContext) {
        controller.debugSelectedHighlight()?.let { highlight ->
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
        controller.debugHoveredHighlight()?.let { highlight ->
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

    private fun renderStyleEditorRows(scope: UiScope, parentNode: DOMNode, ctx: UiMeasureContext) {
        val rows = controller.debugStyleEditorRows()
        rows.forEachIndexed { index, row ->
            val rowNode = scope.div({
                key = "dsgl-system-inspector-editor-row-$index"
                style = {
                    display = Display.Block
                }
            })
            rowNode.backgroundColor = 0x1B293746
            rowNode.border = Border.all(1, 0x553F4A57)
            renderNode(ctx, rowNode, row.rowRect)

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
                Rect(row.rowRect.x + 8, row.rowRect.y + 5, (row.controlRect.x - row.rowRect.x - 14).coerceAtLeast(40), row.rowRect.height - 10),
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
            renderNode(ctx, resetButton, row.resetRect)

            when (row.editorKind) {
                InspectorEditorKind.EnumSelect,
                InspectorEditorKind.FontSelect -> {
                    val selector = scope.button(row.controlValue, {
                        key = "dsgl-system-inspector-editor-select-$index"
                    })
                    selector.backgroundColor = if (row.controlOpen) 0x334D5D70 else if (row.controlHovered) 0x2A425164 else 0x22313D4B
                    selector.border = Border.all(1, if (row.controlOpen) 0xFFA8C6E6.toInt() else 0x77607084)
                    selector.textColor = 0xFFE6EDF6.toInt()
                    selector.fontSize = 18
                    selector.onClick {
                        controller.onToggleValueSelectPressed(row.property)
                    }
                    renderNode(ctx, selector, row.controlRect)
                }

                InspectorEditorKind.StringInput -> {
                    val input = TextInputNode(
                        text = row.controlValue.replace("|", ""),
                        key = "dsgl-system-inspector-editor-input-$index"
                    )
                    input.backgroundColor = if (row.inputActive) 0x334D5D70 else 0x22313D4B
                    input.focusedBackgroundColor = input.backgroundColor
                    input.border = Border.all(1, if (row.inputActive) 0xFFA8C6E6.toInt() else 0x77607084)
                    input.textColor = 0xFFE6EDF6.toInt()
                    input.placeholderColor = 0xAA9AAFC6.toInt()
                    input.fontSize = 18
                    input.onInput = {
                        controller.debugApplyLiteralOverride(row.property, it.value)
                    }
                    input.onValueChange = {
                        controller.debugApplyLiteralOverride(row.property, it.value)
                    }
                        input.applyParent(parentNode)
                    renderNode(ctx, input, row.controlRect)

                    row.colorPreviewRect?.let { previewRect ->
                        val preview = scope.button("", {
                            key = "dsgl-system-inspector-editor-color-preview-$index"
                        })
                        preview.backgroundColor = row.colorPreviewColor ?: 0x663F4A57
                        preview.border = Border.all(1, 0xCC9BB2C9.toInt())
                        preview.onClick {
                            controller.onOpenColorPickerPressed(row.property, previewRect)
                        }
                        renderNode(ctx, preview, previewRect)
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
                        renderNode(ctx, dec, rect)
                    }
                    row.inputRect?.let { rect ->
                        val input = TextInputNode(
                            text = row.controlValue.replace("|", ""),
                            key = "dsgl-system-inspector-editor-numeric-input-$index"
                        )
                        input.allowedChars = "-0123456789."
                        input.backgroundColor = if (row.inputActive) 0x334D5D70 else 0x22313D4B
                        input.focusedBackgroundColor = input.backgroundColor
                        input.border = Border.all(1, if (row.inputActive) 0xFFA8C6E6.toInt() else 0x77607084)
                        input.textColor = 0xFFE6EDF6.toInt()
                        input.placeholderColor = 0xAA9AAFC6.toInt()
                        input.fontSize = 18
                        input.onInput = {
                            controller.debugApplyNumericOverride(row.property, it.value, row.unitValue)
                        }
                        input.onValueChange = {
                            controller.debugApplyNumericOverride(row.property, it.value, row.unitValue)
                        }
                        input.applyParent(parentNode)
                        renderNode(ctx, input, rect)
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
                        renderNode(ctx, inc, rect)
                    }
                    row.unitRect?.let { rect ->
                        val unit = scope.button(row.unitValue ?: "px", {
                            key = "dsgl-system-inspector-editor-unit-$index"
                        })
                        unit.backgroundColor = if (row.unitOpen) 0x334D5D70 else 0x22313D4B
                        unit.border = Border.all(1, if (row.unitOpen) 0xFFA8C6E6.toInt() else 0x77607084)
                        unit.textColor = 0xFFE6EDF6.toInt()
                        unit.fontSize = 18
                        unit.onClick {
                            controller.onToggleUnitSelectPressed(row.property)
                        }
                        renderNode(ctx, unit, rect)
                    }
                }
            }
        }

        val resetRect = controller.debugStyleEditorResetRect()
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
            renderNode(ctx, resetButton, resetRect)
        }

        val clearRect = controller.debugStyleEditorClearRect()
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
            renderNode(ctx, clearButton, clearRect)
        }
    }

    private fun renderDropdowns(scope: UiScope, ctx: UiMeasureContext) {
        controller.debugStyleEditorDropdowns().forEachIndexed { index, dropdown ->
            val popup = scope.div({
                key = "dsgl-system-inspector-dropdown-$index"
                style = {
                    display = Display.Block
                }
            })
            popup.backgroundColor = 0xEE202A36.toInt()
            popup.border = Border.all(1, 0xCC596A80.toInt())
            renderNode(ctx, popup, dropdown.popupRect)

            dropdown.options.forEachIndexed { optionIndex, option ->
                val button = scope.button(option.text, {
                    key = "dsgl-system-inspector-dropdown-$index-option-$optionIndex"
                })
                button.backgroundColor = if (option.hovered) 0x2D4C6279 else 0x22313D4B
                button.border = Border.all(1, if (option.hovered) 0xCC95B3D3.toInt() else 0x664F6076)
                button.textColor = if (option.hovered) 0xFFFFFFFF.toInt() else 0xFFE6EDF6.toInt()
                button.fontSize = 18
                button.onClick {
                    if (dropdown.unitSelect) {
                        controller.onSelectUnitOptionPressed(dropdown.property, option.value)
                    } else {
                        controller.onSelectValueOptionPressed(dropdown.property, option.value)
                    }
                }
                renderNode(ctx, button, option.rect)
            }

            dropdown.footerText?.let { footer ->
                val footerNode = scope.text(props = {
                    key = "dsgl-system-inspector-dropdown-$index-footer"
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
                        dropdown.popupRect.y + dropdown.popupRect.height - 22,
                        (dropdown.popupRect.width - 12).coerceAtLeast(20),
                        20
                    )
                )
            }
        }
    }

    private fun renderTooltip(
        scope: UiScope,
        ctx: UiMeasureContext,
        keyPrefix: String,
        tooltip: org.dreamfinity.dsgl.core.inspector.InspectorTooltipSnapshot?,
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

    private fun isDomOwnedControlTarget(target: DOMNode?): Boolean {
        var current = target
        while (current != null && current !== this) {
            when (current.styleType) {
                "input", "textarea", "select", "toggle", "button" -> return true
            }
            current = current.parent
        }
        return false
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
