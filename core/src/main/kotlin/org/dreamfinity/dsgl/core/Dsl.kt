package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.animation.AnimationListBuilder
import org.dreamfinity.dsgl.core.animation.TransitionBuilder
import org.dreamfinity.dsgl.core.animation.UiTransformBuilder
import org.dreamfinity.dsgl.core.colorpicker.ColorFormatMode
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.dnd.*
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.*
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.ref.ElementHandle
import org.dreamfinity.dsgl.core.ref.RefTarget
import org.dreamfinity.dsgl.core.select.SelectModelBuilder
import org.dreamfinity.dsgl.core.select.selectModel
import org.dreamfinity.dsgl.core.style.*
import java.time.Instant
import java.time.ZoneId

/**
 * Marks the DSGL UI DSL to keep nested scopes safe.
 */
@DslMarker
annotation class DsglDsl

/**
 * Builds a retained DOM tree using the DSGL UI DSL.
 *
 * Call this from [DsglWindow.render] to define the UI hierarchy.
 */
fun ui(block: UiScope.() -> Unit): DomTree {
    return ui(ContainerNode(stackLayout = true), block)
}

fun ui(root: DOMNode, block: UiScope.() -> Unit): DomTree {
    val scope = UiScope(root)
    scope.block()
    return DomTree(root)
}

/**
 * Common visual and interaction props shared by most components.
 *
 * Event callbacks are wired into [org.dreamfinity.dsgl.core.event.EventBus].
 */
open class ComponentProps(
    var style: StyleScope.() -> Unit = {},
    var key: Any? = null,
    var id: String? = null,
    var className: String = "",
    var classes: Set<String> = emptySet(),
    var disabled: Boolean = false,
    var draggable: Boolean = false,
    var droppable: Boolean = false,
    var dragPreviewMode: DragPreviewMode = DragPreviewMode.GHOST,
    var hideSourceWhileDragging: Boolean = false,
    var dragPreview: (DragPreviewScope.() -> Unit)? = null,
    var dragPlaceholder: (PlaceholderScope.() -> Unit)? = null,
    var ref: RefTarget<ElementHandle>? = null,
    var onMouseEnter: ((MouseEnterEvent) -> Unit)? = null,
    var onMouseLeave: ((MouseLeaveEvent) -> Unit)? = null,
    var onMouseOver: ((MouseOverEvent) -> Unit)? = null,
    var onMouseMove: ((MouseMoveEvent) -> Unit)? = null,
    var onMouseDown: ((MouseDownEvent) -> Unit)? = null,
    var onMouseUp: ((MouseUpEvent) -> Unit)? = null,
    var onMouseClick: ((MouseClickEvent) -> Unit)? = null,
    var onMouseDrag: ((MouseDragEvent) -> Unit)? = null,
    var onMouseWheel: ((MouseWheelEvent) -> Unit)? = null,
    var onKeyDown: ((KeyboardKeyDownEvent) -> Unit)? = null,
    var onKeyUp: ((KeyboardKeyUpEvent) -> Unit)? = null,
    var onKeyPressed: ((KeyboardKeyDownEvent) -> Unit)? = null,
    var onKeyReleased: ((KeyboardKeyUpEvent) -> Unit)? = null,
    var onFocusGain: ((FocusGainEvent) -> Unit)? = null,
    var onFocusLose: ((FocusLoseEvent) -> Unit)? = null,
    var onInput: ((InputEvent) -> Unit)? = null,
    var onValueChange: ((ValueChangedEvent) -> Unit)? = null,
    var onDragStart: ((DragStartEvent) -> Unit)? = null,
    var onDrag: ((DragEvent) -> Unit)? = null,
    var onDragEnd: ((DragEndEvent) -> Unit)? = null,
    var onDragEnter: ((DragEnterEvent) -> Unit)? = null,
    var onDragOver: ((DragOverEvent) -> Unit)? = null,
    var onDragLeave: ((DragLeaveEvent) -> Unit)? = null,
    var onDrop: ((DropEvent) -> Unit)? = null
) {
    fun style(block: StyleScope.() -> Unit) {
        style = block
    }

//    fun asFlexRow(): ComponentProps = asFlex(FlexDirection.Row)
//
//    fun asFlexColumn(): ComponentProps = asFlex(FlexDirection.Column)
//
//    private fun asFlex(direction: FlexDirection): ComponentProps {
//        val previous = style
//        style = {
//            display = Display.Flex
//            flexDirection = direction
//            previous()
//        }
//        return this
//    }
}

