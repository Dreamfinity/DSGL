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
import org.dreamfinity.dsgl.core.dsl.div
import org.dreamfinity.dsgl.core.dsl.text
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.TextWrap
import kotlin.math.roundToInt

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

    private val previousSwatchNode: ColorSwatchSurfaceNode = ColorSwatchSurfaceNode(
        key = "dsgl-system-color-picker-swatch-previous"
    ).applyParent(this)
    private val currentSwatchNode: ColorSwatchSurfaceNode = ColorSwatchSurfaceNode(
        key = "dsgl-system-color-picker-swatch-current"
    ).applyParent(this)

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
        ColorSwatchSurfaceNode(
            allowEmpty = true,
            key = "dsgl-system-color-picker-recent-$index"
        ).applyParent(this)
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

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        val controller = popupEngine.debugActiveController()
        val panelRect = popupEngine.debugActivePanelRect()
        if (controller == null || panelRect == null || !controller.viewModeDropdownOpen()) {
            hideAll(ctx)
            return
        }
        val style = popupEngine.debugActiveStyle() ?: controller.style()
        if (appliedStyle != style) {
            applyStaticStyle(style)
            appliedStyle = style
        }

        val layout = popupEngine.debugActiveLayout() ?: run {
            hideAll(ctx)
            return
        }
        val popupRect = layout.modeOptionsRect ?: run {
            hideAll(ctx)
            return
        }
        val hover = controller.viewHoverPosition()
        val hoverX = hover.first
        val hoverY = hover.second

        popupBackgroundNode.display = Display.Block
        syncContainerVisual(
            node = popupBackgroundNode,
            backgroundColor = style.inputBackgroundColor,
            border = Border.all(1, style.inputBorderColor)
        )
        popupBackgroundNode.render(ctx, popupRect.x, popupRect.y, popupRect.width, popupRect.height)

        val state = controller.snapshot()
        val optionsByMode = layout.modeOptions.associateBy { it.mode }
        ColorFormatMode.entries.forEach { mode ->
            val button = modeOptionButtons[mode] ?: return@forEach
            val optionRect = optionsByMode[mode]?.rect
            if (optionRect == null) {
                button.display = Display.None
                button.render(ctx, 0, 0, 0, 0)
                return@forEach
            }
            val hovered = optionRect.contains(hoverX, hoverY)
            val selected = state.mode == mode
            syncPickerButtonVisual(
                button = button,
                text = null,
                style = style,
                hovered = hovered,
                selected = selected
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
    private val swatchNode: ColorSwatchSurfaceNode = ColorSwatchSurfaceNode(
        allowEmpty = false,
        key = "dsgl-system-color-picker-eyedropper-swatch"
    ).applyParent(this)
    private val modeTextNode: TextNode = createOverlayTextNode(
        key = "dsgl-system-color-picker-eyedropper-mode",
        text = ""
    )
    private val valueTextNode: TextNode = createOverlayTextNode(
        key = "dsgl-system-color-picker-eyedropper-value",
        text = ""
    )

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        val controller = popupEngine.debugActiveController() ?: run {
            hideAll(ctx)
            return
        }
        val model = controller.resolveEyedropperOverlayModel(
            viewportWidth = bounds.width.coerceAtLeast(1),
            viewportHeight = bounds.height.coerceAtLeast(1)
        ) ?: run {
            hideAll(ctx)
            return
        }
        val style = popupEngine.debugActiveStyle() ?: controller.style()
        val color = controller.snapshot().color

        syncOverlayText(model.modeText, model.valueText)
        syncContainerVisual(
            node = shadowNode,
            backgroundColor = style.panelShadowColor,
            border = Border.NONE
        )
        syncContainerVisual(
            node = panelNode,
            backgroundColor = style.eyedropperOverlayBackgroundColor,
            border = Border.all(1, style.eyedropperOverlayBorderColor)
        )
        syncContainerVisual(
            node = centerNode,
            backgroundColor = null,
            border = Border.all(1, style.eyedropperCenterBorderColor)
        )

        syncOverlayTextVisual(modeTextNode, style.mutedTextColor, style.fontSize)
        syncOverlayTextVisual(valueTextNode, style.textColor, style.fontSize)

        captureNode.bind(
            sourceRect = model.captureSourceRect,
            fallbackColor = color.toArgbInt()
        )
        swatchNode.bind(style = style, color = color, highlighted = false)
        magnifierDrawNode.bind(
            columns = model.captureSourceRect.width,
            rows = model.captureSourceRect.height,
            cellSize = (model.magnifierRect.width / model.captureSourceRect.width.coerceAtLeast(1)).coerceAtLeast(1),
            gridEnabled = style.eyedropperGridOverlayEnabled,
            gridColor = style.eyedropperGridOverlayColor
        )

        val shadowRect = Rect(
            x = model.panelRect.x + 2,
            y = model.panelRect.y + 2,
            width = model.panelRect.width,
            height = model.panelRect.height
        )
        val textX = model.swatchRect.x + model.swatchRect.width + 8
        val textWidth = (model.panelRect.x + model.panelRect.width - 6 - textX).coerceAtLeast(1)
        val modeRect = Rect(
            x = textX,
            y = model.swatchRect.y + 1,
            width = textWidth,
            height = (style.fontSize + 2).coerceAtLeast(1)
        )
        val valueRect = Rect(
            x = textX,
            y = modeRect.y + style.fontSize,
            width = textWidth,
            height = (style.fontSize + 2).coerceAtLeast(1)
        )

        renderNode(ctx, captureNode, model.panelRect)
        renderNode(ctx, shadowNode, shadowRect)
        renderNode(ctx, panelNode, model.panelRect)
        renderNode(ctx, magnifierDrawNode, model.magnifierRect)
        renderNode(ctx, centerNode, model.centerRect)
        renderNode(ctx, swatchNode, model.swatchRect)
        renderNode(ctx, modeTextNode, modeRect)
        renderNode(ctx, valueTextNode, valueRect)
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

private class EyedropperCaptureNode(
    key: Any?
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker-eyedropper-capture"

    private var sourceRect: Rect? = null
    private var fallbackColor: Int = 0

    fun bind(sourceRect: Rect, fallbackColor: Int) {
        if (this.sourceRect != sourceRect || this.fallbackColor != fallbackColor) {
            markRenderCommandsDirty()
        }
        this.sourceRect = sourceRect
        this.fallbackColor = fallbackColor
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        if (display == Display.None) return
        val source = sourceRect ?: return
        if (source.width <= 0 || source.height <= 0) return
        out += RenderCommand.CaptureScreenRegion(
            sourceX = source.x,
            sourceY = source.y,
            sourceWidth = source.width,
            sourceHeight = source.height,
            fallbackColor = fallbackColor
        )
    }
}

private class EyedropperMagnifierDrawNode(
    key: Any?
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker-eyedropper-magnifier"

    private var columns: Int = 1
    private var rows: Int = 1
    private var cellSize: Int = 1
    private var gridEnabled: Boolean = true
    private var gridColor: Int = 0x66FFFFFF

    fun bind(columns: Int, rows: Int, cellSize: Int, gridEnabled: Boolean, gridColor: Int) {
        val nextColumns = columns.coerceAtLeast(1)
        val nextRows = rows.coerceAtLeast(1)
        val nextCellSize = cellSize.coerceAtLeast(1)
        if (this.columns != nextColumns ||
            this.rows != nextRows ||
            this.cellSize != nextCellSize ||
            this.gridEnabled != gridEnabled ||
            this.gridColor != gridColor
        ) {
            markRenderCommandsDirty()
        }
        this.columns = nextColumns
        this.rows = nextRows
        this.cellSize = nextCellSize
        this.gridEnabled = gridEnabled
        this.gridColor = gridColor
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        if (display == Display.None) return
        if (bounds.width <= 0 || bounds.height <= 0) return
        out += RenderCommand.DrawCapturedScreenRegion(
            x = bounds.x,
            y = bounds.y,
            width = bounds.width,
            height = bounds.height
        )
        if (!gridEnabled) return
        for (column in 1 until columns) {
            val lineX = bounds.x + column * cellSize
            if (lineX <= bounds.x || lineX >= bounds.x + bounds.width) continue
            out += RenderCommand.DrawRect(lineX, bounds.y, 1, bounds.height, gridColor)
        }
        for (row in 1 until rows) {
            val lineY = bounds.y + row * cellSize
            if (lineY <= bounds.y || lineY >= bounds.y + bounds.height) continue
            out += RenderCommand.DrawRect(bounds.x, lineY, bounds.width, 1, gridColor)
        }
    }
}

private class ColorFieldSurfaceNode(
    key: Any?
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker-color-field"

    private var style: ColorPickerStyle = ColorPickerStyle()
    private var hueDeg: Float = 0f
    private var saturation: Float = 0f
    private var brightness: Float = 1f

    fun bind(style: ColorPickerStyle, color: RgbaColor, hueDeg: Float) {
        val hsv = ColorConversions.rgbToHsv(color, hueDeg)
        val nextSaturation = hsv.saturation
        val nextBrightness = hsv.brightness
        if (this.style != style ||
            this.hueDeg != hueDeg ||
            saturation != nextSaturation ||
            brightness != nextBrightness
        ) {
            markRenderCommandsDirty()
        }
        this.style = style
        this.hueDeg = hueDeg
        saturation = nextSaturation
        brightness = nextBrightness
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        if (bounds.width <= 0 || bounds.height <= 0) return
        out += RenderCommand.DrawColorField(
            x = bounds.x,
            y = bounds.y,
            width = bounds.width,
            height = bounds.height,
            hueDeg = hueDeg
        )
        drawBorder(out, bounds, style.inputBorderColor)
        val thumbX = bounds.x + (saturation * bounds.width.toFloat()).roundToInt().coerceIn(0, bounds.width - 1)
        val thumbY =
            bounds.y + ((1f - brightness) * bounds.height.toFloat()).roundToInt().coerceIn(0, bounds.height - 1)
        out += RenderCommand.DrawRect(thumbX - 3, thumbY - 3, 7, 7, style.thumbShadowColor)
        drawBorder(out, Rect(thumbX - 2, thumbY - 2, 5, 5), style.thumbOutlineColor)
    }
}

private class HueSurfaceNode(
    key: Any?
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker-hue-slider"

    private var style: ColorPickerStyle = ColorPickerStyle()
    private var hueDeg: Float = 0f

    fun bind(style: ColorPickerStyle, hueDeg: Float) {
        if (this.style != style || this.hueDeg != hueDeg) {
            markRenderCommandsDirty()
        }
        this.style = style
        this.hueDeg = hueDeg
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        if (bounds.width <= 0 || bounds.height <= 0) return
        out += RenderCommand.DrawHueBar(
            x = bounds.x,
            y = bounds.y,
            width = bounds.width,
            height = bounds.height
        )
        drawBorder(out, bounds, style.inputBorderColor)
        val thumbX = bounds.x + ((hueDeg / 360f) * bounds.width.toFloat()).roundToInt().coerceIn(0, bounds.width - 1)
        out += RenderCommand.DrawRect(thumbX - 1, bounds.y - 1, 3, bounds.height + 2, style.thumbOutlineColor)
    }
}

private class AlphaSurfaceNode(
    key: Any?
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker-alpha-slider"

    private var style: ColorPickerStyle = ColorPickerStyle()
    private var color: RgbaColor = RgbaColor.WHITE

    fun bind(style: ColorPickerStyle, color: RgbaColor) {
        if (this.style != style || this.color != color) {
            markRenderCommandsDirty()
        }
        this.style = style
        this.color = color
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        if (bounds.width <= 0 || bounds.height <= 0) return
        drawChecker(out, bounds, style)
        out += RenderCommand.DrawAlphaBar(
            x = bounds.x,
            y = bounds.y,
            width = bounds.width,
            height = bounds.height,
            rgbColor = color.copy(a = 1f).toArgbInt()
        )
        drawBorder(out, bounds, style.inputBorderColor)
        val thumbX = bounds.x + (color.a * bounds.width.toFloat()).roundToInt().coerceIn(0, bounds.width - 1)
        out += RenderCommand.DrawRect(thumbX - 1, bounds.y - 1, 3, bounds.height + 2, style.thumbOutlineColor)
    }
}

private class ColorSwatchSurfaceNode(
    private val allowEmpty: Boolean = false,
    key: Any?
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker-swatch"

    private var style: ColorPickerStyle = ColorPickerStyle()
    private var color: RgbaColor? = RgbaColor.WHITE
    private var highlighted: Boolean = false

    fun bind(style: ColorPickerStyle, color: RgbaColor?, highlighted: Boolean) {
        if (this.style != style || this.color != color || this.highlighted != highlighted) {
            markRenderCommandsDirty()
        }
        this.style = style
        this.color = color
        this.highlighted = highlighted
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        if (bounds.width <= 0 || bounds.height <= 0) return
        val localColor = color
        if (localColor == null && allowEmpty) {
            out += RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, 0x33222A34)
            drawBorder(out, bounds, if (highlighted) style.inputActiveBorderColor else style.recentGridBorderColor)
            return
        }
        drawChecker(out, bounds, style)
        out += RenderCommand.DrawRect(
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height,
            (localColor ?: RgbaColor.WHITE).toArgbInt()
        )
        drawBorder(out, bounds, if (highlighted) style.inputActiveBorderColor else style.inputBorderColor)
    }
}

private fun drawChecker(out: MutableList<RenderCommand>, rect: Rect, style: ColorPickerStyle) {
    if (rect.width <= 0 || rect.height <= 0) return
    out += RenderCommand.DrawCheckerboard(
        x = rect.x,
        y = rect.y,
        width = rect.width,
        height = rect.height,
        cellSize = 4,
        lightColor = style.checkerLightColor,
        darkColor = style.checkerDarkColor
    )
}

private fun drawBorder(out: MutableList<RenderCommand>, rect: Rect, color: Int) {
    if (rect.width <= 0 || rect.height <= 0) return
    out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, 1, color)
    out += RenderCommand.DrawRect(rect.x, rect.y + rect.height - 1, rect.width, 1, color)
    out += RenderCommand.DrawRect(rect.x, rect.y, 1, rect.height, color)
    out += RenderCommand.DrawRect(rect.x + rect.width - 1, rect.y, 1, rect.height, color)
}

