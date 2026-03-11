package org.dreamfinity.dsgl.core.colorpicker.internal

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.colorpicker.ColorConversions
import org.dreamfinity.dsgl.core.colorpicker.ColorFormatMode
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupEngine
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerStyle
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.colorpicker.RgbChannelOrder
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.TextInputNode
import org.dreamfinity.dsgl.core.dom.elements.TextNode
import org.dreamfinity.dsgl.core.dom.elements.TextSource
import org.dreamfinity.dsgl.core.dom.layout.Border
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
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
    private val modeOptionButtons: Map<ColorFormatMode, ButtonNode> = ColorFormatMode.entries.associateWith { mode ->
        scope.button(mode.name, {
            this.key = "dsgl-system-color-picker-mode-option-${mode.name.lowercase()}"
        })
    }

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

        modeSelectButton.text = if (modeDropdownOpen) "${state.mode.name} ^" else "${state.mode.name} v"
        applyButtonVisual(
            button = modeSelectButton,
            style = style,
            hovered = layout.modeSelectRect.contains(hoverX, hoverY),
            selected = modeDropdownOpen
        )
        renderNode(ctx, modeSelectButton, layout.modeSelectRect)

        val showOrder = layout.rgbaOrderRect != null && layout.argbOrderRect != null
        if (showOrder) {
            val rgbaRect = layout.rgbaOrderRect
            val argbRect = layout.argbOrderRect
            applyButtonVisual(
                button = rgbaOrderButton,
                style = style,
                hovered = rgbaRect.contains(hoverX, hoverY),
                selected = state.rgbOrder == RgbChannelOrder.RGBA
            )
            applyButtonVisual(
                button = argbOrderButton,
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

        previousSwatchNode.bind(style = style, color = state.previous, highlighted = layout.previousSwatchRect.contains(hoverX, hoverY))
        currentSwatchNode.bind(style = style, color = state.color, highlighted = layout.currentSwatchRect.contains(hoverX, hoverY))
        renderNode(ctx, previousSwatchNode, layout.previousSwatchRect)
        renderNode(ctx, currentSwatchNode, layout.currentSwatchRect)

        applyButtonVisual(
            button = copyButton,
            style = style,
            hovered = layout.copyRect.contains(hoverX, hoverY),
            selected = false
        )
        applyButtonVisual(
            button = pasteButton,
            style = style,
            hovered = layout.pasteRect.contains(hoverX, hoverY),
            selected = false
        )
        applyButtonVisual(
            button = pipetteButton,
            style = style,
            hovered = layout.pipetteRect.contains(hoverX, hoverY),
            selected = controller.isEyedropperActive()
        )
        pipetteButton.text = if (controller.isEyedropperActive()) "Pick..." else "Pipette"
        renderNode(ctx, copyButton, layout.copyRect)
        renderNode(ctx, pasteButton, layout.pasteRect)
        renderNode(ctx, pipetteButton, layout.pipetteRect)

        val definitionsByKey = controller.viewInputDefinitions().associate { it.first to it.second }
        for (index in 0 until MAX_INPUT_SLOTS) {
            val inputSlot = layout.inputSlots.getOrNull(index)
            val labelNode = inputLabelNodes[index]
            val inputNode = inputValueNodes[index]
            if (inputSlot == null) {
                inputLabelValues[index] = ""
                renderNode(ctx, labelNode, null)
                renderNode(ctx, inputNode, null)
                continue
            }

            val key = inputSlot.key
            val label = definitionsByKey[key] ?: inputSlot.label
            inputLabelValues[index] = label
            labelNode.color = style.mutedTextColor

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
            if (inputNode.text != value) {
                inputNode.text = value
            }
            inputNode.border = Border.all(1, borderColor)
            inputNode.backgroundColor = style.inputBackgroundColor
            inputNode.focusedBackgroundColor = style.inputBackgroundColor
            inputNode.textColor = style.textColor
            inputNode.placeholderColor = style.mutedTextColor
            inputNode.fontSize = style.fontSize

            renderNode(ctx, labelNode, inputSlot.labelRect)
            renderNode(ctx, inputNode, inputSlot.inputRect)
        }

        val modeOptionsByMode = layout.modeOptions.associateBy { it.mode }
        ColorFormatMode.entries.forEach { mode ->
            val button = modeOptionButtons[mode] ?: return@forEach
            val optionRect = if (modeDropdownOpen) modeOptionsByMode[mode]?.rect else null
            val hovered = optionRect?.contains(hoverX, hoverY) == true
            val selected = state.mode == mode
            applyButtonVisual(button = button, style = style, hovered = hovered, selected = selected)
            renderNode(ctx, button, optionRect)
        }

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
            addAll(modeOptionButtons.values)
        }
        buttons.forEach { button ->
            button.textColor = style.textColor
            button.applyStyle {
                border(1.px, style.inputBorderColor)
                fontSize(style.fontSize.px)
                padding = 0.px
                textWrap = TextWrap.NoWrap
            }
        }
        inputLabelNodes.forEach { label ->
            label.applyStyle {
                fontSize(style.fontSize.px)
                textWrap = TextWrap.NoWrap
            }
        }
        inputValueNodes.forEach { input ->
            input.applyStyle {
                fontSize(style.fontSize.px)
                textWrap = TextWrap.NoWrap
            }
        }
    }

    private fun applyButtonVisual(button: ButtonNode, style: ColorPickerStyle, hovered: Boolean, selected: Boolean) {
        button.backgroundColor = when {
            selected -> style.buttonActiveColor
            hovered -> style.buttonHoverColor
            else -> style.buttonBackgroundColor
        }
        button.border = Border.all(1, if (selected) style.inputActiveBorderColor else style.inputBorderColor)
        button.textColor = style.textColor
        button.fontSize = style.fontSize
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

internal class SystemColorPickerEyedropperOverlayNode(
    private val popupEngine: ColorPickerPopupEngine,
    key: Any? = "dsgl-system-color-picker-native-eyedropper-overlay"
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker-native-eyedropper-overlay"

    private val tooltipSwatch: ColorSwatchSurfaceNode = ColorSwatchSurfaceNode(
        key = "dsgl-system-color-picker-eyedropper-swatch"
    ).applyParent(this)
    private val modeText: TextNode = TextNode(
        textSource = TextSource.Dynamic { modeLabel },
        key = "dsgl-system-color-picker-eyedropper-mode"
    ).applyParent(this)
    private val valueText: TextNode = TextNode(
        textSource = TextSource.Dynamic { colorLabel },
        key = "dsgl-system-color-picker-eyedropper-value"
    ).applyParent(this)

    private var modeLabel: String = ""
    private var colorLabel: String = ""
    private var appliedStyle: ColorPickerStyle? = null
    private var panelBackgroundColor: Int = 0xF01A222C.toInt()
    private var panelBorderColor: Int = 0xFF7F96AD.toInt()

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        val controller = popupEngine.debugActiveController()
        if (controller == null || !controller.isEyedropperActive()) {
            hide(ctx)
            return
        }

        val style = popupEngine.debugActiveStyle() ?: controller.style()
        if (appliedStyle != style) {
            applyStaticStyle(style)
            appliedStyle = style
        }

        val hover = controller.viewHoverPosition()
        if (hover.first == Int.MIN_VALUE || hover.second == Int.MIN_VALUE) {
            hide(ctx)
            return
        }

        modeLabel = if (controller.snapshot().mode == ColorFormatMode.RGB && controller.snapshot().alphaEnabled) {
            "Mode: ${controller.snapshot().mode.name} (${controller.snapshot().rgbOrder.name})"
        } else {
            "Mode: ${controller.snapshot().mode.name}"
        }
        colorLabel = controller.viewFormattedColor()

        val tooltipWidth = style.eyedropperTooltipWidth.coerceAtLeast(156)
        val tooltipHeight = style.eyedropperTooltipHeight.coerceAtLeast(40)
        val preferredX = hover.first + style.eyedropperGapToCursor
        val preferredY = hover.second + style.eyedropperGapToCursor
        val tooltipRect = clampRect(
            Rect(preferredX, preferredY, tooltipWidth, tooltipHeight),
            bounds.width.coerceAtLeast(1),
            bounds.height.coerceAtLeast(1)
        )

        panelBackgroundColor = style.eyedropperOverlayBackgroundColor
        panelBorderColor = style.eyedropperOverlayBorderColor
        display = Display.Block
        super.render(ctx, tooltipRect.x, tooltipRect.y, tooltipRect.width, tooltipRect.height)

        val swatchSize = (tooltipRect.height - 10).coerceAtLeast(20)
        val swatchRect = Rect(
            x = tooltipRect.x + 5,
            y = tooltipRect.y + 5,
            width = swatchSize,
            height = swatchSize
        )
        tooltipSwatch.bind(style = style, color = controller.snapshot().color, highlighted = false)
        tooltipSwatch.display = Display.Block
        tooltipSwatch.render(ctx, swatchRect.x, swatchRect.y, swatchRect.width, swatchRect.height)

        val textX = swatchRect.x + swatchRect.width + 8
        val modeRect = Rect(
            x = textX,
            y = tooltipRect.y + 6,
            width = (tooltipRect.width - (textX - tooltipRect.x) - 6).coerceAtLeast(1),
            height = style.fontSize + 2
        )
        val valueRect = Rect(
            x = textX,
            y = modeRect.y + modeRect.height + 2,
            width = modeRect.width,
            height = style.fontSize + 2
        )
        modeText.display = Display.Block
        valueText.display = Display.Block
        modeText.render(ctx, modeRect.x, modeRect.y, modeRect.width, modeRect.height)
        valueText.render(ctx, valueRect.x, valueRect.y, valueRect.width, valueRect.height)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        if (display == Display.None || bounds.width <= 0 || bounds.height <= 0) return
        out += RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, panelBackgroundColor)
        drawBorder(out, bounds, panelBorderColor)
        super.buildRenderCommands(ctx, out)
    }

    private fun applyStaticStyle(style: ColorPickerStyle) {
        modeText.color = style.mutedTextColor
        valueText.color = style.textColor
        modeText.applyStyle {
            fontSize(style.fontSize.px)
            textWrap = TextWrap.NoWrap
        }
        valueText.applyStyle {
            fontSize(style.fontSize.px)
            textWrap = TextWrap.NoWrap
        }
    }

    private fun clampRect(rect: Rect, viewportWidth: Int, viewportHeight: Int): Rect {
        val safeViewportW = viewportWidth.coerceAtLeast(rect.width + 4)
        val safeViewportH = viewportHeight.coerceAtLeast(rect.height + 4)
        val minX = 2
        val minY = 2
        val maxX = (safeViewportW - rect.width - 2).coerceAtLeast(minX)
        val maxY = (safeViewportH - rect.height - 2).coerceAtLeast(minY)
        return Rect(
            x = rect.x.coerceIn(minX, maxX),
            y = rect.y.coerceIn(minY, maxY),
            width = rect.width,
            height = rect.height
        )
    }

    private fun hide(ctx: UiMeasureContext) {
        display = Display.None
        super.render(ctx, 0, 0, 0, 0)
        tooltipSwatch.display = Display.None
        modeText.display = Display.None
        valueText.display = Display.None
        tooltipSwatch.render(ctx, 0, 0, 0, 0)
        modeText.render(ctx, 0, 0, 0, 0)
        valueText.render(ctx, 0, 0, 0, 0)
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
        this.style = style
        this.hueDeg = hueDeg
        val hsv = ColorConversions.rgbToHsv(color, hueDeg)
        saturation = hsv.saturation
        brightness = hsv.brightness
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
        val thumbY = bounds.y + ((1f - brightness) * bounds.height.toFloat()).roundToInt().coerceIn(0, bounds.height - 1)
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
        out += RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, (localColor ?: RgbaColor.WHITE).toArgbInt())
        drawBorder(out, bounds, if (highlighted) style.inputActiveBorderColor else style.inputBorderColor)
    }
}

private fun drawChecker(out: MutableList<RenderCommand>, rect: Rect, style: ColorPickerStyle) {
    val size = 4
    var y = rect.y
    var row = 0
    while (y < rect.y + rect.height) {
        var x = rect.x
        var col = row % 2
        while (x < rect.x + rect.width) {
            val color = if (col % 2 == 0) style.checkerLightColor else style.checkerDarkColor
            val w = size.coerceAtMost(rect.x + rect.width - x)
            val h = size.coerceAtMost(rect.y + rect.height - y)
            out += RenderCommand.DrawRect(x, y, w, h, color)
            x += size
            col += 1
        }
        y += size
        row += 1
    }
}

private fun drawBorder(out: MutableList<RenderCommand>, rect: Rect, color: Int) {
    if (rect.width <= 0 || rect.height <= 0) return
    out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, 1, color)
    out += RenderCommand.DrawRect(rect.x, rect.y + rect.height - 1, rect.width, 1, color)
    out += RenderCommand.DrawRect(rect.x, rect.y, 1, rect.height, color)
    out += RenderCommand.DrawRect(rect.x + rect.width - 1, rect.y, 1, rect.height, color)
}