/** Static text props. */
open class TextProps(value: String = "") : ComponentProps() {
    var source: TextSource = TextSource.Static(value)

    constructor(valueProvider: () -> String) : this() {
        source = TextSource.Dynamic(valueProvider)
    }

    var value: String
        get() = source.resolve()
        set(newValue) {
            source = TextSource.Static(newValue)
        }
}

/** Multiline text area props. */
open class TextAreaProps(var placeholder: String = "") : TextProps()

/** Input node props, driven by [InputType]. */
open class InputProps(val type: InputType) : TextProps()

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

/** Color picker inline props. */
open class ColorPickerProps : ComponentProps() {
    var closeOnSelect: Boolean = true
    var defaultValue: RgbaColor = RgbaColor.WHITE
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

/** Color picker popup field props. */
open class ColorPickerPopupProps : ColorPickerProps() {
    var popupTitle: String = "Color Picker"
    var popupWidth: Int = 320
    var popupDraggable: Boolean = true
    var popupCloseOnOutsideClick: Boolean = false
}

/** Image node props; accepts resource id, file://, or http(s) URLs in MC host. */
open class ImageProps(var url: String) : ComponentProps()

/** Item stack node props for platform-specific stacks. */
open class ItemStackProps(
    var stack: ItemStackRef,
    var size: Int = 18,
    var rotYDeg: Double = 160.0,
    var rotXDeg: Double = -11.0
) : ComponentProps()

/** Button node props. */
open class ButtonProps(var text: String) : TextProps()

inline fun <T, R> withProps(value: T, block: (T) -> R): R = block(value)

@DsglDsl
/**
 * Root DSL scope used by [ui] to add layout and component nodes.
 */
class UiScope internal constructor(private val parent: DOMNode) {
    /** Generic container; layout is controlled by style.display. */
    fun div(
        props: ComponentProps.() -> Unit,
        ref: RefTarget<ElementHandle>? = null,
        block: UiScope.() -> Unit = {}
    ) = withProps(ComponentProps().apply(props)) { props ->
        ContainerNode(
            stackLayout = false,
            key = props.key
        ).apply {
            applyStyle(this, props.style)
            applyHandlers(this, props)
            applyRef(this, ref)
            add(this)
            UiScope(this).block()
        }
    }

    /** Overlay layout container (children overlap). */
    fun overlay(
        props: ComponentProps.() -> Unit,
        ref: RefTarget<ElementHandle>? = null,
        block: UiScope.() -> Unit = {}
    ) = withProps(ComponentProps().apply(props)) { props ->
        ContainerNode(
            stackLayout = true,
            key = props.key
        ).apply {
            applyStyle(this, props.style)
            applyHandlers(this, props)
            applyRef(this, ref)
            add(this)
            UiScope(this).block()
        }
    }


    /** Text node. Supports static and rebuild-driven dynamic text. */
    fun text(
        props: TextProps.() -> Unit,
        ref: RefTarget<ElementHandle>? = null
    ) = withProps(TextProps().apply(props)) { props ->
        TextNode(
            props.source,
            key = props.key
        ).apply {
            applyStyle(this, props.style)
            applyHandlers(this, props)
            applyRef(this, ref)
            add(this)
        }
    }

    fun text(
        value: String,
        props: (TextProps.() -> Unit) = {},
        ref: RefTarget<ElementHandle>? = null,
    ) {
        text(
            props = {
                this.value = value
                props()
            },
            ref = ref
        )
    }

    fun text(
        value: String,
        minecraftFormatting: Boolean,
        props: TextProps.() -> Unit = {},
        ref: RefTarget<ElementHandle>? = null,
    ) {
        text(
            props = {
                this.value = value
                props()

                if (minecraftFormatting) {
                    val prev = style
                    style = {
                        textFormatting = TextFormatting.Minecraft
                        prev()
                    }
                }
            },
            ref = ref
        )
    }

