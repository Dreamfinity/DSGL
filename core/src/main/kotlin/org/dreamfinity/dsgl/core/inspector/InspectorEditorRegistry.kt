package org.dreamfinity.dsgl.core.inspector

import org.dreamfinity.dsgl.core.font.FontRegistry
import org.dreamfinity.dsgl.core.style.CssLength
import org.dreamfinity.dsgl.core.style.CssUnit
import org.dreamfinity.dsgl.core.style.StyleEditorValueType
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleInspectorEditorKind
import org.dreamfinity.dsgl.core.style.StyleProperty
import org.dreamfinity.dsgl.core.style.StylePropertyRegistry
import org.dreamfinity.dsgl.core.style.StyleValueGrammarKind
import org.dreamfinity.dsgl.core.style.parseCssLength
import org.dreamfinity.dsgl.core.style.parseFloatLike
import org.dreamfinity.dsgl.core.style.parseIntLike
import org.dreamfinity.dsgl.core.style.toCssLiteral

enum class InspectorEditorKind {
    EnumSelect,
    FontSelect,
    StringInput,
    NumericInput
}

data class InspectorEditorDescriptor(
    val kind: InspectorEditorKind,
    val options: List<String> = emptyList(),
    val supportsUnits: Boolean = false,
    val showColorPreview: Boolean = false
)

data class InspectorNumberUnitValue(
    val numberText: String,
    val unit: CssUnit?,
    val isAuto: Boolean
)

object InspectorEditorRegistry {
    private val unitOptions: List<CssUnit> = listOf(
        CssUnit.Px,
        CssUnit.Em,
        CssUnit.Rem,
        CssUnit.Vw,
        CssUnit.Vh,
        CssUnit.Percent
    )

    fun describe(property: StyleProperty, literal: String, expression: StyleExpression?): InspectorEditorDescriptor {
        val descriptor = StylePropertyRegistry.descriptor(property)
        return when (descriptor.inspectorEditorKind) {
            StyleInspectorEditorKind.FontSelect -> {
                InspectorEditorDescriptor(
                    kind = InspectorEditorKind.FontSelect,
                    options = fontOptions()
                )
            }

            StyleInspectorEditorKind.EnumSelect -> {
                InspectorEditorDescriptor(
                    kind = InspectorEditorKind.EnumSelect,
                    options = descriptor.enumOptions
                )
            }

            StyleInspectorEditorKind.NumericInput -> {
                InspectorEditorDescriptor(
                    kind = InspectorEditorKind.NumericInput,
                    supportsUnits = descriptor.grammarKind == StyleValueGrammarKind.LengthLike
                )
            }

            StyleInspectorEditorKind.StringInput -> {
                InspectorEditorDescriptor(
                    kind = InspectorEditorKind.StringInput,
                    showColorPreview = isColorProperty(property) ||
                        looksLikeColorLiteral(literal) ||
                        expression is StyleExpression.VariableRef
                )
            }
        }
    }

    fun parseNumericLiteral(property: StyleProperty, rawLiteral: String): InspectorNumberUnitValue? {
        val descriptor = StylePropertyRegistry.descriptor(property)
        return when (descriptor.valueType) {
            StyleEditorValueType.LengthPx,
            StyleEditorValueType.OptionalLengthPx,
            StyleEditorValueType.SpacingLengthPx -> parseLengthLikeNumberUnit(rawLiteral)

            StyleEditorValueType.IntNumber -> parseUnitlessInt(rawLiteral, allowAuto = false)
            StyleEditorValueType.OptionalIntNumber -> parseUnitlessInt(rawLiteral, allowAuto = true)
            StyleEditorValueType.FloatNumber,
            StyleEditorValueType.Spacing -> parseUnitlessFloat(rawLiteral)

            else -> null
        }
    }

    fun formatNumericLiteral(
        property: StyleProperty,
        numberText: String,
        unitToken: String?
    ): String {
        val descriptor = StylePropertyRegistry.descriptor(property)
        return when (descriptor.valueType) {
            StyleEditorValueType.LengthPx,
            StyleEditorValueType.OptionalLengthPx,
            StyleEditorValueType.SpacingLengthPx -> {
                val unit = parseCssUnitToken(unitToken) ?: CssUnit.Px
                formatNumberUnit(numberText, unit)
            }

            StyleEditorValueType.IntNumber -> parseIntLike(numberText.trim()).toString()

            StyleEditorValueType.OptionalIntNumber -> {
                val normalized = numberText.trim()
                if (normalized.equals("auto", ignoreCase = true)) {
                    "auto"
                } else {
                    parseIntLike(normalized).toString()
                }
            }

            StyleEditorValueType.FloatNumber,
            StyleEditorValueType.Spacing -> {
                val normalized = numberText.trim()
                if (normalized.isEmpty()) {
                    "0"
                } else {
                    stripTrailingZeros(parseFloatLike(normalized))
                }
            }

            else -> numberText.trim()
        }
    }

