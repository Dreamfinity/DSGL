package org.dreamfinity.dsgl.core.colorpicker.internal

import org.dreamfinity.dsgl.core.colorpicker.*
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.*
import org.dreamfinity.dsgl.core.dom.layout.Border
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.dsl.UiScope
import org.dreamfinity.dsgl.core.dsl.button
import org.dreamfinity.dsgl.core.dsl.colorSwatch
import org.dreamfinity.dsgl.core.dsl.div
import org.dreamfinity.dsgl.core.dsl.text
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.TextWrap

internal class SystemColorPickerPopupBodyNode(
    private val popupEngine: ColorPickerPopupEngine,
    key: Any? = "dsgl-system-color-picker-native-body"
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker-native-body"

    private val scope = UiScope(this)
    private val inputLabelValues: MutableList<String> = MutableList(MAX_INPUT_SLOTS) { "" }

    private val modeSelectButton: ButtonNode = scope.button("", {
        this.key = "dsgl-system-color-picker-mode-select"
    })
    private val rgbaOrderButton: ButtonNode = scope.button("RGBA", {
        this.key = "dsgl-system-color-picker-order-rgba"
    })
    private val argbOrderButton: ButtonNode = scope.button("ARGB", {
        this.key = "dsgl-system-color-picker-order-argb"
    })
    private val colorFieldNode: ColorFieldSurfaceNode = ColorFieldSurfaceNode(
        key = "dsgl-system-color-picker-surface-field"
    ).applyParent(this)
    private val hueSliderNode: HueSurfaceNode = HueSurfaceNode(
        key = "dsgl-system-color-picker-surface-hue"
    ).applyParent(this)
    private val alphaSliderNode: AlphaSurfaceNode = AlphaSurfaceNode(
        key = "dsgl-system-color-picker-surface-alpha"
    ).applyParent(this)

    private val previousSwatchNode: ColorSwatchSurfaceNode = scope.colorSwatch({
        this.key = "dsgl-system-color-picker-swatch-previous"
    })
    private val currentSwatchNode: ColorSwatchSurfaceNode = scope.colorSwatch({
        this.key = "dsgl-system-color-picker-swatch-current"
    })

    private val copyButton: ButtonNode = scope.button("Copy", {
        this.key = "dsgl-system-color-picker-button-copy"
    })
    private val pasteButton: ButtonNode = scope.button("Paste", {
        this.key = "dsgl-system-color-picker-button-paste"
    })
    private val pipetteButton: ButtonNode = scope.button("Pipette", {
        this.key = "dsgl-system-color-picker-button-pipette"
    })

    private val inputLabelNodes: List<TextNode> = (0 until MAX_INPUT_SLOTS).map { index ->
        scope.text(props = {
            this.key = "dsgl-system-color-picker-input-label-$index"
            source = TextSource.Dynamic { inputLabelValues[index] }
            style = {
                textWrap = TextWrap.NoWrap
            }
        })
    }
    private val inputValueNodes: List<TextInputNode> = (0 until MAX_INPUT_SLOTS).map { index ->
        TextInputNode(key = "dsgl-system-color-picker-input-value-$index").applyParent(this)
    }

    private val recentSwatchNodes: List<ColorSwatchSurfaceNode> = (0 until RECENT_SWATCH_COUNT).map { index ->
        scope.colorSwatch({
            allowEmpty = true
            this.key = "dsgl-system-color-picker-recent-$index"
        })
    }

    private var appliedStyle: ColorPickerStyle? = null

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        val controller = popupEngine.debugActiveController()
        if (controller == null || popupEngine.debugActivePanelRect() == null) {
            hideAll(ctx)
            return
        }
        val style = popupEngine.debugActiveStyle() ?: controller.style()
        if (appliedStyle != style) {
            applyStaticStyle(style)
            appliedStyle = style
        }

        val layout = popupEngine.debugActiveLayout()?.takeIf { it.bounds == bounds } ?: controller.buildLayout(bounds)
        val state = controller.snapshot()
        val hueDeg = controller.viewHueDeg()
        val hover = controller.viewHoverPosition()
        val hoverX = hover.first
        val hoverY = hover.second
        val nowMs = System.currentTimeMillis()

        val modeDropdownOpen = controller.viewModeDropdownOpen()
        val activeInputKey = controller.viewActiveInputKey()
        val activeInputBuffer = controller.viewActiveInputBuffer()
        val inputValues = controller.viewInputValues()
        val recentColors = controller.viewRecentColors()
        val definitionsByKey = controller.viewInputDefinitions().associate { it.first to it.second }

        renderTopControls(
            ctx = ctx,
            controller = controller,
            layout = layout,
            style = style,
            state = state,
            hueDeg = hueDeg,
            hoverX = hoverX,
            hoverY = hoverY,
            modeDropdownOpen = modeDropdownOpen
        )
        renderInputRows(
            ctx = ctx,
            controller = controller,
            layout = layout,
            style = style,
            hoverX = hoverX,
            hoverY = hoverY,
            nowMs = nowMs,
            activeInputKey = activeInputKey,
            activeInputBuffer = activeInputBuffer,
            inputValues = inputValues,
            definitionsByKey = definitionsByKey
        )
        renderRecentSwatchGrid(
            ctx = ctx,
            layout = layout,
            style = style,
            hoverX = hoverX,
            hoverY = hoverY,
            recentColors = recentColors
        )
    }

    private fun renderTopControls(
        ctx: UiMeasureContext,
        controller: ColorPickerController,
        layout: ColorPickerLayout,
        style: ColorPickerStyle,
        state: ColorPickerState,
        hueDeg: Float,
        hoverX: Int,
        hoverY: Int,
        modeDropdownOpen: Boolean
    ) {
        syncPickerButtonVisual(
            button = modeSelectButton,
            text = if (modeDropdownOpen) "${state.mode.name} ^" else "${state.mode.name} v",
            style = style,
            hovered = layout.modeSelectRect.contains(hoverX, hoverY),
            selected = modeDropdownOpen
        )
        renderNode(ctx, modeSelectButton, layout.modeSelectRect)

        val showOrder = layout.rgbaOrderRect != null && layout.argbOrderRect != null
        if (showOrder) {
            val rgbaRect = layout.rgbaOrderRect
            val argbRect = layout.argbOrderRect
            syncPickerButtonVisual(
                button = rgbaOrderButton,
                text = null,
                style = style,
                hovered = rgbaRect.contains(hoverX, hoverY),
                selected = state.rgbOrder == RgbChannelOrder.RGBA
            )
            syncPickerButtonVisual(
                button = argbOrderButton,
                text = null,
                style = style,
                hovered = argbRect.contains(hoverX, hoverY),
                selected = state.rgbOrder == RgbChannelOrder.ARGB
            )
            renderNode(ctx, rgbaOrderButton, rgbaRect)
            renderNode(ctx, argbOrderButton, argbRect)
        } else {
            renderNode(ctx, rgbaOrderButton, null)
            renderNode(ctx, argbOrderButton, null)
        }

        colorFieldNode.bind(style = style, color = state.color, hueDeg = hueDeg)
        renderNode(ctx, colorFieldNode, layout.colorFieldRect)

        hueSliderNode.bind(style = style, hueDeg = hueDeg)
        renderNode(ctx, hueSliderNode, layout.hueRect)

        if (state.alphaEnabled && layout.alphaRect != null) {
            alphaSliderNode.bind(style = style, color = state.color)
            renderNode(ctx, alphaSliderNode, layout.alphaRect)
        } else {
            renderNode(ctx, alphaSliderNode, null)
        }

        previousSwatchNode.bind(
            style = style,
            color = state.previous,
            highlighted = layout.previousSwatchRect.contains(hoverX, hoverY)
        )
        currentSwatchNode.bind(
            style = style,
            color = state.color,
            highlighted = layout.currentSwatchRect.contains(hoverX, hoverY)
        )
        renderNode(ctx, previousSwatchNode, layout.previousSwatchRect)
        renderNode(ctx, currentSwatchNode, layout.currentSwatchRect)

        syncPickerButtonVisual(
            button = copyButton,
            text = null,
            style = style,
            hovered = layout.copyRect.contains(hoverX, hoverY),
            selected = false
        )
        syncPickerButtonVisual(
            button = pasteButton,
            text = null,
            style = style,
            hovered = layout.pasteRect.contains(hoverX, hoverY),
            selected = false
        )
        syncPickerButtonVisual(
            button = pipetteButton,
            text = if (controller.isEyedropperActive()) "Pick..." else "Pipette",
            style = style,
            hovered = layout.pipetteRect.contains(hoverX, hoverY),
            selected = controller.isEyedropperActive()
        )
        renderNode(ctx, copyButton, layout.copyRect)
        renderNode(ctx, pasteButton, layout.pasteRect)
        renderNode(ctx, pipetteButton, layout.pipetteRect)
    }

    private fun renderInputRows(
        ctx: UiMeasureContext,
        controller: ColorPickerController,
        layout: ColorPickerLayout,
        style: ColorPickerStyle,
        hoverX: Int,
        hoverY: Int,
        nowMs: Long,
        activeInputKey: String?,
        activeInputBuffer: String,
        inputValues: Map<String, String>,
        definitionsByKey: Map<String, String>
    ) {
        for (index in 0 until MAX_INPUT_SLOTS) {
            val inputSlot = layout.inputSlots.getOrNull(index)
            val labelNode = inputLabelNodes[index]
            val inputNode = inputValueNodes[index]
            if (inputSlot == null) {
                inputLabelValues[index] = ""
                syncTextNodeVisual(
                    node = labelNode,
                    text = "",
                    color = style.mutedTextColor
                )
                renderNode(ctx, labelNode, null)
                renderNode(ctx, inputNode, null)
                continue
            }

            val key = inputSlot.key
            val label = definitionsByKey[key] ?: inputSlot.label
            inputLabelValues[index] = label
            syncTextNodeVisual(
                node = labelNode,
                text = label,
                color = style.mutedTextColor
            )

            val borderColor = when {
                activeInputKey == key -> style.inputActiveBorderColor
                inputSlot.inputRect.contains(hoverX, hoverY) -> style.buttonHoverColor
                else -> style.inputBorderColor
            }
            val value = if (activeInputKey == key) {
                activeInputBuffer + if (controller.viewCaretVisible(nowMs)) "|" else ""
            } else {
                inputValues[key].orEmpty()
            }
            syncTextInputVisual(
                node = inputNode,
                value = value,
                border = Border.all(1, borderColor),
                background = style.inputBackgroundColor,
                focusedBackground = style.inputBackgroundColor,
                textColor = style.textColor,
                placeholderColor = style.mutedTextColor,
                fontSize = style.fontSize
            )

            renderNode(ctx, labelNode, inputSlot.labelRect)
            renderNode(ctx, inputNode, inputSlot.inputRect)
        }
    }

    private fun renderRecentSwatchGrid(
        ctx: UiMeasureContext,
        layout: ColorPickerLayout,
        style: ColorPickerStyle,
        hoverX: Int,
        hoverY: Int,
        recentColors: List<RgbaColor>
    ) {
        val hoveredRecent = layout.recentRects.indexOfFirst { it.contains(hoverX, hoverY) }
        for (index in 0 until RECENT_SWATCH_COUNT) {
            val swatchNode = recentSwatchNodes[index]
            val swatchRect = layout.recentRects.getOrNull(index)
            if (swatchRect == null) {
                renderNode(ctx, swatchNode, null)
                continue
            }
            swatchNode.bind(
                style = style,
                color = recentColors.getOrNull(index),
                highlighted = index == hoveredRecent
            )
            renderNode(ctx, swatchNode, swatchRect)
        }
    }

    private fun applyStaticStyle(style: ColorPickerStyle) {
        val buttons = buildList {
            add(modeSelectButton)
            add(rgbaOrderButton)
            add(argbOrderButton)
            add(copyButton)
            add(pasteButton)
            add(pipetteButton)
        }
        buttons.forEach { button ->
            button.textColor = style.textColor
            button.applyStyle {
                border { width = 1.px; color = style.inputBorderColor }
                fontSize = style.fontSize.px
                padding = 0.px
                textWrap = TextWrap.NoWrap
            }
        }
        inputLabelNodes.forEach { label ->
            label.applyStyle {
                fontSize = style.fontSize.px
                textWrap = TextWrap.NoWrap
            }
        }
        inputValueNodes.forEach { input ->
            input.applyStyle {
                fontSize = style.fontSize.px
                textWrap = TextWrap.NoWrap
            }
        }
    }

    private fun syncPickerButtonVisual(
        button: ButtonNode,
        text: String?,
        style: ColorPickerStyle,
        hovered: Boolean,
        selected: Boolean
    ) {
        val nextBackground = when {
            selected -> style.buttonActiveColor
            hovered -> style.buttonHoverColor
            else -> style.buttonBackgroundColor
        }
        val nextBorder = Border.all(1, if (selected) style.inputActiveBorderColor else style.inputBorderColor)
        var changed = false
        if (text != null && button.text != text) {
            button.text = text
            changed = true
        }
        if (button.backgroundColor != nextBackground) {
            button.backgroundColor = nextBackground
            changed = true
        }
        if (button.border != nextBorder) {
            button.border = nextBorder
            changed = true
        }
        if (button.textColor != style.textColor) {
            button.textColor = style.textColor
            changed = true
        }
        if (button.fontSize != style.fontSize) {
            button.fontSize = style.fontSize
            changed = true
        }
        if (changed) {
            button.requestRenderCommandsInvalidation()
        }
    }

    private fun syncTextNodeVisual(node: TextNode, text: String, color: Int) {
        var changed = false
        if (node.text != text) {
            node.setText(text)
            changed = false
        }
        if (node.color != color) {
            node.color = color
            changed = true
        }
        if (changed) {
            node.requestRenderCommandsInvalidation()
        }
    }

    private fun syncTextInputVisual(
        node: TextInputNode,
        value: String,
        border: Border,
        background: Int,
        focusedBackground: Int,
        textColor: Int,
        placeholderColor: Int,
        fontSize: Int
    ) {
        var changed = false
        if (node.text != value) {
            node.text = value
            changed = true
        }
        if (node.border != border) {
            node.border = border
            changed = true
        }
        if (node.backgroundColor != background) {
            node.backgroundColor = background
            changed = true
        }
        if (node.focusedBackgroundColor != focusedBackground) {
            node.focusedBackgroundColor = focusedBackground
            changed = true
        }
        if (node.textColor != textColor) {
            node.textColor = textColor
            changed = true
        }
        if (node.placeholderColor != placeholderColor) {
            node.placeholderColor = placeholderColor
            changed = true
        }
        if (node.fontSize != fontSize) {
            node.fontSize = fontSize
            changed = true
        }
        if (changed) {
            node.requestRenderCommandsInvalidation()
        }
    }


    private fun renderNode(ctx: UiMeasureContext, node: DOMNode, rect: Rect?) {
        if (rect == null || rect.width <= 0 || rect.height <= 0) {
            node.display = Display.None
            node.render(ctx, 0, 0, 0, 0)
            return
        }
        node.display = Display.Block
        node.render(ctx, rect.x, rect.y, rect.width, rect.height)
    }

    private fun hideAll(ctx: UiMeasureContext) {
        children.forEach { child ->
            child.display = Display.None
            child.render(ctx, 0, 0, 0, 0)
        }
    }

    private companion object {
        const val MAX_INPUT_SLOTS: Int = 4
        const val RECENT_SWATCH_COUNT: Int = 64
    }
}

