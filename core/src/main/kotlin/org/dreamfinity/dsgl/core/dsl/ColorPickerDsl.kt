package org.dreamfinity.dsgl.core.dsl

import org.dreamfinity.dsgl.core.colorpicker.ColorFormatMode
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.dom.elements.ColorPickerInlineNode
import org.dreamfinity.dsgl.core.dom.elements.ColorPickerPopupPaneNode
import org.dreamfinity.dsgl.core.hooks.ref.ElementHandle
import org.dreamfinity.dsgl.core.hooks.ref.RefTarget

/** Color picker popup field props. */
open class ColorPickerPopupProps : ColorPickerProps() {
    var popupTitle: String = "Color Picker"
    var popupWidth: Int = 320
    var popupDraggable: Boolean = true
    var popupCloseOnOutsideClick: Boolean = false
}

/** Color picker inline props. */
open class ColorPickerProps : ComponentProps() {
    var closeOnSelect: Boolean = true
    var defaultValue: RgbaColor = RgbaColor.Companion.WHITE
    var previousValue: RgbaColor? = null
    var mode: ColorFormatMode = ColorFormatMode.HEX
    var alphaEnabled: Boolean = true
    var eyedropperEnabled: Boolean = true
    var onPreviewColor: ((RgbaColor) -> Unit)? = null
    var onChangeColor: ((RgbaColor) -> Unit)? = null
    var onCommitColor: ((RgbaColor) -> Unit)? = null
    var onRequestClose: (() -> Unit)? = null

    var value: RgbaColor?
        get() = valueInternal
        set(newValue) {
            valueSpecified = true
            valueInternal = newValue
        }

    internal fun hasControlledValue(): Boolean = valueSpecified

    internal fun controlledValue(): RgbaColor? = valueInternal

    private var valueSpecified: Boolean = false
    private var valueInternal: RgbaColor? = null
}

@DsglDsl
fun UiScope.colorPicker(props: ColorPickerProps.() -> Unit = {}, ref: RefTarget<ElementHandle>? = null) =
    withProps(ColorPickerProps().apply(props)) { props ->
        val controlled = props.hasControlledValue()
        ColorPickerInlineNode(
            controlled = controlled,
            value = if (controlled) props.controlledValue() else null,
            defaultValue = props.defaultValue,
            previousValue = props.previousValue,
            mode = props.mode,
            alphaEnabled = props.alphaEnabled,
            key = props.key,
        ).apply {
            closeOnSelect = props.closeOnSelect
            eyedropperEnabled = props.eyedropperEnabled
            onPreviewColor = props.onPreviewColor
            onChangeColor = props.onChangeColor
            onCommitColor = props.onCommitColor
            onRequestClose = props.onRequestClose
            applyStyle(this, props.style)
            applyHandlers(this, props)
            applyRef(this, ref)
            add(this)
        }
    }

@DsglDsl
fun UiScope.colorPickerPopup(props: ColorPickerPopupProps.() -> Unit = {}, ref: RefTarget<ElementHandle>? = null) =
    withProps(ColorPickerPopupProps().apply(props)) { props ->
        val controlled = props.hasControlledValue()
        ColorPickerPopupPaneNode(
            controlled = controlled,
            value = if (controlled) props.controlledValue() else null,
            defaultValue = props.defaultValue,
            previousValue = props.previousValue,
            mode = props.mode,
            alphaEnabled = props.alphaEnabled,
            key = props.key,
        ).apply {
            closeOnSelect = props.closeOnSelect
            popupTitle = props.popupTitle
            popupWidth = props.popupWidth
            popupDraggable = props.popupDraggable
            popupCloseOnOutsideClick = props.popupCloseOnOutsideClick
            onPreviewColor = props.onPreviewColor
            onChangeColor = props.onChangeColor
            onCommitColor = props.onCommitColor
            applyStyle(this, props.style)
            applyHandlers(this, props)
            applyRef(this, ref)
            add(this)
        }
    }