    fun defaultNumericLiteral(property: StyleProperty): String {
        val descriptor = StylePropertyRegistry.descriptor(property)
        return when (descriptor.grammarKind) {
            StyleValueGrammarKind.LengthLike -> "0px"
            StyleValueGrammarKind.UnitlessInt -> "0"
            else -> "0"
        }
    }

    fun defaultNumericUnit(property: StyleProperty): CssUnit? {
        val descriptor = StylePropertyRegistry.descriptor(property)
        return if (descriptor.grammarKind == StyleValueGrammarKind.LengthLike) CssUnit.Px else null
    }

    fun parseNumberUnit(rawLiteral: String): InspectorNumberUnitValue? {
        return parseLengthLikeNumberUnit(rawLiteral)
    }

    fun formatNumberUnit(numberText: String, unit: CssUnit?): String {
        val trimmed = numberText.trim()
        if (trimmed.isEmpty()) return "0px"
        val value = trimmed.toFloatOrNull() ?: return "0px"
        val normalized = stripTrailingZeros(value)
        val resolvedUnit = unit ?: CssUnit.Px
        return CssLength(value = normalized.toFloat(), unit = resolvedUnit).toCssLiteral()
    }

    fun unitOptions(): List<CssUnit> = unitOptions

    fun isColorProperty(property: StyleProperty): Boolean {
        return property == StyleProperty.BACKGROUND_COLOR ||
            property == StyleProperty.BORDER_COLOR ||
            property == StyleProperty.FOREGROUND_COLOR
    }

    fun looksLikeColorLiteral(literal: String): Boolean {
        val value = literal.trim()
        if (!value.startsWith("#")) return false
        val hex = value.substring(1)
        return hex.length == 3 || hex.length == 6 || hex.length == 8
    }

    private fun fontOptions(): List<String> {
        return FontRegistry.allFontIds().sortedBy { it.lowercase() }
    }

    private fun parseLengthLikeNumberUnit(rawLiteral: String): InspectorNumberUnitValue? {
        val normalized = rawLiteral.trim()
        if (normalized.isEmpty()) return null
        if (normalized.equals("auto", ignoreCase = true)) {
            return InspectorNumberUnitValue(numberText = "0", unit = CssUnit.Px, isAuto = true)
        }
        val token = normalized.split(Regex("\\s+")).firstOrNull()?.trim().orEmpty()
        if (token.isEmpty()) return null
        return runCatching {
            val parsed = parseCssLength(token, allowUnitlessZero = true)
            InspectorNumberUnitValue(
                numberText = stripTrailingZeros(parsed.value),
                unit = parsed.unit,
                isAuto = false
            )
        }.getOrNull()
    }

    private fun parseUnitlessInt(rawLiteral: String, allowAuto: Boolean): InspectorNumberUnitValue? {
        val normalized = rawLiteral.trim()
        if (normalized.isEmpty()) return null
        if (allowAuto && normalized.equals("auto", ignoreCase = true)) {
            return InspectorNumberUnitValue(numberText = "0", unit = null, isAuto = true)
        }
        return runCatching {
            val parsed = parseIntLike(normalized)
            InspectorNumberUnitValue(
                numberText = parsed.toString(),
                unit = null,
                isAuto = false
            )
        }.getOrNull()
    }

    private fun parseUnitlessFloat(rawLiteral: String): InspectorNumberUnitValue? {
        val normalized = rawLiteral.trim()
        if (normalized.isEmpty()) return null
        return runCatching {
            val parsed = parseFloatLike(normalized)
            InspectorNumberUnitValue(
                numberText = stripTrailingZeros(parsed),
                unit = null,
                isAuto = false
            )
        }.getOrNull()
    }

    private fun parseCssUnitToken(unitToken: String?): CssUnit? {
        if (unitToken == null) return null
        return when (unitToken.trim().lowercase()) {
            "px" -> CssUnit.Px
            "em" -> CssUnit.Em
            "rem" -> CssUnit.Rem
            "vw" -> CssUnit.Vw
            "vh" -> CssUnit.Vh
            "%" -> CssUnit.Percent
            else -> null
        }
    }

    private fun stripTrailingZeros(value: Float): String {
        val asLong = value.toLong()
        return if (asLong.toFloat() == value) {
            asLong.toString()
        } else {
            value.toString()
        }
    }
}

