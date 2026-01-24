package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.*
import org.dreamfinity.dsgl.core.dom.layout.Border
import org.dreamfinity.dsgl.core.dom.layout.Insets
import org.dreamfinity.dsgl.core.dom.layout.LayoutDirection
import org.dreamfinity.dsgl.core.event.*
import java.time.Instant
import java.time.ZoneId

@DslMarker
annotation class DsglDsl

fun ui(block: UiScope.() -> Unit): DomTree {
    val root = ContainerNode(layout = LayoutDirection.Stack)
    val scope = UiScope(root)
    scope.block()
    return DomTree(root)
}

open class ComponentProps(
    var color: Int = DsglColors.TEXT,
    var padding: Int = 0,
    var gap: Int = 0,
    var width: Int? = null,
    var height: Int? = null,
    var backgroundColor: Int = DsglColors.PANEL,
    var key: Any? = null,
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
    var onKeyReleased: ((KeyboardKeyUpEvent) -> Unit)? = null
)

open class TextProps(var value: String = "") : ComponentProps()
open class DynamicTextProps(var valueProvider: () -> String, var placeholder: String = "") : TextProps()
open class TextAreaProps(var placeholder: String = "") : TextProps()
open class InputProps(val type: InputType) : TextProps()
open class ImageProps(var url: String) : ComponentProps()
open class ItemStackProps(
    var stack: ItemStackRef,
    var size: Int = 18,
    var rotYDeg: Double? = null,
    var rotXDeg: Double? = null
) : ComponentProps()
open class ButtonProps(var text: String) : TextProps()

@DsglDsl
class UiScope internal constructor(private val parent: ContainerNode) {
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
class StyleScope internal constructor(private val node: DOMNode) {
    fun margin(all: Int) {
        node.margin = Insets.all(all)
    }

    fun margin(horizontal: Int, vertical: Int) {
        node.margin = Insets.horizontalVertical(horizontal, vertical)
    }

    fun margin(top: Int, right: Int, bottom: Int, left: Int) {
        node.margin = Insets(top, right, bottom, left)
    }

    fun padding(all: Int) {
        node.padding = Insets.all(all)
    }

    fun padding(horizontal: Int, vertical: Int) {
        node.padding = Insets.horizontalVertical(horizontal, vertical)
    }

    fun padding(top: Int, right: Int, bottom: Int, left: Int) {
        node.padding = Insets(top, right, bottom, left)
    }

    fun border(width: Int) {
        node.border = Border.all(width, DsglColors.BORDER)
    }

    fun border(width: Int, color: Int) {
        node.border = Border.all(width, color)
    }

    fun border(horizontal: Int, vertical: Int, color: Int = DsglColors.BORDER) {
        node.border = Border.horizontalVertical(horizontal, vertical, color)
    }

    fun border(top: Int, right: Int, bottom: Int, left: Int, color: Int = DsglColors.BORDER) {
        node.border = Border(top, right, bottom, left, color)
    }
}

@DsglDsl
class ButtonScope internal constructor(private val node: ButtonNode) {
    fun onClick(handler: (MouseClickEvent) -> Unit) {
        node.onClick(handler)
    }
}
