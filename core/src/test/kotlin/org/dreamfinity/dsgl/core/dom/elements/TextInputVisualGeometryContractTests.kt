package org.dreamfinity.dsgl.core.dom.elements

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.layout.FontLineMetrics
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.event.FocusManager

class TextInputVisualGeometryContractTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 16

        override fun measureText(text: String): Int = text.length * 8

        override fun fontLineMetrics(fontId: String?, fontSize: Int?): FontLineMetrics {
            return FontLineMetrics(
                emSize = 1f,
                lineHeightEm = 1.25f,
                ascenderEm = 0.8f,
                descenderEm = -0.2f
            )
        }

        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @AfterTest
    fun cleanup() {
        FocusManager.clearFocus()
    }

    @Test
    fun `single line input uses resolved text metrics for text caret and selection geometry`() {
        val root = ContainerNode(key = "root").apply {
            width = 160
            height = 40
        }
        val input = TextInputNode(text = "hello", key = "input").apply {
            width = 140
            height = 24
        }.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 160, 40)
        FocusManager.requestFocus(input)
        val editState = input.readEditState()
        editState.selectionAnchor = 0
        editState.caretIndex = 2
        editState.resetBlinkClock()

        val commands = tree.paint(ctx)
        val textCommand = commands.filterIsInstance<RenderCommand.DrawText>().firstOrNull { it.text == "hello" }
        val selectionRect = commands.filterIsInstance<RenderCommand.DrawRect>()
            .firstOrNull { it.color == input.selectionColor }
        val caretRect = commands.filterIsInstance<RenderCommand.DrawRect>()
            .lastOrNull { it.width == 1 && it.color == input.textColor }

        assertNotNull(textCommand)
        assertNotNull(selectionRect)
        assertNotNull(caretRect)
        assertEquals(2, textCommand.y)
        assertEquals(2, selectionRect.y)
        assertEquals(20, selectionRect.height)
        assertEquals(2, caretRect.y)
        assertEquals(20, caretRect.height)
    }

    @Test
    fun `textarea line spacing and caret height follow resolved text metrics`() {
        val root = ContainerNode(key = "root").apply {
            width = 180
            height = 80
        }
        val area = TextAreaNode(text = "one\ntwo", key = "area").apply {
            width = 160
            height = 48
        }.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, 180, 80)
        FocusManager.requestFocus(area)
        val editState = area.readEditState()
        editState.caretIndex = area.text.length
        editState.selectionAnchor = null
        editState.resetBlinkClock()

        val commands = tree.paint(ctx)
        val textCommands = commands.filterIsInstance<RenderCommand.DrawText>()
            .filter { it.text == "one" || it.text == "two" }
        val caretRect = commands.filterIsInstance<RenderCommand.DrawRect>()
            .lastOrNull { it.width == 1 && it.color == area.textColor }

        assertEquals(2, textCommands.size)
        assertEquals(20, textCommands[1].y - textCommands[0].y)
        assertNotNull(caretRect)
        assertEquals(20, caretRect.height)
    }

    private fun SingleLineInputNode.readEditState(): TextEditState {
        val field = SingleLineInputNode::class.java.getDeclaredField("editState")
        field.isAccessible = true
        return field.get(this) as TextEditState
    }

    private fun TextAreaNode.readEditState(): TextEditState {
        val field = TextAreaNode::class.java.getDeclaredField("editState")
        field.isAccessible = true
        return field.get(this) as TextEditState
    }
}
