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
    val inputRect: Rect
)

data class ColorPickerModeOptionSlot(
    val mode: ColorFormatMode,
    val rect: Rect
)

data class ColorPickerLayout(
    val bounds: Rect,
    val modeSelectRect: Rect,
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
    val recentRects: List<Rect>
)

class ColorPickerController(
    initial: ColorPickerState,
    private val style: ColorPickerStyle = ColorPickerStyle(),
    private val recentHistory: ColorRecentHistory = ColorRecentHistory(),
    private val screenSampler: ScreenColorSampler? = ScreenColorSampler { x, y ->
        val sampled = ScreenColorSamplerBridge.sampleColorAt(x, y) ?: return@ScreenColorSampler null
        sampled.toArgbInt()
    }
) {
    private enum class DragTarget {
        None,
        Field,
        Hue,
        Alpha
    }

    private var state: ColorPickerState = initial.withColor(initial.color)
    private var hueDeg: Float = ColorConversions.rgbToHsv(state.color).hueDeg
    private var dragTarget: DragTarget = DragTarget.None
    private var hoverX: Int = Int.MIN_VALUE
    private var hoverY: Int = Int.MIN_VALUE
    private var activeInputKey: String? = null
    private var activeInputBuffer: String = ""
    private var modeDropdownOpen: Boolean = false
    private var eyedropperActive: Boolean = false
    private var eyedropperBaseColor: RgbaColor = state.color
    private var eyedropperLastSampleX: Int = Int.MIN_VALUE
    private var eyedropperLastSampleY: Int = Int.MIN_VALUE
    private var eyedropperGridColors: IntArray = IntArray(style.eyedropperGridSize * style.eyedropperGridSize)
    private var eyedropperGridValid: Boolean = false
    private val eyedropperOverlayDrag: FloatingPaneDragModel = FloatingPaneDragModel()
    private var eyedropperOverlayRect: Rect? = null

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
        dragTarget = DragTarget.None
        modeDropdownOpen = false
        eyedropperGridValid = false
        eyedropperOverlayDrag.end()
        eyedropperOverlayRect = null
        clearInputEdit()
    }

    fun style(): ColorPickerStyle = style

    fun isEyedropperActive(): Boolean = eyedropperActive

    fun beginEyedropper() {
        if (!state.alphaEnabled) {
            eyedropperBaseColor = state.color.copy(a = 1f)
        } else {
            eyedropperBaseColor = state.color
        }
        eyedropperActive = true
        eyedropperGridValid = false
        eyedropperOverlayDrag.end()
        eyedropperOverlayRect = null
        modeDropdownOpen = false
        clearInputEdit()
    }

    fun cancelEyedropper() {
        if (!eyedropperActive) return
        eyedropperActive = false
        eyedropperGridValid = false
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

        val modeSelectWidth = minOf(style.modeSelectWidth.coerceAtLeast(84), innerW).coerceAtLeast(1)
        val modeSelectRect = Rect(
            x = innerX,
            y = innerY,
            width = modeSelectWidth,
            height = style.modeSelectHeight
        )
        val modeOptions = ArrayList<ColorPickerModeOptionSlot>(ColorFormatMode.values().size)
        val modeOptionsRect = if (modeDropdownOpen) {
            val optionHeight = style.modeOptionHeight
            val popupWidth = maxOf(modeSelectRect.width, style.modeSelectMinWidth)
            val popupHeight = optionHeight * ColorFormatMode.values().size + 2
            val minX = innerX
            val maxX = (innerX + innerW - popupWidth).coerceAtLeast(innerX)
            val popupX = if (minX <= maxX) modeSelectRect.x.coerceIn(minX, maxX) else minX
            val preferredBelowY = modeSelectRect.y + modeSelectRect.height + 2
            val minY = innerY
            val maxY = (bounds.y + bounds.height - padding - popupHeight).coerceAtLeast(minY)
            val popupY = if (preferredBelowY <= maxY) {
                preferredBelowY
            } else {
                if (minY <= maxY) (modeSelectRect.y - popupHeight - 2).coerceIn(minY, maxY) else minY
            }
            ColorFormatMode.values().forEachIndexed { index, mode ->
                modeOptions += ColorPickerModeOptionSlot(
                    mode = mode,
                    rect = Rect(
                        x = popupX + 1,
                        y = popupY + 1 + index * optionHeight,
                        width = popupWidth - 2,
                        height = optionHeight
                    )
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
        val alphaRect = if (state.alphaEnabled) {
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
            val localLabelWidth = if (def.key == "hex") {
                labelWidth + 8
            } else {
                labelWidth
            }
            val inputX = slotX + localLabelWidth + style.inputLabelGap
            val inputW = (inputWidth - localLabelWidth - style.inputLabelGap).coerceAtLeast(20)
            val labelRect = Rect(
                x = slotX,
                y = y,
                width = localLabelWidth,
                height = style.inputHeight
            )
            val inputRect = Rect(
                x = inputX,
                y = y,
                width = inputW,
                height = style.inputHeight
            )
            inputs += ColorPickerInputSlot(
                key = def.key,
                label = def.label,
                labelRect = labelRect,
                inputRect = inputRect
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
            recentRects = recentRects
        )
    }

    fun preferredHeight(alphaEnabled: Boolean = state.alphaEnabled): Int {
        val rowGap = style.rowGap
        val core = style.padding * 2 +
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
        nowMs: Long = System.currentTimeMillis()
    ) {
        val bounds = layout.bounds
        out += RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, style.panelBackgroundColor)
        drawBorder(out, bounds, style.panelBorderColor)

        drawModeSelect(layout, out)

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
            val border = when {
                active -> style.inputActiveBorderColor
                hovered -> style.buttonHoverColor
                else -> style.inputBorderColor
            }
            out += RenderCommand.DrawText(
                text = slot.label,
                x = slot.labelRect.x + 2,
                y = slot.labelRect.y + 2,
                color = style.mutedTextColor,
                fontSize = style.fontSize
            )
            out += RenderCommand.DrawRect(
                slot.inputRect.x,
                slot.inputRect.y,
                slot.inputRect.width,
                slot.inputRect.height,
                style.inputBackgroundColor
            )
            drawBorder(out, slot.inputRect, border)
            val value = if (active) activeInputBuffer + if (caretVisible(nowMs)) "|" else "" else inputValues[slot.key].orEmpty()
            out += RenderCommand.DrawText(
                text = truncate(value, 12),
                x = slot.inputRect.x + 4,
                y = slot.inputRect.y + 2,
                color = style.textColor,
                fontSize = style.fontSize
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

    fun appendEyedropperOverlay(
        viewportWidth: Int,
        viewportHeight: Int,
        out: MutableList<RenderCommand>
    ) {
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
        val desiredRect = clampOverlayRect(
            rect = Rect(preferredX, preferredY, panelWidth, panelHeight),
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        )
        val currentRect = eyedropperOverlayRect
        if (currentRect == null || currentRect.width != panelWidth || currentRect.height != panelHeight) {
            eyedropperOverlayRect = desiredRect
            eyedropperOverlayDrag.begin(mouseX = hoverX, mouseY = hoverY, rect = desiredRect)
        }
        val nextRect = eyedropperOverlayDrag.update(
            mouseX = hoverX,
            mouseY = hoverY,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            clamp = ::clampOverlayRect
        )
        eyedropperOverlayRect = nextRect
        val panelX = nextRect.x
        val panelY = nextRect.y

        out += RenderCommand.DrawRect(panelX + 2, panelY + 2, panelWidth, panelHeight, style.panelShadowColor)
        out += RenderCommand.DrawRect(panelX, panelY, panelWidth, panelHeight, style.eyedropperOverlayBackgroundColor)
        drawBorder(out, Rect(panelX, panelY, panelWidth, panelHeight), style.eyedropperOverlayBorderColor)

        val magnifierX = panelX + 4
        val magnifierY = panelY + 4
        var row = 0
        while (row < gridSize) {
            var col = 0
            while (col < gridSize) {
                val color = if (eyedropperGridValid) {
                    eyedropperGridColors[row * gridSize + col]
                } else {
                    state.color.toArgbInt()
                }
                out += RenderCommand.DrawRect(
                    magnifierX + col * cell,
                    magnifierY + row * cell,
                    cell,
                    cell,
                    color
                )
                col++
            }
            row++
        }
        drawBorder(out, Rect(magnifierX, magnifierY, magnifierContentSize, magnifierContentSize), style.inputBorderColor)

        val center = gridSize / 2
        val centerRect = Rect(
            x = magnifierX + center * cell,
            y = magnifierY + center * cell,
            width = cell,
            height = cell
        )
        drawBorder(out, centerRect, style.eyedropperCenterBorderColor)

        val tooltipY = magnifierY + magnifierContentSize + 6
        val swatchSize = tooltipHeight - 10
        val swatchRect = Rect(panelX + 6, tooltipY + 5, swatchSize, swatchSize)
        drawSwatch(swatchRect, state.color, out)

        val modeText = "Mode: ${state.mode.name}"
        val valueText = ColorTextCodec.format(state.color, state.mode, state.alphaEnabled)
        out += RenderCommand.DrawText(
            text = modeText,
            x = swatchRect.x + swatchRect.width + 8,
            y = tooltipY + 6,
            color = style.mutedTextColor,
            fontSize = style.fontSize
        )
        out += RenderCommand.DrawText(
            text = valueText,
            x = swatchRect.x + swatchRect.width + 8,
            y = tooltipY + 6 + style.fontSize,
            color = style.textColor,
            fontSize = style.fontSize
        )
    }

    fun handleMouseMove(globalX: Int, globalY: Int, layout: ColorPickerLayout): Boolean {
        hoverX = globalX
        hoverY = globalY
        if (eyedropperActive) {
            return true
        }
        when (dragTarget) {
            DragTarget.Field -> {
                updateFromField(globalX, globalY, layout.colorFieldRect, commit = false)
                return true
            }

            DragTarget.Hue -> {
                updateFromHue(globalX, layout.hueRect, commit = false)
                return true
            }

            DragTarget.Alpha -> {
                val alphaRect = layout.alphaRect
                if (alphaRect != null) {
                    updateFromAlpha(globalX, alphaRect, commit = false)
                }
                return true
            }

            DragTarget.None -> {
                if (layout.modeOptionsRect?.contains(globalX, globalY) == true) return true
                return layout.bounds.contains(globalX, globalY)
            }
        }
    }

    fun handleMouseDown(globalX: Int, globalY: Int, button: MouseButton, layout: ColorPickerLayout): Boolean {
        hoverX = globalX
        hoverY = globalY
        if (eyedropperActive) {
            return when (button) {
                MouseButton.LEFT -> {
                    commitCurrentColor()
                    eyedropperActive = false
                    true
                }

                MouseButton.RIGHT -> {
                    cancelEyedropper()
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
            return layout.bounds.contains(globalX, globalY) || layout.modeOptionsRect?.contains(globalX, globalY) == true
        }

        val modeOptionHit = if (modeDropdownOpen) {
            layout.modeOptions.firstOrNull { it.rect.contains(globalX, globalY) }
        } else {
            null
        }
        if (modeOptionHit != null) {
            state = state.copy(mode = modeOptionHit.mode)
            modeDropdownOpen = false
            clearInputEdit()
            return true
        }
        if (layout.modeSelectRect.contains(globalX, globalY)) {
            modeDropdownOpen = !modeDropdownOpen
            clearInputEdit()
            return true
        }
        if (!layout.bounds.contains(globalX, globalY)) {
            modeDropdownOpen = false
            clearInputEdit()
            return false
        }
        modeDropdownOpen = false

        if (layout.colorFieldRect.contains(globalX, globalY)) {
            dragTarget = DragTarget.Field
            clearInputEdit()
            updateFromField(globalX, globalY, layout.colorFieldRect, commit = false)
            return true
        }
        if (layout.hueRect.contains(globalX, globalY)) {
            dragTarget = DragTarget.Hue
            clearInputEdit()
            updateFromHue(globalX, layout.hueRect, commit = false)
            return true
        }
        if (layout.alphaRect?.contains(globalX, globalY) == true) {
            dragTarget = DragTarget.Alpha
            clearInputEdit()
            updateFromAlpha(globalX, layout.alphaRect, commit = false)
            return true
        }

        if (layout.previousSwatchRect.contains(globalX, globalY)) {
            applyColor(state.previous, notifyPreview = true, commit = false)
            return true
        }
        if (layout.currentSwatchRect.contains(globalX, globalY)) {
            commitCurrentColor()
            return true
        }
        if (layout.copyRect.contains(globalX, globalY)) {
            ColorClipboardSupport.copy(state.color, state.mode, state.alphaEnabled)
            return true
        }
        if (layout.pasteRect.contains(globalX, globalY)) {
            val parsed = ColorClipboardSupport.paste()
            if (parsed != null) {
                val next = if (state.alphaEnabled) parsed.color else parsed.color.copy(a = 1f)
                applyColor(next, notifyPreview = true, commit = false)
                state = state.copy(mode = parsed.detectedMode)
            }
            return true
        }
        if (layout.pipetteRect.contains(globalX, globalY)) {
            beginEyedropper()
            return true
        }

        val inputHit = layout.inputSlots.firstOrNull { it.inputRect.contains(globalX, globalY) }
        if (inputHit != null) {
            activeInputKey = inputHit.key
            activeInputBuffer = inputValues()[inputHit.key].orEmpty()
            return true
        }
        clearInputEdit()

        val recentIndex = layout.recentRects.indexOfFirst { it.contains(globalX, globalY) }
        if (recentIndex >= 0) {
            val color = recentHistory.snapshot().getOrNull(recentIndex)
            if (color != null) {
                applyColor(color, notifyPreview = true, commit = false)
            }
            return true
        }

        return true
    }

    fun handleMouseUp(globalX: Int, globalY: Int, button: MouseButton): Boolean {
        hoverX = globalX
        hoverY = globalY
        if (button != MouseButton.LEFT) return eyedropperActive
        val dragged = dragTarget != DragTarget.None
        dragTarget = DragTarget.None
        if (dragged) {
            commitCurrentColor()
            return true
        }
        return eyedropperActive
    }

    fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean {
        if (eyedropperActive && keyCode == KeyCodes.ESCAPE) {
            cancelEyedropper()
            return true
        }
        if (KeyModifiers.shortcutDown && keyCode == KeyCodes.C) {
            ColorClipboardSupport.copy(state.color, state.mode, state.alphaEnabled)
            return true
        }
        if (KeyModifiers.shortcutDown && keyCode == KeyCodes.V) {
            val parsed = ColorClipboardSupport.paste() ?: return true
            applyColor(parsed.color, notifyPreview = true, commit = false)
            state = state.copy(mode = parsed.detectedMode)
            return true
        }
        val key = activeInputKey ?: run {
            if (keyCode == KeyCodes.ESCAPE) {
                if (modeDropdownOpen) {
                    modeDropdownOpen = false
                    return true
                }
            }
            return false
        }
        when (keyCode) {
            KeyCodes.ESCAPE -> {
                clearInputEdit()
                return true
            }

            KeyCodes.ENTER -> {
                commitInputEdit(key)
                return true
            }

            KeyCodes.BACKSPACE -> {
                if (activeInputBuffer.isNotEmpty()) {
                    activeInputBuffer = activeInputBuffer.dropLast(1)
                    applyInputDraft(key)
                }
                return true
            }

            KeyCodes.DELETE -> {
                activeInputBuffer = ""
                return true
            }
        }
        if (keyChar >= ' ' && !Character.isISOControl(keyChar)) {
            activeInputBuffer += keyChar
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
        activeInputKey = null
        activeInputBuffer = ""
    }

    private fun applyInputDraft(key: String): Boolean {
        val value = activeInputBuffer.trim()
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
                val next = RgbaColor(
                    r = (r / 255f).coerceIn(0f, 1f),
                    g = (g / 255f).coerceIn(0f, 1f),
                    b = (b / 255f).coerceIn(0f, 1f),
                    a = (a / 100f).coerceIn(0f, 1f)
                )
                applyColor(next, notifyPreview = true, commit = false)
                return true
            }

            "h", "s", "l", "v" -> {
                val hue = if (key == "h") value.toFloatOrNull() ?: return false else hueDeg
                val currentHsl = ColorConversions.rgbToHsl(current, hueDeg)
                val currentHsv = ColorConversions.rgbToHsv(current, hueDeg)
                val sValue = if (key == "s") value.toFloatOrNull() ?: return false else {
                    if (state.mode == ColorFormatMode.HSL) currentHsl.saturation * 100f else currentHsv.saturation * 100f
                }
                val thirdValue = when {
                    key == "l" || (key == "v" && state.mode == ColorFormatMode.HSB) -> value.toFloatOrNull() ?: return false
                    state.mode == ColorFormatMode.HSL -> currentHsl.lightness * 100f
                    else -> currentHsv.brightness * 100f
                }
                val next = if (state.mode == ColorFormatMode.HSL) {
                    ColorConversions.hslToRgb(
                        HslColor(
                            hueDeg = normalizeHue(hue),
                            saturation = (sValue / 100f).coerceIn(0f, 1f),
                            lightness = (thirdValue / 100f).coerceIn(0f, 1f)
                        ),
                        alpha = current.a
                    )
                } else {
                    ColorConversions.hsvToRgb(
                        HsvColor(
                            hueDeg = normalizeHue(hue),
                            saturation = (sValue / 100f).coerceIn(0f, 1f),
                            brightness = (thirdValue / 100f).coerceIn(0f, 1f)
                        ),
                        alpha = current.a
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
                values["r"] = ((state.color.r * 255f).roundToInt().coerceIn(0, 255)).toString()
                values["g"] = ((state.color.g * 255f).roundToInt().coerceIn(0, 255)).toString()
                values["b"] = ((state.color.b * 255f).roundToInt().coerceIn(0, 255)).toString()
                if (state.alphaEnabled) {
                    values["a"] = ((state.color.a * 100f).roundToInt().coerceIn(0, 100)).toString()
                }
            }

            ColorFormatMode.HSL -> {
                val hsl = ColorConversions.rgbToHsl(state.color, hueDeg)
                values["h"] = hsl.hueDeg.roundToInt().toString()
                values["s"] = (hsl.saturation * 100f).roundToInt().toString()
                values["l"] = (hsl.lightness * 100f).roundToInt().toString()
                if (state.alphaEnabled) {
                    values["a"] = (state.color.a * 100f).roundToInt().toString()
                }
            }

            ColorFormatMode.HSB -> {
                val hsb = ColorConversions.rgbToHsv(state.color, hueDeg)
                values["h"] = hsb.hueDeg.roundToInt().toString()
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
                defs += InputDefinition("r", "R")
                defs += InputDefinition("g", "G")
                defs += InputDefinition("b", "B")
                if (state.alphaEnabled) defs += InputDefinition("a", "A%")
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

    private fun updateFromField(globalX: Int, globalY: Int, rect: Rect, commit: Boolean) {
        val px = ((globalX - rect.x).toFloat() / rect.width.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        val py = ((globalY - rect.y).toFloat() / rect.height.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        val saturation = px
        val brightness = 1f - py
        val next = ColorConversions.hsvToRgb(
            hsv = HsvColor(hueDeg = hueDeg, saturation = saturation, brightness = brightness),
            alpha = state.color.a
        )
        applyColor(next, notifyPreview = true, commit = commit)
    }

    private fun updateFromHue(globalX: Int, rect: Rect, commit: Boolean) {
        val progress = ((globalX - rect.x).toFloat() / rect.width.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        hueDeg = progress * 360f
        val hsv = ColorConversions.rgbToHsv(state.color, hueDeg)
        val next = ColorConversions.hsvToRgb(
            hsv.copy(hueDeg = hueDeg),
            alpha = state.color.a
        )
        applyColor(next, notifyPreview = true, commit = commit)
    }

    private fun updateFromAlpha(globalX: Int, rect: Rect, commit: Boolean) {
        if (!state.alphaEnabled) return
        val progress = ((globalX - rect.x).toFloat() / rect.width.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        applyColor(state.color.copy(a = progress), notifyPreview = true, commit = commit)
    }

    private fun sampleEyedropper(x: Int, y: Int, commit: Boolean) {
        ensureEyedropperGridSample(x, y)
        val gridSize = normalizedEyedropperGridSize()
        val centerIndex = (gridSize / 2) * gridSize + (gridSize / 2)
        val argb = if (eyedropperGridValid && centerIndex in eyedropperGridColors.indices) {
            eyedropperGridColors[centerIndex]
        } else {
            screenSampler?.sampleColorAt(x, y) ?: return
        }
        val sampled = RgbaColor.fromArgbInt(argb)
        val color = if (state.alphaEnabled) {
            sampled.copy(a = state.color.a)
        } else {
            sampled.copy(a = 1f)
        }
        applyColor(color, notifyPreview = true, commit = commit)
    }

    private fun ensureEyedropperGridSample(centerX: Int, centerY: Int) {
        val gridSize = normalizedEyedropperGridSize()
        val required = gridSize * gridSize
        if (eyedropperGridColors.size != required) {
            eyedropperGridColors = IntArray(required)
        }
        if (eyedropperGridValid && eyedropperLastSampleX == centerX && eyedropperLastSampleY == centerY) {
            return
        }
        val half = gridSize / 2
        val startX = centerX - half
        val startY = centerY - half
        eyedropperGridValid = screenSampler?.sampleArea(
            x = startX,
            y = startY,
            width = gridSize,
            height = gridSize,
            outArgb = eyedropperGridColors
        ) == true
        eyedropperLastSampleX = centerX
        eyedropperLastSampleY = centerY
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
            height = rect.height
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
        out += RenderCommand.DrawText(
            text = state.mode.name,
            x = rect.x + 6,
            y = rect.y + 2,
            color = style.textColor,
            fontSize = style.fontSize
        )
        out += RenderCommand.DrawText(
            text = if (modeDropdownOpen) "^" else "v",
            x = rect.x + rect.width - 12,
            y = rect.y + 2,
            color = style.textColor,
            fontSize = style.fontSize
        )
    }

    private fun drawModeOptions(layout: ColorPickerLayout, out: MutableList<RenderCommand>) {
        val popupRect = layout.modeOptionsRect ?: return
        out += RenderCommand.DrawRect(popupRect.x, popupRect.y, popupRect.width, popupRect.height, style.inputBackgroundColor)
        drawBorder(out, popupRect, style.inputActiveBorderColor)
        layout.modeOptions.forEach { option ->
            val hovered = option.rect.contains(hoverX, hoverY)
            val selected = option.mode == state.mode
            if (hovered || selected) {
                out += RenderCommand.DrawRect(
                    option.rect.x,
                    option.rect.y,
                    option.rect.width,
                    option.rect.height,
                    if (selected) style.buttonActiveColor else style.buttonHoverColor
                )
            }
            out += RenderCommand.DrawText(
                text = option.mode.name,
                x = option.rect.x + 6,
                y = option.rect.y + 2,
                color = style.textColor,
                fontSize = style.fontSize
            )
        }
    }

    private fun drawButton(rect: Rect, label: String, hovered: Boolean, out: MutableList<RenderCommand>) {
        out += RenderCommand.DrawRect(
            rect.x,
            rect.y,
            rect.width,
            rect.height,
            if (hovered) style.buttonHoverColor else style.buttonBackgroundColor
        )
        drawBorder(out, rect, style.inputBorderColor)
        out += RenderCommand.DrawText(
            text = label,
            x = rect.x + 4,
            y = rect.y + 2,
            color = style.textColor,
            fontSize = style.fontSize
        )
    }

    private fun drawSwatch(rect: Rect, color: RgbaColor, out: MutableList<RenderCommand>) {
        drawChecker(rect, out)
        out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, rect.height, color.toArgbInt())
        drawBorder(out, rect, style.inputBorderColor)
    }

    private fun drawColorField(rect: Rect, out: MutableList<RenderCommand>) {
        val stepX = 2
        val stepY = 2
        var x = 0
        while (x < rect.width) {
            val sat = x.toFloat() / rect.width.coerceAtLeast(1).toFloat()
            val color = ColorConversions.hsvToRgb(
                HsvColor(hueDeg = hueDeg, saturation = sat, brightness = 1f)
            )
            out += RenderCommand.DrawRect(rect.x + x, rect.y, stepX.coerceAtMost(rect.width - x), rect.height, color.toArgbInt())
            x += stepX
        }
        var y = 0
        while (y < rect.height) {
            val darkness = y.toFloat() / rect.height.coerceAtLeast(1).toFloat()
            val alpha = (darkness * 255f).roundToInt().coerceIn(0, 255)
            val shade = (alpha shl 24)
            out += RenderCommand.DrawRect(rect.x, rect.y + y, rect.width, stepY.coerceAtMost(rect.height - y), shade)
            y += stepY
        }
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
        val step = 2
        var x = 0
        while (x < rect.width) {
            val hue = (x.toFloat() / rect.width.coerceAtLeast(1).toFloat()) * 360f
            val color = ColorConversions.hsvToRgb(HsvColor(hue, 1f, 1f))
            out += RenderCommand.DrawRect(rect.x + x, rect.y, step.coerceAtMost(rect.width - x), rect.height, color.toArgbInt())
            x += step
        }
        drawBorder(out, rect, style.inputBorderColor)
    }

    private fun drawHueThumb(rect: Rect, out: MutableList<RenderCommand>) {
        val x = rect.x + ((hueDeg / 360f) * rect.width.toFloat()).roundToInt().coerceIn(0, rect.width - 1)
        out += RenderCommand.DrawRect(x - 1, rect.y - 1, 3, rect.height + 2, style.thumbOutlineColor)
    }

    private fun drawAlphaSlider(rect: Rect, out: MutableList<RenderCommand>) {
        drawChecker(rect, out)
        val rgb = state.color.copy(a = 1f)
        val step = 2
        var x = 0
        while (x < rect.width) {
            val alpha = (x.toFloat() / rect.width.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
            val color = rgb.copy(a = alpha)
            out += RenderCommand.DrawRect(rect.x + x, rect.y, step.coerceAtMost(rect.width - x), rect.height, color.toArgbInt())
            x += step
        }
        drawBorder(out, rect, style.inputBorderColor)
    }

    private fun drawAlphaThumb(rect: Rect, out: MutableList<RenderCommand>) {
        val x = rect.x + (state.color.a * rect.width.toFloat()).roundToInt().coerceIn(0, rect.width - 1)
        out += RenderCommand.DrawRect(x - 1, rect.y - 1, 3, rect.height + 2, style.thumbOutlineColor)
    }

    private fun drawChecker(rect: Rect, out: MutableList<RenderCommand>) {
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

    private fun caretVisible(nowMs: Long): Boolean {
        return ((nowMs / 500L) % 2L) == 0L
    }

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

    private data class InputDefinition(
        val key: String,
        val label: String
    )
}