    fun dynamicText(
        value: () -> String
    ) {
        dynamicText(value = value, ref = null)
    }

    fun dynamicText(
        value: () -> String,
        props: TextProps.() -> Unit = {},
        ref: RefTarget<ElementHandle>? = null,
    ) {
        text(
            props = {
                source = TextSource.Dynamic(value)
                props()
            },
            ref = ref
        )
    }

    fun dynamicText(
        value: () -> String,
        minecraftFormatting: Boolean,
        props: TextProps.() -> Unit = {},
        ref: RefTarget<ElementHandle>? = null,
    ) {
        text(
            props = {
                source = TextSource.Dynamic(value)
                props()

                if (minecraftFormatting) {
                    val prev = style
                    style = {
                        textFormatting = TextFormatting.Minecraft
                        prev()
                    }
                }
            },
            ref = ref
        )
    }

    /** Button node with optional extra button scope. */
    fun button(
        text: String,
        props: ButtonProps.() -> Unit = {},
        ref: RefTarget<ElementHandle>? = null,
        block: ButtonScope.() -> Unit = {}
    ) = withProps(ButtonProps(text).apply(props)) { props ->
        ButtonNode(
            props.text,
            key = props.key
        ).apply {
            applyStyle(this, props.style)
            applyHandlers(this, props)
            applyRef(this, ref)
            add(this)
            ButtonScope(this).block()
        }
    }


    /** Image node from resource, file, or URL (host-dependent). */
    fun img(
        url: String,
        props: ImageProps.() -> Unit = {},
        ref: RefTarget<ElementHandle>? = null,
    ) = withProps(ImageProps(url).apply(props)) { props ->
        ImageNode(
            props.url,
            key = props.key
        ).apply {
            applyStyle(props.style)
            applyHandlers(props)
            applyRef(this, ref)
            add(this)
        }
    }

    /** Item stack node for platform-specific stack types. */
    fun itemStack(
        itemStack: ItemStackRef,
        props: ItemStackProps.() -> Unit = {},
        ref: RefTarget<ElementHandle>? = null,
    ) = withProps(ItemStackProps(itemStack).apply(props)) { props ->
        ItemStackNode(
            props.stack,
            props.size,
            props.rotYDeg,
            props.rotXDeg,
            props.key
        ).apply {
            applyStyle(this, props.style)
            applyHandlers(this, props)
            applyRef(this, ref)
            add(this)
        }
    }

