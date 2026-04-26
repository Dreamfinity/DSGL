package org.dreamfinity.dsgl.core.colorpicker

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.popup.FloatingPaneDragModel
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.math.roundToInt

data class ColorPickerInputSlot(
    val key: String,
    val label: String,
    val labelRect: Rect,
    val inputRect: Rect,
)

data class ColorPickerModeOptionSlot(
    val mode: ColorFormatMode,
    val rect: Rect,
)

data class ColorPickerLayout(
    val bounds: Rect,
    val modeSelectRect: Rect,
    val rgbaOrderRect: Rect?,
    val argbOrderRect: Rect?,
    val modeOptionsRect: Rect?,
    val modeOptions: List<ColorPickerModeOptionSlot>,
    val colorFieldRect: Rect,
    val hueRect: Rect,
    val alphaRect: Rect?,
    val previousSwatchRect: Rect,
    val currentSwatchRect: Rect,
    val copyRect: Rect,
    val pasteRect: Rect,
    val pipetteRect: Rect,
    val inputSlots: List<ColorPickerInputSlot>,
    val recentRects: List<Rect>,
)

data class ColorPickerEyedropperOverlayModel(
    val panelRect: Rect,
    val magnifierRect: Rect,
    val captureSourceRect: Rect,
    val centerRect: Rect,
    val swatchRect: Rect,
    val modeText: String,
    val valueText: String,
)

