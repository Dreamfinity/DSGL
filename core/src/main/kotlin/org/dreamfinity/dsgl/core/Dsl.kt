package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.*
import org.dreamfinity.dsgl.core.dom.layout.Insets
import org.dreamfinity.dsgl.core.dom.layout.LayoutDirection
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.style.StyleAlign
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty
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
    val root = ContainerNode(layout = LayoutDirection.Stack)
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
    var color: Int = DsglColors.TEXT,
    var padding: Int = 0,
    var gap: Int = 0,
    var width: Int? = null,
    var height: Int? = null,
    var backgroundColor: Int = DsglColors.PANEL,
    var key: Any? = null,
    var id: String? = null,
    var className: String = "",
    var classes: Set<String> = emptySet(),
    var disabled: Boolean = false,
    var style: StyleScope.() -> Unit = {},
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
    var onValueChange: ((ValueChangedEvent) -> Unit)? = null
) {
    fun classNames(value: String) {
        className = value
    }
}

/** Static text props. */
open class TextProps(var value: String = "") : ComponentProps()
/** Dynamic text computed on each rebuild. */
open class DynamicTextProps(var placeholder: String = "", var valueProvider: () -> String) : TextProps()
/** Multiline text area props. */
open class TextAreaProps(var placeholder: String = "") : TextProps()
/** Input node props, driven by [InputType]. */
open class InputProps(val type: InputType) : TextProps()
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

@DsglDsl
/**
 * Root DSL scope used by [ui] to add layout and component nodes.
 */
class UiScope internal constructor(private val parent: ContainerNode) {
    /** Vertical layout container. */
    fun column(
        props: ComponentProps = ComponentProps(),
        block: UiScope.() -> Unit = {}
    ): ContainerNode = ContainerNode(
        LayoutDirection.Column,
        props.padding,
        props.gap,
        props.backgroundColor,
        props.key
    ).apply {
        width = props.width
        height = props.height
        applyStyle(this, props.style)
        applyHandlers(this, props)
        add(this)
        UiScope(this).block()
    }


    /** Horizontal layout container. */
    fun row(
        props: ComponentProps = ComponentProps(),
        block: UiScope.() -> Unit = {}
    ): ContainerNode = ContainerNode(
        LayoutDirection.Row,
        props.padding,
        props.gap,
        props.backgroundColor,
        props.key
    ).apply {
        this.width = props.width
        this.height = props.height
        applyStyle(this, props.style)
        applyHandlers(this, props)
        add(this)
        UiScope(this).block()
    }


    /** Shorthand for a column container. */
    fun div(
        props: ComponentProps = ComponentProps(),
        block: UiScope.() -> Unit = {}
    ) = ContainerNode(
        LayoutDirection.Column,
        props.padding,
        props.gap,
        props.backgroundColor,
        props.key
    ).apply {
        this.width = props.width
        this.height = props.height
        applyStyle(this, props.style)
        applyHandlers(this, props)
        add(this)
        UiScope(this).block()
    }


    /** Static text node. */
    fun text(props: TextProps) = TextNode(
        props.value,
        props.color,
        props.key
    ).apply {
        this.width = props.width
        this.height = props.height
        applyStyle(this, props.style)
        applyHandlers(this, props)
        add(this)
    }

    /** Dynamic text node built from a provider. */
    fun dynamicText(props: DynamicTextProps) = DynamicTextNode(
        props.valueProvider,
        props.color,
        props.key
    ).apply {
        this.width = props.width
        this.height = props.height
        applyStyle(this, props.style)
        applyHandlers(this, props)
        add(this)
    }


    /** Button node with optional extra button scope. */
    fun button(
        props: ButtonProps,
        block: ButtonScope.() -> Unit = {}
    ) = ButtonNode(
        props.text,
        props.color,
        props.backgroundColor,
        props.padding,
        props.key
    ).apply {
        this.width = props.width
        this.height = props.height
        applyStyle(this, props.style)
        applyHandlers(this, props)
        props.onMouseClick?.let { this.onClick(it) }
        add(this)
        ButtonScope(this).block()
    }


    /** Image node from resource, file, or URL (host-dependent). */
    fun img(props: ImageProps) = ImageNode(
        props.url,
        props.width ?: 0,
        props.height ?: 0,
        props.key
    ).apply {
        applyStyle(props.style)
        applyHandlers(props)
        add(this)
    }

