package org.dreamfinity.dsgl.core.colorpicker

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorTextCodecTests {
    @Test
    fun `parses supported text formats`() {
        val hex = ColorTextCodec.parse("#3366CC80")
        val rgb = ColorTextCodec.parse("rgba(51, 102, 204, 0.5)")
        val hsl = ColorTextCodec.parse("hsl(220, 60%, 50%)")
        val hsb = ColorTextCodec.parse("hsba(220, 75%, 80%, 50%)")

        assertNotNull(hex)
        assertNotNull(rgb)
        assertNotNull(hsl)
        assertNotNull(hsb)
        assertEquals(ColorFormatMode.HEX, hex.detectedMode)
        assertEquals(ColorFormatMode.RGB, rgb.detectedMode)
        assertEquals(ColorFormatMode.HSL, hsl.detectedMode)
        assertEquals(ColorFormatMode.HSB, hsb.detectedMode)
    }

    @Test
    fun `rejects invalid color text`() {
        assertNull(ColorTextCodec.parse("oops"))
        assertNull(ColorTextCodec.parse("#12"))
        assertNull(ColorTextCodec.parse("rgb(300,0,0)"))
        assertNull(ColorTextCodec.parse("hsl(20, 20)"))
    }

    @Test
    fun `hex round-trip preserves channels`() {
        val source = RgbaColor(0.2f, 0.4f, 0.6f, 0.8f)
        val text = ColorTextCodec.format(source, ColorFormatMode.HEX, includeAlpha = true)
        val parsed = ColorTextCodec.parse(text)
        assertNotNull(parsed)
        assertTrue(closeEnough(source.r, parsed.color.r))
        assertTrue(closeEnough(source.g, parsed.color.g))
        assertTrue(closeEnough(source.b, parsed.color.b))
        assertTrue(closeEnough(source.a, parsed.color.a))
    }

    @Test
    fun `rgb hsl and hsb formatting includes mode-specific prefixes`() {
        val source = RgbaColor(0.1f, 0.5f, 0.9f, 0.7f)
        val rgb = ColorTextCodec.format(source, ColorFormatMode.RGB, includeAlpha = true)
        val hsl = ColorTextCodec.format(source, ColorFormatMode.HSL, includeAlpha = true)
        val hsb = ColorTextCodec.format(source, ColorFormatMode.HSB, includeAlpha = true)

        assertTrue(rgb.startsWith("rgba("))
        assertTrue(hsl.startsWith("hsla("))
        assertTrue(hsb.startsWith("hsba("))
    }

    @Test
    fun `conversions keep grayscale hue stable with fallback`() {
        val gray = RgbaColor(0.45f, 0.45f, 0.45f, 1f)
        val hsv = ColorConversions.rgbToHsv(gray, fallbackHueDeg = 123f)
        val hsl = ColorConversions.rgbToHsl(gray, fallbackHueDeg = 234f)
        assertEquals(123f, hsv.hueDeg)
        assertEquals(234f, hsl.hueDeg)
    }

    private fun closeEnough(a: Float, b: Float): Boolean {
        return abs(a - b) <= 0.01f
    }
}
