package org.dreamfinity.dsgl.core.dsl

import org.dreamfinity.dsgl.core.dom.elements.*
import org.dreamfinity.dsgl.core.hooks.ref.ElementHandle
import org.dreamfinity.dsgl.core.hooks.ref.RefTarget
import org.dreamfinity.dsgl.core.select.SelectModelBuilder
import org.dreamfinity.dsgl.core.select.selectModel
import java.time.Instant
import java.time.ZoneId

/** Multiline text area props. */
open class TextAreaProps(var placeholder: String = "") : TextProps()

/** Select input props. */
open class SelectProps : ComponentProps() {
    var closeOnSelect: Boolean = true
    var defaultValue: String? = null

    var value: String?
        get() = valueInternal
        set(newValue) {
            valueSpecified = true
            valueInternal = newValue
        }

    internal fun hasControlledValue(): Boolean = valueSpecified
    internal fun controlledValue(): String? = valueInternal

    private var valueSpecified: Boolean = false
    private var valueInternal: String? = null
}

/** Input node props, driven by [org.dreamfinity.dsgl.core.dom.elements.InputType]. */
open class InputProps(val type: InputType) : TextProps()

/** Toggle/switch input props. */
open class ToggleProps : ComponentProps() {
    var defaultChecked: Boolean = false
    var trackOnColor: Int = 0xFF34C759.toInt()
    var trackOffColor: Int = 0xFF656A73.toInt()
    var trackDisabledColor: Int = 0xFF4B4F56.toInt()
    var thumbColor: Int = 0xFFFFFFFF.toInt()
    var thumbDisabledColor: Int = 0xFFB7BBC1.toInt()
    var focusOutlineColor: Int = 0xAA6FB4FF.toInt()
    var switchWidthPx: Int = 34
    var switchHeightPx: Int = 20

    var checked: Boolean
        get() = checkedInternal
        set(newValue) {
            checkedSpecified = true
            checkedInternal = newValue
        }

    internal fun hasControlledChecked(): Boolean = checkedSpecified
    internal fun controlledChecked(): Boolean = checkedInternal

    private var checkedSpecified: Boolean = false
    private var checkedInternal: Boolean = false
}


/** Input node backed by an [org.dreamfinity.dsgl.core.dom.elements.InputType]. */
@DsglDsl
fun UiScope.input(
    type: InputType,
    props: InputProps.() -> Unit = {},
    ref: RefTarget<ElementHandle>? = null,
) = withProps(InputProps(type).apply(props)) { props ->
    when (props.type) {
        is InputType.Text -> TextInputNode(
            text = props.type.value,
            placeholder = props.type.placeholder,
            allowedChars = props.type.allowedChars,
            minLength = props.type.minLength,
            maxLength = props.type.maxLength,
            key = props.key
        )

        is InputType.Password -> PasswordInputNode(
            text = props.type.value,
            placeholder = props.type.placeholder,
            minLength = props.type.minLength,
            maxLength = props.type.maxLength,
            key = props.key
        )

        is InputType.Number -> NumberInputNode(
            value = props.type.value,
            placeholder = props.type.placeholder,
            min = props.type.min,
            max = props.type.max,
            key = props.key
        )

        is InputType.Range -> RangeInputNode(
            value = props.type.value,
            min = props.type.min,
            max = props.type.max,
            step = props.type.step,
            key = props.key
        )

        is InputType.Checkbox -> CheckboxGroupNode(
            variants = props.type.variants,
            selected = props.type.selected,
            minSelected = props.type.minSelected,
            maxSelected = props.type.maxSelected,
            key = props.key
        )

        is InputType.Radio -> RadioGroupNode(
            variants = props.type.variants,
            selectedId = props.type.selected,
            key = props.key
        )

        is InputType.Date -> DateInputNode(
            value = props.type.value ?: Instant.now(),
            zoneId = props.type.zoneId ?: ZoneId.systemDefault(),
            placeholder = props.type.placeholder,
            key = props.key
        )
    }.apply {
        applyStyle(this, props.style)
        applyHandlers(this, props)
        applyRef(this, ref)
        add(this)
    }
}

/** Multiline text input area. */
@DsglDsl
fun UiScope.textarea(
    props: TextAreaProps.() -> Unit = {},
    ref: RefTarget<ElementHandle>? = null
) = withProps(TextAreaProps().apply(props)) { props ->
    TextAreaNode(
        props.value,
        props.placeholder,
        props.key
    ).apply {
        applyStyle(this, props.style)
        applyHandlers(this, props)
        applyRef(this, ref)
        add(this)
    }
}

@DsglDsl
fun UiScope.select(
    props: SelectProps.() -> Unit = {},
    ref: RefTarget<ElementHandle>? = null,
    block: SelectModelBuilder.() -> Unit
) = withProps(SelectProps().apply(props)) { props ->
    val model = selectModel(block = block)
    val controlled = props.hasControlledValue()
    SelectNode(
        model = model,
        controlled = controlled,
        value = if (controlled) props.controlledValue() else null,
        defaultValue = props.defaultValue,
        closeOnSelect = props.closeOnSelect,
        key = props.key
    ).apply {
        applyStyle(this, props.style)
        applyHandlers(this, props)
        applyRef(this, ref)
        add(this)
    }
}

@DsglDsl
fun UiScope.toggle(
    props: ToggleProps.() -> Unit = {},
    ref: RefTarget<ElementHandle>? = null
) = withProps(ToggleProps().apply(props)) { props ->
    val controlled = props.hasControlledChecked()
    ToggleNode(
        controlled = controlled,
        checked = if (controlled) props.controlledChecked() else false,
        defaultChecked = props.defaultChecked,
        key = props.key
    ).apply {
        trackOnColor = props.trackOnColor
        trackOffColor = props.trackOffColor
        trackDisabledColor = props.trackDisabledColor
        thumbColor = props.thumbColor
        thumbDisabledColor = props.thumbDisabledColor
        focusOutlineColor = props.focusOutlineColor
        switchWidthPx = props.switchWidthPx
        switchHeightPx = props.switchHeightPx
        applyStyle(this, props.style)
        applyHandlers(this, props)
        applyRef(this, ref)
        add(this)
    }
}