    /** Input node backed by an [InputType]. */
    fun input(
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

    fun select(
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

    fun toggle(
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

    fun colorPicker(
        props: ColorPickerProps.() -> Unit = {},
        ref: RefTarget<ElementHandle>? = null
    ) = withProps(ColorPickerProps().apply(props)) { props ->
        val controlled = props.hasControlledValue()
        ColorPickerInlineNode(
            controlled = controlled,
            value = if (controlled) props.controlledValue() else null,
            defaultValue = props.defaultValue,
            previousValue = props.previousValue,
            mode = props.mode,
            alphaEnabled = props.alphaEnabled,
            key = props.key
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

    fun colorPickerPopup(
        props: ColorPickerPopupProps.() -> Unit = {},
        ref: RefTarget<ElementHandle>? = null
    ) = withProps(ColorPickerPopupProps().apply(props)) { props ->
        val controlled = props.hasControlledValue()
        ColorPickerPopupPaneNode(
            controlled = controlled,
            value = if (controlled) props.controlledValue() else null,
            defaultValue = props.defaultValue,
            previousValue = props.previousValue,
            mode = props.mode,
            alphaEnabled = props.alphaEnabled,
            key = props.key
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


    /** Multiline text input area. */
    fun textarea(
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

    private fun <T : DOMNode> add(node: T): T {
        return node.applyParent(parent)
    }

    internal fun <T : DOMNode> mount(node: T): T {
        return add(node)
    }

    private fun applyHandlers(node: DOMNode, props: ComponentProps) {
        node.applyHandlers(props)
    }

    private fun applyStyle(node: DOMNode, style: StyleScope.() -> Unit) {
        node.applyStyle(style)
    }

    private fun applyRef(node: DOMNode, ref: RefTarget<ElementHandle>?) {
        if (ref != null) {
            node.refTarget = ref
        }
    }
}

@DsglDsl
/**
 * Styling DSL attached to a [DOMNode].
 */
class StyleScope internal constructor(private val node: DOMNode) {
    val Number.px: CssLength
        get() = CssLength(value = this.toFloat(), unit = CssUnit.Px)
    val Number.rem: CssLength
        get() = CssLength(value = this.toFloat(), unit = CssUnit.Rem)
    val Number.em: CssLength
        get() = CssLength(value = this.toFloat(), unit = CssUnit.Em)
    val Number.vw: CssLength
        get() = CssLength(value = this.toFloat(), unit = CssUnit.Vw)
    val Number.vh: CssLength
        get() = CssLength(value = this.toFloat(), unit = CssUnit.Vh)
    val Number.percent: CssLength
        get() = CssLength(value = this.toFloat(), unit = CssUnit.Percent)

    var display: Display
        get() = Display.Block
        set(value) {
            setLiteral(StyleProperty.DISPLAY, value.toCssLiteral())
        }

    var flexDirection: FlexDirection
        get() = FlexDirection.Row
        set(value) {
            setLiteral(StyleProperty.FLEX_DIRECTION, value.toCssLiteral())
        }

    var justifyContent: JustifyContent
        get() = JustifyContent.Start
        set(value) {
            setLiteral(StyleProperty.JUSTIFY_CONTENT, value.toCssLiteral())
        }

    var alignItems: AlignItems
        get() = AlignItems.Stretch
        set(value) {
            setLiteral(StyleProperty.ALIGN_ITEMS, value.toCssLiteral())
        }

    var justifyItems: JustifyItems
        get() = JustifyItems.Stretch
        set(value) {
            setLiteral(StyleProperty.JUSTIFY_ITEMS, value.toCssLiteral())
        }

    var padding: CssLength
        get() = CssLength.ZERO_PX
        set(value) {
            requireNonNegative(value, "padding")
            setSpacing(StyleProperty.PADDING, value, value, value, value)
        }

    var width: CssLength?
        get() = null
        set(value) {
            if (value == null) {
                setLiteral(StyleProperty.WIDTH, "auto")
            } else {
                requireNonNegative(value, "width")
                setLiteral(StyleProperty.WIDTH, value.toCssLiteral())
            }
        }

    var height: CssLength?
        get() = null
        set(value) {
            if (value == null) {
                setLiteral(StyleProperty.HEIGHT, "auto")
            } else {
                requireNonNegative(value, "height")
                setLiteral(StyleProperty.HEIGHT, value.toCssLiteral())
            }
        }

    var color: Int
        get() = DsglColors.TEXT
        set(value) {
            setLiteral(StyleProperty.FOREGROUND_COLOR, toColorLiteral(value))
        }

    var backgroundColor: Int?
        get() = null
        set(value) {
            if (value == null) {
                setLiteral(StyleProperty.BACKGROUND_COLOR, "#00000000")
            } else {
                setLiteral(StyleProperty.BACKGROUND_COLOR, toColorLiteral(value))
            }
        }

    var gap: CssLength
        get() = CssLength.ZERO_PX
        set(value) {
            requireNonNegative(value, "gap")
            setLiteral(StyleProperty.GAP, value.toCssLiteral())
        }

    var flexGrow: Float
        get() = 0f
        set(value) {
            setLiteral(StyleProperty.FLEX_GROW, value.coerceAtLeast(0f).toString())
        }

    var flexShrink: Float
        get() = 1f
        set(value) {
            setLiteral(StyleProperty.FLEX_SHRINK, value.coerceAtLeast(0f).toString())
        }

    var flexBasis: CssLength?
        get() = null
        set(value) {
            if (value == null) {
                setLiteral(StyleProperty.FLEX_BASIS, "auto")
            } else {
                requireNonNegative(value, "flex-basis")
                setLiteral(StyleProperty.FLEX_BASIS, value.toCssLiteral())
            }
        }

    var gridColumns: Int
        get() = 2
        set(value) {
            setLiteral(StyleProperty.GRID_COLUMNS, value.coerceAtLeast(1).toString())
        }

    var gridRows: Int?
        get() = null
        set(value) {
            if (value == null) {
                setLiteral(StyleProperty.GRID_ROWS, "auto")
            } else {
                setLiteral(StyleProperty.GRID_ROWS, value.coerceAtLeast(1).toString())
            }
        }

    var gridAutoFlow: GridAutoFlow
        get() = GridAutoFlow.Row
        set(value) {
            setLiteral(StyleProperty.GRID_AUTO_FLOW, value.toCssLiteral())
        }

    var gridColumnSpan: Int
        get() = 1
        set(value) {
            setLiteral(StyleProperty.GRID_COLUMN_SPAN, value.coerceAtLeast(1).toString())
        }

    var gridRowSpan: Int
        get() = 1
        set(value) {
            setLiteral(StyleProperty.GRID_ROW_SPAN, value.coerceAtLeast(1).toString())
        }

    var textWrap: TextWrap
        get() = TextWrap.NoWrap
        set(value) {
            setLiteral(StyleProperty.TEXT_WRAP, value.toCssLiteral())
        }

    var textFormatting: TextFormatting
        get() = TextFormatting.None
        set(value) {
            setLiteral(StyleProperty.TEXT_FORMATTING, value.toCssLiteral())
        }

    var fontWeight: FontWeight
        get() = FontWeight.Normal
        set(value) {
            setLiteral(StyleProperty.FONT_WEIGHT, value.toCssLiteral())
        }

    var fontStyle: FontStyle
        get() = FontStyle.Normal
        set(value) {
            setLiteral(StyleProperty.FONT_STYLE, value.toCssLiteral())
        }

    var textDecoration: TextDecoration
        get() = TextDecoration.None
        set(value) {
            setLiteral(StyleProperty.TEXT_DECORATION, value.toCssLiteral())
        }

    var obfuscated: Boolean
        get() = false
        set(value) {
            setLiteral(StyleProperty.OBFUSCATED, value.toString())
        }

    var opacity: Float
        get() = 1f
        set(value) {
            setLiteral(StyleProperty.OPACITY, value.coerceIn(0f, 1f).toString())
        }

    fun transform(value: UiTransform) {
        setLiteral(StyleProperty.TRANSFORM, value.toCssLiteral())
    }

    fun transform(block: UiTransformBuilder.() -> Unit) {
        transform(UiTransformBuilder().apply(block).build())
    }

    fun transformOrigin(originX: Float, originY: Float) {
        val x = originX.coerceIn(0f, 1f)
        val y = originY.coerceIn(0f, 1f)
        setLiteral(StyleProperty.TRANSFORM_ORIGIN, "$x $y")
    }

    fun transition(block: TransitionBuilder.() -> Unit) {
        node.transitionSpec = TransitionBuilder().apply(block).build()
    }

    fun animation(block: AnimationListBuilder.() -> Unit) {
        node.animationSpecs = AnimationListBuilder().apply(block).build()
    }

    fun margin(all: CssLength) {
        setSpacing(StyleProperty.MARGIN, all, all, all, all)
    }

    fun margin(horizontal: CssLength, vertical: CssLength) {
        setSpacing(StyleProperty.MARGIN, vertical, horizontal, vertical, horizontal)
    }

    fun margin(top: CssLength, right: CssLength, bottom: CssLength, left: CssLength) {
        setSpacing(StyleProperty.MARGIN, top, right, bottom, left)
    }

    fun padding(all: CssLength) {
        requireNonNegative(all, "padding")
        setSpacing(StyleProperty.PADDING, all, all, all, all)
    }

    fun padding(horizontal: CssLength, vertical: CssLength) {
        requireNonNegative(horizontal, "padding")
        requireNonNegative(vertical, "padding")
        setSpacing(StyleProperty.PADDING, vertical, horizontal, vertical, horizontal)
    }

    fun padding(top: CssLength, right: CssLength, bottom: CssLength, left: CssLength) {
        requireNonNegative(top, "padding")
        requireNonNegative(right, "padding")
        requireNonNegative(bottom, "padding")
        requireNonNegative(left, "padding")
        setSpacing(StyleProperty.PADDING, top, right, bottom, left)
    }

    fun border(width: CssLength) {
        requireNonNegative(width, "border-width")
        borderWidth(width)
        borderColor(DsglColors.BORDER)
    }

    fun border(width: CssLength, color: Int) {
        requireNonNegative(width, "border-width")
        borderWidth(width)
        borderColor(color)
    }

    fun border(horizontal: CssLength, vertical: CssLength, color: Int = DsglColors.BORDER) {
        border(maxLength(horizontal, vertical), color)
    }

    fun border(top: CssLength, right: CssLength, bottom: CssLength, left: CssLength, color: Int = DsglColors.BORDER) {
        border(maxLength(top, right, bottom, left), color)
    }

    fun backgroundColor(color: Int) {
        setLiteral(StyleProperty.BACKGROUND_COLOR, toColorLiteral(color))
    }

    fun backgroundColor(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.BACKGROUND_COLOR, variable)
    }

    fun backgroundImage(path: String) {
        setLiteral(StyleProperty.BACKGROUND_IMAGE, "\"$path\"")
    }

    fun backgroundImage(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.BACKGROUND_IMAGE, variable)
    }

    fun borderColor(color: Int) {
        setLiteral(StyleProperty.BORDER_COLOR, toColorLiteral(color))
    }

    fun borderColor(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.BORDER_COLOR, variable)
    }

    fun borderWidth(value: CssLength) {
        requireNonNegative(value, "border-width")
        setLiteral(StyleProperty.BORDER_WIDTH, value.toCssLiteral())
    }

    fun borderWidth(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.BORDER_WIDTH, variable)
    }

    fun borderRadius(value: CssLength) {
        requireNonNegative(value, "border-radius")
        setLiteral(StyleProperty.BORDER_RADIUS, value.toCssLiteral())
    }

    fun borderRadius(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.BORDER_RADIUS, variable)
    }

    fun foregroundColor(color: Int) {
        setLiteral(StyleProperty.FOREGROUND_COLOR, toColorLiteral(color))
    }

    fun foregroundColor(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.FOREGROUND_COLOR, variable)
    }

    fun fontId(value: String) {
        setLiteral(StyleProperty.FONT_ID, "\"${value.trim()}\"")
    }

    fun fontId(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.FONT_ID, variable)
    }

    fun fontSize(value: CssLength) {
        requireNonNegative(value, "font-size")
        setLiteral(StyleProperty.FONT_SIZE, value.toCssLiteral())
    }

    fun fontSize(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.FONT_SIZE, variable)
    }