class ColorPickerController(
    initial: ColorPickerState,
    private val style: ColorPickerStyle = ColorPickerStyle(),
    private val recentHistory: ColorRecentHistory = ColorRecentHistory(),
    private val screenSampler: ScreenColorSampler? =
        ScreenColorSampler { x, y ->
            val sampled = ScreenColorSamplerBridge.sampleColorAt(x, y) ?: return@ScreenColorSampler null
            sampled.toArgbInt()
        },
) {
    private var state: ColorPickerState = initial.withColor(initial.color)
    private var hueDeg: Float = ColorConversions.rgbToHsv(state.color).hueDeg
    private val interaction: ColorPickerInteractionSession = ColorPickerInteractionSession()
    private var dragTarget: ColorPickerDragTarget
        get() = interaction.dragTarget
        set(value) {
            interaction.dragTarget = value
        }
    private var hoverX: Int
        get() = interaction.hoverX
        set(value) {
            interaction.hoverX = value
        }
    private var hoverY: Int
        get() = interaction.hoverY
        set(value) {
            interaction.hoverY = value
        }
    private var activeInputKey: String?
        get() = interaction.textInput.activeKey
        set(value) {
            interaction.textInput.activeKey = value
        }
    private var activeInputBuffer: String
        get() = interaction.textInput.buffer
        set(value) {
            interaction.textInput.buffer = value
        }
    private var modeDropdownOpen: Boolean
        get() = interaction.modeDropdownOpen
        set(value) {
            interaction.modeDropdownOpen = value
        }
    private var eyedropperActive: Boolean = false
    private var eyedropperBaseColor: RgbaColor = state.color
    private val eyedropperOverlayDrag: FloatingPaneDragModel = FloatingPaneDragModel()
    private var eyedropperOverlayRect: Rect? = null
    private var domFocusedInputKey: String? = null
    private var domInputFocusResyncRequested: Boolean = false
    private var domLastFocusedInputKey: String? = null
    private var domPendingFocusResyncKey: String? = null

    var onPreview: ((RgbaColor) -> Unit)? = null
    var onChange: ((RgbaColor) -> Unit)? = null
    var onCommit: ((RgbaColor) -> Unit)? = null
    var onRequestClose: (() -> Unit)? = null

    init {
        recentHistory.add(state.color)
    }

    fun snapshot(): ColorPickerState = state

    fun setState(next: ColorPickerState) {
        state = next.withColor(next.color)
        hueDeg = ColorConversions.rgbToHsv(state.color, hueDeg).hueDeg
        eyedropperActive = false
        interaction.clearDragTarget()
        modeDropdownOpen = false
        eyedropperOverlayDrag.end()
        eyedropperOverlayRect = null
        domFocusedInputKey = null
        domInputFocusResyncRequested = false
        domLastFocusedInputKey = null
        domPendingFocusResyncKey = null
        clearInputEdit()
    }

    fun style(): ColorPickerStyle = style

    fun isEyedropperActive(): Boolean = eyedropperActive

    fun hasActiveDragTarget(): Boolean = dragTarget != ColorPickerDragTarget.None

    fun hasActiveTextInput(): Boolean = activeInputKey != null

    fun hasActiveInteraction(): Boolean = hasActiveDragTarget() || isEyedropperActive() || hasActiveTextInput()

    internal fun viewHueDeg(): Float = hueDeg

    internal fun viewHoverPosition(): Pair<Int, Int> = hoverX to hoverY

    internal fun viewModeDropdownOpen(): Boolean = modeDropdownOpen

    internal fun viewActiveInputKey(): String? = activeInputKey

    internal fun viewActiveInputBuffer(): String = activeInputBuffer

    internal fun viewInputValues(): Map<String, String> = inputValues()

    internal fun viewInputDefinitions(): List<Pair<String, String>> = inputDefinitions().map { it.key to it.label }

    internal fun viewRecentColors(): List<RgbaColor> = recentHistory.snapshot()

    internal fun viewCaretVisible(nowMs: Long): Boolean = caretVisible(nowMs)

    internal fun viewFormattedColor(): String =
        ColorTextCodec.format(state.color, state.mode, state.alphaEnabled, state.rgbOrder)

    internal fun handleDomInputFocused(key: String) {
        domFocusedInputKey = key
        domLastFocusedInputKey = key
        if (activeInputKey == key) {
            clearInputEdit()
        }
    }

    internal fun handleDomInputBlurred(key: String) {
        if (domFocusedInputKey == key) {
            domFocusedInputKey = null
        }
    }

    internal fun handleDomInputDraft(key: String, value: String): Boolean {
        domFocusedInputKey = key
        domLastFocusedInputKey = key
        return semanticApplyInputDraftValue(key, value)
    }

    internal fun commitDomInputEdit(key: String, value: String): Boolean {
        domFocusedInputKey = key
        domLastFocusedInputKey = key
        return semanticCommitInputEdit(key, value)
    }

    internal fun cancelDomInputEdit(key: String) {
        if (domFocusedInputKey == key) {
            domFocusedInputKey = null
        }
        semanticCancelInputEdit()
    }

    internal fun resolveDomInputValue(key: String): String = inputValues()[key].orEmpty()

    internal fun consumeDomInputFocusResyncKey(): String? {
        if (!domInputFocusResyncRequested) return null
        domInputFocusResyncRequested = false
        val key = domPendingFocusResyncKey
        domPendingFocusResyncKey = null
        return key
    }

    internal fun semanticSetMode(mode: ColorFormatMode) {
        state = state.copy(mode = mode)
        modeDropdownOpen = false
        requestDomInputFocusResync()
        clearInputEdit()
    }

    internal fun semanticSetRgbOrder(order: RgbChannelOrder) {
        state = state.copy(rgbOrder = order)
        modeDropdownOpen = false
        requestDomInputFocusResync()
        clearInputEdit()
    }

    internal fun semanticToggleModeDropdown() {
        modeDropdownOpen = !modeDropdownOpen
        clearInputEdit()
    }

    internal fun semanticCloseModeDropdown() {
        modeDropdownOpen = false
    }

    internal fun semanticUpdateFromField(
        globalX: Int,
        globalY: Int,
        rect: Rect,
        commit: Boolean,
    ) {
        updateFromField(globalX, globalY, rect, commit)
    }

    internal fun semanticUpdateFromHue(globalX: Int, rect: Rect, commit: Boolean) {
        updateFromHue(globalX, rect, commit)
    }

    internal fun semanticUpdateFromAlpha(globalX: Int, rect: Rect, commit: Boolean) {
        updateFromAlpha(globalX, rect, commit)
    }

    internal fun semanticPreviewPreviousSwatch() {
        applyColor(state.previous, notifyPreview = true, commit = false)
    }

    internal fun semanticCommitCurrentColor() {
        commitCurrentColor()
    }

    internal fun semanticCopyCurrentColor() {
        ColorClipboardSupport.copy(state.color, state.mode, state.alphaEnabled, state.rgbOrder)
    }

    internal fun semanticPasteFromClipboard(): Boolean {
        val parsed = ColorClipboardSupport.paste() ?: return false
        val next = if (state.alphaEnabled) parsed.color else parsed.color.copy(a = 1f)
        applyColor(next, notifyPreview = true, commit = false)
        state =
            state.copy(
                mode = parsed.detectedMode,
                rgbOrder = parsed.detectedRgbOrder ?: state.rgbOrder,
            )
        return true
    }

    internal fun semanticBeginEyedropper() {
        beginEyedropper()
    }

    internal fun semanticCancelEyedropper() {
        cancelEyedropper()
    }

    internal fun semanticAcceptEyedropperSelection() {
        commitCurrentColor()
        eyedropperActive = false
    }

    internal fun semanticBeginInputEdit(key: String) {
        interaction.textInput.begin(key, inputValues()[key].orEmpty())
    }

    internal fun semanticPreviewRecentSwatch(index: Int): Boolean {
        val color = recentHistory.snapshot().getOrNull(index) ?: return false
        applyColor(color, notifyPreview = true, commit = false)
        return true
    }

    internal fun semanticApplyInputDraftValue(key: String, value: String): Boolean = applyInputDraftValue(key, value)

    internal fun semanticCommitInputEdit(key: String, value: String): Boolean {
        val applied = semanticApplyInputDraftValue(key, value)
        semanticCommitCurrentColor()
        semanticCancelInputEdit()
        return applied
    }

    internal fun semanticCancelInputEdit() {
        clearInputEdit()
    }

    internal fun semanticRequestClose() {
        onRequestClose?.invoke()
    }

    fun beginEyedropper() {
        if (!state.alphaEnabled) {
            eyedropperBaseColor = state.color.copy(a = 1f)
        } else {
            eyedropperBaseColor = state.color
        }
        eyedropperActive = true
        eyedropperOverlayDrag.end()
        eyedropperOverlayRect = null
        modeDropdownOpen = false
        clearInputEdit()
    }

    fun cancelEyedropper() {
        if (!eyedropperActive) return
        eyedropperActive = false
        eyedropperOverlayDrag.end()
        eyedropperOverlayRect = null
        applyColor(eyedropperBaseColor, notifyPreview = true, commit = false)
    }

    fun buildLayout(bounds: Rect): ColorPickerLayout {
        val padding = style.padding
        val innerX = bounds.x + padding
        val innerY = bounds.y + padding
        val innerW = (bounds.width - padding * 2).coerceAtLeast(1)
        val rowGap = style.rowGap

        val orderSlotGap = style.rgbOrderLabelGap.coerceAtLeast(1)
        val modeRowGap = style.modeRowGap.coerceAtLeast(1)
        val orderLabelWidth = style.rgbOrderLabelWidth.coerceAtLeast(32)
        val modeSelectMinWidth = style.modeSelectMinWidth.coerceAtLeast(84)
        val showRgbOrderSwitch =
            state.alphaEnabled &&
                innerW >= (modeSelectMinWidth + modeRowGap + orderLabelWidth * 2 + orderSlotGap)
        val modeRowReserved =
            if (showRgbOrderSwitch) {
                orderLabelWidth * 2 + orderSlotGap + modeRowGap
            } else {
                0
            }
        val modeSelectMax = (innerW - modeRowReserved).coerceAtLeast(1)
        val modeSelectWidth = minOf(style.modeSelectWidth.coerceAtLeast(84), modeSelectMax).coerceAtLeast(1)
        val modeSelectRect =
            Rect(
                x = innerX,
                y = innerY,
                width = modeSelectWidth,
                height = style.modeSelectHeight,
            )
        val rgbaOrderRect: Rect?
        val argbOrderRect: Rect?
        if (showRgbOrderSwitch) {
            val orderStartX = modeSelectRect.x + modeSelectRect.width + modeRowGap
            val orderAvailable = (innerX + innerW - orderStartX).coerceAtLeast(0)
            if (orderAvailable >= 2) {
                val gap = orderSlotGap.coerceAtMost((orderAvailable - 2).coerceAtLeast(1))
                val firstWidth = ((orderAvailable - gap) / 2).coerceAtLeast(1)
                val secondX = orderStartX + firstWidth + gap
                val secondWidth = (innerX + innerW - secondX).coerceAtLeast(1)
                rgbaOrderRect =
                    Rect(
                        x = orderStartX,
                        y = innerY,
                        width = firstWidth,
                        height = style.modeSelectHeight,
                    )
                argbOrderRect =
                    Rect(
                        x = secondX,
                        y = innerY,
                        width = secondWidth,
                        height = style.modeSelectHeight,
                    )
            } else {
                rgbaOrderRect = null
                argbOrderRect = null
            }
        } else {
            rgbaOrderRect = null
            argbOrderRect = null
        }
        val modeOptions = ArrayList<ColorPickerModeOptionSlot>(ColorFormatMode.entries.size)
        val modeOptionsRect =
            if (modeDropdownOpen) {
                val optionHeight = style.modeOptionHeight
                val popupWidth = maxOf(modeSelectRect.width, style.modeSelectMinWidth)
                val popupHeight = optionHeight * ColorFormatMode.entries.size + 2
                val minX = innerX
                val maxX = (innerX + innerW - popupWidth).coerceAtLeast(innerX)
                val popupX = if (minX <= maxX) modeSelectRect.x.coerceIn(minX, maxX) else minX
                val preferredBelowY = modeSelectRect.y + modeSelectRect.height + 2
                val minY = innerY
                val maxY = (bounds.y + bounds.height - padding - popupHeight).coerceAtLeast(minY)
                val popupY =
                    if (preferredBelowY <= maxY) {
                        preferredBelowY
                    } else {
                        if (minY <= maxY) (modeSelectRect.y - popupHeight - 2).coerceIn(minY, maxY) else minY
                    }
                ColorFormatMode.entries.forEachIndexed { index, mode ->
                    modeOptions +=
                        ColorPickerModeOptionSlot(
                            mode = mode,
                            rect =
                                Rect(
                                    x = popupX + 1,
                                    y = popupY + 1 + index * optionHeight,
                                    width = popupWidth - 2,
                                    height = optionHeight,
                                ),
                        )
                }
                Rect(popupX, popupY, popupWidth, popupHeight)
            } else {
                null
            }

        var y = innerY + style.modeSelectHeight + rowGap
        val fieldRect = Rect(innerX, y, innerW, style.colorFieldHeight)
        y += style.colorFieldHeight + rowGap
        val hueRect = Rect(innerX, y, innerW, style.sliderHeight)
        y += style.sliderHeight + rowGap
        val alphaRect =
            if (state.alphaEnabled) {
                Rect(innerX, y, innerW, style.sliderHeight).also {
                    y += style.sliderHeight + rowGap
                }
            } else {
                null
            }

        val swatchWidth = ((innerW - rowGap * 4) / 5).coerceAtLeast(24)
        val previousSwatchRect = Rect(innerX, y, swatchWidth, style.swatchHeight)
        val currentSwatchRect = Rect(previousSwatchRect.x + swatchWidth + rowGap, y, swatchWidth, style.swatchHeight)
        val copyRect = Rect(currentSwatchRect.x + swatchWidth + rowGap, y, swatchWidth, style.swatchHeight)
        val pasteRect = Rect(copyRect.x + swatchWidth + rowGap, y, swatchWidth, style.swatchHeight)
        val pipetteRect = Rect(pasteRect.x + swatchWidth + rowGap, y, swatchWidth, style.swatchHeight)
        y += style.swatchHeight + rowGap

        val inputDefs = inputDefinitions()
        val inputCount = inputDefs.size.coerceAtLeast(1)
        val inputWidth = ((innerW - rowGap * (inputCount - 1)) / inputCount).coerceAtLeast(44)
        val inputs = ArrayList<ColorPickerInputSlot>(inputCount)
        val labelWidth = style.inputLabelWidth.coerceAtLeast(12)
        inputDefs.forEachIndexed { index, def ->
            val slotX = innerX + index * (inputWidth + rowGap)
            val localLabelWidth =
                if (def.key == "hex") {
                    labelWidth + 8
                } else {
                    labelWidth
                }
            val inputX = slotX + localLabelWidth + style.inputLabelGap
            val inputW = (inputWidth - localLabelWidth - style.inputLabelGap).coerceAtLeast(20)
            val labelRect =
                Rect(
                    x = slotX,
                    y = y,
                    width = localLabelWidth,
                    height = style.inputHeight,
                )
            val inputRect =
                Rect(
                    x = inputX,
                    y = y,
                    width = inputW,
                    height = style.inputHeight,
                )
            inputs +=
                ColorPickerInputSlot(
                    key = def.key,
                    label = def.label,
                    labelRect = labelRect,
                    inputRect = inputRect,
                )
        }
        y += style.inputHeight + rowGap

        val recentCell = style.recentCellSize
        val recentGap = style.recentCellGap
        val recentRects = ArrayList<Rect>(64)
        val rows = 8
        val cols = 8
        val gridWidth = cols * recentCell + (cols - 1) * recentGap
        val gridX = innerX + ((innerW - gridWidth) / 2).coerceAtLeast(0)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val rx = gridX + col * (recentCell + recentGap)
                val ry = y + row * (recentCell + recentGap)
                recentRects += Rect(rx, ry, recentCell, recentCell)
            }
        }

        return ColorPickerLayout(
            bounds = bounds,
            modeSelectRect = modeSelectRect,
            rgbaOrderRect = rgbaOrderRect,
            argbOrderRect = argbOrderRect,
            modeOptionsRect = modeOptionsRect,
            modeOptions = modeOptions,
            colorFieldRect = fieldRect,
            hueRect = hueRect,
            alphaRect = alphaRect,
            previousSwatchRect = previousSwatchRect,
            currentSwatchRect = currentSwatchRect,
            copyRect = copyRect,
            pasteRect = pasteRect,
            pipetteRect = pipetteRect,
            inputSlots = inputs,
            recentRects = recentRects,
        )
    }

    fun preferredHeight(alphaEnabled: Boolean = state.alphaEnabled): Int {
        val rowGap = style.rowGap
        val core =
            style.padding * 2 +
                style.modeSelectHeight + rowGap +
                style.colorFieldHeight + rowGap +
                style.sliderHeight + rowGap +
                (if (alphaEnabled) style.sliderHeight + rowGap else 0) +
                style.swatchHeight + rowGap +
                style.inputHeight + rowGap
        val recentGrid = style.recentCellSize * 8 + style.recentCellGap * 7
        return core + recentGrid
    }

    fun appendCommands(
        layout: ColorPickerLayout,
        out: MutableList<RenderCommand>,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val bounds = layout.bounds
        out += RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, style.panelBackgroundColor)
        drawBorder(out, bounds, style.panelBorderColor)

        drawModeSelect(layout, out)
        drawRgbOrderSwitch(layout, out)

        drawColorField(layout.colorFieldRect, out)
        drawFieldThumb(layout.colorFieldRect, out)
        drawHueSlider(layout.hueRect, out)
        drawHueThumb(layout.hueRect, out)
        layout.alphaRect?.let { alphaRect ->
            drawAlphaSlider(alphaRect, out)
            drawAlphaThumb(alphaRect, out)
        }

        drawSwatch(layout.previousSwatchRect, state.previous, out)
        drawSwatch(layout.currentSwatchRect, state.color, out)
        drawButton(layout.copyRect, "Copy", layout.copyRect.contains(hoverX, hoverY), out)
        drawButton(layout.pasteRect, "Paste", layout.pasteRect.contains(hoverX, hoverY), out)
        val pipetteLabel = if (eyedropperActive) "Pick..." else "Pipette"
        drawButton(layout.pipetteRect, pipetteLabel, layout.pipetteRect.contains(hoverX, hoverY), out)

        val inputValues = inputValues()
        layout.inputSlots.forEach { slot ->
            val active = activeInputKey == slot.key
            val hovered = slot.inputRect.contains(hoverX, hoverY)
            val border =
                when {
                    active -> style.inputActiveBorderColor
                    hovered -> style.buttonHoverColor
                    else -> style.inputBorderColor
                }
            out +=
                RenderCommand.DrawText(
                    text = slot.label,
                    x = slot.labelRect.x + 2,
                    y = slot.labelRect.y + 2,
                    color = style.mutedTextColor,
                    fontSize = style.fontSize,
                )
            out +=
                RenderCommand.DrawRect(
                    slot.inputRect.x,
                    slot.inputRect.y,
                    slot.inputRect.width,
                    slot.inputRect.height,
                    style.inputBackgroundColor,
                )
            drawBorder(out, slot.inputRect, border)
            val value =
                if (active) {
                    activeInputBuffer +
                        if (caretVisible(
                                nowMs,
                            )
                        ) {
                            "|"
                        } else {
                            ""
                        }
                } else {
                    inputValues[slot.key].orEmpty()
                }
            out +=
                RenderCommand.DrawText(
                    text = truncate(value, 12),
                    x = slot.inputRect.x + 4,
                    y = slot.inputRect.y + 2,
                    color = style.textColor,
                    fontSize = style.fontSize,
                )
        }

        val recents = recentHistory.snapshot()
        val hoveredRecent = layout.recentRects.indexOfFirst { it.contains(hoverX, hoverY) }
        layout.recentRects.forEachIndexed { index, rect ->
            val color = recents.getOrNull(index)
            if (color == null) {
                out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, rect.height, 0x33222A34)
                drawBorder(out, rect, style.recentGridBorderColor)
            } else {
                drawSwatch(rect, color, out)
            }
            if (index == hoveredRecent) {
                drawBorder(out, rect, style.inputActiveBorderColor)
            }
        }
        drawModeOptions(layout, out)
    }

    fun appendEyedropperOverlay(viewportWidth: Int, viewportHeight: Int, out: MutableList<RenderCommand>) {
        if (!eyedropperActive) return
        if (hoverX == Int.MIN_VALUE || hoverY == Int.MIN_VALUE) return

        val gridSize = normalizedEyedropperGridSize()
        val cell = style.eyedropperCellSize.coerceAtLeast(2)
        val magnifierContentSize = gridSize * cell
        val magnifierWidth = magnifierContentSize + 8
        val magnifierHeight = magnifierContentSize + 8
        val tooltipWidth = style.eyedropperTooltipWidth.coerceAtLeast(156)
        val tooltipHeight = style.eyedropperTooltipHeight.coerceAtLeast(40)
        val panelWidth = maxOf(magnifierWidth, tooltipWidth)
        val panelHeight = magnifierHeight + 6 + tooltipHeight

        val preferredX = hoverX + style.eyedropperGapToCursor
        val preferredY = hoverY + style.eyedropperGapToCursor
        val desiredRect =
            clampOverlayRect(
                rect = Rect(preferredX, preferredY, panelWidth, panelHeight),
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            )
        val currentRect = eyedropperOverlayRect
        if (currentRect == null || currentRect.width != panelWidth || currentRect.height != panelHeight) {
            eyedropperOverlayRect = desiredRect
            eyedropperOverlayDrag.begin(mouseX = hoverX, mouseY = hoverY, rect = desiredRect)
        }
        val nextRect =
            eyedropperOverlayDrag.update(
                mouseX = hoverX,
                mouseY = hoverY,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                clamp = ::clampOverlayRect,
            )
        eyedropperOverlayRect = nextRect
        val panelX = nextRect.x
        val panelY = nextRect.y

        val magnifierX = panelX + 4
        val magnifierY = panelY + 4
        out +=
            RenderCommand.CaptureScreenRegion(
                sourceX = hoverX - gridSize / 2,
                sourceY = hoverY - gridSize / 2,
                sourceWidth = gridSize,
                sourceHeight = gridSize,
                fallbackColor = state.color.toArgbInt(),
            )
        out += RenderCommand.DrawRect(panelX + 2, panelY + 2, panelWidth, panelHeight, style.panelShadowColor)
        out += RenderCommand.DrawRect(panelX, panelY, panelWidth, panelHeight, style.eyedropperOverlayBackgroundColor)
        drawBorder(out, Rect(panelX, panelY, panelWidth, panelHeight), style.eyedropperOverlayBorderColor)
        out +=
            RenderCommand.DrawCapturedScreenRegion(
                x = magnifierX,
                y = magnifierY,
                width = magnifierContentSize,
                height = magnifierContentSize,
            )
        if (style.eyedropperGridOverlayEnabled) {
            drawEyedropperGridOverlay(
                out = out,
                rect = Rect(magnifierX, magnifierY, magnifierContentSize, magnifierContentSize),
                columns = gridSize,
                rows = gridSize,
                cellSize = cell,
                color = style.eyedropperGridOverlayColor,
            )
        }
        drawBorder(
            out,
            Rect(magnifierX, magnifierY, magnifierContentSize, magnifierContentSize),
            style.inputBorderColor,
        )

        val center = gridSize / 2
        val centerRect =
            Rect(
                x = magnifierX + center * cell,
                y = magnifierY + center * cell,
                width = cell,
                height = cell,
            )
        drawBorder(out, centerRect, style.eyedropperCenterBorderColor)

        val tooltipY = magnifierY + magnifierContentSize + 6
        val swatchSize = tooltipHeight - 10
        val swatchRect = Rect(panelX + 6, tooltipY + 5, swatchSize, swatchSize)
        drawSwatch(swatchRect, state.color, out)

        val modeText =
            if (state.mode == ColorFormatMode.RGB && state.alphaEnabled) {
                "Mode: ${state.mode.name} (${state.rgbOrder.name})"
            } else {
                "Mode: ${state.mode.name}"
            }
        val valueText = ColorTextCodec.format(state.color, state.mode, state.alphaEnabled, state.rgbOrder)
        out +=
            RenderCommand.DrawText(
                text = modeText,
                x = swatchRect.x + swatchRect.width + 8,
                y = tooltipY + 6,
                color = style.mutedTextColor,
                fontSize = style.fontSize,
            )
        out +=
            RenderCommand.DrawText(
                text = valueText,
                x = swatchRect.x + swatchRect.width + 8,
                y = tooltipY + 6 + style.fontSize,
                color = style.textColor,
                fontSize = style.fontSize,
            )
    }

    internal fun resolveEyedropperOverlayModel(
        viewportWidth: Int,
        viewportHeight: Int,
    ): ColorPickerEyedropperOverlayModel? {
        if (!eyedropperActive) return null
        if (hoverX == Int.MIN_VALUE || hoverY == Int.MIN_VALUE) return null

        val gridSize = normalizedEyedropperGridSize()
        val cell = style.eyedropperCellSize.coerceAtLeast(2)
        val magnifierContentSize = gridSize * cell
        val magnifierWidth = magnifierContentSize + 8
        val magnifierHeight = magnifierContentSize + 8
        val tooltipWidth = style.eyedropperTooltipWidth.coerceAtLeast(156)
        val tooltipHeight = style.eyedropperTooltipHeight.coerceAtLeast(40)
        val panelWidth = maxOf(magnifierWidth, tooltipWidth)
        val panelHeight = magnifierHeight + 6 + tooltipHeight

        val preferredX = hoverX + style.eyedropperGapToCursor
        val preferredY = hoverY + style.eyedropperGapToCursor
        val desiredRect =
            clampOverlayRect(
                rect = Rect(preferredX, preferredY, panelWidth, panelHeight),
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            )
        val currentRect = eyedropperOverlayRect
        if (currentRect == null || currentRect.width != panelWidth || currentRect.height != panelHeight) {
            eyedropperOverlayRect = desiredRect
            eyedropperOverlayDrag.begin(mouseX = hoverX, mouseY = hoverY, rect = desiredRect)
        }
        val nextRect =
            eyedropperOverlayDrag.update(
                mouseX = hoverX,
                mouseY = hoverY,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                clamp = ::clampOverlayRect,
            )
        eyedropperOverlayRect = nextRect

        val magnifierRect =
            Rect(
                x = nextRect.x + 4,
                y = nextRect.y + 4,
                width = magnifierContentSize,
                height = magnifierContentSize,
            )
        val center = gridSize / 2
        val centerRect =
            Rect(
                x = magnifierRect.x + center * cell,
                y = magnifierRect.y + center * cell,
                width = cell,
                height = cell,
            )
        val tooltipY = magnifierRect.y + magnifierRect.height + 6
        val swatchSize = tooltipHeight - 10
        val swatchRect = Rect(nextRect.x + 6, tooltipY + 5, swatchSize, swatchSize)
        val modeText =
            if (state.mode == ColorFormatMode.RGB && state.alphaEnabled) {
                "Mode: ${state.mode.name} (${state.rgbOrder.name})"
            } else {
                "Mode: ${state.mode.name}"
            }
        val valueText = ColorTextCodec.format(state.color, state.mode, state.alphaEnabled, state.rgbOrder)
        return ColorPickerEyedropperOverlayModel(
            panelRect = nextRect,
            magnifierRect = magnifierRect,
            captureSourceRect =
                Rect(
                    x = hoverX - gridSize / 2,
                    y = hoverY - gridSize / 2,
                    width = gridSize,
                    height = gridSize,
                ),
            centerRect = centerRect,
            swatchRect = swatchRect,
            modeText = modeText,
            valueText = valueText,
        )
    }

    fun handleMouseMove(globalX: Int, globalY: Int, layout: ColorPickerLayout): Boolean {
        interaction.setHover(globalX, globalY)
        if (eyedropperActive) {
            return true
        }
        when (dragTarget) {
            ColorPickerDragTarget.Field -> {
                semanticUpdateFromField(globalX, globalY, layout.colorFieldRect, commit = false)
                return true
            }

            ColorPickerDragTarget.Hue -> {
                semanticUpdateFromHue(globalX, layout.hueRect, commit = false)
                return true
            }

            ColorPickerDragTarget.Alpha -> {
                val alphaRect = layout.alphaRect
                if (alphaRect != null) {
                    semanticUpdateFromAlpha(globalX, alphaRect, commit = false)
                }
                return true
            }

            ColorPickerDragTarget.None -> {
                if (layout.modeOptionsRect?.contains(globalX, globalY) == true) return true
                return layout.bounds.contains(globalX, globalY)
            }
        }
    }

    fun handleMouseDown(
        globalX: Int,
        globalY: Int,
        button: MouseButton,
        layout: ColorPickerLayout,
    ): Boolean {
        interaction.setHover(globalX, globalY)
        if (eyedropperActive) {
            return when (button) {
                MouseButton.LEFT -> {
                    semanticAcceptEyedropperSelection()
                    true
                }

                MouseButton.RIGHT -> {
                    semanticCancelEyedropper()
                    true
                }

                MouseButton.MIDDLE -> true
            }
        }
        if (button != MouseButton.LEFT) {
            if (button == MouseButton.RIGHT && modeDropdownOpen) {
                modeDropdownOpen = false
                return true
            }
            return layout.bounds.contains(globalX, globalY) ||
                layout.modeOptionsRect?.contains(globalX, globalY) == true
        }

        val modeOptionHit =
            if (modeDropdownOpen) {
                layout.modeOptions.firstOrNull { it.rect.contains(globalX, globalY) }
            } else {
                null
            }
        if (modeOptionHit != null) {
            semanticSetMode(modeOptionHit.mode)
            return true
        }
        if (layout.rgbaOrderRect?.contains(globalX, globalY) == true) {
            semanticSetRgbOrder(RgbChannelOrder.RGBA)
            return true
        }
        if (layout.argbOrderRect?.contains(globalX, globalY) == true) {
            semanticSetRgbOrder(RgbChannelOrder.ARGB)
            return true
        }
        if (layout.modeSelectRect.contains(globalX, globalY)) {
            semanticToggleModeDropdown()
            return true
        }
        if (!layout.bounds.contains(globalX, globalY)) {
            semanticCloseModeDropdown()
            semanticCancelInputEdit()
            return false
        }
        semanticCloseModeDropdown()

        if (layout.colorFieldRect.contains(globalX, globalY)) {
            dragTarget = ColorPickerDragTarget.Field
            semanticCancelInputEdit()
            semanticUpdateFromField(globalX, globalY, layout.colorFieldRect, commit = false)
            return true
        }
        if (layout.hueRect.contains(globalX, globalY)) {
            dragTarget = ColorPickerDragTarget.Hue
            semanticCancelInputEdit()
            semanticUpdateFromHue(globalX, layout.hueRect, commit = false)
            return true
        }
        if (layout.alphaRect?.contains(globalX, globalY) == true) {
            dragTarget = ColorPickerDragTarget.Alpha
            semanticCancelInputEdit()
            semanticUpdateFromAlpha(globalX, layout.alphaRect, commit = false)
            return true
        }

        if (layout.previousSwatchRect.contains(globalX, globalY)) {
            semanticPreviewPreviousSwatch()
            return true
        }
        if (layout.currentSwatchRect.contains(globalX, globalY)) {
            semanticCommitCurrentColor()
            return true
        }
        if (layout.copyRect.contains(globalX, globalY)) {
            semanticCopyCurrentColor()
            return true
        }
        if (layout.pasteRect.contains(globalX, globalY)) {
            semanticPasteFromClipboard()
            return true
        }
        if (layout.pipetteRect.contains(globalX, globalY)) {
            semanticBeginEyedropper()
            return true
        }

        val inputHit = layout.inputSlots.firstOrNull { it.inputRect.contains(globalX, globalY) }
        if (inputHit != null) {
            semanticBeginInputEdit(inputHit.key)
            return true
        }
        semanticCancelInputEdit()

        val recentIndex = layout.recentRects.indexOfFirst { it.contains(globalX, globalY) }
        if (recentIndex >= 0) {
            semanticPreviewRecentSwatch(recentIndex)
            return true
        }

        return true
    }

    fun handleMouseUp(globalX: Int, globalY: Int, button: MouseButton): Boolean {
        interaction.setHover(globalX, globalY)
        if (button != MouseButton.LEFT) return eyedropperActive
        val dragged = interaction.hasActiveDragTarget()
        interaction.clearDragTarget()
        if (dragged) {
            semanticCommitCurrentColor()
            return true
        }
        return eyedropperActive
    }

    fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean {
        if (eyedropperActive && keyCode == KeyCodes.ESCAPE) {
            semanticCancelEyedropper()
            return true
        }
        if (KeyModifiers.shortcutDown && keyCode == KeyCodes.C) {
            semanticCopyCurrentColor()
            return true
        }
        if (KeyModifiers.shortcutDown && keyCode == KeyCodes.V) {
            semanticPasteFromClipboard()
            return true
        }
        val key =
            activeInputKey ?: run {
                if (keyCode == KeyCodes.ESCAPE) {
                    if (modeDropdownOpen) {
                        semanticCloseModeDropdown()
                        return true
                    }
                }
                return false
            }
        when (keyCode) {
            KeyCodes.ESCAPE -> {
                semanticCancelInputEdit()
                return true
            }

            KeyCodes.ENTER -> {
                commitInputEdit(key)
                return true
            }

            KeyCodes.BACKSPACE -> {
                if (activeInputBuffer.isNotEmpty()) {
                    interaction.textInput.backspace()
                    applyInputDraft(key)
                }
                return true
            }

            KeyCodes.DELETE -> {
                interaction.textInput.clearBuffer()
                return true
            }
        }
        if (keyChar >= ' ' && !Character.isISOControl(keyChar)) {
            interaction.textInput.append(keyChar)
            applyInputDraft(key)
            return true
        }
        return false
    }

    fun sampleEyedropperAtHover() {
        if (!eyedropperActive) return
        if (hoverX == Int.MIN_VALUE || hoverY == Int.MIN_VALUE) return
        sampleEyedropper(hoverX, hoverY, commit = false)
    }

    private fun commitInputEdit(key: String) {
        if (!applyInputDraft(key)) {
            activeInputBuffer = inputValues()[key].orEmpty()
        }
        clearInputEdit()
        commitCurrentColor()
    }

    private fun clearInputEdit() {
        interaction.textInput.clear()
    }

    private fun applyInputDraft(key: String): Boolean = applyInputDraftValue(key, activeInputBuffer)

    private fun applyInputDraftValue(key: String, rawValue: String): Boolean {
        val value = rawValue.trim()
        if (value.isEmpty()) return false
        val current = state.color
        when (key) {
            "hex" -> {
                val parsed = ColorTextCodec.parse(value)?.color ?: return false
                applyColor(parsed, notifyPreview = true, commit = false)
                return true
            }

            "r", "g", "b", "a" -> {
                val r = if (key == "r") value.toFloatOrNull() ?: return false else current.r * 255f
                val g = if (key == "g") value.toFloatOrNull() ?: return false else current.g * 255f
                val b = if (key == "b") value.toFloatOrNull() ?: return false else current.b * 255f
                val a = if (key == "a") value.toFloatOrNull() ?: return false else current.a * 100f
                val next =
                    RgbaColor(
                        r = (r / 255f).coerceIn(0f, 1f),
                        g = (g / 255f).coerceIn(0f, 1f),
                        b = (b / 255f).coerceIn(0f, 1f),
                        a = (a / 100f).coerceIn(0f, 1f),
                    )
                applyColor(next, notifyPreview = true, commit = false)
                return true
            }

            "h", "s", "l", "v" -> {
                val hue = if (key == "h") value.toFloatOrNull() ?: return false else hueDeg
                val currentHsl = ColorConversions.rgbToHsl(current, hueDeg)
                val currentHsv = ColorConversions.rgbToHsv(current, hueDeg)
                val sValue =
                    if (key == "s") {
                        value.toFloatOrNull() ?: return false
                    } else {
                        if (state.mode ==
                            ColorFormatMode.HSL
                        ) {
                            currentHsl.saturation * 100f
                        } else {
                            currentHsv.saturation * 100f
                        }
                    }
                val thirdValue =
                    when {
                        key == "l" || (key == "v" && state.mode == ColorFormatMode.HSB) ->
                            value.toFloatOrNull()
                                ?: return false
                        state.mode == ColorFormatMode.HSL -> currentHsl.lightness * 100f
                        else -> currentHsv.brightness * 100f
                    }
                val next =
                    if (state.mode == ColorFormatMode.HSL) {
                        ColorConversions.hslToRgb(
                            HslColor(
                                hueDeg = normalizeHue(hue),
                                saturation = (sValue / 100f).coerceIn(0f, 1f),
                                lightness = (thirdValue / 100f).coerceIn(0f, 1f),
                            ),
                            alpha = current.a,
                        )
                    } else {
                        ColorConversions.hsvToRgb(
                            HsvColor(
                                hueDeg = normalizeHue(hue),
                                saturation = (sValue / 100f).coerceIn(0f, 1f),
                                brightness = (thirdValue / 100f).coerceIn(0f, 1f),
                            ),
                            alpha = current.a,
                        )
                    }
                applyColor(next, notifyPreview = true, commit = false)
                return true
            }
        }
        return false
    }

    private fun inputValues(): Map<String, String> {
        val values = linkedMapOf<String, String>()
        when (state.mode) {
            ColorFormatMode.HEX -> {
                values["hex"] = ColorTextCodec.formatHex(state.color, includeAlpha = state.alphaEnabled)
            }

            ColorFormatMode.RGB -> {
                val rgbValues =
                    hashMapOf(
                        "r" to ((state.color.r * 255f).roundToInt().coerceIn(0, 255)).toString(),
                        "g" to ((state.color.g * 255f).roundToInt().coerceIn(0, 255)).toString(),
                        "b" to ((state.color.b * 255f).roundToInt().coerceIn(0, 255)).toString(),
                    )
                if (state.alphaEnabled) {
                    rgbValues["a"] = ((state.color.a * 100f).roundToInt().coerceIn(0, 100)).toString()
                }
                rgbInputOrder().forEach { key ->
                    rgbValues[key]?.let { values[key] = it }
                }
            }

            ColorFormatMode.HSL -> {
                val hsl = ColorConversions.rgbToHsl(state.color, hueDeg)
                values["h"] =
                    hsl.hueDeg
                        .roundToInt()
                        .toString()
                values["s"] = (hsl.saturation * 100f).roundToInt().toString()
                values["l"] = (hsl.lightness * 100f).roundToInt().toString()
                if (state.alphaEnabled) {
                    values["a"] = (state.color.a * 100f).roundToInt().toString()
                }
            }

            ColorFormatMode.HSB -> {
                val hsb = ColorConversions.rgbToHsv(state.color, hueDeg)
                values["h"] =
                    hsb.hueDeg
                        .roundToInt()
                        .toString()
                values["s"] = (hsb.saturation * 100f).roundToInt().toString()
                values["v"] = (hsb.brightness * 100f).roundToInt().toString()
                if (state.alphaEnabled) {
                    values["a"] = (state.color.a * 100f).roundToInt().toString()
                }
            }
        }
        return values
    }

    private fun inputDefinitions(): List<InputDefinition> {
        return when (state.mode) {
            ColorFormatMode.HEX -> listOf(InputDefinition("hex", "HEX"))
            ColorFormatMode.RGB -> {
                val defs = ArrayList<InputDefinition>()
                rgbInputOrder().forEach { key ->
                    defs +=
                        when (key) {
                            "r" -> InputDefinition("r", "R")
                            "g" -> InputDefinition("g", "G")
                            "b" -> InputDefinition("b", "B")
                            "a" -> InputDefinition("a", "A%")
                            else -> return@forEach
                        }
                }
                defs
            }

            ColorFormatMode.HSL -> {
                val defs = ArrayList<InputDefinition>()
                defs += InputDefinition("h", "H")
                defs += InputDefinition("s", "S%")
                defs += InputDefinition("l", "L%")
                if (state.alphaEnabled) defs += InputDefinition("a", "A%")
                defs
            }

            ColorFormatMode.HSB -> {
                val defs = ArrayList<InputDefinition>()
                defs += InputDefinition("h", "H")
                defs += InputDefinition("s", "S%")
                defs += InputDefinition("v", "B%")
                if (state.alphaEnabled) defs += InputDefinition("a", "A%")
                defs
            }
        }
    }

    private fun updateFromField(
        globalX: Int,
        globalY: Int,
        rect: Rect,
        commit: Boolean,
    ) {
        val px =
            (
                (globalX - rect.x).toFloat() /
                    rect.width
                        .coerceAtLeast(1)
                        .toFloat()
            ).coerceIn(0f, 1f)
        val py =
            (
                (globalY - rect.y).toFloat() /
                    rect.height
                        .coerceAtLeast(1)
                        .toFloat()
            ).coerceIn(0f, 1f)
        val saturation = px
        val brightness = 1f - py
        val next =
            ColorConversions.hsvToRgb(
                hsv = HsvColor(hueDeg = hueDeg, saturation = saturation, brightness = brightness),
                alpha = state.color.a,
            )
        applyColor(next, notifyPreview = true, commit = commit)
    }

    private fun updateFromHue(globalX: Int, rect: Rect, commit: Boolean) {
        val progress =
            (
                (globalX - rect.x).toFloat() /
                    rect.width
                        .coerceAtLeast(1)
                        .toFloat()
            ).coerceIn(0f, 1f)
        hueDeg = progress * 360f
        val hsv = ColorConversions.rgbToHsv(state.color, hueDeg)
        val next =
            ColorConversions.hsvToRgb(
                hsv.copy(hueDeg = hueDeg),
                alpha = state.color.a,
            )
        applyColor(next, notifyPreview = true, commit = commit)
    }

    private fun updateFromAlpha(globalX: Int, rect: Rect, commit: Boolean) {
        if (!state.alphaEnabled) return
        val progress =
            (
                (globalX - rect.x).toFloat() /
                    rect.width
                        .coerceAtLeast(1)
                        .toFloat()
            ).coerceIn(0f, 1f)
        applyColor(state.color.copy(a = progress), notifyPreview = true, commit = commit)
    }

    private fun sampleEyedropper(x: Int, y: Int, commit: Boolean) {
        val argb = screenSampler?.sampleColorAt(x, y) ?: return
        val sampled = RgbaColor.fromArgbInt(argb)
        val color =
            if (state.alphaEnabled) {
                sampled.copy(a = state.color.a)
            } else {
                sampled.copy(a = 1f)
            }
        applyColor(color, notifyPreview = true, commit = commit)
    }

    private fun normalizedEyedropperGridSize(): Int {
        val raw = style.eyedropperGridSize.coerceAtLeast(3)
        return if ((raw and 1) == 0) raw + 1 else raw
    }

    private fun clampOverlayRect(rect: Rect, viewportWidth: Int, viewportHeight: Int): Rect {
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
            height = rect.height,
        )
    }

    private fun applyColor(next: RgbaColor, notifyPreview: Boolean, commit: Boolean) {
        val normalized = if (state.alphaEnabled) next.normalized() else next.normalized().copy(a = 1f)
        val unchanged = normalized.toArgbInt() == state.color.toArgbInt()
        state = state.copy(color = normalized)
        hueDeg = ColorConversions.rgbToHsv(normalized, hueDeg).hueDeg
        if (notifyPreview && !unchanged) {
            onPreview?.invoke(normalized)
            onChange?.invoke(normalized)
        }
        if (commit) {
            commitCurrentColor()
        }
    }

    fun commitCurrentColor() {
        state = state.copy(previous = state.color)
        recentHistory.add(state.color)
        onCommit?.invoke(state.color)
    }

    private fun drawModeSelect(layout: ColorPickerLayout, out: MutableList<RenderCommand>) {
        val rect = layout.modeSelectRect
        val hovered = rect.contains(hoverX, hoverY)
        val fill = if (hovered || modeDropdownOpen) style.buttonHoverColor else style.buttonBackgroundColor
        out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, rect.height, fill)
        drawBorder(out, rect, if (modeDropdownOpen) style.inputActiveBorderColor else style.inputBorderColor)
        out +=
            RenderCommand.DrawText(
                text = state.mode.name,
                x = rect.x + 6,
                y = rect.y + 2,
                color = style.textColor,
                fontSize = style.fontSize,
            )
        out +=
            RenderCommand.DrawText(
                text = if (modeDropdownOpen) "^" else "v",
                x = rect.x + rect.width - 12,
                y = rect.y + 2,
                color = style.textColor,
                fontSize = style.fontSize,
            )
    }

    private fun drawRgbOrderSwitch(layout: ColorPickerLayout, out: MutableList<RenderCommand>) {
        val rgbaRect = layout.rgbaOrderRect ?: return
        val argbRect = layout.argbOrderRect ?: return
        drawOrderLabel(
            rect = rgbaRect,
            label = "RGBA",
            active = state.rgbOrder == RgbChannelOrder.RGBA,
            hovered = rgbaRect.contains(hoverX, hoverY),
            out = out,
        )
        drawOrderLabel(
            rect = argbRect,
            label = "ARGB",
            active = state.rgbOrder == RgbChannelOrder.ARGB,
            hovered = argbRect.contains(hoverX, hoverY),
            out = out,
        )
    }

    private fun drawOrderLabel(
        rect: Rect,
        label: String,
        active: Boolean,
        hovered: Boolean,
        out: MutableList<RenderCommand>,
    ) {
        val fill =
            when {
                active -> style.buttonActiveColor
                hovered -> style.buttonHoverColor
                else -> style.buttonBackgroundColor
            }
        out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, rect.height, fill)
        drawBorder(out, rect, if (active) style.inputActiveBorderColor else style.inputBorderColor)
        out +=
            RenderCommand.DrawText(
                text = label,
                x = rect.x + 6,
                y = rect.y + 2,
                color = style.textColor,
                fontSize = style.fontSize,
            )
    }

    private fun drawModeOptions(layout: ColorPickerLayout, out: MutableList<RenderCommand>) {
        val popupRect = layout.modeOptionsRect ?: return
        out +=
            RenderCommand.DrawRect(
                popupRect.x,
                popupRect.y,
                popupRect.width,
                popupRect.height,
                style.inputBackgroundColor,
            )
        drawBorder(out, popupRect, style.inputActiveBorderColor)
        layout.modeOptions.forEach { option ->
            val hovered = option.rect.contains(hoverX, hoverY)
            val selected = option.mode == state.mode
            if (hovered || selected) {
                out +=
                    RenderCommand.DrawRect(
                        option.rect.x,
                        option.rect.y,
                        option.rect.width,
                        option.rect.height,
                        if (selected) style.buttonActiveColor else style.buttonHoverColor,
                    )
            }
            out +=
                RenderCommand.DrawText(
                    text = option.mode.name,
                    x = option.rect.x + 6,
                    y = option.rect.y + 2,
                    color = style.textColor,
                    fontSize = style.fontSize,
                )
        }
    }

    private fun drawButton(
        rect: Rect,
        label: String,
        hovered: Boolean,
        out: MutableList<RenderCommand>,
    ) {
        out +=
            RenderCommand.DrawRect(
                rect.x,
                rect.y,
                rect.width,
                rect.height,
                if (hovered) style.buttonHoverColor else style.buttonBackgroundColor,
            )
        drawBorder(out, rect, style.inputBorderColor)
        out +=
            RenderCommand.DrawText(
                text = label,
                x = rect.x + 4,
                y = rect.y + 2,
                color = style.textColor,
                fontSize = style.fontSize,
            )
    }

    private fun drawSwatch(rect: Rect, color: RgbaColor, out: MutableList<RenderCommand>) {
        drawChecker(rect, out)
        out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, rect.height, color.toArgbInt())
        drawBorder(out, rect, style.inputBorderColor)
    }

    private fun drawColorField(rect: Rect, out: MutableList<RenderCommand>) {
        out +=
            RenderCommand.DrawColorField(
                x = rect.x,
                y = rect.y,
                width = rect.width,
                height = rect.height,
                hueDeg = hueDeg,
            )
        drawBorder(out, rect, style.inputBorderColor)
    }

    private fun drawFieldThumb(rect: Rect, out: MutableList<RenderCommand>) {
        val hsv = ColorConversions.rgbToHsv(state.color, hueDeg)
        val x = rect.x + (hsv.saturation * rect.width.toFloat()).roundToInt().coerceIn(0, rect.width - 1)
        val y = rect.y + ((1f - hsv.brightness) * rect.height.toFloat()).roundToInt().coerceIn(0, rect.height - 1)
        out += RenderCommand.DrawRect(x - 3, y - 3, 7, 7, style.thumbShadowColor)
        drawBorder(out, Rect(x - 2, y - 2, 5, 5), style.thumbOutlineColor)
    }

    private fun drawHueSlider(rect: Rect, out: MutableList<RenderCommand>) {
        out +=
            RenderCommand.DrawHueBar(
                x = rect.x,
                y = rect.y,
                width = rect.width,
                height = rect.height,
            )
        drawBorder(out, rect, style.inputBorderColor)
    }

    private fun drawHueThumb(rect: Rect, out: MutableList<RenderCommand>) {
        val x = rect.x + ((hueDeg / 360f) * rect.width.toFloat()).roundToInt().coerceIn(0, rect.width - 1)
        out += RenderCommand.DrawRect(x - 1, rect.y - 1, 3, rect.height + 2, style.thumbOutlineColor)
    }

    private fun drawAlphaSlider(rect: Rect, out: MutableList<RenderCommand>) {
        drawChecker(rect, out)
        val rgb = state.color.copy(a = 1f)
        out +=
            RenderCommand.DrawAlphaBar(
                x = rect.x,
                y = rect.y,
                width = rect.width,
                height = rect.height,
                rgbColor = rgb.toArgbInt(),
            )
        drawBorder(out, rect, style.inputBorderColor)
    }

    private fun drawAlphaThumb(rect: Rect, out: MutableList<RenderCommand>) {
        val x = rect.x + (state.color.a * rect.width.toFloat()).roundToInt().coerceIn(0, rect.width - 1)
        out += RenderCommand.DrawRect(x - 1, rect.y - 1, 3, rect.height + 2, style.thumbOutlineColor)
    }

    private fun drawEyedropperGridOverlay(
        out: MutableList<RenderCommand>,
        rect: Rect,
        columns: Int,
        rows: Int,
        cellSize: Int,
        color: Int,
    ) {
        if (rect.width <= 1 || rect.height <= 1) return
        if (cellSize <= 0) return
        val safeColumns = columns.coerceAtLeast(1)
        val safeRows = rows.coerceAtLeast(1)
        for (column in 1 until safeColumns) {
            val lineX = rect.x + column * cellSize
            if (lineX <= rect.x || lineX >= rect.x + rect.width) continue
            out += RenderCommand.DrawRect(lineX, rect.y, 1, rect.height, color)
        }
        for (row in 1 until safeRows) {
            val lineY = rect.y + row * cellSize
            if (lineY <= rect.y || lineY >= rect.y + rect.height) continue
            out += RenderCommand.DrawRect(rect.x, lineY, rect.width, 1, color)
        }
    }

    private fun drawChecker(rect: Rect, out: MutableList<RenderCommand>) {
        if (rect.width <= 0 || rect.height <= 0) return
        out +=
            RenderCommand.DrawCheckerboard(
                x = rect.x,
                y = rect.y,
                width = rect.width,
                height = rect.height,
                cellSize = 4,
                lightColor = style.checkerLightColor,
                darkColor = style.checkerDarkColor,
            )
    }

    private fun drawBorder(out: MutableList<RenderCommand>, rect: Rect, color: Int) {
        if (rect.width <= 0 || rect.height <= 0) return
        out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, 1, color)
        out += RenderCommand.DrawRect(rect.x, rect.y + rect.height - 1, rect.width, 1, color)
        out += RenderCommand.DrawRect(rect.x, rect.y, 1, rect.height, color)
        out += RenderCommand.DrawRect(rect.x + rect.width - 1, rect.y, 1, rect.height, color)
    }

    private fun caretVisible(nowMs: Long): Boolean = ((nowMs / 500L) % 2L) == 0L

    private fun truncate(value: String, maxChars: Int): String {
        if (value.length <= maxChars) return value
        if (maxChars <= 3) return value.take(maxChars)
        return value.take(maxChars - 3) + "..."
    }

    private fun normalizeHue(raw: Float): Float {
        var hue = raw % 360f
        if (hue < 0f) hue += 360f
        return hue
    }

    private fun rgbInputOrder(): List<String> {
        if (!state.alphaEnabled) return listOf("r", "g", "b")
        return when (state.rgbOrder) {
            RgbChannelOrder.RGBA -> listOf("r", "g", "b", "a")
            RgbChannelOrder.ARGB -> listOf("a", "r", "g", "b")
        }
    }

    private fun requestDomInputFocusResync() {
        val key = domFocusedInputKey ?: domLastFocusedInputKey ?: return
        domPendingFocusResyncKey = key
        domInputFocusResyncRequested = true
    }

    private data class InputDefinition(
        val key: String,
        val label: String,
    )
}
