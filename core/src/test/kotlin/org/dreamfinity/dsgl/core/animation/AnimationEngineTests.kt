package org.dreamfinity.dsgl.core.animation

import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.TextNode
import org.dreamfinity.dsgl.core.dom.elements.TextSource
import org.dreamfinity.dsgl.core.style.ComputedStyle
import org.dreamfinity.dsgl.core.style.ComputedStyleDefaults
import org.dreamfinity.dsgl.core.style.UiTransform
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnimationEngineTests {
    @AfterTest
    fun cleanup() {
        StyleAnimationEngine.clear()
        KeyframesRegistry.clear()
    }

    @Test
    fun `transform interpolation uses shortest rotation path`() {
        val from = UiTransform(rotateDeg = 350f)
        val to = UiTransform(rotateDeg = 10f)
        val mid = TransformAnimatable.interpolate(from, to, 0.5f)
        assertTrue(mid.rotateDeg in 359f..361f)
    }

    @Test
    fun `transition retargeting starts from current interpolated value`() {
        val root = ContainerNode(key = "root")
        val node = TextNode(TextSource.Static("x"), key = "animated").applyParent(root)
        node.transitionSpec =
            TransitionSpec(
                listOf(
                    TransitionPropertySpec(
                        property = AnimatedStyleProperty.Opacity,
                        durationMs = 1000,
                        easing = Easings.LINEAR,
                    ),
                ),
            )

        node.applyComputedStyle(style(opacity = 0f))
        node.applyComputedStyle(style(opacity = 1f))
        StyleAnimationEngine.tickAndApply(root, 0.5, null)
        val mid = node.effectiveOpacity()
        assertTrue(mid in 0.49f..0.51f, "expected ~0.5, actual=$mid")

        node.applyComputedStyle(style(opacity = 0f))
        StyleAnimationEngine.tickAndApply(root, 0.25, null)
        val retargeted = node.effectiveOpacity()
        assertTrue(retargeted in 0.36f..0.39f, "expected ~0.375, actual=$retargeted")
    }

    @Test
    fun `transition delay is respected`() {
        val root = ContainerNode(key = "root")
        val node = TextNode(TextSource.Static("x"), key = "animated").applyParent(root)
        node.transitionSpec =
            TransitionSpec(
                listOf(
                    TransitionPropertySpec(
                        property = AnimatedStyleProperty.Opacity,
                        durationMs = 500,
                        delayMs = 300,
                        easing = Easings.LINEAR,
                    ),
                ),
            )

        node.applyComputedStyle(style(opacity = 0f))
        node.applyComputedStyle(style(opacity = 1f))
        StyleAnimationEngine.tickAndApply(root, 0.2, null)
        assertEquals(0f, node.effectiveOpacity())
        StyleAnimationEngine.tickAndApply(root, 0.2, null)
        assertTrue(node.effectiveOpacity() > 0f)
    }

    @Test
    fun `keyframes support direction and fill behavior`() {
        keyframes("pulse") {
            at(0f) { opacity = 0f }
            at(100f) { opacity = 1f }
        }
        val root = ContainerNode(key = "root")
        val node = TextNode(TextSource.Static("x"), key = "animated").applyParent(root)
        node.animationSpecs =
            listOf(
                AnimationSpec(
                    name = "pulse",
                    durationMs = 1000,
                    delayMs = 200,
                    easing = Easings.LINEAR,
                    direction = AnimationDirection.Reverse,
                    fillMode = AnimationFillMode.Both,
                ),
            )

        node.applyComputedStyle(style(opacity = 1f))
        StyleAnimationEngine.tickAndApply(root, 0.1, null)
        assertEquals(1f, node.effectiveOpacity())
        StyleAnimationEngine.tickAndApply(root, 0.2, null)
        val running = node.effectiveOpacity()
        assertTrue(running in 0.6f..0.9f, "expected reverse running in [0.6..0.9], actual=$running")

        StyleAnimationEngine.tickAndApply(root, 1.0, null)
        assertEquals(0f, node.effectiveOpacity(), "reverse fill should end at first keyframe")
    }

    @Test
    fun `keyframes with missing property do not override base`() {
        keyframes("fadeOnly") {
            at(0f) { opacity = 0f }
            at(100f) { opacity = 1f }
        }
        val root = ContainerNode(key = "root")
        val node = TextNode(TextSource.Static("x"), key = "animated").applyParent(root)
        node.animationSpecs =
            listOf(
                AnimationSpec(
                    name = "fadeOnly",
                    durationMs = 1000,
                    easing = Easings.LINEAR,
                ),
            )
        node.applyComputedStyle(style(opacity = 1f, color = 0xFF123456.toInt()))
        StyleAnimationEngine.tickAndApply(root, 0.5, null)

        assertNotNull(node.effectiveOpacity())
        assertEquals(0xFF123456.toInt(), node.color)
    }

    @Test
    fun `alternate direction and forwards fill resolve final edge correctly`() {
        keyframes("altDir") {
            at(0f) { opacity = 0f }
            at(100f) { opacity = 1f }
        }
        val root = ContainerNode(key = "root")
        val node = TextNode(TextSource.Static("x"), key = "animated").applyParent(root)
        node.animationSpecs =
            listOf(
                AnimationSpec(
                    name = "altDir",
                    durationMs = 1000,
                    easing = Easings.LINEAR,
                    iterationCount = IterationCount.Count(2),
                    direction = AnimationDirection.Alternate,
                    fillMode = AnimationFillMode.Forwards,
                ),
            )
        node.applyComputedStyle(style(opacity = 1f))
        StyleAnimationEngine.tickAndApply(root, 1.25, null)
        assertTrue(node.effectiveOpacity() in 0.70f..0.80f)
        StyleAnimationEngine.tickAndApply(root, 1.0, null)
        assertEquals(0f, node.effectiveOpacity())
    }

    @Test
    fun `backwards fill applies first frame during delay`() {
        keyframes("delayBackwards") {
            at(0f) { opacity = 0f }
            at(100f) { opacity = 1f }
        }
        val root = ContainerNode(key = "root")
        val node = TextNode(TextSource.Static("x"), key = "animated").applyParent(root)
        node.animationSpecs =
            listOf(
                AnimationSpec(
                    name = "delayBackwards",
                    durationMs = 1000,
                    delayMs = 400,
                    easing = Easings.LINEAR,
                    fillMode = AnimationFillMode.Backwards,
                ),
            )
        node.applyComputedStyle(style(opacity = 1f))
        StyleAnimationEngine.tickAndApply(root, 0.2, null)
        assertEquals(0f, node.effectiveOpacity())
    }

    @Test
    fun `transition animates transform opacity and color together`() {
        val root = ContainerNode(key = "root")
        val node = TextNode(TextSource.Static("x"), key = "animated").applyParent(root)
        node.transitionSpec =
            TransitionSpec(
                listOf(
                    TransitionPropertySpec(AnimatedStyleProperty.Transform, 1000, easing = Easings.LINEAR),
                    TransitionPropertySpec(AnimatedStyleProperty.Opacity, 1000, easing = Easings.LINEAR),
                    TransitionPropertySpec(AnimatedStyleProperty.Color, 1000, easing = Easings.LINEAR),
                ),
            )
        node.applyComputedStyle(style(opacity = 1f, color = 0xFFFFFFFF.toInt(), transform = UiTransform.IDENTITY))
        node.applyComputedStyle(
            style(
                opacity = 0.5f,
                color = 0xFF000000.toInt(),
                transform = UiTransform(translateX = 10f, translateY = 0f, scaleX = 1f, scaleY = 1f, rotateDeg = 0f),
            ),
        )
        StyleAnimationEngine.tickAndApply(root, 0.5, null)

        assertTrue(node.effectiveOpacity() in 0.74f..0.76f)
        assertTrue(node.effectiveTransform().translateX in 4.9f..5.1f)
        val alpha = (node.color ushr 24) and 0xFF
        val red = (node.color ushr 16) and 0xFF
        assertEquals(0xFF, alpha)
        assertTrue(red in 120..136)
    }

    private fun style(transform: UiTransform = UiTransform.IDENTITY, opacity: Float = 1f, color: Int = 0xFFFFFFFF.toInt()): ComputedStyle =
        ComputedStyleDefaults(
            foregroundColor = color,
            opacity = opacity,
            transform = transform,
        ).toComputedStyle()
}