    /** Item stack node for platform-specific stack types. */
    fun itemStack(props: ItemStackProps) = ItemStackNode(
        props.stack,
        props.size,
        props.rotYDeg,
        props.rotXDeg,
        props.key
    ).apply {
        this.width = props.width
        this.height = props.height
        applyStyle(this, props.style)
        applyHandlers(this, props)
        add(this)
    }

    /** Input node backed by an [InputType]. */
    fun input(props: InputProps) = when (props.type) {
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
        this.width = props.width
        this.height = props.height
        applyStyle(this, props.style)
        applyHandlers(this, props)
        add(this)
    }


    /** Multiline text input area. */
    fun textarea(props: TextAreaProps) = TextAreaNode(
        props.value,
        props.placeholder,
        props.key
    ).apply {
        width = props.width
        height = props.height
        applyStyle(this, props.style)
        applyHandlers(this, props)
        add(this)
    }

    private fun <T : DOMNode> add(node: T): T {
        return node.applyParent(parent)
    }

    private fun applyHandlers(node: DOMNode, props: ComponentProps) {
        node.applyHandlers(props)
    }

    private fun applyStyle(node: DOMNode, style: StyleScope.() -> Unit) {
        node.applyStyle(style)
    }
}

@DsglDsl
/**
 * Styling DSL attached to a [DOMNode].
 */
class StyleScope internal constructor(private val node: DOMNode) {
    fun margin(all: Int) {
        setSpacing(StyleProperty.MARGIN, Insets.all(all))
    }

    fun margin(horizontal: Int, vertical: Int) {
        setSpacing(StyleProperty.MARGIN, Insets.horizontalVertical(horizontal, vertical))
    }

    fun margin(top: Int, right: Int, bottom: Int, left: Int) {
        setSpacing(StyleProperty.MARGIN, Insets(top, right, bottom, left))
    }

    fun padding(all: Int) {
        setSpacing(StyleProperty.PADDING, Insets.all(all))
    }

    fun padding(horizontal: Int, vertical: Int) {
        setSpacing(StyleProperty.PADDING, Insets.horizontalVertical(horizontal, vertical))
    }

    fun padding(top: Int, right: Int, bottom: Int, left: Int) {
        setSpacing(StyleProperty.PADDING, Insets(top, right, bottom, left))
    }

    fun border(width: Int) {
        borderWidth(width)
        borderColor(DsglColors.BORDER)
    }

    fun border(width: Int, color: Int) {
        borderWidth(width)
        borderColor(color)
    }

    fun border(horizontal: Int, vertical: Int, color: Int = DsglColors.BORDER) {
        border(maxOf(horizontal, vertical), color)
    }

    fun border(top: Int, right: Int, bottom: Int, left: Int, color: Int = DsglColors.BORDER) {
        border(maxOf(top, right, bottom, left), color)
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

    fun borderWidth(value: Int) {
        setLiteral(StyleProperty.BORDER_WIDTH, value.toString())
    }

    fun borderWidth(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.BORDER_WIDTH, variable)
    }

    fun borderRadius(value: Int) {
        setLiteral(StyleProperty.BORDER_RADIUS, value.toString())
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

    fun fontSize(value: Int) {
        setLiteral(StyleProperty.FONT_SIZE, value.toString())
    }

    fun fontSize(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.FONT_SIZE, variable)
    }

    fun width(value: Int) {
        setLiteral(StyleProperty.WIDTH, value.toString())
    }

    fun width(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.WIDTH, variable)
    }

    fun height(value: Int) {
        setLiteral(StyleProperty.HEIGHT, value.toString())
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

    private fun setSpacing(property: StyleProperty, value: Insets) {
        setLiteral(property, "${value.top} ${value.right} ${value.bottom} ${value.left}")
    }

    private fun setLiteral(property: StyleProperty, rawValue: String) {
        node.inlineStyleDecls.set(property, StyleExpression.Literal(rawValue))
    }

    private fun setExpression(property: StyleProperty, expression: StyleExpression.VariableRef) {
        node.inlineStyleDecls.set(property, expression)
    }

    private fun toColorLiteral(value: Int): String {
        val unsigned = value.toLong() and 0xFFFFFFFFL
        return "#" + unsigned.toString(16).padStart(8, '0').uppercase()
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
