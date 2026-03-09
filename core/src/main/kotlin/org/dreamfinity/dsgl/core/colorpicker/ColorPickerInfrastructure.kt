package org.dreamfinity.dsgl.core.colorpicker

import org.dreamfinity.dsgl.core.input.ClipboardBridge

fun interface ScreenColorSampler {
    fun sampleColorAt(x: Int, y: Int): Int?

    fun sampleArea(x: Int, y: Int, width: Int, height: Int, outArgb: IntArray): Boolean {
        if (width <= 0 || height <= 0) return false
        val required = width * height
        if (outArgb.size < required) return false
        var any = false
        var index = 0
        var py = 0
        while (py < height) {
            var px = 0
            while (px < width) {
                val sampled = sampleColorAt(x + px, y + py)
                if (sampled != null) {
                    outArgb[index] = sampled
                    any = true
                } else {
                    outArgb[index] = 0
                }
                index++
                px++
            }
            py++
        }
        return any
    }
}

object ScreenColorSamplerBridge {
    @Volatile
    private var sampler: ScreenColorSampler? = null

    fun install(next: ScreenColorSampler?) {
        sampler = next
    }

    fun sampleColorAt(x: Int, y: Int): RgbaColor? {
        val argb = sampler?.sampleColorAt(x, y) ?: return null
        return RgbaColor.fromArgbInt(argb).normalized()
    }

    fun sampleArea(x: Int, y: Int, width: Int, height: Int, outArgb: IntArray): Boolean {
        return sampler?.sampleArea(x, y, width, height, outArgb) == true
    }
}

object ColorClipboardSupport {
    fun copy(color: RgbaColor, mode: ColorFormatMode, includeAlpha: Boolean) {
        copy(color, mode, includeAlpha, RgbChannelOrder.RGBA)
    }

    fun copy(
        color: RgbaColor,
        mode: ColorFormatMode,
        includeAlpha: Boolean,
        rgbOrder: RgbChannelOrder = RgbChannelOrder.RGBA
    ) {
        val text = ColorTextCodec.format(color, mode, includeAlpha, rgbOrder)
        ClipboardBridge.writeText(text)
    }

    fun paste(): ParsedColorText? {
        val raw = ClipboardBridge.readText()
        return ColorTextCodec.parse(raw)
    }
}
