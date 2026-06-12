package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.debug.LayoutDebug
import org.dreamfinity.dsgl.core.dom.debug.LayoutValidator
import org.dreamfinity.dsgl.core.dom.layout.Border
import org.dreamfinity.dsgl.core.dom.layout.Insets
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutValidatorTests {
    @Test
    fun `block layout keeps child inside parent content`() {
        LayoutDebug.validateLayouts = true
        LayoutDebug.logViolations = false
        LayoutDebug.strictBounds = false

        val ctx = testMeasureContext()
        val root =
            ContainerNode(key = "root").apply {
                width = 28
                padding = Insets.all(2)
                border = Border.all(1, 0xFF000000.toInt())
            }
        TextNode(
            textSource = TextSource.Static("this is a long sentence that must wrap"),
            key = "child.text",
        ).apply {
            margin = Insets(1, 1, 1, 1)
        }.applyParent(root)

        val measured = root.measure(ctx)
        root.render(ctx, 0, 0, measured.width, measured.height)

        val violations = LayoutValidator.validate(root, ctx)
        assertTrue(
            violations.none { it.code == "CHILD_OUTSIDE_PARENT_CONTENT" },
            "Expected child to stay inside parent content, got: $violations",
        )
    }

    @Test
    fun `wrapped text nodes stack without overlap`() {
        LayoutDebug.validateLayouts = true
        LayoutDebug.logViolations = false
        LayoutDebug.strictBounds = false

        val ctx = testMeasureContext()
        val root =
            ContainerNode(key = "root.wrap").apply {
                width = 32
                gap = 1
            }
        val first =
            TextNode(TextSource.Static("first wrapped text line that should occupy multiple rows"), key = "text.a")
        val second = TextNode(TextSource.Static("second wrapped text line must be below the first one"), key = "text.b")
        first.applyParent(root)
        second.applyParent(root)

        val measured = root.measure(ctx)
        root.render(ctx, 0, 0, measured.width, measured.height)

        assertTrue(
            second.bounds.y >= first.bounds.y + first.bounds.height,
            "Second text node should start after first node bottom.",
        )
        val violations = LayoutValidator.validate(root, ctx)
        assertTrue(
            violations.none { it.code == "TEXT_HEIGHT_UNDERSIZED" || it.code == "TEXT_LINE_COLLISION" },
            "Wrapped text must not produce line-stack violations: $violations",
        )
    }

    @Test
    fun `validator detects escaped child bounds`() {
        LayoutDebug.validateLayouts = true
        LayoutDebug.logViolations = false
        LayoutDebug.strictBounds = false

        val ctx = testMeasureContext()
        val root =
            ContainerNode(key = "root.rogue").apply {
                width = 20
                height = 10
                padding = Insets.all(1)
                border = Border.all(1, 0xFF000000.toInt())
            }
        RogueNode(key = "rogue").applyParent(root)

        val measured = root.measure(ctx)
        root.render(ctx, 0, 0, measured.width, measured.height)

        val violations = LayoutValidator.validate(root, ctx)
        assertEquals(1, violations.count { it.code == "CHILD_OUTSIDE_PARENT_CONTENT" })
    }

    private fun testMeasureContext(): UiMeasureContext =
        object : UiMeasureContext {
            override val fontHeight: Int = 8

            override fun measureText(text: String): Int = text.length * 4

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    private class RogueNode(
        key: Any?,
    ) : DOMNode(key) {
        override fun measure(ctx: UiMeasureContext): Size = Size(6, 4)

        override fun render(
            ctx: UiMeasureContext,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
        ) {
            bounds = Rect(x + 100, y, width, height)
        }
    }
}
