package org.dreamfinity.dsgl.core.colorpicker

import org.dreamfinity.dsgl.core.dom.elements.ColorPickerInlineNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseDownEvent
import org.dreamfinity.dsgl.core.event.MouseDragEvent
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
}
