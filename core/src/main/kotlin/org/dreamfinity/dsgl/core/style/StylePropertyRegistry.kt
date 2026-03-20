package org.dreamfinity.dsgl.core.style

enum class StyleEditorValueType {
    EnumChoice,
    IntNumber,
    LengthPx,
    FloatNumber,
    OptionalIntNumber,
    OptionalLengthPx,
    Spacing,
    SpacingLengthPx,
    ColorHex,
    StringPreset
}

enum class StyleValueGrammarKind {
    Enum,
    UnitlessInt,
    LengthLike,
    Other
}

enum class StyleInspectorEditorKind {
    EnumSelect,
    FontSelect,
    NumericInput,
    StringInput
}

data class StylePropertyDescriptor(
    val property: StyleProperty,
    val valueType: StyleEditorValueType,
    val enumOptions: List<String> = emptyList(),
    val numericStep: Float = 1f,
    val minInt: Int = 0,
    val minFloat: Float = 0f,
    val isInherited: Boolean = false,
    val grammarKind: StyleValueGrammarKind = defaultGrammarKind(valueType),
    val inspectorEditorKind: StyleInspectorEditorKind = defaultInspectorEditorKind(property, valueType)
)

object StylePropertyRegistry {
    val all: List<StylePropertyDescriptor> = listOf(
        StylePropertyDescriptor(StyleProperty.DISPLAY, StyleEditorValueType.EnumChoice, enumOptions = listOf("block", "inline", "none", "flex", "grid")),
        StylePropertyDescriptor(
            property = StyleProperty.POSITION,
            valueType = StyleEditorValueType.EnumChoice,
            enumOptions = listOf("static", "relative", "absolute", "fixed"),
            grammarKind = StyleValueGrammarKind.Enum,
            inspectorEditorKind = StyleInspectorEditorKind.EnumSelect
        ),
        StylePropertyDescriptor(
            property = StyleProperty.LEFT,
            valueType = StyleEditorValueType.OptionalLengthPx,
            numericStep = 1f,
            grammarKind = StyleValueGrammarKind.LengthLike,
            inspectorEditorKind = StyleInspectorEditorKind.NumericInput
        ),
        StylePropertyDescriptor(
            property = StyleProperty.TOP,
            valueType = StyleEditorValueType.OptionalLengthPx,
            numericStep = 1f,
            grammarKind = StyleValueGrammarKind.LengthLike,
            inspectorEditorKind = StyleInspectorEditorKind.NumericInput
        ),
        StylePropertyDescriptor(
            property = StyleProperty.RIGHT,
            valueType = StyleEditorValueType.OptionalLengthPx,
            numericStep = 1f,
            grammarKind = StyleValueGrammarKind.LengthLike,
            inspectorEditorKind = StyleInspectorEditorKind.NumericInput
        ),
        StylePropertyDescriptor(
            property = StyleProperty.BOTTOM,
            valueType = StyleEditorValueType.OptionalLengthPx,
            numericStep = 1f,
            grammarKind = StyleValueGrammarKind.LengthLike,
            inspectorEditorKind = StyleInspectorEditorKind.NumericInput
        ),
        StylePropertyDescriptor(
            property = StyleProperty.Z_INDEX,
            valueType = StyleEditorValueType.IntNumber,
            numericStep = 1f,
            minInt = Int.MIN_VALUE,
            grammarKind = StyleValueGrammarKind.UnitlessInt,
            inspectorEditorKind = StyleInspectorEditorKind.NumericInput
        ),
        StylePropertyDescriptor(StyleProperty.WIDTH, StyleEditorValueType.LengthPx, numericStep = 4f),
        StylePropertyDescriptor(StyleProperty.HEIGHT, StyleEditorValueType.LengthPx, numericStep = 4f),
        StylePropertyDescriptor(StyleProperty.MIN_WIDTH, StyleEditorValueType.OptionalLengthPx, numericStep = 4f),
        StylePropertyDescriptor(StyleProperty.MIN_HEIGHT, StyleEditorValueType.OptionalLengthPx, numericStep = 4f),
        StylePropertyDescriptor(StyleProperty.MAX_WIDTH, StyleEditorValueType.OptionalLengthPx, numericStep = 4f),
        StylePropertyDescriptor(StyleProperty.MAX_HEIGHT, StyleEditorValueType.OptionalLengthPx, numericStep = 4f),
        StylePropertyDescriptor(
            StyleProperty.OVERFLOW,
            StyleEditorValueType.EnumChoice,
            enumOptions = listOf("visible", "hidden", "scroll", "auto")
        ),
        StylePropertyDescriptor(
            StyleProperty.OVERFLOW_X,
            StyleEditorValueType.EnumChoice,
            enumOptions = listOf("visible", "hidden", "scroll", "auto")
        ),
        StylePropertyDescriptor(
            StyleProperty.OVERFLOW_Y,
            StyleEditorValueType.EnumChoice,
            enumOptions = listOf("visible", "hidden", "scroll", "auto")
        ),
        StylePropertyDescriptor(StyleProperty.MARGIN, StyleEditorValueType.SpacingLengthPx, numericStep = 1f),
        StylePropertyDescriptor(StyleProperty.PADDING, StyleEditorValueType.SpacingLengthPx, numericStep = 1f),
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
        StylePropertyDescriptor(StyleProperty.BORDER_WIDTH, StyleEditorValueType.LengthPx, numericStep = 1f),
        StylePropertyDescriptor(StyleProperty.BORDER_RADIUS, StyleEditorValueType.LengthPx, numericStep = 1f),
        StylePropertyDescriptor(StyleProperty.FOREGROUND_COLOR, StyleEditorValueType.ColorHex, enumOptions = colorPalette(), isInherited = true),
        StylePropertyDescriptor(
            StyleProperty.FONT_ID,
            StyleEditorValueType.StringPreset,
            enumOptions = listOf("minecraft", "ubuntu", "JetBrains Mono"),
            isInherited = true,
            inspectorEditorKind = StyleInspectorEditorKind.FontSelect
        ),
        StylePropertyDescriptor(StyleProperty.FONT_SIZE, StyleEditorValueType.LengthPx, numericStep = 1f, minInt = 1, isInherited = true),
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
        StylePropertyDescriptor(StyleProperty.GAP, StyleEditorValueType.LengthPx, numericStep = 1f),
        StylePropertyDescriptor(StyleProperty.FLEX_GROW, StyleEditorValueType.FloatNumber, numericStep = 0.25f),
        StylePropertyDescriptor(StyleProperty.FLEX_SHRINK, StyleEditorValueType.FloatNumber, numericStep = 0.25f),
        StylePropertyDescriptor(StyleProperty.FLEX_BASIS, StyleEditorValueType.OptionalLengthPx, numericStep = 4f),
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

    fun parseEnumLiteral(property: StyleProperty, literal: String): String {
        val descriptor = descriptor(property)
        require(descriptor.grammarKind == StyleValueGrammarKind.Enum) {
            "Property '${property.key}' does not use enum grammar."
        }
        val normalized = literal.trim().lowercase()
        val matched = descriptor.enumOptions.firstOrNull { it.equals(normalized, ignoreCase = true) }
            ?: error("Unsupported value '$literal' for '${property.key}'.")
        return matched
    }

    fun parseUnitlessIntLiteral(property: StyleProperty, literal: String): Int {
        val descriptor = descriptor(property)
        require(descriptor.grammarKind == StyleValueGrammarKind.UnitlessInt) {
            "Property '${property.key}' does not use unitless-int grammar."
        }
        return parseIntLike(literal)
    }

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

private fun defaultGrammarKind(valueType: StyleEditorValueType): StyleValueGrammarKind {
    return when (valueType) {
        StyleEditorValueType.EnumChoice -> StyleValueGrammarKind.Enum
        StyleEditorValueType.IntNumber -> StyleValueGrammarKind.UnitlessInt
        StyleEditorValueType.LengthPx,
        StyleEditorValueType.OptionalLengthPx,
        StyleEditorValueType.SpacingLengthPx -> StyleValueGrammarKind.LengthLike
        else -> StyleValueGrammarKind.Other
    }
}

private fun defaultInspectorEditorKind(
    property: StyleProperty,
    valueType: StyleEditorValueType
): StyleInspectorEditorKind {
    if (property == StyleProperty.FONT_ID) {
        return StyleInspectorEditorKind.FontSelect
    }
    return when (valueType) {
        StyleEditorValueType.EnumChoice -> StyleInspectorEditorKind.EnumSelect
        StyleEditorValueType.IntNumber,
        StyleEditorValueType.OptionalIntNumber,
        StyleEditorValueType.FloatNumber,
        StyleEditorValueType.LengthPx,
        StyleEditorValueType.OptionalLengthPx,
        StyleEditorValueType.Spacing,
        StyleEditorValueType.SpacingLengthPx -> StyleInspectorEditorKind.NumericInput
        else -> StyleInspectorEditorKind.StringInput
    }
}

