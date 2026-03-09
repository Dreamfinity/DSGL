package org.dreamfinity.dsgl.core.colorpicker

import kotlin.math.abs
import kotlin.math.roundToInt

data class ParsedColorText(
    val color: RgbaColor,
    val detectedMode: ColorFormatMode,
    val detectedRgbOrder: RgbChannelOrder? = null
) {
    constructor(
        color: RgbaColor,
        detectedMode: ColorFormatMode
    ) : this(
        color = color,
        detectedMode = detectedMode,
        detectedRgbOrder = null
    )
}

object ColorTextCodec {
    fun format(
        color: RgbaColor,
        mode: ColorFormatMode,
        includeAlpha: Boolean
    ): String {
        return format(color, mode, includeAlpha, RgbChannelOrder.RGBA)
    }

    fun parse(raw: String): ParsedColorText? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        parseHex(text)?.let { return ParsedColorText(it, ColorFormatMode.HEX) }
        parseRgbLike(text)?.let { return it }
        parseHslLike(text)?.let { return ParsedColorText(it, ColorFormatMode.HSL) }
        parseHsbLike(text)?.let { return ParsedColorText(it, ColorFormatMode.HSB) }
        return null
    }

    fun format(
        color: RgbaColor,
        mode: ColorFormatMode,
        includeAlpha: Boolean,
        rgbOrder: RgbChannelOrder = RgbChannelOrder.RGBA
    ): String {
        val normalized = color.normalized()
        return when (mode) {
            ColorFormatMode.HEX -> formatHex(normalized, includeAlpha)
            ColorFormatMode.RGB -> formatRgb(normalized, includeAlpha, rgbOrder)
            ColorFormatMode.HSL -> formatHsl(normalized, includeAlpha)
            ColorFormatMode.HSB -> formatHsb(normalized, includeAlpha)
        }
    }

    fun formatHex(color: RgbaColor, includeAlpha: Boolean): String {
        val c = color.normalized()
        val r = (c.r * 255f + 0.5f).toInt().coerceIn(0, 255)
        val g = (c.g * 255f + 0.5f).toInt().coerceIn(0, 255)
        val b = (c.b * 255f + 0.5f).toInt().coerceIn(0, 255)
        val a = (c.a * 255f + 0.5f).toInt().coerceIn(0, 255)
        return if (includeAlpha) {
            "#%02X%02X%02X%02X".format(r, g, b, a)
        } else {
            "#%02X%02X%02X".format(r, g, b)
        }
    }

    fun formatRgb(
        color: RgbaColor,
        includeAlpha: Boolean,
        rgbOrder: RgbChannelOrder = RgbChannelOrder.RGBA
    ): String {
        val c = color.normalized()
        val a = formatFraction(c.a)
        val r = (c.r * 255f + 0.5f).toInt().coerceIn(0, 255)
        val g = (c.g * 255f + 0.5f).toInt().coerceIn(0, 255)
        val b = (c.b * 255f + 0.5f).toInt().coerceIn(0, 255)
        return if (includeAlpha) {
            when (rgbOrder) {
                RgbChannelOrder.RGBA -> "rgba($r, $g, $b, $a)"
                RgbChannelOrder.ARGB -> "argb($a, $r, $g, $b)"
            }
        } else {
            "rgb($r, $g, $b)"
        }
    }

    fun formatRgb(color: RgbaColor, includeAlpha: Boolean): String {
        return formatRgb(color, includeAlpha, RgbChannelOrder.RGBA)
    }

    fun formatHsl(
        color: RgbaColor,
        includeAlpha: Boolean,
        fallbackHueDeg: Float = 0f
    ): String {
        val c = color.normalized()
        val hsl = ColorConversions.rgbToHsl(c, fallbackHueDeg)
        val h = hsl.hueDeg.roundToInt().coerceIn(0, 360)
        val s = (hsl.saturation * 100f).roundToInt().coerceIn(0, 100)
        val l = (hsl.lightness * 100f).roundToInt().coerceIn(0, 100)
        return if (includeAlpha) {
            "hsla($h, ${s}%, ${l}%, ${formatFraction(c.a)})"
        } else {
            "hsl($h, ${s}%, ${l}%)"
        }
    }

    fun formatHsb(
        color: RgbaColor,
        includeAlpha: Boolean,
        fallbackHueDeg: Float = 0f
    ): String {
        val c = color.normalized()
        val hsb = ColorConversions.rgbToHsv(c, fallbackHueDeg)
        val h = hsb.hueDeg.roundToInt().coerceIn(0, 360)
        val s = (hsb.saturation * 100f).roundToInt().coerceIn(0, 100)
        val b = (hsb.brightness * 100f).roundToInt().coerceIn(0, 100)
        return if (includeAlpha) {
            "hsba($h, ${s}%, ${b}%, ${formatFraction(c.a)})"
        } else {
            "hsb($h, ${s}%, ${b}%)"
        }
    }

    private fun parseHex(raw: String): RgbaColor? {
        if (!raw.startsWith("#")) return null
        val hex = raw.substring(1)
        val expanded = when (hex.length) {
            3 -> "${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}FF"
            4 -> "${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}${hex[3]}${hex[3]}"
            6 -> "${hex}FF"
            8 -> hex
            else -> return null
        }
        val value = expanded.toLongOrNull(16) ?: return null
        val r = ((value ushr 24) and 0xFF).toInt() / 255f
        val g = ((value ushr 16) and 0xFF).toInt() / 255f
        val b = ((value ushr 8) and 0xFF).toInt() / 255f
        val a = (value and 0xFF).toInt() / 255f
        return RgbaColor(r, g, b, a).normalized()
    }

    private fun parseRgbLike(raw: String): ParsedColorText? {
        val prefix = when {
            raw.startsWith("rgba(", ignoreCase = true) -> "rgba"
            raw.startsWith("argb(", ignoreCase = true) -> "argb"
            raw.startsWith("rgb(", ignoreCase = true) -> "rgb"
            else -> return null
        }
        val values = parseFunctionArgs(raw, prefix) ?: return null
        if (prefix == "rgb" && values.size != 3) return null
        if ((prefix == "rgba" || prefix == "argb") && values.size != 4) return null
        if (values.size !in 3..4) return null
        val (r, g, b, a, order) = if (prefix == "argb") {
            val a = parseAlphaComponent(values[0]) ?: return null
            val r = parseRgbComponent(values[1]) ?: return null
            val g = parseRgbComponent(values[2]) ?: return null
            val b = parseRgbComponent(values[3]) ?: return null
            RgbParseResult(r, g, b, a, RgbChannelOrder.ARGB)
        } else {
            val r = parseRgbComponent(values[0]) ?: return null
            val g = parseRgbComponent(values[1]) ?: return null
            val b = parseRgbComponent(values[2]) ?: return null
            val a = if (values.size >= 4) parseAlphaComponent(values[3]) ?: return null else 1f
            RgbParseResult(r, g, b, a, if (values.size == 4) RgbChannelOrder.RGBA else null)
        }
        return ParsedColorText(
            color = RgbaColor(r, g, b, a).normalized(),
            detectedMode = ColorFormatMode.RGB,
            detectedRgbOrder = order
        )
    }

    private fun parseHslLike(raw: String): RgbaColor? {
        val prefix = when {
            raw.startsWith("hsla(", ignoreCase = true) -> "hsla"
            raw.startsWith("hsl(", ignoreCase = true) -> "hsl"
            else -> return null
        }
        val values = parseFunctionArgs(raw, prefix) ?: return null
        if (prefix == "hsl" && values.size != 3) return null
        if (prefix == "hsla" && values.size != 4) return null
        val h = parseHue(values[0]) ?: return null
        val s = parsePercent(values[1]) ?: return null
        val l = parsePercent(values[2]) ?: return null
        val a = if (values.size >= 4) parseAlphaComponent(values[3]) ?: return null else 1f
        return ColorConversions.hslToRgb(
            hsl = HslColor(h, s, l),
            alpha = a
        )
    }

    private fun parseHsbLike(raw: String): RgbaColor? {
        val prefix = when {
            raw.startsWith("hsba(", ignoreCase = true) -> "hsba"
            raw.startsWith("hsv(", ignoreCase = true) -> "hsv"
            raw.startsWith("hsb(", ignoreCase = true) -> "hsb"
            else -> return null
        }
        val values = parseFunctionArgs(raw, prefix) ?: return null
        if ((prefix == "hsb" || prefix == "hsv") && values.size != 3) return null
        if (prefix == "hsba" && values.size != 4) return null
        val h = parseHue(values[0]) ?: return null
        val s = parsePercent(values[1]) ?: return null
        val b = parsePercent(values[2]) ?: return null
        val a = if (values.size >= 4) parseAlphaComponent(values[3]) ?: return null else 1f
        return ColorConversions.hsvToRgb(
            hsv = HsvColor(h, s, b),
            alpha = a
        )
    }

    private fun parseFunctionArgs(raw: String, prefix: String): List<String>? {
        if (!raw.endsWith(")")) return null
        val head = raw.indexOf('(')
        if (head < 0) return null
        val name = raw.substring(0, head).trim().lowercase()
        if (name != prefix) return null
        val body = raw.substring(head + 1, raw.length - 1)
        if (body.isBlank()) return emptyList()
        return body.split(',').map { it.trim() }
    }

    private fun parseRgbComponent(raw: String): Float? {
        val value = raw.trim()
        return if (value.endsWith("%")) {
            val p = value.dropLast(1).toFloatOrNull() ?: return null
            if (p < 0f || p > 100f) return null
            p / 100f
        } else {
            val number = value.toFloatOrNull() ?: return null
            if (number < 0f || number > 255f) return null
            number / 255f
        }
    }

    private fun parseAlphaComponent(raw: String): Float? {
        val value = raw.trim()
        return if (value.endsWith("%")) {
            val p = value.dropLast(1).toFloatOrNull() ?: return null
            if (p < 0f || p > 100f) return null
            p / 100f
        } else {
            val f = value.toFloatOrNull() ?: return null
            if (f < 0f) return null
            if (f > 1f) {
                if (f > 255f) return null
                f / 255f
            } else {
                f
            }
        }
    }

    private fun parseHue(raw: String): Float? {
        val normalized = raw.trim().removeSuffix("deg").trim()
        val value = normalized.toFloatOrNull() ?: return null
        var hue = value % 360f
        if (hue < 0f) hue += 360f
        return hue
    }

    private fun parsePercent(raw: String): Float? {
        val value = raw.trim()
        return if (value.endsWith("%")) {
            val p = value.dropLast(1).toFloatOrNull() ?: return null
            if (p < 0f || p > 100f) return null
            p / 100f
        } else {
            val f = value.toFloatOrNull() ?: return null
            if (f < 0f) return null
            if (f > 1f || abs(f - 1f) < 1e-6f) {
                if (f > 100f) return null
                f / 100f
            } else {
                f
            }
        }
    }

    private fun formatFraction(value: Float): String {
        val clamped = value.coerceIn(0f, 1f)
        val rounded = (clamped * 1000f).roundToInt() / 1000f
        return if (rounded % 1f == 0f) {
            rounded.toInt().toString()
        } else {
            rounded.toString()
        }
    }

    private data class RgbParseResult(
        val r: Float,
        val g: Float,
        val b: Float,
        val a: Float,
        val order: RgbChannelOrder?
    )
}
