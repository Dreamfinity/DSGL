package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.colorpicker.*
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.render.RenderCommand

class ColorPickerInlineNode(
    controlled: Boolean = false,
    value: RgbaColor? = null,
    defaultValue: RgbaColor = RgbaColor.WHITE,
    previousValue: RgbaColor? = null,
    mode: ColorFormatMode = ColorFormatMode.HEX,
    alphaEnabled: Boolean = true,
    key: Any? = null,
) : DOMNode(key) {
    override val styleType: String = "color-picker"
    override val focusable: Boolean = true

    var controlled: Boolean = controlled
    var controlledValue: RgbaColor? = value
    var defaultValue: RgbaColor = defaultValue
    var previousValue: RgbaColor? = previousValue
    var mode: ColorFormatMode = mode
    var alphaEnabled: Boolean = alphaEnabled
    var closeOnSelect: Boolean = true
    var onPreviewColor: ((RgbaColor) -> Unit)? = null
    var onChangeColor: ((RgbaColor) -> Unit)? = null
    var onCommitColor: ((RgbaColor) -> Unit)? = null
    var onRequestClose: (() -> Unit)? = null
    var eyedropperEnabled: Boolean = true

    private var uncontrolledValue: RgbaColor = value ?: defaultValue
    private var uncontrolledPrevious: RgbaColor = previousValue ?: uncontrolledValue
    private val recentHistory: ColorRecentHistory = ColorRecentHistory(64)
    private val controller: ColorPickerController =
        ColorPickerController(
            initial =
                ColorPickerState(
                    color = effectiveColor(),
                    previous = effectivePreviousColor(),
                    mode = mode,
                    alphaEnabled = alphaEnabled,
                    closeOnSelect = closeOnSelect,
                ),
            recentHistory = recentHistory,
        )
    private var layout: ColorPickerLayout? = null
    private var dragCaptured: Boolean = false
    private var syncedColorArgb: Int = Int.MIN_VALUE
    private var syncedPreviousArgb: Int = Int.MIN_VALUE
    private var syncedMode: ColorFormatMode = mode
    private var syncedAlphaEnabled: Boolean = alphaEnabled
    private var syncedCloseOnSelect: Boolean = closeOnSelect

    init {
        bindController()
        EventBus.run {
            this@ColorPickerInlineNode.addEventListener(Events.MOUSEDOWN) { event: MouseDownEvent ->
                if (this@ColorPickerInlineNode.styleDisabled) return@addEventListener
                val inside = this@ColorPickerInlineNode.containsGlobalPoint(event.mouseX, event.mouseY)
                if (!inside && !controller.isEyedropperActive()) return@addEventListener
                if (inside) {
                    FocusManager.requestFocus(this@ColorPickerInlineNode)
                }
                val currentLayout = layout ?: return@addEventListener
                val consumed = controller.handleMouseDown(event.mouseX, event.mouseY, event.mouseButton, currentLayout)
                if (consumed) {
                    refreshLayout()
                    dragCaptured = event.mouseButton == MouseButton.LEFT && controller.hasActiveDragTarget()
                    event.cancelled = true
                    markRenderCommandsDirty()
                }
            }
            this@ColorPickerInlineNode.addEventListener(Events.MOUSEUP) { event: MouseUpEvent ->
                val consumed = controller.handleMouseUp(event.mouseX, event.mouseY, event.mouseButton)
                if (event.mouseButton == MouseButton.LEFT) {
                    dragCaptured = false
                }
                if (consumed) {
                    refreshLayout()
                    event.cancelled = true
                    markRenderCommandsDirty()
                }
            }
            this@ColorPickerInlineNode.addEventListener(Events.MOUSEMOVE) { event: MouseMoveEvent ->
                if (!shouldAcceptPointerHoverEvent(event.target)) return@addEventListener
                val currentLayout = layout ?: return@addEventListener
                val moved = controller.handleMouseMove(event.mouseX, event.mouseY, currentLayout)
                if (moved) {
                    refreshLayout()
                    markRenderCommandsDirty()
                }
            }
            this@ColorPickerInlineNode.addEventListener(Events.DRAG) { event: MouseDragEvent ->
                if (!dragCaptured) return@addEventListener
                val currentLayout = layout ?: return@addEventListener
                val mouseX = event.lastMouseX + event.dx
                val mouseY = event.lastMouseY + event.dy
                if (controller.handleMouseMove(mouseX, mouseY, currentLayout)) {
                    refreshLayout()
                    event.cancelled = true
                    markRenderCommandsDirty()
                }
            }
            this@ColorPickerInlineNode.addEventListener(Events.MOUSEOVER) { event: MouseOverEvent ->
                if (!shouldAcceptPointerHoverEvent(event.target)) return@addEventListener
                val currentLayout = layout ?: return@addEventListener
                if (controller.handleMouseMove(event.mouseX, event.mouseY, currentLayout)) {
                    markRenderCommandsDirty()
                }
            }
            this@ColorPickerInlineNode.addEventListener(Events.MOUSELEAVE) { _: MouseLeaveEvent ->
                if (controller.clearHover()) {
                    markRenderCommandsDirty()
                }
            }
            this@ColorPickerInlineNode.addEventListener(Events.KEYDOWN) { event: KeyboardKeyDownEvent ->
                if (!FocusManager.isFocused(this@ColorPickerInlineNode)) return@addEventListener
                if (controller.handleKeyDown(event.keyCode, event.keyChar)) {
                    refreshLayout()
                    event.cancelled = true
                    markRenderCommandsDirty()
                }
            }
        }
    }

    override fun shouldCapturePointerDrag(mouseX: Int, mouseY: Int): Boolean =
        dragCaptured || containsGlobalPoint(mouseX, mouseY)

    private fun shouldAcceptPointerHoverEvent(target: DOMNode?): Boolean {
        if (dragCaptured || controller.isEyedropperActive()) return true
        var current = target ?: return false
        while (true) {
            if (current === this) return true
            current = current.parent ?: return false
        }
    }

    fun wantsGlobalPointerInput(): Boolean = controller.isEyedropperActive()

    fun appendEyedropperPortalCommands(viewportWidth: Int, viewportHeight: Int, out: MutableList<RenderCommand>) {
        controller.appendEyedropperPreview(
            viewportWidth = viewportWidth.coerceAtLeast(1),
            viewportHeight = viewportHeight.coerceAtLeast(1),
            out = out,
        )
    }

    fun captureEyedropperSample() {
        if (controller.sampleEyedropperAtHoverAndReportChange()) {
            refreshLayout()
            markRenderCommandsDirty()
        }
    }

    internal override fun measureForLayout(ctx: UiMeasureContext, availableOuterWidth: Int?): Size =
        measureWithConstraint(availableOuterWidth)

    override fun measure(ctx: UiMeasureContext): Size = measureWithConstraint(null)

    private fun measureWithConstraint(availableOuterWidth: Int?): Size {
        val minWidth = controller.style().minWidth
        val contentLimit = resolvedContentLimit(availableOuterWidth)
        val contentWidth = contentLimit?.let { minOf(it, width ?: minWidth) } ?: (width ?: minWidth)
        val contentHeight = height ?: controller.preferredHeight(alphaEnabled)
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    private fun resolvedContentLimit(availableOuterWidth: Int?): Int? {
        val explicit = width
        val extras = margin.horizontal + padding.horizontal + border.horizontal
        val constrainedByParent = availableOuterWidth?.let { (it - extras).coerceAtLeast(0) }
        return when {
            explicit != null && constrainedByParent != null -> minOf(explicit, constrainedByParent)
            explicit != null -> explicit
            else -> constrainedByParent
        }
    }

    override fun render(
        ctx: UiMeasureContext,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        bounds = Rect(x, y, width, height)
        syncControllerStateIfNeeded()
        refreshLayout()
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        syncControllerStateIfNeeded()
        refreshLayout()
        val currentLayout = layout ?: return
        controller.appendCommands(currentLayout, out)
        addBorderCommands(out)
    }

    internal fun syncFrom(template: ColorPickerInlineNode) {
        controlled = template.controlled
        controlledValue = template.controlledValue
        defaultValue = template.defaultValue
        previousValue = template.previousValue
        mode = template.mode
        alphaEnabled = template.alphaEnabled
        closeOnSelect = template.closeOnSelect
        onPreviewColor = template.onPreviewColor
        onChangeColor = template.onChangeColor
        onCommitColor = template.onCommitColor
        onRequestClose = template.onRequestClose
        eyedropperEnabled = template.eyedropperEnabled
        if (!controlled) {
            uncontrolledValue = template.uncontrolledValue
            uncontrolledPrevious = template.uncontrolledPrevious
        }
        bindController()
        syncControllerStateIfNeeded(force = !dragCaptured && !controller.isEyedropperActive())
    }

    private fun syncControllerStateIfNeeded(force: Boolean = false) {
        if (!force && (dragCaptured || controller.isEyedropperActive())) {
            return
        }
        val current = effectiveColor()
        val previous = effectivePreviousColor()
        val currentArgb = current.toArgbInt()
        val previousArgb = previous.toArgbInt()
        if (!force &&
            currentArgb == syncedColorArgb &&
            previousArgb == syncedPreviousArgb &&
            mode == syncedMode &&
            alphaEnabled == syncedAlphaEnabled &&
            closeOnSelect == syncedCloseOnSelect
        ) {
            return
        }
        syncedColorArgb = currentArgb
        syncedPreviousArgb = previousArgb
        syncedMode = mode
        syncedAlphaEnabled = alphaEnabled
        syncedCloseOnSelect = closeOnSelect
        controller.setState(
            ColorPickerState(
                color = current,
                previous = previous,
                mode = mode,
                alphaEnabled = alphaEnabled,
                closeOnSelect = closeOnSelect,
            ),
        )
        bindController()
    }

    private fun bindController() {
        controller.onPreview = { color ->
            if (!controlled) {
                uncontrolledValue = color
            }
            onPreviewColor?.invoke(color)
            onChangeColor?.invoke(color)
            postInput(this, color.toArgbInt().toString(), color.toArgbInt())
        }
        controller.onChange = { color ->
            if (!controlled) {
                uncontrolledValue = color
            }
            onChangeColor?.invoke(color)
        }
        controller.onCommit = { color ->
            if (!controlled) {
                uncontrolledValue = color
                uncontrolledPrevious = color
            }
            onCommitColor?.invoke(color)
            postChange(this, color.toArgbInt().toString(), color.toArgbInt())
        }
        controller.onRequestClose = {
            onRequestClose?.invoke()
        }
    }

    private fun effectiveColor(): RgbaColor =
        if (controlled) {
            (controlledValue ?: defaultValue).normalized()
        } else {
            uncontrolledValue.normalized()
        }

    private fun effectivePreviousColor(): RgbaColor =
        if (controlled) {
            (previousValue ?: controlledValue ?: defaultValue).normalized()
        } else {
            (previousValue ?: uncontrolledPrevious).normalized()
        }

    private fun refreshLayout() {
        if (bounds.width <= 0 || bounds.height <= 0) return
        layout =
            controller.buildLayout(
                Rect(
                    contentX(),
                    contentY(),
                    contentWidth().coerceAtLeast(1),
                    contentHeight().coerceAtLeast(1),
                ),
            )
    }
}
