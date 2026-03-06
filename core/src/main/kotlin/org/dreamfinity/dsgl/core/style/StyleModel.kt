package org.dreamfinity.dsgl.core.style

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.font.FontRegistry

enum class StylePseudoState {
    HOVER,
    ACTIVE,
    FOCUS,
    DISABLED
}

enum class StyleAlign {
    START,
    CENTER,
    END
}

enum class Display {
    Block,
    Inline,
    None,
    Flex,
    Grid
}

enum class FlexDirection {
    Row,
    Column
}

enum class JustifyContent {
    Start,
    Center,
    End,
    SpaceBetween,
    SpaceAround,
    SpaceEvenly
}

enum class AlignItems {
    Start,
    Center,
    End,
    Stretch
}

enum class JustifyItems {
    Start,
    Center,
    End,
    Stretch
}

enum class GridAutoFlow {
    Row,
    Column
}

enum class TextWrap {
    Wrap,
    NoWrap
}

enum class TextFormatting {
    None,
    Minecraft
}

enum class FontWeight {
    Normal,
    Bold
}

enum class FontStyle {
    Normal,
    Italic
}

enum class TextDecoration {
    None,
    Underline,
    Strikethrough,
    UnderlineStrikethrough
}

data class UiTransform(
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotateDeg: Float = 0f
) {
    fun translated(x: Float, y: Float): UiTransform = copy(translateX = translateX + x, translateY = translateY + y)
    fun scaled(x: Float, y: Float = x): UiTransform = copy(scaleX = scaleX * x, scaleY = scaleY * y)
    fun rotated(deg: Float): UiTransform = copy(rotateDeg = rotateDeg + deg)

    fun isIdentity(): Boolean {
        return translateX == 0f &&
                translateY == 0f &&
                scaleX == 1f &&
                scaleY == 1f &&
                rotateDeg == 0f
    }

    companion object {
        val IDENTITY: UiTransform = UiTransform()
    }
}

data class TransformOrigin(
    val originX: Float = 0.5f,
    val originY: Float = 0.5f
) {
    init {
        require(originX in 0f..1f) { "transform originX must be in [0..1]" }
        require(originY in 0f..1f) { "transform originY must be in [0..1]" }
    }

    companion object {
        val CENTER: TransformOrigin = TransformOrigin(0.5f, 0.5f)
    }
}

enum class StyleProperty(val key: String) {
    MARGIN("margin"),
    PADDING("padding"),
    BACKGROUND_COLOR("background-color"),
    BACKGROUND_IMAGE("background-image"),
    BORDER_COLOR("border-color"),
    BORDER_WIDTH("border-width"),
    BORDER_RADIUS("border-radius"),
    FOREGROUND_COLOR("color"),
    FONT_ID("font-id"),
    FONT_SIZE("font-size"),
    FONT_WEIGHT("font-weight"),
    FONT_STYLE("font-style"),
    TEXT_DECORATION("text-decoration"),
    OBFUSCATED("obfuscated"),
    WIDTH("width"),
    HEIGHT("height"),
    ALIGN("align"),
    DISPLAY("display"),
    FLEX_DIRECTION("flex-direction"),
    JUSTIFY_CONTENT("justify-content"),
    ALIGN_ITEMS("align-items"),
    JUSTIFY_ITEMS("justify-items"),
    GAP("gap"),
    FLEX_GROW("flex-grow"),
    FLEX_SHRINK("flex-shrink"),
    FLEX_BASIS("flex-basis"),
    GRID_COLUMNS("grid-columns"),
    GRID_ROWS("grid-rows"),
    GRID_AUTO_FLOW("grid-auto-flow"),
    GRID_COLUMN_SPAN("grid-column-span"),
    GRID_ROW_SPAN("grid-row-span"),
    TEXT_WRAP("text-wrap"),
    TEXT_FORMATTING("text-formatting"),
    TRANSFORM("transform"),
    TRANSFORM_ORIGIN("transform-origin"),
    OPACITY("opacity");