    fun width(value: CssLength) {
        requireNonNegative(value, "width")
        setLiteral(StyleProperty.WIDTH, value.toCssLiteral())
    }

    fun width(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.WIDTH, variable)
    }

    fun height(value: CssLength) {
        requireNonNegative(value, "height")
        setLiteral(StyleProperty.HEIGHT, value.toCssLiteral())
    }

    fun height(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.HEIGHT, variable)
    }

    fun align(value: StyleAlign) {
        setLiteral(StyleProperty.ALIGN, value.name.lowercase())
    }

    fun align(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.ALIGN, variable)
    }

    fun `var`(name: String): StyleExpression.VariableRef {
        val normalized = if (name.startsWith("--")) name else "--$name"
        return StyleExpression.VariableRef(normalized)
    }

    private fun setSpacing(
        property: StyleProperty,
        top: CssLength,
        right: CssLength,
        bottom: CssLength,
        left: CssLength
    ) {
        setLiteral(
            property,
            "${top.toCssLiteral()} ${right.toCssLiteral()} ${bottom.toCssLiteral()} ${left.toCssLiteral()}"
        )
    }

    private fun requireNonNegative(value: CssLength, propertyName: String) {
        require(value.value >= 0f) { "$propertyName cannot be negative: ${value.toCssLiteral()}" }
    }

    private fun maxLength(first: CssLength, second: CssLength): CssLength {
        require(first.unit == second.unit) {
            "Border shorthand with mixed units is not supported: ${first.toCssLiteral()} and ${second.toCssLiteral()}"
        }
        return if (first.value >= second.value) first else second
    }

    private fun maxLength(first: CssLength, second: CssLength, third: CssLength, fourth: CssLength): CssLength {
        val firstMax = maxLength(first, second)
        val secondMax = maxLength(third, fourth)
        return maxLength(firstMax, secondMax)
    }

    private fun setLiteral(property: StyleProperty, rawValue: String) {
        node.inlineStyleDeclarations.set(property, StyleExpression.Literal(rawValue))
    }

    private fun setExpression(property: StyleProperty, expression: StyleExpression.VariableRef) {
        node.inlineStyleDeclarations.set(property, expression)
    }

    private fun toColorLiteral(value: Int): String {
        val unsigned = value.toLong() and 0xFFFFFFFFL
        return "#" + unsigned.toString(16).padStart(8, '0').uppercase()
    }

    private fun Display.toCssLiteral(): String = when (this) {
        Display.Block -> "block"
        Display.Inline -> "inline"
        Display.None -> "none"
        Display.Flex -> "flex"
        Display.Grid -> "grid"
    }

    private fun FlexDirection.toCssLiteral(): String = when (this) {
        FlexDirection.Row -> "row"
        FlexDirection.Column -> "column"
    }

    private fun JustifyContent.toCssLiteral(): String = when (this) {
        JustifyContent.Start -> "start"
        JustifyContent.Center -> "center"
        JustifyContent.End -> "end"
        JustifyContent.SpaceBetween -> "space-between"
        JustifyContent.SpaceAround -> "space-around"
        JustifyContent.SpaceEvenly -> "space-evenly"
    }

    private fun AlignItems.toCssLiteral(): String = when (this) {
        AlignItems.Start -> "start"
        AlignItems.Center -> "center"
        AlignItems.End -> "end"
        AlignItems.Stretch -> "stretch"
    }

    private fun JustifyItems.toCssLiteral(): String = when (this) {
        JustifyItems.Start -> "start"
        JustifyItems.Center -> "center"
        JustifyItems.End -> "end"
        JustifyItems.Stretch -> "stretch"
    }

    private fun GridAutoFlow.toCssLiteral(): String = when (this) {
        GridAutoFlow.Row -> "row"
        GridAutoFlow.Column -> "column"
    }

    private fun TextWrap.toCssLiteral(): String = when (this) {
        TextWrap.Wrap -> "wrap"
        TextWrap.NoWrap -> "nowrap"
    }

    private fun TextFormatting.toCssLiteral(): String = when (this) {
        TextFormatting.None -> "none"
        TextFormatting.Minecraft -> "minecraft"
    }

    private fun FontWeight.toCssLiteral(): String = when (this) {
        FontWeight.Normal -> "normal"
        FontWeight.Bold -> "bold"
    }

    private fun FontStyle.toCssLiteral(): String = when (this) {
        FontStyle.Normal -> "normal"
        FontStyle.Italic -> "italic"
    }

    private fun TextDecoration.toCssLiteral(): String = when (this) {
        TextDecoration.None -> "none"
        TextDecoration.Underline -> "underline"
        TextDecoration.Strikethrough -> "strikethrough"
        TextDecoration.UnderlineStrikethrough -> "underline-strikethrough"
    }

    private fun UiTransform.toCssLiteral(): String {
        if (isIdentity()) return "none"
        return buildString {
            append("translate(")
            append(translateX)
            append(",")
            append(translateY)
            append(") ")
            append("scale(")
            append(scaleX)
            append(",")
            append(scaleY)
            append(") ")
            append("rotate(")
            append(rotateDeg)
            append("deg)")
        }
    }
}

@DsglDsl
/**
 * Button-specific DSL scope.
 */
class ButtonScope internal constructor(private val node: ButtonNode) {
    fun onClick(handler: (MouseClickEvent) -> Unit) {
        node.onClick(handler)
    }
}
