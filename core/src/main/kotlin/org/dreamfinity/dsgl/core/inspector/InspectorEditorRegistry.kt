package org.dreamfinity.dsgl.core.inspector

import org.dreamfinity.dsgl.core.font.FontRegistry
import org.dreamfinity.dsgl.core.style.CssLength
import org.dreamfinity.dsgl.core.style.CssUnit
import org.dreamfinity.dsgl.core.style.StyleEditorValueType
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty
import org.dreamfinity.dsgl.core.style.StylePropertyRegistry
import org.dreamfinity.dsgl.core.style.parseCssLength
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
    private val lengthValueTypes: Set<StyleEditorValueType> = linkedSetOf(
        StyleEditorValueType.LengthPx,
        StyleEditorValueType.OptionalLengthPx,
        StyleEditorValueType.SpacingLengthPx
    )
    private val numericValueTypes: Set<StyleEditorValueType> = linkedSetOf(
        StyleEditorValueType.IntNumber,
        StyleEditorValueType.OptionalIntNumber,
        StyleEditorValueType.FloatNumber,
        StyleEditorValueType.Spacing
    )
    private val unitOptions: List<CssUnit> = listOf(
        CssUnit.Px,
        CssUnit.Em,
        CssUnit.Rem,
        CssUnit.Vw,
        CssUnit.Vh,
        CssUnit.Percent
    )

    fun describe(property: StyleProperty, literal: String, expression: StyleExpression?): InspectorEditorDescriptor {
        if (property == StyleProperty.FONT_ID) {
            return InspectorEditorDescriptor(
                kind = InspectorEditorKind.FontSelect,
                options = fontOptions()
            )
        }
        val descriptor = StylePropertyRegistry.descriptor(property)
        if (descriptor.valueType == StyleEditorValueType.EnumChoice || descriptor.valueType == StyleEditorValueType.StringPreset) {
            return InspectorEditorDescriptor(
                kind = InspectorEditorKind.EnumSelect,
                options = descriptor.enumOptions
            )
        }
        if (descriptor.valueType in lengthValueTypes) {
            return InspectorEditorDescriptor(
                kind = InspectorEditorKind.NumericInput,
                supportsUnits = true
            )
        }
        if (descriptor.valueType in numericValueTypes) {
            return InspectorEditorDescriptor(
                kind = InspectorEditorKind.NumericInput,
                supportsUnits = false
            )
        }
        return InspectorEditorDescriptor(
            kind = InspectorEditorKind.StringInput,
            showColorPreview = isColorProperty(property) || looksLikeColorLiteral(literal) || expression is StyleExpression.VariableRef
        )
    }

    fun parseNumberUnit(rawLiteral: String): InspectorNumberUnitValue? {
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

    private fun stripTrailingZeros(value: Float): String {
        val asLong = value.toLong()
        return if (asLong.toFloat() == value) {
            asLong.toString()
        } else {
            value.toString()
        }
    }
}

