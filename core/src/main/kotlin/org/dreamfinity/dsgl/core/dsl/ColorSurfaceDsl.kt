package org.dreamfinity.dsgl.core.dsl

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerStyle
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.colorpicker.internal.AlphaSurfaceNode
import org.dreamfinity.dsgl.core.colorpicker.internal.ColorFieldSurfaceNode
import org.dreamfinity.dsgl.core.colorpicker.internal.ColorSwatchSurfaceNode
import org.dreamfinity.dsgl.core.colorpicker.internal.EyedropperMagnifierDrawNode
import org.dreamfinity.dsgl.core.colorpicker.internal.HueSurfaceNode
import org.dreamfinity.dsgl.core.hooks.ref.ElementHandle
import org.dreamfinity.dsgl.core.hooks.ref.RefTarget

internal open class ColorSwatchProps : ComponentProps() {
    var allowEmpty: Boolean = false
    var color: RgbaColor? = RgbaColor.WHITE
    var highlighted: Boolean = false
    var palette: ColorPickerStyle = ColorPickerStyle()
}

internal open class HueSliderProps : ComponentProps() {
    var hueDeg: Float = 0f
    var palette: ColorPickerStyle = ColorPickerStyle()
}

internal open class AlphaSliderProps : ComponentProps() {
    var color: RgbaColor = RgbaColor.WHITE
    var palette: ColorPickerStyle = ColorPickerStyle()
}

internal open class ColorFieldProps : ComponentProps() {
    var color: RgbaColor = RgbaColor.WHITE
    var hueDeg: Float = 0f
    var palette: ColorPickerStyle = ColorPickerStyle()
}

internal open class EyedropperMagnifierProps : ComponentProps() {
    var sourceColumns: Int = 1
    var sourceRows: Int = 1
    var magnification: Int = 1
    var showGrid: Boolean = true
    var gridColor: Int = 0x66FFFFFF
}

@DsglDsl
internal fun UiScope.colorSwatch(props: ColorSwatchProps.() -> Unit = {}, ref: RefTarget<ElementHandle>? = null) =
    withProps(ColorSwatchProps().apply(props)) { props ->
        ColorSwatchSurfaceNode(
            allowEmpty = props.allowEmpty,
            key = props.key,
        ).apply {
            bind(style = props.palette, color = props.color, highlighted = props.highlighted)
            applyStyle(this, props.style)
            applyHandlers(this, props)
            applyRef(this, ref)
            add(this)
        }
    }

@DsglDsl
internal fun UiScope.hueSlider(props: HueSliderProps.() -> Unit = {}, ref: RefTarget<ElementHandle>? = null) =
    withProps(HueSliderProps().apply(props)) { props ->
        HueSurfaceNode(
            key = props.key,
        ).apply {
            bind(style = props.palette, hueDeg = props.hueDeg)
            applyStyle(this, props.style)
            applyHandlers(this, props)
            applyRef(this, ref)
            add(this)
        }
    }

@DsglDsl
internal fun UiScope.alphaSlider(props: AlphaSliderProps.() -> Unit = {}, ref: RefTarget<ElementHandle>? = null) =
    withProps(AlphaSliderProps().apply(props)) { props ->
        AlphaSurfaceNode(
            key = props.key,
        ).apply {
            bind(style = props.palette, color = props.color)
            applyStyle(this, props.style)
            applyHandlers(this, props)
            applyRef(this, ref)
            add(this)
        }
    }

@DsglDsl
internal fun UiScope.colorField(props: ColorFieldProps.() -> Unit = {}, ref: RefTarget<ElementHandle>? = null) =
    withProps(ColorFieldProps().apply(props)) { props ->
        ColorFieldSurfaceNode(
            key = props.key,
        ).apply {
            bind(style = props.palette, color = props.color, hueDeg = props.hueDeg)
            applyStyle(this, props.style)
            applyHandlers(this, props)
            applyRef(this, ref)
            add(this)
        }
    }

@DsglDsl
internal fun UiScope.eyedropperMagnifier(
    props: EyedropperMagnifierProps.() -> Unit = {},
    ref: RefTarget<ElementHandle>? = null,
) = withProps(EyedropperMagnifierProps().apply(props)) { props ->
    EyedropperMagnifierDrawNode(
        key = props.key,
    ).apply {
        bind(
            columns = props.sourceColumns,
            rows = props.sourceRows,
            magnification = props.magnification,
            gridEnabled = props.showGrid,
            gridColor = props.gridColor,
        )
        applyStyle(this, props.style)
        applyHandlers(this, props)
        applyRef(this, ref)
        add(this)
    }
}