internal class SystemColorPickerTransientOverlayNode(
    private val popupEngine: ColorPickerPopupEngine,
    key: Any? = "dsgl-system-color-picker-native-transient"
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker-native-transient"

    private val modeDropdownOverlayNode: SystemColorPickerModeDropdownOverlayNode =
        SystemColorPickerModeDropdownOverlayNode(popupEngine).applyParent(this)
    private val eyedropperOverlayNode: SystemColorPickerEyedropperOverlayNode =
        SystemColorPickerEyedropperOverlayNode(popupEngine).applyParent(this)

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        modeDropdownOverlayNode.render(ctx, x, y, width, height)
        eyedropperOverlayNode.render(ctx, x, y, width, height)
    }
}

internal class SystemColorPickerModeDropdownOverlayNode(
    private val popupEngine: ColorPickerPopupEngine,
    key: Any? = "dsgl-system-color-picker-native-mode-dropdown-overlay"
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker-native-mode-dropdown-overlay"

    private val scope = UiScope(this)
    private val popupBackgroundNode: ContainerNode = ContainerNode(
        key = "dsgl-system-color-picker-mode-dropdown-background"
    ).applyParent(this)
    private val modeOptionButtons: Map<ColorFormatMode, ButtonNode> = ColorFormatMode.entries.associateWith { mode ->
        scope.button(mode.name, {
            this.key = "dsgl-system-color-picker-mode-option-${mode.name.lowercase()}"
        })
    }
    private var appliedStyle: ColorPickerStyle? = null

    private data class ModeDropdownRenderState(
        val style: ColorPickerStyle,
        val layout: ColorPickerLayout,
        val popupRect: Rect,
        val hoverX: Int,
        val hoverY: Int,
        val selectedMode: ColorFormatMode
    )

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        val renderState = resolveRenderState() ?: run {
            hideAll(ctx)
            return
        }
        renderPopupBackground(ctx, renderState)
        renderModeOptions(ctx, renderState)
    }

    private fun resolveRenderState(): ModeDropdownRenderState? {
        val controller = popupEngine.debugActiveController() ?: return null
        val panelRect = popupEngine.debugActivePanelRect()
        if (panelRect == null || !controller.viewModeDropdownOpen()) return null
        val style = popupEngine.debugActiveStyle() ?: controller.style()
        if (appliedStyle != style) {
            applyStaticStyle(style)
            appliedStyle = style
        }
        val layout = popupEngine.debugActiveLayout() ?: return null
        val popupRect = layout.modeOptionsRect ?: return null
        val hover = controller.viewHoverPosition()
        return ModeDropdownRenderState(
            style = style,
            layout = layout,
            popupRect = popupRect,
            hoverX = hover.first,
            hoverY = hover.second,
            selectedMode = controller.snapshot().mode
        )
    }

    private fun renderPopupBackground(ctx: UiMeasureContext, state: ModeDropdownRenderState) {
        popupBackgroundNode.display = Display.Block
        syncContainerVisual(
            node = popupBackgroundNode,
            backgroundColor = state.style.inputBackgroundColor,
            border = Border.all(1, state.style.inputBorderColor)
        )
        popupBackgroundNode.render(
            ctx,
            state.popupRect.x,
            state.popupRect.y,
            state.popupRect.width,
            state.popupRect.height
        )
    }

    private fun renderModeOptions(ctx: UiMeasureContext, state: ModeDropdownRenderState) {
        val optionsByMode = state.layout.modeOptions.associateBy { it.mode }
        ColorFormatMode.entries.forEach { mode ->
            val button = modeOptionButtons[mode] ?: return@forEach
            val optionRect = optionsByMode[mode]?.rect
            if (optionRect == null) {
                button.display = Display.None
                button.render(ctx, 0, 0, 0, 0)
                return@forEach
            }
            syncPickerButtonVisual(
                button = button,
                text = null,
                style = state.style,
                hovered = optionRect.contains(state.hoverX, state.hoverY),
                selected = state.selectedMode == mode
            )
            button.display = Display.Block
            button.render(ctx, optionRect.x, optionRect.y, optionRect.width, optionRect.height)
        }
    }

    private fun applyStaticStyle(style: ColorPickerStyle) {
        modeOptionButtons.values.forEach { button ->
            button.applyStyle {
                border { width = 1.px; color = style.inputBorderColor }
                fontSize = style.fontSize.px
                padding = 0.px
                textWrap = TextWrap.NoWrap
            }
        }
    }

    private fun syncPickerButtonVisual(
        button: ButtonNode,
        text: String?,
        style: ColorPickerStyle,
        hovered: Boolean,
        selected: Boolean
    ) {
        val nextBackground = when {
            selected -> style.buttonActiveColor
            hovered -> style.buttonHoverColor
            else -> style.buttonBackgroundColor
        }
        val nextBorder = Border.all(1, if (selected) style.inputActiveBorderColor else style.inputBorderColor)
        var changed = false
        if (text != null && button.text != text) {
            button.text = text
            changed = true
        }
        if (button.backgroundColor != nextBackground) {
            button.backgroundColor = nextBackground
            changed = true
        }
        if (button.border != nextBorder) {
            button.border = nextBorder
            changed = true
        }
        if (button.textColor != style.textColor) {
            button.textColor = style.textColor
            changed = true
        }
        if (button.fontSize != style.fontSize) {
            button.fontSize = style.fontSize
            changed = true
        }
        if (changed) {
            button.requestRenderCommandsInvalidation()
        }
    }

    private fun syncContainerVisual(node: ContainerNode, backgroundColor: Int?, border: Border) {
        var changed = false
        if (node.backgroundColor != backgroundColor) {
            node.backgroundColor = backgroundColor
            changed = true
        }
        if (node.border != border) {
            node.border = border
            changed = true
        }
        if (changed) {
            node.requestRenderCommandsInvalidation()
        }
    }

    private fun hideAll(ctx: UiMeasureContext) {
        popupBackgroundNode.display = Display.None
        popupBackgroundNode.render(ctx, 0, 0, 0, 0)
        modeOptionButtons.values.forEach { button ->
            button.display = Display.None
            button.render(ctx, 0, 0, 0, 0)
        }
    }
}