    companion object {
        private val byName: Map<String, StyleProperty> = entries.associateBy { it.key.lowercase() } +
                mapOf(
                    "backgroundcolor" to BACKGROUND_COLOR,
                    "background-color" to BACKGROUND_COLOR,
                    "backgroundimage" to BACKGROUND_IMAGE,
                    "background-image" to BACKGROUND_IMAGE,
                    "bordercolor" to BORDER_COLOR,
                    "border-color" to BORDER_COLOR,
                    "borderwidth" to BORDER_WIDTH,
                    "border-width" to BORDER_WIDTH,
                    "borderradius" to BORDER_RADIUS,
                    "border-radius" to BORDER_RADIUS,
                    "foregroundcolor" to FOREGROUND_COLOR,
                    "foreground-color" to FOREGROUND_COLOR,
                    "fontid" to FONT_ID,
                    "font-id" to FONT_ID,
                    "font" to FONT_ID,
                    "fontsize" to FONT_SIZE,
                    "font-size" to FONT_SIZE,
                    "fontweight" to FONT_WEIGHT,
                    "font-weight" to FONT_WEIGHT,
                    "fontstyle" to FONT_STYLE,
                    "font-style" to FONT_STYLE
                ) + mapOf(
            "flexdirection" to FLEX_DIRECTION,
            "flex-direction" to FLEX_DIRECTION,
            "justifycontent" to JUSTIFY_CONTENT,
            "justify-content" to JUSTIFY_CONTENT,
            "alignitems" to ALIGN_ITEMS,
            "align-items" to ALIGN_ITEMS,
            "justifyitems" to JUSTIFY_ITEMS,
            "justify-items" to JUSTIFY_ITEMS,
            "flexgrow" to FLEX_GROW,
            "flex-grow" to FLEX_GROW,
            "flexshrink" to FLEX_SHRINK,
            "flex-shrink" to FLEX_SHRINK,
            "flexbasis" to FLEX_BASIS,
            "flex-basis" to FLEX_BASIS,
            "gridcolumns" to GRID_COLUMNS,
            "grid-columns" to GRID_COLUMNS,
            "gridrows" to GRID_ROWS,
            "grid-rows" to GRID_ROWS,
            "gridautoflow" to GRID_AUTO_FLOW,
            "grid-auto-flow" to GRID_AUTO_FLOW,
            "gridcolumnspan" to GRID_COLUMN_SPAN,
            "grid-column-span" to GRID_COLUMN_SPAN,
            "gridrowspan" to GRID_ROW_SPAN,
            "grid-row-span" to GRID_ROW_SPAN,
            "textwrap" to TEXT_WRAP,
            "text-wrap" to TEXT_WRAP,
            "textformatting" to TEXT_FORMATTING,
            "text-formatting" to TEXT_FORMATTING,
            "textdecoration" to TEXT_DECORATION,
            "text-decoration" to TEXT_DECORATION,
            "obfuscated" to OBFUSCATED,
            "transform" to TRANSFORM,
            "transformorigin" to TRANSFORM_ORIGIN,
            "transform-origin" to TRANSFORM_ORIGIN,
            "opacity" to OPACITY
        )

        fun fromKeyOrNull(name: String): StyleProperty? = byName[name.trim().lowercase()]
    }
}

sealed class StyleExpression {
    data class Literal(val value: String) : StyleExpression()
    data class VariableRef(val name: String) : StyleExpression()
}

data class StyleDeclarations(
    val values: MutableMap<StyleProperty, StyleExpression> = linkedMapOf(),
    val importantProperties: MutableSet<StyleProperty> = linkedSetOf()
) {
    fun set(property: StyleProperty, value: StyleExpression, important: Boolean = false) {
        values[property] = value
        if (important) {
            importantProperties += property
        } else {
            importantProperties -= property
        }
    }

    fun get(property: StyleProperty): StyleExpression? = values[property]

    fun isImportant(property: StyleProperty): Boolean = property in importantProperties

    fun remove(property: StyleProperty) {
        values.remove(property)
        importantProperties.remove(property)
    }

    fun isEmpty(): Boolean = values.isEmpty()

    fun mergeFrom(other: StyleDeclarations) {
        other.values.forEach { (property, expression) ->
            set(property, expression, other.isImportant(property))
        }
    }

    fun toStableHash(): Int {
        if (values.isEmpty()) {
            return 1
        }
        var result = 1
        for (property in StyleProperty.entries) {
            val expression = values[property] ?: continue
            result = 31 * result + property.ordinal
            result = 31 * result + expression.hashCode()
            result = 31 * result + if (property in importantProperties) 1 else 0
        }
        return result
    }
}

