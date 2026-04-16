package org.dreamfinity.dsgl.core.dsl

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.animation.*
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.style.*

data class StyleBorder(
    val width: CssLength,
    val color: Int = DsglColors.BORDER
)

data class StyleSpacing(
    val top: CssLength,
    val right: CssLength,
    val bottom: CssLength,
    val left: CssLength
)

data class StyleOverflowAxes(
    val x: Overflow,
    val y: Overflow
)

interface CssLengthUnitsDsl {
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
}

@DsglDsl
class StyleBorderBuilder : CssLengthUnitsDsl {
    var width: CssLength = CssLength.ZERO_PX
    var color: Int = DsglColors.BORDER

    internal fun build(): StyleBorder = StyleBorder(width = width, color = color)
}

@DsglDsl
class StyleTransformOriginBuilder : CssLengthUnitsDsl {
    var x: Float = 0.5f
    var y: Float = 0.5f

    var originX: Float
        get() = x
        set(value) {
            x = value
        }

    var originY: Float
        get() = y
        set(value) {
            y = value
        }

    internal fun build(): TransformOrigin {
        return TransformOrigin(
            originX = x.coerceIn(0f, 1f),
            originY = y.coerceIn(0f, 1f)
        )
    }
}

@DsglDsl
class StyleSpacingBuilder : CssLengthUnitsDsl {
    var top: CssLength = CssLength.ZERO_PX
    var right: CssLength = CssLength.ZERO_PX
    var bottom: CssLength = CssLength.ZERO_PX
    var left: CssLength = CssLength.ZERO_PX

    fun all(value: CssLength) {
        top = value
        right = value
        bottom = value
        left = value
    }

    fun horizontal(value: CssLength) {
        left = value
        right = value
    }

    fun vertical(value: CssLength) {
        top = value
        bottom = value
    }

    internal fun build(): StyleSpacing {
        return StyleSpacing(
            top = top,
            right = right,
            bottom = bottom,
            left = left
        )
    }
}

@DsglDsl
class StyleLineHeightBuilder : CssLengthUnitsDsl {
    private var useNormal: Boolean = false
    private var length: CssLength? = null

    fun normal() {
        useNormal = true
        length = null
    }

    fun length(value: CssLength) {
        useNormal = false
        length = value
    }

    var value: CssLength?
        get() = length
        set(newValue) {
            if (newValue == null) {
                normal()
            } else {
                length(newValue)
            }
        }

    internal fun build(): LineHeightValue {
        val currentLength = length
        return when {
            currentLength != null -> LineHeightValue.Length(currentLength)
            useNormal -> LineHeightValue.Normal
            else -> LineHeightValue.Normal
        }
    }
}

@DsglDsl
class StyleOverflowBuilder : CssLengthUnitsDsl {
    var x: Overflow = Overflow.Visible
    var y: Overflow = Overflow.Visible

    fun all(value: Overflow) {
        x = value
        y = value
    }

    internal fun build(): StyleOverflowAxes = StyleOverflowAxes(x = x, y = y)
}

@DsglDsl
class StyleInsetBuilder : CssLengthUnitsDsl {
    private var leftAssigned: Boolean = false
    private var topAssigned: Boolean = false
    private var rightAssigned: Boolean = false
    private var bottomAssigned: Boolean = false

    var left: CssLength? = null
        set(value) {
            leftAssigned = true
            field = value
        }

    var top: CssLength? = null
        set(value) {
            topAssigned = true
            field = value
        }

    var right: CssLength? = null
        set(value) {
            rightAssigned = true
            field = value
        }

    var bottom: CssLength? = null
        set(value) {
            bottomAssigned = true
            field = value
        }

    internal fun hasLeft(): Boolean = leftAssigned
    internal fun hasTop(): Boolean = topAssigned
    internal fun hasRight(): Boolean = rightAssigned
    internal fun hasBottom(): Boolean = bottomAssigned
}

@DsglDsl
/**
 * Styling DSL attached to a [org.dreamfinity.dsgl.core.dom.DOMNode].
 */
class StyleScope internal constructor(private val node: DOMNode) : CssLengthUnitsDsl {

    var display: Display
        get() = Display.Block
        set(value) {
            setLiteral(StyleProperty.DISPLAY, value.toCssLiteral())
        }

    var position: PositionMode
        get() = PositionMode.Static
        set(value) {
            setLiteral(StyleProperty.POSITION, value.toCssLiteral())
        }

    var left: CssLength?
        get() = null
        set(value) {
            if (value == null) {
                setLiteral(StyleProperty.LEFT, "auto")
            } else {
                setLiteral(StyleProperty.LEFT, value.toCssLiteral())
            }
        }

    var top: CssLength?
        get() = null
        set(value) {
            if (value == null) {
                setLiteral(StyleProperty.TOP, "auto")
            } else {
                setLiteral(StyleProperty.TOP, value.toCssLiteral())
            }
        }

