package org.dreamfinity.dsgl.core.style

import org.dreamfinity.dsgl.core.dom.layout.Insets
import kotlin.math.roundToInt

private val varRegex = Regex("""^var\(\s*(--[a-zA-Z0-9_-]+)\s*\)$""")
private val numberRegex = Regex("""^-?\d+(\.\d+)?$""")

fun parseExpression(rawValue: String): StyleExpression {
    val trimmed = rawValue.trim()
    val varMatch = varRegex.matchEntire(trimmed)
    return if (varMatch != null) {
        StyleExpression.VariableRef(varMatch.groupValues[1])
    } else {
        StyleExpression.Literal(trimmed)
    }
}

fun parseSpacingShorthand(raw: String): Insets {
    val parts = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    require(parts.isNotEmpty()) { "Spacing value cannot be empty." }
    val nums = parts.map { parseIntLike(it) }
    return when (nums.size) {
        1 -> Insets.all(nums[0])
        2 -> Insets(nums[0], nums[1], nums[0], nums[1])
        3 -> Insets(nums[0], nums[1], nums[2], nums[1])
        4 -> Insets(nums[0], nums[1], nums[2], nums[3])
        else -> error("Spacing supports 1, 2, 3, or 4 values.")
    }
}

fun parseColor(raw: String): Int {
    val value = raw.trim()
    require(value.startsWith("#")) { "Color must start with '#'." }
    val hex = value.substring(1)
    return when (hex.length) {
        3 -> {
            val r = "${hex[0]}${hex[0]}".toInt(16)
            val g = "${hex[1]}${hex[1]}".toInt(16)
            val b = "${hex[2]}${hex[2]}".toInt(16)
            ((0xFF shl 24) or (r shl 16) or (g shl 8) or b)
        }

        6 -> {
            val rgb = hex.toLong(16).toInt()
            (0xFF shl 24) or rgb
        }

        8 -> hex.toLong(16).toInt()
        else -> error("Unsupported color format '$raw'.")
    }
}

fun parseAlign(raw: String): StyleAlign {
    return when (raw.trim().lowercase()) {
        "start", "left", "top" -> StyleAlign.START
        "center", "middle" -> StyleAlign.CENTER
        "end", "right", "bottom" -> StyleAlign.END
        else -> error("Unsupported align value '$raw'.")
    }
}

fun parseDisplay(raw: String): Display {
    return when (raw.trim().lowercase()) {
        "block" -> Display.Block
        "inline" -> Display.Inline
        "none" -> Display.None
        "flex" -> Display.Flex
        "grid" -> Display.Grid
        else -> error("Unsupported display value '$raw'.")
    }
}

fun parseFlexDirection(raw: String): FlexDirection {
    return when (raw.trim().lowercase()) {
        "row" -> FlexDirection.Row
        "column" -> FlexDirection.Column
        else -> error("Unsupported flex-direction value '$raw'.")
    }
}

fun parseJustifyContent(raw: String): JustifyContent {
    return when (raw.trim().lowercase()) {
        "start", "flex-start", "left", "top" -> JustifyContent.Start
        "center" -> JustifyContent.Center
        "end", "flex-end", "right", "bottom" -> JustifyContent.End
        "space-between" -> JustifyContent.SpaceBetween
        "space-around" -> JustifyContent.SpaceAround
        "space-evenly" -> JustifyContent.SpaceEvenly
        else -> error("Unsupported justify-content value '$raw'.")
    }
}

fun parseAlignItems(raw: String): AlignItems {
    return when (raw.trim().lowercase()) {
        "start", "flex-start", "top", "left" -> AlignItems.Start
        "center" -> AlignItems.Center
        "end", "flex-end", "bottom", "right" -> AlignItems.End
        "stretch" -> AlignItems.Stretch
        else -> error("Unsupported align-items value '$raw'.")
    }
}

fun parseJustifyItems(raw: String): JustifyItems {
    return when (raw.trim().lowercase()) {
        "start", "left", "top" -> JustifyItems.Start
        "center" -> JustifyItems.Center
        "end", "right", "bottom" -> JustifyItems.End
        "stretch" -> JustifyItems.Stretch
        else -> error("Unsupported justify-items value '$raw'.")
    }
}

