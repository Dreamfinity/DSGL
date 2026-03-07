package org.dreamfinity.dsgl.core.style

import org.dreamfinity.dsgl.core.dom.layout.Insets
import kotlin.math.roundToInt

enum class CssUnit(val token: String) {
    Px("px"),
    Rem("rem"),
    Em("em"),
    Vw("vw"),
    Vh("vh"),
    Percent("%")
}

data class CssLength(
    val value: Float,
    val unit: CssUnit
) {
    override fun toString(): String {
        return toCssLiteral()
    }

    companion object {
        val ZERO_PX: CssLength = CssLength(0f, CssUnit.Px)

        fun px(value: Number): CssLength = CssLength(value.toFloat(), CssUnit.Px)
    }
}

fun CssLength.coerceAtLeast(minimumValue: Int): String {
    return CssLength(value.coerceAtLeast(minimumValue.toFloat()), unit).toCssLiteral()
}

fun Number.px(): CssLength {
    return CssLength(value = this.toFloat(), unit = CssUnit.Px)
}

fun Number.em(): CssLength {
    return CssLength(value = this.toFloat(), unit = CssUnit.Em)
}

fun Number.rem(): CssLength {
    return CssLength(value = this.toFloat(), unit = CssUnit.Rem)
}

fun Number.vw(): CssLength {
    return CssLength(value = this.toFloat(), unit = CssUnit.Vw)
}

fun Number.vh(): CssLength {
    return CssLength(value = this.toFloat(), unit = CssUnit.Vh)
}

fun Number.percent(): CssLength {
    return CssLength(value = this.toFloat(), unit = CssUnit.Percent)
}

data class LengthInsets(
    val top: CssLength,
    val right: CssLength,
    val bottom: CssLength,
    val left: CssLength
) {
    companion object {
        val ZERO: LengthInsets = all(CssLength.ZERO_PX)

        fun all(value: CssLength): LengthInsets = LengthInsets(value, value, value, value)

        fun fromInsets(insets: Insets): LengthInsets {
            return LengthInsets(
                top = CssLength.px(insets.top),
                right = CssLength.px(insets.right),
                bottom = CssLength.px(insets.bottom),
                left = CssLength.px(insets.left)
            )
        }
    }
}

enum class LengthPercentBase {
    ContainerWidth,
    ContainerHeight,
    CurrentFontSize,
    InheritedFontSize
}

data class LengthResolveContext(
    val viewportWidthPx: Float = 0f,
    val viewportHeightPx: Float = 0f,
    val containingBlockWidthPx: Float? = null,
    val containingBlockHeightPx: Float? = null,
    val rootFontSizePx: Float = 16f,
    val currentFontSizePx: Float = 16f,
    val inheritedFontSizePx: Float = 16f
)

fun interface StyleWarningReporter {
    fun warnOnce(key: String, message: String)
}

private val knownLengthUnits: Set<String> = linkedSetOf("px", "rem", "em", "vw", "vh", "%")
private val knownLengthUnitsPattern = knownLengthUnits.joinToString("|") { Regex.escape(it) }
private val cssLengthRegex = Regex(
    pattern = """^(-?(?:\d+(?:\.\d+)?|\.\d+))(?:($knownLengthUnitsPattern))?$""",
    option = RegexOption.IGNORE_CASE
)
private val cssLengthAnyUnitRegex = Regex(
    pattern = """^(-?(?:\d+(?:\.\d+)?|\.\d+))(?:([a-zA-Z%]+))?$""",
    option = RegexOption.IGNORE_CASE
)

fun parseCssLength(
    raw: String,
    allowUnitlessZero: Boolean = true
): CssLength {
    val trimmed = raw.trim()
    val match = cssLengthRegex.matchEntire(trimmed)
        ?: run {
            val anyUnitMatch = cssLengthAnyUnitRegex.matchEntire(trimmed)
            if (anyUnitMatch != null) {
                val unknownUnit = anyUnitMatch.groupValues.getOrNull(2).orEmpty().trim()
                if (unknownUnit.isNotEmpty()) {
                    error("Unknown length unit '$unknownUnit'. Supported units: px, rem, em, vw, vh, %.")
                }
            }
            error("Expected CSS length but got '$raw'.")
        }
    val value = match.groupValues[1].toFloat()
    val unitToken = match.groupValues.getOrNull(2).orEmpty().trim().lowercase()
    if (unitToken.isEmpty()) {
        if (allowUnitlessZero && value == 0f) {
            return CssLength.ZERO_PX
        }
        error("Expected explicit unit in '$raw'.")
    }
    val unit = when (unitToken) {
        "px" -> CssUnit.Px
        "rem" -> CssUnit.Rem
        "em" -> CssUnit.Em
        "vw" -> CssUnit.Vw
        "vh" -> CssUnit.Vh
        "%" -> CssUnit.Percent
        else -> error("Unknown length unit '$unitToken'. Supported units: px, rem, em, vw, vh, %.")
    }
    return CssLength(value = value, unit = unit)
}