internal class SystemColorPickerEyedropperOverlayNode(
    private val popupEngine: ColorPickerPopupEngine,
    key: Any? = "dsgl-system-color-picker-native-eyedropper-overlay"
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker-native-eyedropper-overlay"

    private val scope = UiScope(this)
    private val captureNode: EyedropperCaptureNode = EyedropperCaptureNode(
        key = "dsgl-system-color-picker-eyedropper-capture"
    ).applyParent(this)
    private val shadowNode: ContainerNode = scope.div({
        this.key = "dsgl-system-color-picker-eyedropper-shadow"
    })
    private val panelNode: ContainerNode = scope.div({
        this.key = "dsgl-system-color-picker-eyedropper-panel"
    })
    private val magnifierDrawNode: EyedropperMagnifierDrawNode = EyedropperMagnifierDrawNode(
        key = "dsgl-system-color-picker-eyedropper-magnifier"
    ).applyParent(this)
    private val centerNode: ContainerNode = scope.div({
        this.key = "dsgl-system-color-picker-eyedropper-center"
    })
    private val swatchNode: ColorSwatchSurfaceNode = scope.colorSwatch({
        allowEmpty = false
        this.key = "dsgl-system-color-picker-eyedropper-swatch"
    })
    private val modeTextNode: TextNode = createOverlayTextNode(
        key = "dsgl-system-color-picker-eyedropper-mode",
        text = ""
    )
    private val valueTextNode: TextNode = createOverlayTextNode(
        key = "dsgl-system-color-picker-eyedropper-value",
        text = ""
    )

    private data class EyedropperRenderState(
        val model: ColorPickerEyedropperOverlayModel,
        val style: ColorPickerStyle,
        val color: RgbaColor
    )

    private data class EyedropperTextRects(
        val modeRect: Rect,
        val valueRect: Rect
    )

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        val renderState = resolveRenderState() ?: run {
            hideAll(ctx)
            return
        }

        syncVisuals(renderState)
        bindVisualNodes(renderState)
        renderOverlayNodes(ctx, renderState)
    }

    private fun resolveRenderState(): EyedropperRenderState? {
        val controller = popupEngine.debugActiveController() ?: return null
        val model = controller.resolveEyedropperOverlayModel(
            viewportWidth = bounds.width.coerceAtLeast(1),
            viewportHeight = bounds.height.coerceAtLeast(1)
        ) ?: return null
        val style = popupEngine.debugActiveStyle() ?: controller.style()
        return EyedropperRenderState(
            model = model,
            style = style,
            color = controller.snapshot().color
        )
    }

    private fun syncVisuals(state: EyedropperRenderState) {
        syncOverlayText(state.model.modeText, state.model.valueText)
        syncContainerVisual(
            node = shadowNode,
            backgroundColor = state.style.panelShadowColor,
            border = Border.NONE
        )
        syncContainerVisual(
            node = panelNode,
            backgroundColor = state.style.eyedropperOverlayBackgroundColor,
            border = Border.all(1, state.style.eyedropperOverlayBorderColor)
        )
        syncContainerVisual(
            node = centerNode,
            backgroundColor = null,
            border = Border.all(1, state.style.eyedropperCenterBorderColor)
        )
        syncOverlayTextVisual(modeTextNode, state.style.mutedTextColor, state.style.fontSize)
        syncOverlayTextVisual(valueTextNode, state.style.textColor, state.style.fontSize)
    }

    private fun bindVisualNodes(state: EyedropperRenderState) {
        captureNode.bind(
            sourceRect = state.model.captureSourceRect,
            fallbackColor = state.color.toArgbInt()
        )
        swatchNode.bind(style = state.style, color = state.color, highlighted = false)
        magnifierDrawNode.bind(
            columns = state.model.captureSourceRect.width,
            rows = state.model.captureSourceRect.height,
            cellSize = (
                state.model.magnifierRect.width /
                        state.model.captureSourceRect.width.coerceAtLeast(1)
                ).coerceAtLeast(1),
            gridEnabled = state.style.eyedropperGridOverlayEnabled,
            gridColor = state.style.eyedropperGridOverlayColor
        )
    }

    private fun renderOverlayNodes(ctx: UiMeasureContext, state: EyedropperRenderState) {
        val shadowRect = Rect(
            x = state.model.panelRect.x + 2,
            y = state.model.panelRect.y + 2,
            width = state.model.panelRect.width,
            height = state.model.panelRect.height
        )
        val textRects = resolveTextRects(state)

        renderNode(ctx, captureNode, state.model.panelRect)
        renderNode(ctx, shadowNode, shadowRect)
        renderNode(ctx, panelNode, state.model.panelRect)
        renderNode(ctx, magnifierDrawNode, state.model.magnifierRect)
        renderNode(ctx, centerNode, state.model.centerRect)
        renderNode(ctx, swatchNode, state.model.swatchRect)
        renderNode(ctx, modeTextNode, textRects.modeRect)
        renderNode(ctx, valueTextNode, textRects.valueRect)
    }

    private fun resolveTextRects(state: EyedropperRenderState): EyedropperTextRects {
        val textX = state.model.swatchRect.x + state.model.swatchRect.width + 8
        val textWidth = (
            state.model.panelRect.x + state.model.panelRect.width - 6 - textX
            ).coerceAtLeast(1)
        val modeRect = Rect(
            x = textX,
            y = state.model.swatchRect.y + 1,
            width = textWidth,
            height = (state.style.fontSize + 2).coerceAtLeast(1)
        )
        val valueRect = Rect(
            x = textX,
            y = modeRect.y + state.style.fontSize,
            width = textWidth,
            height = (state.style.fontSize + 2).coerceAtLeast(1)
        )
        return EyedropperTextRects(
            modeRect = modeRect,
            valueRect = valueRect
        )
    }

    private fun syncOverlayText(modeText: String, valueText: String) {
        if (modeTextNode.text != modeText) {
            modeTextNode.setText(modeText)
        }
        if (valueTextNode.text != valueText) {
            valueTextNode.setText(valueText)
        }
    }

    private fun syncOverlayTextVisual(node: TextNode, color: Int, fontSize: Int) {
        var changed = false
        if (node.color != color) {
            node.color = color
            changed = true
        }
        if (node.fontSize != fontSize) {
            node.fontSize = fontSize
            changed = true
        }
        if (changed) {
            node.requestRenderCommandsInvalidation()
        }
    }

    private fun syncContainerVisual(node: ContainerNode, backgroundColor: Int?, border: Border) {
        var changed = false
        if (node.backgroundColor != backgroundColor) {
            node.backgroundColor = backgroundColor
            changed = true
        }
        if (node.border != border) {
            node.border = border
            changed = true
        }
        if (changed) {
            node.requestRenderCommandsInvalidation()
        }
    }

    private fun createOverlayTextNode(key: Any, text: String): TextNode {
        return TextNode(TextSource.Static(text), key = key).apply {
            textWrap = TextWrap.NoWrap
        }.applyParent(this)
    }

    private fun renderNode(ctx: UiMeasureContext, node: DOMNode, rect: Rect?) {
        if (rect == null || rect.width <= 0 || rect.height <= 0) {
            node.display = Display.None
            node.render(ctx, 0, 0, 0, 0)
            return
        }
        node.display = Display.Block
        node.render(ctx, rect.x, rect.y, rect.width, rect.height)
    }

    private fun hideAll(ctx: UiMeasureContext) {
        children.forEach { child ->
            child.display = Display.None
            child.render(ctx, 0, 0, 0, 0)
        }
    }
}

