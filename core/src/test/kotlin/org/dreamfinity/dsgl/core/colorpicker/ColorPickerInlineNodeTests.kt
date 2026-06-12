package org.dreamfinity.dsgl.core.colorpicker

import org.dreamfinity.dsgl.core.dom.elements.ColorPickerInlineNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseDownEvent
import org.dreamfinity.dsgl.core.event.MouseDragEvent
import org.dreamfinity.dsgl.core.event.MouseLeaveEvent
import org.dreamfinity.dsgl.core.event.MouseMoveEvent
import org.dreamfinity.dsgl.core.event.MouseOverEvent
import org.dreamfinity.dsgl.core.event.MouseUpEvent
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ColorPickerInlineNodeTests {
    private val ctx =
        object : UiMeasureContext {
            override fun measureText(text: String): Int = text.length * 6

            override fun measureText(text: String, fontId: String?, fontSize: Int?): Int = text.length * 6

            override val fontHeight: Int = 9

            override fun fontHeight(fontId: String?, fontSize: Int?): Int = 9

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @Test
    fun `controlled inline picker drag survives controlled rerender`() {
        var controlledValue = RgbaColor(1f, 0f, 0f, 1f)

        fun createPicker(): ColorPickerInlineNode =
            ColorPickerInlineNode(
                controlled = true,
                value = controlledValue,
                mode = ColorFormatMode.HSB,
                alphaEnabled = true,
                key = "picker",
            ).apply {
                closeOnSelect = false
                onPreviewColor = { controlledValue = it }
                onChangeColor = { controlledValue = it }
                onCommitColor = { controlledValue = it }
            }

        val retained = createPicker()
        retained.render(ctx, 0, 0, 350, 392)

        val layoutProbe =
            ColorPickerController(
                initial =
                    ColorPickerState(
                        color = RgbaColor(1f, 0f, 0f, 1f),
                        previous = RgbaColor(1f, 0f, 0f, 1f),
                        mode = ColorFormatMode.HSB,
                        alphaEnabled = true,
                        closeOnSelect = false,
                    ),
            ).buildLayout(Rect(0, 0, 350, 392))

        val startX = layoutProbe.colorFieldRect.x + 4
        val startY = layoutProbe.colorFieldRect.y + layoutProbe.colorFieldRect.height - 4
        EventBus.post(MouseDownEvent(startX, startY, MouseButton.LEFT).also { it.target = retained })
        val afterPress = controlledValue

        val template = createPicker()
        retained.syncFrom(template)
        retained.render(ctx, 0, 0, 350, 392)

        val endX = layoutProbe.colorFieldRect.x + layoutProbe.colorFieldRect.width - 4
        val endY = layoutProbe.colorFieldRect.y + 4
        EventBus.post(
            MouseDragEvent(
                lastMouseX = startX,
                lastMouseY = startY,
                dx = endX - startX,
                dy = endY - startY,
                mouseButton = MouseButton.LEFT,
            ).also { it.target = retained },
        )

        assertTrue(afterPress.toArgbInt() != controlledValue.toArgbInt())
    }

    @Test
    fun `inline picker draws mode options after clicking mode selector`() {
        val picker =
            ColorPickerInlineNode(
                controlled = true,
                value = RgbaColor.WHITE,
                mode = ColorFormatMode.HEX,
                alphaEnabled = true,
                key = "picker",
            ).apply {
                closeOnSelect = false
            }

        picker.render(ctx, 0, 0, 350, 392)
        val probeLayout = layoutProbe(mode = ColorFormatMode.HEX, alphaEnabled = true)

        EventBus.post(
            MouseDownEvent(
                probeLayout.modeSelectRect.x + 4,
                probeLayout.modeSelectRect.y + 4,
                MouseButton.LEFT,
            ).also { it.target = picker },
        )

        val commands = buildCommands(picker)
        val modeTexts =
            commands
                .filterIsInstance<RenderCommand.DrawText>()
                .map { it.text }
                .filter { text -> ColorFormatMode.entries.any { it.name == text } }

        assertTrue(modeTexts.size >= 5)
    }

    @Test
    fun `inline picker clears custom hover state on mouse leave`() {
        val picker =
            ColorPickerInlineNode(
                controlled = true,
                value = RgbaColor.WHITE,
                mode = ColorFormatMode.RGB,
                alphaEnabled = true,
                key = "picker",
            ).apply {
                closeOnSelect = false
            }
        picker.render(ctx, 0, 0, 350, 392)
        val probeLayout = layoutProbe(mode = ColorFormatMode.RGB, alphaEnabled = true)
        val style = ColorPickerStyle()
        val copyX = probeLayout.copyRect.x + 2
        val copyY = probeLayout.copyRect.y + 2

        EventBus.post(MouseMoveEvent(copyX, copyY, copyX - 1, copyY - 1).also { it.target = picker })

        assertEquals(style.buttonHoverColor, fillForRect(buildCommands(picker), probeLayout.copyRect))

        EventBus.post(MouseLeaveEvent(copyX, copyY).also { it.target = picker })

        assertEquals(style.buttonBackgroundColor, fillForRect(buildCommands(picker), probeLayout.copyRect))
    }

    @Test
    fun `inline picker ignores targetless hover events`() {
        val picker =
            ColorPickerInlineNode(
                controlled = true,
                value = RgbaColor.WHITE,
                mode = ColorFormatMode.RGB,
                alphaEnabled = true,
                key = "picker",
            ).apply {
                closeOnSelect = false
            }
        picker.render(ctx, 0, 0, 350, 392)
        val probeLayout = layoutProbe(mode = ColorFormatMode.RGB, alphaEnabled = true)
        val style = ColorPickerStyle()
        val inputX =
            probeLayout.inputSlots
                .first()
                .inputRect.x + 2
        val inputY =
            probeLayout.inputSlots
                .first()
                .inputRect.y + 2

        EventBus.post(MouseMoveEvent(inputX, inputY, inputX - 1, inputY - 1))
        EventBus.post(MouseOverEvent(inputX, inputY))

        assertEquals(
            style.inputBorderColor,
            borderColorForRect(
                buildCommands(picker),
                probeLayout.inputSlots
                    .first()
                    .inputRect,
            ),
        )
    }

    @Test
    fun `inline picker draws eyedropper preview after clicking pipette`() {
        val picker =
            ColorPickerInlineNode(
                controlled = true,
                value = RgbaColor.WHITE,
                mode = ColorFormatMode.RGB,
                alphaEnabled = true,
                key = "picker",
            ).apply {
                closeOnSelect = false
            }

        picker.render(ctx, 0, 0, 350, 392)
        val probeLayout = layoutProbe(mode = ColorFormatMode.RGB, alphaEnabled = true)

        EventBus.post(
            MouseDownEvent(
                probeLayout.pipetteRect.x + 4,
                probeLayout.pipetteRect.y + 4,
                MouseButton.LEFT,
            ).also { it.target = picker },
        )

        val commands = buildGlobalEyedropperCommands(picker)
        val texts = commands.filterIsInstance<RenderCommand.DrawText>().map { it.text }

        assertTrue(texts.any { it.startsWith("Mode: RGB") })
    }

    @Test
    fun `inline eyedropper samples color on mouse move even outside picker bounds`() {
        ScreenColorSamplerBridge.install(
            ScreenColorSampler { x, y ->
                (0xFF shl 24) or ((x and 0xFF) shl 16) or ((y and 0xFF) shl 8) or 0x44
            },
        )
        try {
            var current = RgbaColor.WHITE
            val picker =
                ColorPickerInlineNode(
                    controlled = true,
                    value = current,
                    mode = ColorFormatMode.RGB,
                    alphaEnabled = true,
                    key = "picker",
                ).apply {
                    closeOnSelect = false
                    onPreviewColor = { current = it }
                    onChangeColor = { current = it }
                }

            picker.render(ctx, 0, 0, 350, 392)
            val probeLayout = layoutProbe(mode = ColorFormatMode.RGB, alphaEnabled = true)
            EventBus.post(
                MouseDownEvent(
                    probeLayout.pipetteRect.x + 4,
                    probeLayout.pipetteRect.y + 4,
                    MouseButton.LEFT,
                ).also { it.target = picker },
            )

            EventBus.post(
                MouseMoveEvent(
                    mouseX = 1200,
                    mouseY = 900,
                    prevX = 300,
                    prevY = 250,
                ).also { it.target = picker },
            )
            picker.captureEyedropperSample()

            val first = current.toArgbInt()
            assertEquals((0xFF shl 24) or (0xB0 shl 16) or (0x84 shl 8) or 0x44, first)

            EventBus.post(
                MouseMoveEvent(
                    mouseX = 1234,
                    mouseY = 912,
                    prevX = 1200,
                    prevY = 900,
                ).also { it.target = picker },
            )
            picker.captureEyedropperSample()

            assertEquals((0xFF shl 24) or (0xD2 shl 16) or (0x90 shl 8) or 0x44, current.toArgbInt())
            assertNotEquals(first, current.toArgbInt())
        } finally {
            ScreenColorSamplerBridge.install(null)
        }
    }

    @Test
    fun `inline eyedropper session survives reconcile syncFrom`() {
        val sampledArgb = 0xFF3BC47A.toInt()
        ScreenColorSamplerBridge.install(ScreenColorSampler { _, _ -> sampledArgb })
        try {
            var current = RgbaColor.WHITE

            fun createPicker(value: RgbaColor): ColorPickerInlineNode =
                ColorPickerInlineNode(
                    controlled = true,
                    value = value,
                    mode = ColorFormatMode.RGB,
                    alphaEnabled = true,
                    key = "picker",
                ).apply {
                    closeOnSelect = false
                    onPreviewColor = { current = it }
                    onChangeColor = { current = it }
                }

            val retained = createPicker(current)
            retained.render(ctx, 0, 0, 350, 392)
            val probeLayout = layoutProbe(mode = ColorFormatMode.RGB, alphaEnabled = true)
            EventBus.post(
                MouseDownEvent(
                    probeLayout.pipetteRect.x + 4,
                    probeLayout.pipetteRect.y + 4,
                    MouseButton.LEFT,
                ).also { it.target = retained },
            )

            val template = createPicker(current)
            retained.syncFrom(template)
            retained.render(ctx, 0, 0, 350, 392)

            EventBus.post(
                MouseMoveEvent(
                    mouseX = 1440,
                    mouseY = 820,
                    prevX = 300,
                    prevY = 250,
                ).also { it.target = retained },
            )
            retained.captureEyedropperSample()

            assertEquals(sampledArgb, current.toArgbInt())
            assertTrue(retained.wantsGlobalPointerInput())
        } finally {
            ScreenColorSamplerBridge.install(null)
        }
    }

    @Test
    fun `external mode and alpha changes apply after non drag click`() {
        val retained =
            ColorPickerInlineNode(
                controlled = true,
                value = RgbaColor.WHITE,
                mode = ColorFormatMode.HEX,
                alphaEnabled = true,
                key = "picker",
            ).apply {
                closeOnSelect = false
            }

        retained.render(ctx, 0, 0, 350, 392)
        val firstLayout = layoutProbe(mode = ColorFormatMode.HEX, alphaEnabled = true)
        EventBus.post(
            MouseDownEvent(
                firstLayout.modeSelectRect.x + 4,
                firstLayout.modeSelectRect.y + 4,
                MouseButton.LEFT,
            ).also { it.target = retained },
        )
        EventBus.post(
            MouseUpEvent(
                firstLayout.modeSelectRect.x + 4,
                firstLayout.modeSelectRect.y + 4,
                MouseButton.LEFT,
            ).also { it.target = retained },
        )

        val template =
            ColorPickerInlineNode(
                controlled = true,
                value = RgbaColor.WHITE,
                mode = ColorFormatMode.RGB,
                alphaEnabled = false,
                key = "picker",
            ).apply {
                closeOnSelect = false
            }

        retained.syncFrom(template)
        retained.render(ctx, 0, 0, 350, 392)

        val commands = buildCommands(retained)
        val texts = commands.filterIsInstance<RenderCommand.DrawText>().map { it.text }

        assertTrue(texts.any { it == "RGB" })
        assertTrue(commands.none { it is RenderCommand.DrawAlphaBar })
    }

    private fun buildCommands(picker: ColorPickerInlineNode): List<RenderCommand> {
        val out = ArrayList<RenderCommand>()
        picker.buildRenderCommands(ctx, out)
        return out
    }

    private fun buildGlobalEyedropperCommands(picker: ColorPickerInlineNode): List<RenderCommand> {
        val out = ArrayList<RenderCommand>()
        picker.appendEyedropperPortalCommands(
            viewportWidth = 1920,
            viewportHeight = 1080,
            out = out,
        )
        return out
    }

    private fun fillForRect(commands: List<RenderCommand>, rect: Rect): Int =
        commands
            .filterIsInstance<RenderCommand.DrawRect>()
            .first { command ->
                command.x == rect.x &&
                    command.y == rect.y &&
                    command.width == rect.width &&
                    command.height == rect.height
            }.color

    private fun borderColorForRect(commands: List<RenderCommand>, rect: Rect): Int =
        commands
            .filterIsInstance<RenderCommand.DrawRect>()
            .first { command ->
                command.x == rect.x &&
                    command.y == rect.y &&
                    command.width == rect.width &&
                    command.height == 1
            }.color

    private fun layoutProbe(mode: ColorFormatMode, alphaEnabled: Boolean): ColorPickerLayout =
        ColorPickerController(
            initial =
                ColorPickerState(
                    color = RgbaColor.WHITE,
                    previous = RgbaColor.WHITE,
                    mode = mode,
                    alphaEnabled = alphaEnabled,
                    closeOnSelect = false,
                ),
        ).buildLayout(Rect(0, 0, 350, 392))
}