fun parseOptionalCssLength(raw: String, allowUnitlessZero: Boolean = true): CssLength? {
    val normalized = raw.trim().lowercase()
    if (normalized == "auto") return null
    return parseCssLength(raw, allowUnitlessZero)
}

fun CssLength.toCssLiteral(): String {
    if (value == 0f && unit == CssUnit.Px) {
        return "0px"
    }
    val asLong = value.toLong()
    val number = if (asLong.toFloat() == value) {
        asLong.toString()
    } else {
        value.toString()
    }
    return number + unit.token
}

fun CssLength.resolvePx(
    context: LengthResolveContext,
    percentBase: LengthPercentBase
): Float {
    return when (unit) {
        CssUnit.Px -> value
        CssUnit.Rem -> value * context.rootFontSizePx
        CssUnit.Em -> value * context.currentFontSizePx
        CssUnit.Vw -> (value / 100f) * context.viewportWidthPx
        CssUnit.Vh -> (value / 100f) * context.viewportHeightPx
        CssUnit.Percent -> (value / 100f) * percentBaseValue(context, percentBase)
    }
}

fun LengthInsets.resolveToInsets(context: LengthResolveContext): Insets {
    return Insets(
        top = top.resolvePx(context, LengthPercentBase.ContainerHeight).roundToInt(),
        right = right.resolvePx(context, LengthPercentBase.ContainerWidth).roundToInt(),
        bottom = bottom.resolvePx(context, LengthPercentBase.ContainerHeight).roundToInt(),
        left = left.resolvePx(context, LengthPercentBase.ContainerWidth).roundToInt()
    )
}

fun parseLengthPx(
    raw: String,
    allowNegative: Boolean,
    percentBase: LengthPercentBase = LengthPercentBase.ContainerWidth,
    context: LengthResolveContext = LengthResolveContext(),
    allowUnitlessZero: Boolean = true
): Float {
    val px = parseCssLength(
        raw = raw,
        allowUnitlessZero = allowUnitlessZero
    ).resolvePx(context, percentBase)
    if (!allowNegative && px < 0f) {
        error("Negative length is not allowed: '$raw'.")
    }
    return px
}

fun parseLengthPxInt(
    raw: String,
    allowNegative: Boolean,
    percentBase: LengthPercentBase = LengthPercentBase.ContainerWidth,
    context: LengthResolveContext = LengthResolveContext(),
    allowUnitlessZero: Boolean = true
): Int {
    return parseLengthPx(
        raw = raw,
        allowNegative = allowNegative,
        percentBase = percentBase,
        context = context,
        allowUnitlessZero = allowUnitlessZero
    ).roundToInt()
}

fun parseOptionalLengthPxInt(
    raw: String,
    allowNegative: Boolean,
    percentBase: LengthPercentBase = LengthPercentBase.ContainerWidth,
    context: LengthResolveContext = LengthResolveContext(),
    allowUnitlessZero: Boolean = true
): Int? {
    val normalized = raw.trim().lowercase()
    if (normalized == "auto") return null
    return parseLengthPxInt(
        raw = raw,
        allowNegative = allowNegative,
        percentBase = percentBase,
        context = context,
        allowUnitlessZero = allowUnitlessZero
    )
}

private fun percentBaseValue(context: LengthResolveContext, base: LengthPercentBase): Float {
    return when (base) {
        LengthPercentBase.ContainerWidth -> context.containingBlockWidthPx ?: 0f
        LengthPercentBase.ContainerHeight -> context.containingBlockHeightPx ?: 0f
        LengthPercentBase.CurrentFontSize -> context.currentFontSizePx
        LengthPercentBase.InheritedFontSize -> context.inheritedFontSizePx
    }
}
