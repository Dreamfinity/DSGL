package org.dreamfinity.dsgl.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.debug.LayoutDebug
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.render.RenderCommand

class LayoutStrictModeTests {
    @Test
    fun `strict layout mode invalidates safely without throwing`() {
        val prevValidate = LayoutDebug.validateLayouts
        val prevStrict = LayoutDebug.strictBounds
        val prevDraw = LayoutDebug.drawBounds
        val prevLog = LayoutDebug.logViolations
        try {
            LayoutDebug.validateLayouts = true
            LayoutDebug.strictBounds = true
            LayoutDebug.drawBounds = false
            LayoutDebug.logViolations = false

            val root = ContainerNode(key = "root").apply {
                width = 20
                height = 10
            }
            RogueNode(key = "rogue").applyParent(root)
            val tree = DomTree(root)
            val ctx = testMeasureContext()

            tree.render(ctx, 20, 10)
            val commands = tree.paint(ctx)

            assertTrue(LayoutDebug.lastViolationCount > 0, "Expected strict mode to detect violations.")
            assertTrue(commands.isEmpty(), "Strict invalid layout should suppress normal paint commands.")
            assertFalse(
                tree.dispatchClick(MouseClickEvent(5, 5, MouseButton.LEFT)),
                "Strict invalid layout should suppress hit-testing."
            )
        } finally {
            LayoutDebug.validateLayouts = prevValidate
            LayoutDebug.strictBounds = prevStrict
            LayoutDebug.drawBounds = prevDraw
            LayoutDebug.logViolations = prevLog
        }
    }

    private class RogueNode(key: Any?) : DOMNode(key) {
        override fun measure(ctx: UiMeasureContext): Size = Size(6, 4)

        override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
            bounds = Rect(x + 128, y + 64, width, height)
        }
    }

    private fun testMeasureContext(): UiMeasureContext {
        return object : UiMeasureContext {
            override val fontHeight: Int = 8
            override fun measureText(text: String): Int = text.length * 6
            override fun paint(commands: List<RenderCommand>) = Unit
        }
    }
}
