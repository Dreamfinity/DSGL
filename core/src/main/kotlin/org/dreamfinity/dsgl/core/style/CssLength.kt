package org.dreamfinity.dsgl.core.style

import kotlin.math.roundToInt

enum class CssUnit {
    Px
}

data class CssLength(
    val value: Float,
    val unit: CssUnit
)

data class LengthContext(
    val fontSizePx: Float = 16f,
    val parentSizePx: Float = 0f
)

fun interface StyleWarningReporter {
    fun warnOnce(key: String, message: String)
}

private val knownLengthUnits: Set<String> = linkedSetOf("px")
private val knownLengthUnitsPattern = knownLengthUnits.joinToString("|") { Regex.escape(it) }
private val cssLengthRegex = Regex(
    pattern = """^(-?\d+(?:\.\d+)?)(?:($knownLengthUnitsPattern))?$""",
    option = RegexOption.IGNORE_CASE
)
private val cssLengthAnyUnitRegex = Regex(
    pattern = """^(-?\d+(?:\.\d+)?)(?:([a-zA-Z%]+))?$""",
    option = RegexOption.IGNORE_CASE
)

fun CssLength.toPx(context: LengthContext = LengthContext()): Float {
    return when (unit) {
        CssUnit.Px -> value
    }
}

fun parseCssLength(
    raw: String,
    allowUnitlessPx: Boolean = true,
    warningReporter: StyleWarningReporter? = null,
    warningKey: String = "deprecated.unitless-length"
): CssLength {
    val trimmed = raw.trim()
    val match = cssLengthRegex.matchEntire(trimmed)
        ?: run {
            val anyUnitMatch = cssLengthAnyUnitRegex.matchEntire(trimmed)
            if (anyUnitMatch != null) {
                val unknownUnit = anyUnitMatch.groupValues.getOrNull(2).orEmpty().trim()
                if (unknownUnit.isNotEmpty()) {
                    error("Unknown length unit '$unknownUnit'. Supported units: px.")
                }
            }
            error("Expected CSS length but got '$raw'.")
        }
    val value = match.groupValues[1].toFloat()
    val unitToken = match.groupValues.getOrNull(2).orEmpty().trim().lowercase()
    if (unitToken.isEmpty()) {
        if (!allowUnitlessPx) {
            error("Expected explicit unit in '$raw'. Use 'px'.")
        }
        warningReporter?.warnOnce(
            warningKey,
            "Deprecated unitless length literals are interpreted as pixels. Use explicit 'px' units."
        )
        return CssLength(value = value, unit = CssUnit.Px)
    }
    if (unitToken != "px") error("Unknown length unit '$unitToken'. Supported units: px.")
    return CssLength(value = value, unit = CssUnit.Px)
}

fun parseLengthPx(
    raw: String,
    allowNegative: Boolean,
    allowUnitlessPx: Boolean = true,
    warningReporter: StyleWarningReporter? = null,
    warningKey: String = "deprecated.unitless-length"
): Float {
    val px = parseCssLength(
        raw = raw,
        allowUnitlessPx = allowUnitlessPx,
        warningReporter = warningReporter,
        warningKey = warningKey
    ).toPx()
    if (!allowNegative && px < 0f) {
        error("Negative length is not allowed: '$raw'.")
    }
    return px
}

fun parseLengthPxInt(
    raw: String,
    allowNegative: Boolean,
    allowUnitlessPx: Boolean = true,
    warningReporter: StyleWarningReporter? = null,
    warningKey: String = "deprecated.unitless-length"
): Int {
    return parseLengthPx(
        raw = raw,
        allowNegative = allowNegative,
        allowUnitlessPx = allowUnitlessPx,
        warningReporter = warningReporter,
        warningKey = warningKey
    ).roundToInt()
}

fun parseOptionalLengthPxInt(
    raw: String,
    allowNegative: Boolean,
    allowUnitlessPx: Boolean = true,
    warningReporter: StyleWarningReporter? = null,
    warningKey: String = "deprecated.unitless-length"
): Int? {
    val normalized = raw.trim().lowercase()
    if (normalized == "auto") return null
    return parseLengthPxInt(
        raw = raw,
        allowNegative = allowNegative,
        allowUnitlessPx = allowUnitlessPx,
        warningReporter = warningReporter,
        warningKey = warningKey
    )
}
