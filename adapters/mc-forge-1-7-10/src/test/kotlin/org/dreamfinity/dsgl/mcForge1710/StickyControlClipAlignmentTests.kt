package org.dreamfinity.dsgl.mcForge1710

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.NumberInputNode
import org.dreamfinity.dsgl.core.dom.elements.SelectNode
import org.dreamfinity.dsgl.core.dom.elements.SingleLineInputNode
import org.dreamfinity.dsgl.core.dom.elements.TextInputNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.select.selectModel
import org.dreamfinity.dsgl.core.style.Overflow
import org.dreamfinity.dsgl.core.style.StyleDeclarations
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleExpression
import org.dreamfinity.dsgl.core.style.StyleProperty
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StickyControlClipAlignmentTests {
    private val viewportWidth = 420
    private val viewportHeight = 260

    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanup() {
        FocusManager.clearFocus()
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `non-sticky text input keeps draw and clip aligned`() {
        val fixture =
            createControlFixture(
                sticky = false,
                scrollY = 0,
                controlFactory = { TextInputNode(text = "hello", key = "align-text") },
            )
        FocusManager.requestFocus(fixture.control as SingleLineInputNode)
        fixture.tree.paint(ctx)

        val observations = observeCommands(fixture.tree.paint(ctx))
        assertTextInsideActiveClip(observations, "hello")
        assertCaretInsideActiveClip(observations, "hello")
    }

    @Test
    fun `sticky-clamped text input keeps shell text and caret clip-aligned`() {
        val fixture =
            createControlFixture(
                sticky = true,
                scrollY = 46,
                controlFactory = { TextInputNode(text = "hello", key = "align-sticky-text") },
            )
        FocusManager.requestFocus(fixture.control as SingleLineInputNode)
        fixture.tree.paint(ctx)

        val visible = visibleRect(fixture.control)
        assertNotEquals(fixture.control.bounds.y, visible.y)

        val observations = observeCommands(fixture.tree.paint(ctx))
        assertTextInsideActiveClip(observations, "hello")
        assertCaretInsideActiveClip(observations, "hello")
    }

    @Test
    fun `sticky-clamped number input keeps text and clip aligned`() {
        val fixture =
            createControlFixture(
                sticky = true,
                scrollY = 46,
                controlFactory = { NumberInputNode(value = 42, key = "align-sticky-number") },
            )
        fixture.tree.paint(ctx)

        val visible = visibleRect(fixture.control)
        assertNotEquals(fixture.control.bounds.y, visible.y)

        val observations = observeCommands(fixture.tree.paint(ctx))
        assertTextInsideActiveClip(observations, "42")
    }

    @Test
    fun `sticky-clamped closed select keeps label text and clip aligned`() {
        val fixture =
            createControlFixture(
                sticky = true,
                scrollY = 46,
                controlFactory = {
                    SelectNode(
                        model =
                            selectModel(id = "sticky-select-model") {
                                option("a", "Alpha")
                                option("b", "Beta")
                            },
                        key = "align-sticky-select",
                    )
                },
            )
        fixture.tree.paint(ctx)

        val visible = visibleRect(fixture.control)
        assertNotEquals(fixture.control.bounds.y, visible.y)

        val observations = observeCommands(fixture.tree.paint(ctx))
        assertTextInsideActiveClip(observations, "Alpha")
    }

    @Test
    fun `nested clip path stays coherent for sticky-clamped text input`() {
        val root =
            ContainerNode(key = "nested-clip-root").apply {
                width = 220
                height = 86
                overflowY = Overflow.Auto
            }
        ContainerNode(key = "nested-clip-top-spacer")
            .apply {
                width = 220
                height = 22
            }.applyParent(root)
        val stickyRow =
            ContainerNode(key = "nested-clip-sticky-row")
                .apply {
                    width = 220
                    height = 30
                    inlineStyleDeclarations =
                        styleDeclarations(
                            StyleProperty.POSITION to "sticky",
                            StyleProperty.TOP to "0px",
                        )
                }.applyParent(root)
        val nestedClip =
            ContainerNode(key = "nested-clip-wrapper")
                .apply {
                    width = 180
                    height = 20
                    overflowX = Overflow.Hidden
                    overflowY = Overflow.Hidden
                }.applyParent(stickyRow)
        val input =
            TextInputNode(text = "nested", key = "nested-clip-input")
                .apply {
                    width = 140
                    height = 18
                }.applyParent(nestedClip)
        ContainerNode(key = "nested-clip-filler")
            .apply {
                width = 220
                height = 280
            }.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, viewportWidth, viewportHeight)
        root.setScrollOffsets(0, 46)
        tree.render(ctx, viewportWidth, viewportHeight)

        val commands = tree.paint(ctx)
        val observations = observeCommands(commands)
        val nestedText = observations.texts.firstOrNull { it.text == "nested" }
        assertNotNull(nestedText)
        assertNotNull(nestedText.activeClip)
        assertTrue(contains(nestedText.activeClip, nestedText.x, nestedText.y))

        val transformedClipCount = observations.pushClips.count { it.transformed != it.raw }
        assertTrue(transformedClipCount >= 1)

        val visible = visibleRect(input)
        assertNotEquals(input.bounds.y, visible.y)
    }

    private fun createControlFixture(sticky: Boolean, scrollY: Int, controlFactory: () -> DOMNode): ControlFixture {
        val root =
            ContainerNode(key = "clip-align-root").apply {
                width = 220
                height = 86
                overflowY = Overflow.Auto
            }
        if (sticky) {
            ContainerNode(key = "clip-align-top-spacer")
                .apply {
                    width = 220
                    height = 22
                }.applyParent(root)
        }
        val host =
            ContainerNode(key = "clip-align-host")
                .apply {
                    width = 220
                    height = 28
                    if (sticky) {
                        inlineStyleDeclarations =
                            styleDeclarations(
                                StyleProperty.POSITION to "sticky",
                                StyleProperty.TOP to "0px",
                            )
                    }
                }.applyParent(root)
        val control =
            controlFactory()
                .apply {
                    width = 140
                    height = 18
                }.applyParent(host)
        ContainerNode(key = "clip-align-filler")
            .apply {
                width = 220
                height = 280
            }.applyParent(root)

        val tree = DomTree(root)
        tree.render(ctx, viewportWidth, viewportHeight)
        if (scrollY > 0) {
            root.setScrollOffsets(0, scrollY)
            tree.render(ctx, viewportWidth, viewportHeight)
        }
        return ControlFixture(tree = tree, control = control)
    }

    private fun observeCommands(commands: List<RenderCommand>): CommandObservations {
        val transform = RenderCommandTransformStack()
        val clipStack = ArrayDeque<GuiClipRect>()
        val pushClips = ArrayList<ObservedClipPush>()
        val texts = ArrayList<ObservedText>()
        val rects = ArrayList<ObservedRect>()
        commands.forEach { command ->
            when (command) {
                is RenderCommand.PushTransform -> transform.push(command)
                is RenderCommand.PopTransform -> transform.pop()
                is RenderCommand.PushClip -> {
                    val transformed = transform.resolveClipRect(command.x, command.y, command.width, command.height)
                    val raw =
                        GuiClipRect(
                            command.x,
                            command.y,
                            command.width.coerceAtLeast(0),
                            command.height.coerceAtLeast(0),
                        )
                    pushClips += ObservedClipPush(raw = raw, transformed = transformed)
                    clipStack.addLast(transformed)
                }

                is RenderCommand.PopClip -> if (clipStack.isNotEmpty()) clipStack.removeLast()
                is RenderCommand.DrawText -> {
                    val point = transform.transformPoint(command.x.toFloat(), command.y.toFloat())
                    texts +=
                        ObservedText(
                            text = command.text,
                            x = floorToInt(point.first),
                            y = floorToInt(point.second),
                            activeClip = clipStack.lastOrNull(),
                        )
                }

                is RenderCommand.DrawRect -> {
                    val transformed = transform.resolveClipRect(command.x, command.y, command.width, command.height)
                    rects +=
                        ObservedRect(
                            transformed = transformed,
                            rawWidth = command.width.coerceAtLeast(0),
                            rawHeight = command.height.coerceAtLeast(0),
                            activeClip = clipStack.lastOrNull(),
                        )
                }

                else -> Unit
            }
        }
        return CommandObservations(pushClips = pushClips, texts = texts, rects = rects)
    }

    private fun assertTextInsideActiveClip(observations: CommandObservations, text: String) {
        val observed = observations.texts.firstOrNull { it.text == text }
        assertNotNull(observed, "Expected DrawText('$text')")
        assertNotNull(observed.activeClip, "Expected active clip while drawing '$text'")
        assertTrue(
            contains(observed.activeClip, observed.x, observed.y),
            "Expected transformed clip to contain transformed text point for '$text': " +
                "point=(${observed.x},${observed.y}) clip=${observed.activeClip}",
        )
    }

    private fun assertCaretInsideActiveClip(observations: CommandObservations, nearText: String) {
        val text = observations.texts.firstOrNull { it.text == nearText }
        assertNotNull(text, "Expected DrawText('$nearText') for caret alignment assertion")
        val caret =
            observations.rects.firstOrNull { rect ->
                rect.rawWidth == 1 &&
                    rect.activeClip != null &&
                    abs(rect.transformed.y - text.y) <= 2
            }
        assertNotNull(caret, "Expected caret-like 1px rect near text '$nearText'")
        assertNotNull(caret.activeClip)
        assertTrue(
            containsRect(caret.activeClip, caret.transformed),
            "Expected caret rect to be clipped by transformed active clip: " +
                "caret=${caret.transformed} clip=${caret.activeClip}",
        )
    }

    private fun visibleRect(node: DOMNode): Rect {
        val world = node.worldTransformMatrix()
        val b = node.bounds
        val topLeft = world.transform(b.x.toFloat(), b.y.toFloat())
        val topRight = world.transform((b.x + b.width).toFloat(), b.y.toFloat())
        val bottomLeft = world.transform(b.x.toFloat(), (b.y + b.height).toFloat())
        val bottomRight = world.transform((b.x + b.width).toFloat(), (b.y + b.height).toFloat())
        val minX = minOf(topLeft.first, topRight.first, bottomLeft.first, bottomRight.first)
        val maxX = maxOf(topLeft.first, topRight.first, bottomLeft.first, bottomRight.first)
        val minY = minOf(topLeft.second, topRight.second, bottomLeft.second, bottomRight.second)
        val maxY = maxOf(topLeft.second, topRight.second, bottomLeft.second, bottomRight.second)
        val x =
            kotlin.math
                .floor(minX.toDouble())
                .toInt()
        val y =
            kotlin.math
                .floor(minY.toDouble())
                .toInt()
        val w =
            kotlin.math
                .ceil((maxX - minX).toDouble())
                .toInt()
                .coerceAtLeast(0)
        val h =
            kotlin.math
                .ceil((maxY - minY).toDouble())
                .toInt()
                .coerceAtLeast(0)
        return Rect(x, y, w, h)
    }

    private fun contains(clip: GuiClipRect?, x: Int, y: Int): Boolean {
        clip ?: return false
        return x >= clip.x && y >= clip.y && x < clip.x + clip.width && y < clip.y + clip.height
    }

    private fun containsRect(clip: GuiClipRect?, rect: GuiClipRect): Boolean {
        clip ?: return false
        val right = rect.x + rect.width
        val bottom = rect.y + rect.height
        return rect.x >= clip.x &&
            rect.y >= clip.y &&
            right <= clip.x + clip.width &&
            bottom <= clip.y + clip.height
    }

    private fun floorToInt(value: Float): Int =
        kotlin.math
            .floor(value.toDouble())
            .toInt()

    private fun styleDeclarations(vararg entries: Pair<StyleProperty, String>): StyleDeclarations =
        StyleDeclarations().apply {
            entries.forEach { (property, literal) ->
                set(property, StyleExpression.Literal(literal))
            }
        }

    private data class ControlFixture(
        val tree: DomTree,
        val control: DOMNode,
    )

    private data class CommandObservations(
        val pushClips: List<ObservedClipPush>,
        val texts: List<ObservedText>,
        val rects: List<ObservedRect>,
    )

    private data class ObservedClipPush(
        val raw: GuiClipRect,
        val transformed: GuiClipRect,
    )

    private data class ObservedText(
        val text: String,
        val x: Int,
        val y: Int,
        val activeClip: GuiClipRect?,
    )

    private data class ObservedRect(
        val transformed: GuiClipRect,
        val rawWidth: Int,
        val rawHeight: Int,
        val activeClip: GuiClipRect?,
    )
}