data class ComputedStyle(
    val margin: LengthInsets,
    val padding: LengthInsets,
    val backgroundColor: Int?,
    val backgroundImage: String?,
    val borderColor: Int,
    val borderWidth: CssLength,
    val borderRadius: CssLength,
    val foregroundColor: Int,
    val fontId: String?,
    val fontSize: Int?,
    val fontSizeValue: CssLength?,
    val fontWeight: FontWeight,
    val fontStyle: FontStyle,
    val textDecoration: TextDecoration,
    val obfuscated: Boolean,
    val width: CssLength?,
    val height: CssLength?,
    val align: StyleAlign,
    val display: Display,
    val flexDirection: FlexDirection,
    val justifyContent: JustifyContent,
    val alignItems: AlignItems,
    val justifyItems: JustifyItems,
    val gap: CssLength,
    val flexGrow: Float,
    val flexShrink: Float,
    val flexBasis: CssLength?,
    val gridColumns: Int,
    val gridRows: Int?,
    val gridAutoFlow: GridAutoFlow,
    val gridColumnSpan: Int,
    val gridRowSpan: Int,
    val textWrap: TextWrap,
    val textFormatting: TextFormatting,
    val transform: UiTransform,
    val transformOrigin: TransformOrigin,
    val opacity: Float
)

data class ComputedStyleDefaults(
    val margin: LengthInsets = LengthInsets.ZERO,
    val padding: LengthInsets = LengthInsets.ZERO,
    val backgroundColor: Int? = null,
    val backgroundImage: String? = null,
    val borderColor: Int = DsglColors.BORDER,
    val borderWidth: CssLength = CssLength.ZERO_PX,
    val borderRadius: CssLength = CssLength.ZERO_PX,
    val foregroundColor: Int = DsglColors.TEXT,
    val fontId: String? = FontRegistry.DEFAULT_FONT_ID,
    val fontSize: Int? = null,
    val fontSizeValue: CssLength? = null,
    val fontWeight: FontWeight = FontWeight.Normal,
    val fontStyle: FontStyle = FontStyle.Normal,
    val textDecoration: TextDecoration = TextDecoration.None,
    val obfuscated: Boolean = false,
    val width: CssLength? = null,
    val height: CssLength? = null,
    val align: StyleAlign = StyleAlign.START,
    val display: Display = Display.Block,
    val flexDirection: FlexDirection = FlexDirection.Row,
    val justifyContent: JustifyContent = JustifyContent.Start,
    val alignItems: AlignItems = AlignItems.Stretch,
    val justifyItems: JustifyItems = JustifyItems.Stretch,
    val gap: CssLength = CssLength.ZERO_PX,
    val flexGrow: Float = 0f,
    val flexShrink: Float = 1f,
    val flexBasis: CssLength? = null,
    val gridColumns: Int = 2,
    val gridRows: Int? = null,
    val gridAutoFlow: GridAutoFlow = GridAutoFlow.Row,
    val gridColumnSpan: Int = 1,
    val gridRowSpan: Int = 1,
    val textWrap: TextWrap = TextWrap.Wrap,
    val textFormatting: TextFormatting = TextFormatting.None,
    val transform: UiTransform = UiTransform.IDENTITY,
    val transformOrigin: TransformOrigin = TransformOrigin.CENTER,
    val opacity: Float = 1f
) {
    fun toComputedStyle(): ComputedStyle {
        return ComputedStyle(
            margin = margin,
            padding = padding,
            backgroundColor = backgroundColor,
            backgroundImage = backgroundImage,
            borderColor = borderColor,
            borderWidth = borderWidth,
            borderRadius = borderRadius,
            foregroundColor = foregroundColor,
            fontId = fontId,
            fontSize = fontSize,
            fontSizeValue = fontSizeValue,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textDecoration = textDecoration,
            obfuscated = obfuscated,
            width = width,
            height = height,
            align = align,
            display = display,
            flexDirection = flexDirection,
            justifyContent = justifyContent,
            alignItems = alignItems,
            justifyItems = justifyItems,
            gap = gap,
            flexGrow = flexGrow,
            flexShrink = flexShrink,
            flexBasis = flexBasis,
            gridColumns = gridColumns,
            gridRows = gridRows,
            gridAutoFlow = gridAutoFlow,
            gridColumnSpan = gridColumnSpan,
            gridRowSpan = gridRowSpan,
            textWrap = textWrap,
            textFormatting = textFormatting,
            transform = transform,
            transformOrigin = transformOrigin,
            opacity = opacity
        )
    }
}
