package org.dreamfinity.dsgl.core.dsl

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerStyle
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.colorpicker.internal.ColorSwatchSurfaceNode
import org.dreamfinity.dsgl.core.hooks.ref.ElementHandle
import org.dreamfinity.dsgl.core.hooks.ref.RefTarget

internal open class ColorSwatchProps : ComponentProps() {
    var allowEmpty: Boolean = false
    var color: RgbaColor? = RgbaColor.WHITE
    var highlighted: Boolean = false
    var palette: ColorPickerStyle = ColorPickerStyle()
}

@DsglDsl
internal fun UiScope.colorSwatch(
    props: ColorSwatchProps.() -> Unit = {},
    ref: RefTarget<ElementHandle>? = null
) = withProps(ColorSwatchProps().apply(props)) { props ->
    ColorSwatchSurfaceNode(
        allowEmpty = props.allowEmpty,
        key = props.key
    ).apply {
        bind(style = props.palette, color = props.color, highlighted = props.highlighted)
        applyStyle(this, props.style)
        applyHandlers(this, props)
        applyRef(this, ref)
        add(this)
    }
}
