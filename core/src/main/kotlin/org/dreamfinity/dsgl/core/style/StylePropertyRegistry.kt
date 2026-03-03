package org.dreamfinity.dsgl.core.style

enum class StyleEditorValueType {
    EnumChoice,
    IntNumber,
    FloatNumber,
    OptionalIntNumber,
    Spacing,
    ColorHex,
    StringPreset
}

data class StylePropertyDescriptor(
    val property: StyleProperty,
    val valueType: StyleEditorValueType,
    val enumOptions: List<String> = emptyList(),
    val numericStep: Float = 1f,
    val minInt: Int = 0,
    val minFloat: Float = 0f,
    val isInherited: Boolean = false
)

object StylePropertyRegistry {
    val all: List<StylePropertyDescriptor> = listOf(
        StylePropertyDescriptor(StyleProperty.DISPLAY, StyleEditorValueType.EnumChoice, enumOptions = listOf("block", "inline", "none", "flex", "grid")),
        StylePropertyDescriptor(StyleProperty.WIDTH, StyleEditorValueType.IntNumber, numericStep = 4f),
        StylePropertyDescriptor(StyleProperty.HEIGHT, StyleEditorValueType.IntNumber, numericStep = 4f),
        StylePropertyDescriptor(StyleProperty.MARGIN, StyleEditorValueType.Spacing, numericStep = 1f),
        StylePropertyDescriptor(StyleProperty.PADDING, StyleEditorValueType.Spacing, numericStep = 1f),
        StylePropertyDescriptor(StyleProperty.BACKGROUND_COLOR, StyleEditorValueType.ColorHex, enumOptions = colorPalette()),
        StylePropertyDescriptor(
            StyleProperty.BACKGROUND_IMAGE,
            StyleEditorValueType.StringPreset,
            enumOptions = listOf(
                "textures/gui/options_background.png",
                "minecraft:textures/blocks/stone.png"
            )
        ),
        StylePropertyDescriptor(StyleProperty.BORDER_COLOR, StyleEditorValueType.ColorHex, enumOptions = colorPalette()),
        StylePropertyDescriptor(StyleProperty.BORDER_WIDTH, StyleEditorValueType.IntNumber, numericStep = 1f),
        StylePropertyDescriptor(StyleProperty.BORDER_RADIUS, StyleEditorValueType.IntNumber, numericStep = 1f),
        StylePropertyDescriptor(StyleProperty.FOREGROUND_COLOR, StyleEditorValueType.ColorHex, enumOptions = colorPalette(), isInherited = true),
        StylePropertyDescriptor(
            StyleProperty.FONT_ID,
            StyleEditorValueType.StringPreset,
            enumOptions = listOf("minecraft", "ubuntu", "JetBrains Mono"),
            isInherited = true
        ),
        StylePropertyDescriptor(StyleProperty.FONT_SIZE, StyleEditorValueType.IntNumber, numericStep = 1f, minInt = 1, isInherited = true),
        StylePropertyDescriptor(StyleProperty.FONT_WEIGHT, StyleEditorValueType.EnumChoice, enumOptions = listOf("normal", "bold"), isInherited = true),
        StylePropertyDescriptor(StyleProperty.FONT_STYLE, StyleEditorValueType.EnumChoice, enumOptions = listOf("normal", "italic"), isInherited = true),
        StylePropertyDescriptor(
            StyleProperty.TEXT_DECORATION,
            StyleEditorValueType.EnumChoice,
            enumOptions = listOf("none", "underline", "strikethrough", "underline-strikethrough")
        ),
        StylePropertyDescriptor(StyleProperty.OBFUSCATED, StyleEditorValueType.EnumChoice, enumOptions = listOf("false", "true")),
        StylePropertyDescriptor(StyleProperty.ALIGN, StyleEditorValueType.EnumChoice, enumOptions = listOf("start", "center", "end")),
        StylePropertyDescriptor(StyleProperty.FLEX_DIRECTION, StyleEditorValueType.EnumChoice, enumOptions = listOf("row", "column")),
        StylePropertyDescriptor(
            StyleProperty.JUSTIFY_CONTENT,
            StyleEditorValueType.EnumChoice,
            enumOptions = listOf("start", "center", "end", "space-between", "space-around", "space-evenly")
        ),
        StylePropertyDescriptor(StyleProperty.ALIGN_ITEMS, StyleEditorValueType.EnumChoice, enumOptions = listOf("start", "center", "end", "stretch")),
        StylePropertyDescriptor(StyleProperty.JUSTIFY_ITEMS, StyleEditorValueType.EnumChoice, enumOptions = listOf("start", "center", "end", "stretch")),
        StylePropertyDescriptor(StyleProperty.GAP, StyleEditorValueType.IntNumber, numericStep = 1f),
        StylePropertyDescriptor(StyleProperty.FLEX_GROW, StyleEditorValueType.FloatNumber, numericStep = 0.25f),
        StylePropertyDescriptor(StyleProperty.FLEX_SHRINK, StyleEditorValueType.FloatNumber, numericStep = 0.25f),
        StylePropertyDescriptor(StyleProperty.FLEX_BASIS, StyleEditorValueType.OptionalIntNumber, numericStep = 4f),
        StylePropertyDescriptor(StyleProperty.GRID_COLUMNS, StyleEditorValueType.IntNumber, numericStep = 1f, minInt = 1),
        StylePropertyDescriptor(StyleProperty.GRID_ROWS, StyleEditorValueType.OptionalIntNumber, numericStep = 1f, minInt = 1),
        StylePropertyDescriptor(StyleProperty.GRID_AUTO_FLOW, StyleEditorValueType.EnumChoice, enumOptions = listOf("row", "column")),
        StylePropertyDescriptor(StyleProperty.GRID_COLUMN_SPAN, StyleEditorValueType.IntNumber, numericStep = 1f, minInt = 1),
        StylePropertyDescriptor(StyleProperty.GRID_ROW_SPAN, StyleEditorValueType.IntNumber, numericStep = 1f, minInt = 1),
        StylePropertyDescriptor(StyleProperty.TEXT_WRAP, StyleEditorValueType.EnumChoice, enumOptions = listOf("wrap", "nowrap")),
        StylePropertyDescriptor(StyleProperty.TEXT_FORMATTING, StyleEditorValueType.EnumChoice, enumOptions = listOf("none", "minecraft")),
        StylePropertyDescriptor(StyleProperty.TRANSFORM, StyleEditorValueType.StringPreset, enumOptions = listOf("none", "translate(12, 0)", "scale(1.2)", "rotate(15deg)")),
        StylePropertyDescriptor(StyleProperty.TRANSFORM_ORIGIN, StyleEditorValueType.StringPreset, enumOptions = listOf("0 0", "0.5 0.5", "1 1", "50% 50%")),
        StylePropertyDescriptor(StyleProperty.OPACITY, StyleEditorValueType.FloatNumber, numericStep = 0.05f, minFloat = 0f)
    )

    private val byProperty: Map<StyleProperty, StylePropertyDescriptor> = all.associateBy { it.property }

    fun descriptor(property: StyleProperty): StylePropertyDescriptor {
        return byProperty[property] ?: error("Missing style descriptor for '${property.key}'.")
    }

    fun isInherited(property: StyleProperty): Boolean = descriptor(property).isInherited

    private fun colorPalette(): List<String> {
        return listOf(
            "#FF1B1F24",
            "#FF2D3748",
            "#FF4A5568",
            "#FF718096",
            "#FFCBD5E0",
            "#FFFFFFFF",
            "#FFE53E3E",
            "#FFDD6B20",
            "#FFD69E2E",
            "#FF38A169",
            "#FF3182CE",
            "#FF805AD5"
        )
    }
}
