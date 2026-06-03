package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.colorpicker.*
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.portal.DomainPortalServices
import org.dreamfinity.dsgl.core.render.RenderCommand

class ColorPickerPopupPaneNode(
    controlled: Boolean = false,
    value: RgbaColor? = null,
    defaultValue: RgbaColor = RgbaColor.WHITE,
    previousValue: RgbaColor? = null,
    mode: ColorFormatMode = ColorFormatMode.HEX,
    alphaEnabled: Boolean = true,
    key: Any? = null,
) : DOMNode(key) {
    override val styleType: String = "color-picker-popup"
    override val focusable: Boolean = true

    private val ownerToken: Any
        get() = key ?: this

    var controlled: Boolean = controlled
    var controlledValue: RgbaColor? = value
    var defaultValue: RgbaColor = defaultValue
    var previousValue: RgbaColor? = previousValue
    var mode: ColorFormatMode = mode
    var alphaEnabled: Boolean = alphaEnabled
    var closeOnSelect: Boolean = false
    var popupTitle: String = "Color Picker"
    var popupWidth: Int = 320
    var popupDraggable: Boolean = true
    var popupCloseOnOutsideClick: Boolean = false
    var textColor: Int = DsglColors.WHITE
    var backgroundColor: Int = 0xFF2B3542.toInt()
    var borderColor: Int = 0xFF607286.toInt()
    var onPreviewColor: ((RgbaColor) -> Unit)? = null
    var onChangeColor: ((RgbaColor) -> Unit)? = null
    var onCommitColor: ((RgbaColor) -> Unit)? = null

    private var uncontrolledValue: RgbaColor = value ?: defaultValue
    private var uncontrolledPrevious: RgbaColor = previousValue ?: uncontrolledValue

    init {
        EventBus.run {
            this@ColorPickerPopupPaneNode.addEventListener(Events.MOUSEDOWN) { event: MouseDownEvent ->
                if (this@ColorPickerPopupPaneNode.styleDisabled) return@addEventListener
                if (event.mouseButton != MouseButton.LEFT) return@addEventListener
                if (!this@ColorPickerPopupPaneNode.containsGlobalPoint(
                        event.mouseX,
                        event.mouseY,
                    )
                ) {
                    return@addEventListener
                }
                FocusManager.requestFocus(this@ColorPickerPopupPaneNode)
                if (DomainPortalServices.applicationColorPickerEngine.isOpenFor(ownerToken)) {
                    DomainPortalServices.applicationColorPickerEngine.close(ownerToken)
                } else {
                    openPopup()
                }
                event.cancelled = true
            }
        }
    }

    internal override fun measureForLayout(ctx: UiMeasureContext, availableOuterWidth: Int?): Size =
        measureWithConstraint(ctx, availableOuterWidth)

    override fun measure(ctx: UiMeasureContext): Size = measureWithConstraint(ctx, null)

    private fun measureWithConstraint(ctx: UiMeasureContext, availableOuterWidth: Int?): Size {
        val label = ColorTextCodec.format(effectiveColor(), mode, alphaEnabled)
        val contentLimit = resolvedContentLimit(availableOuterWidth)
        val naturalWidth = width ?: maxOf(120, measureText(ctx, label) + 42)
        val contentWidth = contentLimit?.let { minOf(it, naturalWidth) } ?: naturalWidth
        val contentHeight = height ?: 24
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

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        syncPopupIfOpen()
        val rect = bounds
        out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, rect.height, backgroundColor)
        addBorderCommands(out)
        val swatchSize = (rect.height - 8).coerceAtLeast(10)
        val swatchRect = Rect(rect.x + 4, rect.y + ((rect.height - swatchSize) / 2), swatchSize, swatchSize)
        out +=
            RenderCommand.DrawRect(
                swatchRect.x,
                swatchRect.y,
                swatchRect.width,
                swatchRect.height,
                effectiveColor().toArgbInt(),
            )
        out += RenderCommand.DrawRect(swatchRect.x, swatchRect.y, swatchRect.width, 1, borderColor)
        out +=
            RenderCommand.DrawRect(swatchRect.x, swatchRect.y + swatchRect.height - 1, swatchRect.width, 1, borderColor)
        out += RenderCommand.DrawRect(swatchRect.x, swatchRect.y, 1, swatchRect.height, borderColor)
        out +=
            RenderCommand.DrawRect(swatchRect.x + swatchRect.width - 1, swatchRect.y, 1, swatchRect.height, borderColor)
        out +=
            drawTextCommand(
                ctx,
                text = ColorTextCodec.format(effectiveColor(), mode, alphaEnabled),
                x = swatchRect.x + swatchRect.width + 6,
                y = rect.y + 3,
                color = textColor,
            )
        out +=
            if (DomainPortalServices.applicationColorPickerEngine.isOpenFor(ownerToken)) {
                drawTextCommand(
                    ctx,
                    text = "^",
                    x = rect.x + rect.width - 14,
                    y = rect.y + 3,
                    color = textColor,
                )
            } else {
                drawTextCommand(
                    ctx,
                    text = "v",
                    x = rect.x + rect.width - 14,
                    y = rect.y + 3,
                    color = textColor,
                )
            }
    }

    internal fun syncFrom(template: ColorPickerPopupPaneNode) {
        controlled = template.controlled
        controlledValue = template.controlledValue
        defaultValue = template.defaultValue
        previousValue = template.previousValue
        mode = template.mode
        alphaEnabled = template.alphaEnabled
        closeOnSelect = template.closeOnSelect
        popupTitle = template.popupTitle
        popupWidth = template.popupWidth
        popupDraggable = template.popupDraggable
        popupCloseOnOutsideClick = template.popupCloseOnOutsideClick
        textColor = template.textColor
        backgroundColor = template.backgroundColor
        borderColor = template.borderColor
        onPreviewColor = template.onPreviewColor
        onChangeColor = template.onChangeColor
        onCommitColor = template.onCommitColor
        if (!controlled) {
            uncontrolledValue = template.uncontrolledValue
            uncontrolledPrevious = template.uncontrolledPrevious
        }
    }

    override fun defaultBackgroundColor(): Int = backgroundColor

    override fun applyBackgroundColor(value: Int?) {
        if (value != null) {
            backgroundColor = value
        }
    }

    override fun defaultForegroundColor(): Int = textColor

    override fun applyForegroundColor(value: Int) {
        textColor = value
    }

    private fun syncPopupIfOpen() {
        if (!DomainPortalServices.applicationColorPickerEngine.isOpenFor(ownerToken)) return
        DomainPortalServices.applicationColorPickerEngine.sync(openRequest())
        setOpenState(true)
    }

    private fun openPopup() {
        DomainPortalServices.applicationColorPickerEngine.open(openRequest())
        setOpenState(true)
    }

    private fun openRequest(): ColorPickerPopupRequest =
        ColorPickerPopupRequest(
            owner = ownerToken,
            anchorRect = bounds,
            title = popupTitle,
            state =
                ColorPickerState(
                    color = effectiveColor(),
                    previous = effectivePreviousColor(),
                    mode = mode,
                    alphaEnabled = alphaEnabled,
                    closeOnSelect = closeOnSelect,
                ),
            width = popupWidth,
            draggable = popupDraggable,
            closeOnOutsideClick = popupCloseOnOutsideClick,
            onPreview = { color ->
                if (!controlled) {
                    uncontrolledValue = color
                }
                onPreviewColor?.invoke(color)
                onChangeColor?.invoke(color)
                postInput(this, color.toArgbInt().toString(), color.toArgbInt())
            },
            onChange = { color ->
                if (!controlled) {
                    uncontrolledValue = color
                }
                onChangeColor?.invoke(color)
            },
            onCommit = { color ->
                if (!controlled) {
                    uncontrolledValue = color
                    uncontrolledPrevious = color
                }
                onCommitColor?.invoke(color)
                postChange(this, color.toArgbInt().toString(), color.toArgbInt())
            },
            onClose = {
                setOpenState(false)
            },
        )

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
}
