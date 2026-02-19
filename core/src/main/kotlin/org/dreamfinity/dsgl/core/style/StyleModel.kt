package org.dreamfinity.dsgl.core.style

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.layout.Insets

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

enum class StyleProperty(val key: String) {
    MARGIN("margin"),
    PADDING("padding"),
    BACKGROUND_COLOR("background-color"),
    BACKGROUND_IMAGE("background-image"),
    BORDER_COLOR("border-color"),
    BORDER_WIDTH("border-width"),
    BORDER_RADIUS("border-radius"),
    FOREGROUND_COLOR("color"),
    FONT_SIZE("font-size"),
    WIDTH("width"),
    HEIGHT("height"),
    ALIGN("align");

    companion object {
        private val byName: Map<String, StyleProperty> = entries.associateBy { it.key.lowercase() } +
            mapOf(
                "background-color" to BACKGROUND_COLOR,
                "background-image" to BACKGROUND_IMAGE,
                "border-color" to BORDER_COLOR,
                "border-width" to BORDER_WIDTH,
                "border-radius" to BORDER_RADIUS,
                "foreground-color" to FOREGROUND_COLOR,
                "font-size" to FONT_SIZE
            )

        fun fromKeyOrNull(name: String): StyleProperty? = byName[name.trim().lowercase()]
    }
}

sealed class StyleExpression {
    data class Literal(val value: String) : StyleExpression()
    data class VariableRef(val name: String) : StyleExpression()
}

data class StyleDecls(
    val values: MutableMap<StyleProperty, StyleExpression> = linkedMapOf()
) {
    fun set(property: StyleProperty, value: StyleExpression) {
        values[property] = value
    }

    fun get(property: StyleProperty): StyleExpression? = values[property]

    fun isEmpty(): Boolean = values.isEmpty()

    fun mergeFrom(other: StyleDecls) {
        other.values.forEach { (property, expression) ->
            values[property] = expression
        }
    }

    fun toStableHash(): Int {
        var result = 1
        values.entries.sortedBy { it.key.ordinal }.forEach { entry ->
            result = 31 * result + entry.key.ordinal
            result = 31 * result + entry.value.hashCode()
        }
        return result
    }
}

data class ComputedStyle(
    val margin: Insets,
    val padding: Insets,
    val backgroundColor: Int?,
    val backgroundImage: String?,
    val borderColor: Int,
    val borderWidth: Int,
    val borderRadius: Int,
    val foregroundColor: Int,
    val fontSize: Int?,
    val width: Int?,
    val height: Int?,
    val align: StyleAlign
)

data class ComputedStyleDefaults(
    val margin: Insets = Insets.ZERO,
    val padding: Insets = Insets.ZERO,
    val backgroundColor: Int? = null,
    val backgroundImage: String? = null,
    val borderColor: Int = DsglColors.BORDER,
    val borderWidth: Int = 0,
    val borderRadius: Int = 0,
    val foregroundColor: Int = DsglColors.TEXT,
    val fontSize: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val align: StyleAlign = StyleAlign.START
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
            fontSize = fontSize,
            width = width,
            height = height,
            align = align
        )
    }
}
