package org.dreamfinity.dsgl.core.colorpicker

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorPickerControllerTests {
    @Test
    fun `mode switch keeps selected color`() {
        val initial = RgbaColor(0.25f, 0.5f, 0.75f, 0.6f)
        val controller =
            ColorPickerController(
                initial =
                    ColorPickerState(
                        color = initial,
                        previous = initial,
                        mode = ColorFormatMode.RGB,
                        alphaEnabled = true,
                    ),
            )
        val firstLayout = controller.buildLayout(Rect(0, 0, 320, controller.preferredHeight(true)))
        controller.handleMouseDown(
            firstLayout.modeSelectRect.x + 2,
            firstLayout.modeSelectRect.y + 2,
            MouseButton.LEFT,
            firstLayout,
        )
        val dropdownLayout = controller.buildLayout(Rect(0, 0, 320, controller.preferredHeight(true)))
        val hslOption =
            dropdownLayout.modeOptions.firstOrNull { it.mode == ColorFormatMode.HSL } ?: error("HSL option missing")
        controller.handleMouseDown(hslOption.rect.x + 2, hslOption.rect.y + 2, MouseButton.LEFT, dropdownLayout)

        val after = controller.snapshot()
        assertEquals(ColorFormatMode.HSL, after.mode)
        assertTrue(closeEnough(initial.r, after.color.r))
        assertTrue(closeEnough(initial.g, after.color.g))
        assertTrue(closeEnough(initial.b, after.color.b))
        assertTrue(closeEnough(initial.a, after.color.a))
    }

    @Test
    fun `eyedropper previews sampled color and commits on click`() {
        val sampled = 0xCC112233.toInt()
        val history = ColorRecentHistory()
        var committed: RgbaColor? = null
        val controller =
            ColorPickerController(
                initial = ColorPickerState(RgbaColor.WHITE, closeOnSelect = false),
                recentHistory = history,
                screenSampler = ScreenColorSampler { _, _ -> sampled },
            )
        controller.onCommit = { committed = it }
        controller.beginEyedropper()
        val layout = controller.buildLayout(Rect(0, 0, 320, controller.preferredHeight(true)))
        val expected = (0xFF shl 24) or (sampled and 0x00FFFFFF)

        controller.handleMouseMove(12, 12, layout)
        controller.sampleEyedropperAtHover()
        val preview = controller.snapshot().color
        assertEquals(expected, preview.toArgbInt())

        controller.handleMouseDown(12, 12, MouseButton.LEFT, layout)
        assertEquals(expected, committed?.toArgbInt())
    }

    @Test
    fun `commit stores color in recents`() {
        val history = ColorRecentHistory()
        val controller =
            ColorPickerController(
                initial = ColorPickerState(color = RgbaColor.WHITE),
                recentHistory = history,
            )
        val next = RgbaColor(0.1f, 0.2f, 0.3f, 0.4f)
        controller.setState(
            ColorPickerState(
                color = next,
                previous = RgbaColor.WHITE,
                mode = ColorFormatMode.HEX,
                alphaEnabled = true,
            ),
        )
        controller.commitCurrentColor()
        assertEquals(
            next.toArgbInt(),
            history
                .snapshot()
                .first()
                .toArgbInt(),
        )
    }

    @Test
    fun `input slot layout separates label and input rectangles`() {
        val controller =
            ColorPickerController(
                initial =
                    ColorPickerState(
                        color = RgbaColor.WHITE,
                        mode = ColorFormatMode.RGB,
                        alphaEnabled = true,
                    ),
            )
        val layout = controller.buildLayout(Rect(0, 0, 360, controller.preferredHeight(true)))
        assertTrue(layout.inputSlots.isNotEmpty())
        layout.inputSlots.forEach { slot ->
            assertTrue(slot.labelRect.width > 0)
            assertTrue(slot.inputRect.width > 0)
            assertTrue(slot.inputRect.x > slot.labelRect.x + slot.labelRect.width - 1)
        }
    }

    @Test
    fun `rgb channel order switch updates active mode and input order`() {
        val controller =
            ColorPickerController(
                initial =
                    ColorPickerState(
                        color = RgbaColor(0.3f, 0.4f, 0.5f, 0.6f),
                        mode = ColorFormatMode.RGB,
                        rgbOrder = RgbChannelOrder.RGBA,
                        alphaEnabled = true,
                    ),
            )
        val firstLayout = controller.buildLayout(Rect(0, 0, 360, controller.preferredHeight(true)))
        val argbRect = firstLayout.argbOrderRect ?: error("ARGB switch not rendered")
        controller.handleMouseDown(argbRect.x + 2, argbRect.y + 2, MouseButton.LEFT, firstLayout)

        val after = controller.snapshot()
        assertEquals(RgbChannelOrder.ARGB, after.rgbOrder)

        val secondLayout = controller.buildLayout(Rect(0, 0, 360, controller.preferredHeight(true)))
        val keys = secondLayout.inputSlots.map { it.key }
        assertEquals(listOf("a", "r", "g", "b"), keys)
    }

    @Test
    fun `eyedropper preview shows mode and formatted value tooltip`() {
        val sampled = 0xFF336699.toInt()
        val sampler =
            object : ScreenColorSampler {
                override fun sampleColorAt(x: Int, y: Int): Int? = sampled
            }
        val controller =
            ColorPickerController(
                initial =
                    ColorPickerState(
                        color = RgbaColor.WHITE,
                        mode = ColorFormatMode.RGB,
                        rgbOrder = RgbChannelOrder.ARGB,
                        alphaEnabled = true,
                    ),
                screenSampler = sampler,
            )
        controller.beginEyedropper()
        val layout = controller.buildLayout(Rect(20, 20, 340, controller.preferredHeight(true)))
        controller.handleMouseMove(120, 160, layout)
        controller.sampleEyedropperAtHover()
        val out = ArrayList<RenderCommand>()
        controller.appendEyedropperPreview(800, 600, out)

        val textCommands = out.filterIsInstance<RenderCommand.DrawText>()
        assertTrue(textCommands.any { it.text.contains("Mode: RGB (ARGB)") })
        val expected =
            ColorTextCodec.format(
                RgbaColor.fromArgbInt(sampled).normalized(),
                ColorFormatMode.RGB,
                true,
                RgbChannelOrder.ARGB,
            )
        assertTrue(textCommands.any { it.text == expected })
    }

    @Test
    fun `eyedropper preview preview path does not use area sampling`() {
        val sampler = RecordingSampler()
        val controller =
            ColorPickerController(
                initial =
                    ColorPickerState(
                        color = RgbaColor.WHITE,
                        mode = ColorFormatMode.HEX,
                        alphaEnabled = true,
                    ),
                screenSampler = sampler,
            )
        controller.beginEyedropper()
        val layout = controller.buildLayout(Rect(0, 0, 360, controller.preferredHeight(true)))
        controller.handleMouseMove(80, 90, layout)
        controller.sampleEyedropperAtHover()
        val out = ArrayList<RenderCommand>()
        controller.appendEyedropperPreview(640, 480, out)

        assertEquals(0, sampler.areaCalls)
        assertTrue(sampler.colorCalls > 0)
        assertTrue(out.any { it is RenderCommand.CaptureScreenRegion })
        assertTrue(out.any { it is RenderCommand.DrawCapturedScreenRegion })
    }

    @Test
    fun `eyedropper preview emits capture and textured magnifier commands instead of per-cell rectangles`() {
        val controller =
            ColorPickerController(
                initial =
                    ColorPickerState(
                        color = RgbaColor.WHITE,
                        mode = ColorFormatMode.HEX,
                        alphaEnabled = true,
                    ),
            )
        controller.beginEyedropper()
        val layout = controller.buildLayout(Rect(0, 0, 360, controller.preferredHeight(true)))
        controller.handleMouseMove(80, 90, layout)
        val out = ArrayList<RenderCommand>()
        controller.appendEyedropperPreview(640, 480, out)

        assertTrue(out.any { it is RenderCommand.CaptureScreenRegion })
        assertTrue(out.any { it is RenderCommand.DrawCapturedScreenRegion })
        assertTrue(
            out.none { command ->
                command is RenderCommand.DrawRect && command.width == 8 && command.height == 8
            },
        )
    }

    @Test
    fun `eyedropper preview draws aligned light grid over captured magnifier`() {
        val gridColor = 0x7F57C2FF
        val controller =
            ColorPickerController(
                initial =
                    ColorPickerState(
                        color = RgbaColor.WHITE,
                        mode = ColorFormatMode.HEX,
                        alphaEnabled = true,
                    ),
                style =
                    ColorPickerStyle(
                        eyedropperGridSize = 5,
                        eyedropperCellSize = 4,
                        eyedropperGridEnabled = true,
                        eyedropperGridColor = gridColor,
                    ),
            )
        controller.beginEyedropper()
        val layout = controller.buildLayout(Rect(0, 0, 360, controller.preferredHeight(true)))
        controller.handleMouseMove(80, 90, layout)
        val out = ArrayList<RenderCommand>()
        controller.appendEyedropperPreview(640, 480, out)

        val magnifier = out.filterIsInstance<RenderCommand.DrawCapturedScreenRegion>().single()
        val gridLines = out.filterIsInstance<RenderCommand.DrawRect>().filter { it.color == gridColor }
        assertEquals(8, gridLines.size)
        assertTrue(gridLines.any { it.width == 1 && it.height == magnifier.height })
        assertTrue(gridLines.any { it.width == magnifier.width && it.height == 1 })
    }

    @Test
    fun `eyedropper keeps existing alpha while sampling rgb`() {
        val sampled = 0x00336699
        val controller =
            ColorPickerController(
                initial =
                    ColorPickerState(
                        color = RgbaColor(0.1f, 0.2f, 0.3f, 0.4f),
                        mode = ColorFormatMode.HEX,
                        alphaEnabled = true,
                    ),
                screenSampler = ScreenColorSampler { _, _ -> sampled },
            )
        controller.beginEyedropper()
        val layout = controller.buildLayout(Rect(0, 0, 320, controller.preferredHeight(true)))
        controller.handleMouseMove(20, 24, layout)
        controller.sampleEyedropperAtHover()

        val color = controller.snapshot().color
        assertEquals(0.4f, color.a)
        assertTrue(closeEnough(0x33 / 255f, color.r))
        assertTrue(closeEnough(0x66 / 255f, color.g))
        assertTrue(closeEnough(0x99 / 255f, color.b))
    }

    @Test
    fun `picker draws gradient bars with dedicated render commands`() {
        val controller =
            ColorPickerController(
                initial =
                    ColorPickerState(
                        color = RgbaColor.WHITE,
                        mode = ColorFormatMode.RGB,
                        alphaEnabled = true,
                    ),
            )
        val layout = controller.buildLayout(Rect(0, 0, 320, controller.preferredHeight(true)))
        val out = ArrayList<RenderCommand>()
        controller.appendCommands(layout, out)

        assertTrue(out.any { it is RenderCommand.DrawColorField })
        assertTrue(out.any { it is RenderCommand.DrawHueBar })
        assertTrue(out.any { it is RenderCommand.DrawAlphaBar })
    }

    @Test
    fun `picker checker backgrounds use dedicated checkerboard command`() {
        val checkerLight = 0x7FA0D010
        val checkerDark = 0x7F104090
        val controller =
            ColorPickerController(
                initial =
                    ColorPickerState(
                        color = RgbaColor.WHITE,
                        mode = ColorFormatMode.RGB,
                        alphaEnabled = true,
                    ),
                style =
                    ColorPickerStyle(
                        checkerLightColor = checkerLight,
                        checkerDarkColor = checkerDark,
                    ),
            )
        val layout = controller.buildLayout(Rect(0, 0, 320, controller.preferredHeight(true)))
        val out = ArrayList<RenderCommand>()
        controller.appendCommands(layout, out)

        assertTrue(out.any { it is RenderCommand.DrawCheckerboard })
        assertTrue(
            out.none { command ->
                command is RenderCommand.DrawRect && (command.color == checkerLight || command.color == checkerDark)
            },
        )
    }

    private class RecordingSampler : ScreenColorSampler {
        var colorCalls: Int = 0
        var areaCalls: Int = 0

        override fun sampleColorAt(x: Int, y: Int): Int? {
            colorCalls += 1
            return 0xFF112233.toInt()
        }

        override fun sampleArea(
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            outArgb: IntArray,
        ): Boolean {
            areaCalls += 1
            var index = 0
            while (index < width * height && index < outArgb.size) {
                outArgb[index] = 0xFF112233.toInt()
                index++
            }
            return true
        }
    }

    private fun closeEnough(a: Float, b: Float): Boolean = kotlin.math.abs(a - b) <= 0.01f
}