    var right: CssLength?
        get() = null
        set(value) {
            if (value == null) {
                setLiteral(StyleProperty.RIGHT, "auto")
            } else {
                setLiteral(StyleProperty.RIGHT, value.toCssLiteral())
            }
        }

    var bottom: CssLength?
        get() = null
        set(value) {
            if (value == null) {
                setLiteral(StyleProperty.BOTTOM, "auto")
            } else {
                setLiteral(StyleProperty.BOTTOM, value.toCssLiteral())
            }
        }

    var zIndex: Int
        get() = 0
        set(value) {
            setLiteral(StyleProperty.Z_INDEX, value.toString())
        }

    var overflow: Overflow
        get() = Overflow.Visible
        set(value) {
            setLiteral(StyleProperty.OVERFLOW, value.toCssLiteral())
        }

    var overflowX: Overflow
        get() = Overflow.Visible
        set(value) {
            setLiteral(StyleProperty.OVERFLOW_X, value.toCssLiteral())
        }

    var overflowY: Overflow
        get() = Overflow.Visible
        set(value) {
            setLiteral(StyleProperty.OVERFLOW_Y, value.toCssLiteral())
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

    var minWidth: CssLength?
        get() = null
        set(value) {
            if (value == null) {
                setLiteral(StyleProperty.MIN_WIDTH, "auto")
            } else {
                requireNonNegative(value, "min-width")
                setLiteral(StyleProperty.MIN_WIDTH, value.toCssLiteral())
            }
        }

    var minHeight: CssLength?
        get() = null
        set(value) {
            if (value == null) {
                setLiteral(StyleProperty.MIN_HEIGHT, "auto")
            } else {
                requireNonNegative(value, "min-height")
                setLiteral(StyleProperty.MIN_HEIGHT, value.toCssLiteral())
            }
        }

    var maxWidth: CssLength?
        get() = null
        set(value) {
            if (value == null) {
                setLiteral(StyleProperty.MAX_WIDTH, "auto")
            } else {
                requireNonNegative(value, "max-width")
                setLiteral(StyleProperty.MAX_WIDTH, value.toCssLiteral())
            }
        }

    var maxHeight: CssLength?
        get() = null
        set(value) {
            if (value == null) {
                setLiteral(StyleProperty.MAX_HEIGHT, "auto")
            } else {
                requireNonNegative(value, "max-height")
                setLiteral(StyleProperty.MAX_HEIGHT, value.toCssLiteral())
            }
        }

    var color: Int
        get() = DsglColors.TEXT
        set(value) {
            setLiteral(StyleProperty.FOREGROUND_COLOR, toColorLiteral(value))
        }

    var foregroundColor: Int
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

    var backgroundImage: String
        get() = ""
        set(value) {
            setLiteral(StyleProperty.BACKGROUND_IMAGE, "\"$value\"")
        }

    var borderColor: Int
        get() = DsglColors.BORDER
        set(value) {
            setLiteral(StyleProperty.BORDER_COLOR, toColorLiteral(value))
        }

    var borderWidth: CssLength
        get() = CssLength.ZERO_PX
        set(value) {
            requireNonNegative(value, "border-width")
            setLiteral(StyleProperty.BORDER_WIDTH, value.toCssLiteral())
        }

    var borderRadius: CssLength
        get() = CssLength.ZERO_PX
        set(value) {
            requireNonNegative(value, "border-radius")
            setLiteral(StyleProperty.BORDER_RADIUS, value.toCssLiteral())
        }

    var border: StyleBorder
        get() = StyleBorder(width = CssLength.ZERO_PX)
        set(value) {
            requireNonNegative(value.width, "border-width")
            borderWidth = value.width
            borderColor = value.color
        }

    var fontId: String
        get() = ""
        set(value) {
            setLiteral(StyleProperty.FONT_ID, "\"${value.trim()}\"")
        }

    var fontSize: CssLength
        get() = CssLength.ZERO_PX
        set(value) {
            requireNonNegative(value, "font-size")
            setLiteral(StyleProperty.FONT_SIZE, value.toCssLiteral())
        }

    var align: StyleAlign
        get() = StyleAlign.START
        set(value) {
            setLiteral(StyleProperty.ALIGN, value.name.lowercase())
        }

    var transform: UiTransform
        get() = UiTransform.IDENTITY
        set(value) {
            setLiteral(StyleProperty.TRANSFORM, value.toCssLiteral())
        }

    var transformOrigin: TransformOrigin
        get() = TransformOrigin.CENTER
        set(value) {
            setLiteral(StyleProperty.TRANSFORM_ORIGIN, "${value.originX} ${value.originY}")
        }

    var transition: TransitionSpec
        get() = TransitionSpec.NONE
        set(value) {
            node.transitionSpec = value
        }

    var animations: List<AnimationSpec>
        get() = emptyList()
        set(value) {
            node.animationSpecs = value
        }

    var gap: CssLength
        get() = CssLength.Companion.ZERO_PX
        set(value) {
            requireNonNegative(value, "gap")
            setLiteral(StyleProperty.GAP, value.toCssLiteral())
        }

    var flexGrow: Float
        get() = 1.0f
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
    var lineHeight: LineHeightValue
        get() = LineHeightValue.Normal
        set(value) {
            when (value) {
                LineHeightValue.Normal -> setLiteral(StyleProperty.LINE_HEIGHT, "normal")
                is LineHeightValue.Length -> {
                    requireNonNegative(value.value, "line-height")
                    setLiteral(StyleProperty.LINE_HEIGHT, value.value.toCssLiteral())
                }
            }
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

    fun transform(block: UiTransformBuilder.() -> Unit) {
        transform = UiTransformBuilder().apply(block).build()
    }

    fun transformOrigin(value: TransformOrigin) {
        transformOrigin = value
    }

    fun transformOrigin(originX: Float, originY: Float) {
        transformOrigin = TransformOrigin(
            originX = originX.coerceIn(0f, 1f),
            originY = originY.coerceIn(0f, 1f)
        )
    }

    fun transformOrigin(block: StyleTransformOriginBuilder.() -> Unit) {
        transformOrigin = StyleTransformOriginBuilder().apply(block).build()
    }

    fun transition(block: TransitionBuilder.() -> Unit) {
        transition = TransitionBuilder().apply(block).build()
    }

    fun animation(block: AnimationListBuilder.() -> Unit) {
        animations = AnimationListBuilder().apply(block).build()
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

    fun margin(block: StyleSpacingBuilder.() -> Unit) {
        val spacing = StyleSpacingBuilder().apply(block).build()
        setSpacing(StyleProperty.MARGIN, spacing.top, spacing.right, spacing.bottom, spacing.left)
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

    fun padding(block: StyleSpacingBuilder.() -> Unit) {
        val spacing = StyleSpacingBuilder().apply(block).build()
        requireNonNegative(spacing.top, "padding")
        requireNonNegative(spacing.right, "padding")
        requireNonNegative(spacing.bottom, "padding")
        requireNonNegative(spacing.left, "padding")
        setSpacing(StyleProperty.PADDING, spacing.top, spacing.right, spacing.bottom, spacing.left)
    }

    fun border(value: StyleBorder) {
        border = value
    }

    fun border(block: StyleBorderBuilder.() -> Unit) {
        border = StyleBorderBuilder().apply(block).build()
    }

    fun border(width: CssLength) {
        border = StyleBorder(width = width)
    }

    fun border(width: CssLength, color: Int) {
        border = StyleBorder(width = width, color = color)
    }

    fun border(horizontal: CssLength, vertical: CssLength, color: Int = DsglColors.BORDER) {
        border(maxLength(horizontal, vertical), color)
    }

    fun border(top: CssLength, right: CssLength, bottom: CssLength, left: CssLength, color: Int = DsglColors.BORDER) {
        border(maxLength(top, right, bottom, left), color)
    }

    fun lineHeight(block: StyleLineHeightBuilder.() -> Unit) {
        lineHeight = StyleLineHeightBuilder().apply(block).build()
    }

    fun overflow(block: StyleOverflowBuilder.() -> Unit) {
        val axes = StyleOverflowBuilder().apply(block).build()
        overflowX = axes.x
        overflowY = axes.y
    }

    fun inset(block: StyleInsetBuilder.() -> Unit) {
        val inset = StyleInsetBuilder().apply(block)
        if (inset.hasLeft()) {
            left = inset.left
        }
        if (inset.hasTop()) {
            top = inset.top
        }
        if (inset.hasRight()) {
            right = inset.right
        }
        if (inset.hasBottom()) {
            bottom = inset.bottom
        }
    }

    fun backgroundColor(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.BACKGROUND_COLOR, variable)
    }

    fun backgroundImage(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.BACKGROUND_IMAGE, variable)
    }

    fun borderColor(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.BORDER_COLOR, variable)
    }

    fun borderWidth(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.BORDER_WIDTH, variable)
    }

    fun borderRadius(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.BORDER_RADIUS, variable)
    }

    fun foregroundColor(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.FOREGROUND_COLOR, variable)
    }

    fun fontId(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.FONT_ID, variable)
    }

    fun fontSize(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.FONT_SIZE, variable)
    }

    fun lineHeight(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.LINE_HEIGHT, variable)
    }

    fun width(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.WIDTH, variable)
    }

    fun height(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.HEIGHT, variable)
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

    private fun PositionMode.toCssLiteral(): String = when (this) {
        PositionMode.Static -> "static"
        PositionMode.Relative -> "relative"
        PositionMode.Absolute -> "absolute"
        PositionMode.Fixed -> "fixed"
        PositionMode.Sticky -> "sticky"
    }

    private fun Overflow.toCssLiteral(): String = when (this) {
        Overflow.Visible -> "visible"
        Overflow.Hidden -> "hidden"
        Overflow.Scroll -> "scroll"
        Overflow.Auto -> "auto"
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
