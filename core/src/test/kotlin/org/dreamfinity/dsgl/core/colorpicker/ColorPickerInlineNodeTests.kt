package org.dreamfinity.dsgl.core.colorpicker

import org.dreamfinity.dsgl.core.dom.elements.ColorPickerInlineNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseDownEvent
import org.dreamfinity.dsgl.core.event.MouseDragEvent
import org.dreamfinity.dsgl.core.event.MouseUpEvent
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.Test
import kotlin.test.assertTrue

class ColorPickerInlineNodeTests {
    private val ctx = object : UiMeasureContext {
        override fun measureText(text: String): Int = text.length * 6
        override fun measureText(text: String, fontId: String?, fontSize: Int?): Int = text.length * 6
        override val fontHeight: Int = 9
        override fun fontHeight(fontId: String?, fontSize: Int?): Int = 9
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @Test
    fun `controlled inline picker drag survives controlled rerender`() {
        var controlledValue = RgbaColor(1f, 0f, 0f, 1f)

        fun createPicker(): ColorPickerInlineNode {
            return ColorPickerInlineNode(
                controlled = true,
                value = controlledValue,
                mode = ColorFormatMode.HSB,
                alphaEnabled = true,
                key = "picker"
            ).apply {
                closeOnSelect = false
                onPreviewColor = { controlledValue = it }
                onChangeColor = { controlledValue = it }
                onCommitColor = { controlledValue = it }
            }
        }

        val retained = createPicker()
        retained.render(ctx, 0, 0, 350, 392)

        val layoutProbe = ColorPickerController(
            initial = ColorPickerState(
                color = RgbaColor(1f, 0f, 0f, 1f),
                previous = RgbaColor(1f, 0f, 0f, 1f),
                mode = ColorFormatMode.HSB,
                alphaEnabled = true,
                closeOnSelect = false
            )
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
                mouseButton = MouseButton.LEFT
            ).also { it.target = retained }
        )

        assertTrue(afterPress.toArgbInt() != controlledValue.toArgbInt())
    }

    @Test
    fun `inline picker draws mode options after clicking mode selector`() {
        val picker = ColorPickerInlineNode(
            controlled = true,
            value = RgbaColor.WHITE,
            mode = ColorFormatMode.HEX,
            alphaEnabled = true,
            key = "picker"
        ).apply {
            closeOnSelect = false
        }

        picker.render(ctx, 0, 0, 350, 392)
        val probeLayout = layoutProbe(mode = ColorFormatMode.HEX, alphaEnabled = true)

        EventBus.post(
            MouseDownEvent(
                probeLayout.modeSelectRect.x + 4,
                probeLayout.modeSelectRect.y + 4,
                MouseButton.LEFT
            ).also { it.target = picker }
        )

        val commands = buildCommands(picker)
        val modeTexts = commands.filterIsInstance<RenderCommand.DrawText>()
            .map { it.text }
            .filter { text -> ColorFormatMode.entries.any { it.name == text } }

        assertTrue(modeTexts.size >= 5)
    }

    @Test
    fun `inline picker draws eyedropper overlay after clicking pipette`() {
        val picker = ColorPickerInlineNode(
            controlled = true,
            value = RgbaColor.WHITE,
            mode = ColorFormatMode.RGB,
            alphaEnabled = true,
            key = "picker"
        ).apply {
            closeOnSelect = false
        }

        picker.render(ctx, 0, 0, 350, 392)
        val probeLayout = layoutProbe(mode = ColorFormatMode.RGB, alphaEnabled = true)

        EventBus.post(
            MouseDownEvent(
                probeLayout.pipetteRect.x + 4,
                probeLayout.pipetteRect.y + 4,
                MouseButton.LEFT
            ).also { it.target = picker }
        )

        val commands = buildCommands(picker)
        val texts = commands.filterIsInstance<RenderCommand.DrawText>().map { it.text }

        assertTrue(texts.any { it.startsWith("Mode: RGB") })
    }

    @Test
    fun `external mode and alpha changes apply after non drag click`() {
        val retained = ColorPickerInlineNode(
            controlled = true,
            value = RgbaColor.WHITE,
            mode = ColorFormatMode.HEX,
            alphaEnabled = true,
            key = "picker"
        ).apply {
            closeOnSelect = false
        }

        retained.render(ctx, 0, 0, 350, 392)
        val firstLayout = layoutProbe(mode = ColorFormatMode.HEX, alphaEnabled = true)
        EventBus.post(
            MouseDownEvent(
                firstLayout.modeSelectRect.x + 4,
                firstLayout.modeSelectRect.y + 4,
                MouseButton.LEFT
            ).also { it.target = retained }
        )
        EventBus.post(
            MouseUpEvent(
                firstLayout.modeSelectRect.x + 4,
                firstLayout.modeSelectRect.y + 4,
                MouseButton.LEFT
            ).also { it.target = retained }
        )

        val template = ColorPickerInlineNode(
            controlled = true,
            value = RgbaColor.WHITE,
            mode = ColorFormatMode.RGB,
            alphaEnabled = false,
            key = "picker"
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

    private fun layoutProbe(mode: ColorFormatMode, alphaEnabled: Boolean): ColorPickerLayout {
        return ColorPickerController(
            initial = ColorPickerState(
                color = RgbaColor.WHITE,
                previous = RgbaColor.WHITE,
                mode = mode,
                alphaEnabled = alphaEnabled,
                closeOnSelect = false
            )
        ).buildLayout(Rect(0, 0, 350, 392))
    }
}
