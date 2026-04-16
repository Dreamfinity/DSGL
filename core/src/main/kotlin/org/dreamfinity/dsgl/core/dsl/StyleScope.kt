package org.dreamfinity.dsgl.core.dsl

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.animation.AnimationListBuilder
import org.dreamfinity.dsgl.core.animation.TransitionBuilder
import org.dreamfinity.dsgl.core.animation.UiTransformBuilder
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.style.AlignItems
import org.dreamfinity.dsgl.core.style.CssLength
import org.dreamfinity.dsgl.core.style.CssUnit
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.FontStyle
import org.dreamfinity.dsgl.core.style.FontWeight
import org.dreamfinity.dsgl.core.style.GridAutoFlow
import org.dreamfinity.dsgl.core.style.JustifyContent
import org.dreamfinity.dsgl.core.style.JustifyItems
import org.dreamfinity.dsgl.core.style.LineHeightValue
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.PositionMode
import org.dreamfinity.dsgl.core.style.StyleAlign
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty
import org.dreamfinity.dsgl.core.style.TextDecoration
import org.dreamfinity.dsgl.core.style.TextFormatting
import org.dreamfinity.dsgl.core.style.TextWrap
import org.dreamfinity.dsgl.core.style.UiTransform
import org.dreamfinity.dsgl.core.style.toCssLiteral

@DsglDsl
/**
 * Styling DSL attached to a [org.dreamfinity.dsgl.core.dom.DOMNode].
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

    fun lineHeightNormal() {
        setLiteral(StyleProperty.LINE_HEIGHT, "normal")
    }

    fun lineHeight(value: CssLength) {
        requireNonNegative(value, "line-height")
        setLiteral(StyleProperty.LINE_HEIGHT, value.toCssLiteral())
    }

    fun lineHeight(variable: StyleExpression.VariableRef) {
        setExpression(StyleProperty.LINE_HEIGHT, variable)
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