fun parseGridAutoFlow(raw: String): GridAutoFlow {
    return when (raw.trim().lowercase()) {
        "row" -> GridAutoFlow.Row
        "column" -> GridAutoFlow.Column
        else -> error("Unsupported grid-auto-flow value '$raw'.")
    }
}

fun parseTextWrap(raw: String): TextWrap {
    return when (raw.trim().lowercase()) {
        "wrap" -> TextWrap.Wrap
        "nowrap" -> TextWrap.NoWrap
        else -> error("Unsupported text-wrap value '$raw'.")
    }
}

fun parseIntLike(raw: String): Int {
    val trimmed = raw.trim()
    require(numberRegex.matches(trimmed)) { "Expected number but got '$raw'." }
    return trimmed.toDouble().roundToInt()
}

fun parseFloatLike(raw: String): Float {
    val trimmed = raw.trim()
    require(numberRegex.matches(trimmed)) { "Expected number but got '$raw'." }
    return trimmed.toFloat()
}

fun parseOptionalInt(raw: String): Int? {
    val normalized = raw.trim().lowercase()
    return if (normalized == "auto") null else parseIntLike(raw)
}

fun parseStringLiteral(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"') {
        return trimmed.substring(1, trimmed.length - 1)
    }
    if (trimmed.length >= 2 && trimmed.first() == '\'' && trimmed.last() == '\'') {
        return trimmed.substring(1, trimmed.length - 1)
    }
    return trimmed
}

fun validateLiteralForProperty(property: StyleProperty, literal: String) {
    when (property) {
        StyleProperty.MARGIN,
        StyleProperty.PADDING -> parseSpacingShorthand(literal)

        StyleProperty.BACKGROUND_COLOR,
        StyleProperty.BORDER_COLOR,
        StyleProperty.FOREGROUND_COLOR -> parseColor(literal)

        StyleProperty.BACKGROUND_IMAGE -> parseStringLiteral(literal)

        StyleProperty.BORDER_WIDTH,
        StyleProperty.BORDER_RADIUS,
        StyleProperty.FONT_SIZE,
        StyleProperty.WIDTH,
        StyleProperty.HEIGHT -> parseIntLike(literal)

        StyleProperty.ALIGN -> parseAlign(literal)
        StyleProperty.DISPLAY -> parseDisplay(literal)
        StyleProperty.FLEX_DIRECTION -> parseFlexDirection(literal)
        StyleProperty.JUSTIFY_CONTENT -> parseJustifyContent(literal)
        StyleProperty.ALIGN_ITEMS -> parseAlignItems(literal)
        StyleProperty.JUSTIFY_ITEMS -> parseJustifyItems(literal)
        StyleProperty.GAP -> parseIntLike(literal)
        StyleProperty.FLEX_GROW -> parseFloatLike(literal)
        StyleProperty.FLEX_SHRINK -> parseFloatLike(literal)
        StyleProperty.FLEX_BASIS -> parseOptionalInt(literal)
        StyleProperty.GRID_COLUMNS -> parseIntLike(literal)
        StyleProperty.GRID_ROWS -> parseOptionalInt(literal)
        StyleProperty.GRID_AUTO_FLOW -> parseGridAutoFlow(literal)
        StyleProperty.GRID_COLUMN_SPAN -> parseIntLike(literal)
        StyleProperty.GRID_ROW_SPAN -> parseIntLike(literal)
        StyleProperty.TEXT_WRAP -> parseTextWrap(literal)
    }
}

fun resolveExpressionToLiteral(
    expression: StyleExpression,
    variables: Map<String, String>,
    resolving: MutableSet<String> = linkedSetOf()
): String {
    return when (expression) {
        is StyleExpression.Literal -> expression.value
        is StyleExpression.VariableRef -> {
            val varName = expression.name
            if (!resolving.add(varName)) {
                error("Cyclic variable reference for '$varName'.")
            }
            val raw = variables[varName] ?: error("Undefined variable '$varName'.")
            val nested = parseExpression(raw)
            val resolved = resolveExpressionToLiteral(nested, variables, resolving)
            resolving.remove(varName)
            resolved
        }
    }
}
