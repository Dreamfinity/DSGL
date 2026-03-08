package org.dreamfinity.dsgl.core.colorpicker

import kotlin.math.abs

enum class ColorFormatMode {
    HEX,
    RGB,
    HSL,
    HSB
}

data class RgbaColor(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float = 1f
) {
    fun normalized(): RgbaColor {
        return RgbaColor(
            r = r.coerceIn(0f, 1f),
            g = g.coerceIn(0f, 1f),
            b = b.coerceIn(0f, 1f),
            a = a.coerceIn(0f, 1f)
        )
    }

    fun toArgbInt(): Int {
        val c = normalized()
        val aa = (c.a * 255f + 0.5f).toInt().coerceIn(0, 255)
        val rr = (c.r * 255f + 0.5f).toInt().coerceIn(0, 255)
        val gg = (c.g * 255f + 0.5f).toInt().coerceIn(0, 255)
        val bb = (c.b * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (aa shl 24) or (rr shl 16) or (gg shl 8) or bb
    }

    companion object {
        val WHITE: RgbaColor = RgbaColor(1f, 1f, 1f, 1f)

        fun fromArgbInt(argb: Int): RgbaColor {
            val a = ((argb ushr 24) and 0xFF) / 255f
            val r = ((argb ushr 16) and 0xFF) / 255f
            val g = ((argb ushr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            return RgbaColor(r, g, b, a)
        }
    }
}

data class HsvColor(
    val hueDeg: Float,
    val saturation: Float,
    val brightness: Float
) {
    fun normalized(): HsvColor {
        var hue = hueDeg % 360f
        if (hue < 0f) hue += 360f
        return HsvColor(
            hueDeg = hue,
            saturation = saturation.coerceIn(0f, 1f),
            brightness = brightness.coerceIn(0f, 1f)
        )
    }
}

data class HslColor(
    val hueDeg: Float,
    val saturation: Float,
    val lightness: Float
) {
    fun normalized(): HslColor {
        var hue = hueDeg % 360f
        if (hue < 0f) hue += 360f
        return HslColor(
            hueDeg = hue,
            saturation = saturation.coerceIn(0f, 1f),
            lightness = lightness.coerceIn(0f, 1f)
        )
    }
}

object ColorConversions {
    fun rgbToHsv(color: RgbaColor, fallbackHueDeg: Float = 0f): HsvColor {
        val c = color.normalized()
        val max = maxOf(c.r, maxOf(c.g, c.b))
        val min = minOf(c.r, minOf(c.g, c.b))
        val delta = max - min
        val brightness = max
        val saturation = if (max <= 0f) 0f else delta / max
        val hue = when {
            delta <= 1e-6f -> normalizeHue(fallbackHueDeg)
            abs(max - c.r) < 1e-6f -> normalizeHue((60f * ((c.g - c.b) / delta) + 360f) % 360f)
            abs(max - c.g) < 1e-6f -> normalizeHue(60f * ((c.b - c.r) / delta) + 120f)
            else -> normalizeHue(60f * ((c.r - c.g) / delta) + 240f)
        }
        return HsvColor(hue, saturation, brightness).normalized()
    }

    fun hsvToRgb(hsv: HsvColor, alpha: Float = 1f): RgbaColor {
        val n = hsv.normalized()
        val h = n.hueDeg / 60f
        val c = n.brightness * n.saturation
        val x = c * (1f - abs(h % 2f - 1f))
        val m = n.brightness - c
        val (r1, g1, b1) = when {
            h < 1f -> Triple(c, x, 0f)
            h < 2f -> Triple(x, c, 0f)
            h < 3f -> Triple(0f, c, x)
            h < 4f -> Triple(0f, x, c)
            h < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return RgbaColor(
            r = r1 + m,
            g = g1 + m,
            b = b1 + m,
            a = alpha.coerceIn(0f, 1f)
        ).normalized()
    }

    fun rgbToHsl(color: RgbaColor, fallbackHueDeg: Float = 0f): HslColor {
        val c = color.normalized()
        val max = maxOf(c.r, maxOf(c.g, c.b))
        val min = minOf(c.r, minOf(c.g, c.b))
        val delta = max - min
        val lightness = (max + min) * 0.5f
        val saturation = if (delta <= 1e-6f) {
            0f
        } else {
            delta / (1f - abs(2f * lightness - 1f))
        }
        val hue = when {
            delta <= 1e-6f -> normalizeHue(fallbackHueDeg)
            abs(max - c.r) < 1e-6f -> normalizeHue(60f * (((c.g - c.b) / delta) % 6f))
            abs(max - c.g) < 1e-6f -> normalizeHue(60f * (((c.b - c.r) / delta) + 2f))
            else -> normalizeHue(60f * (((c.r - c.g) / delta) + 4f))
        }
        return HslColor(hue, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
    }

    fun hslToRgb(hsl: HslColor, alpha: Float = 1f): RgbaColor {
        val n = hsl.normalized()
        val c = (1f - abs(2f * n.lightness - 1f)) * n.saturation
        val h = n.hueDeg / 60f
        val x = c * (1f - abs(h % 2f - 1f))
        val m = n.lightness - c * 0.5f
        val (r1, g1, b1) = when {
            h < 1f -> Triple(c, x, 0f)
            h < 2f -> Triple(x, c, 0f)
            h < 3f -> Triple(0f, c, x)
            h < 4f -> Triple(0f, x, c)
            h < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return RgbaColor(r1 + m, g1 + m, b1 + m, alpha.coerceIn(0f, 1f)).normalized()
    }

    private fun normalizeHue(hueDeg: Float): Float {
        var hue = hueDeg % 360f
        if (hue < 0f) hue += 360f
        return hue
    }
}
