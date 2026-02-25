package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.*
import org.dreamfinity.dsgl.core.animation.*
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.mc1710.demo.ShowcaseWindow
import org.dreamfinity.dsgl.mc1710.demo.support.DEMO_MUTED

private val easingOptions: List<Pair<String, Easing>> = listOf(
    "linear" to Easings.LINEAR,
    "ease" to Easings.EASE,
    "ease-in" to Easings.EASE_IN,
    "ease-out" to Easings.EASE_OUT,
    "ease-in-out" to Easings.EASE_IN_OUT
)

private val directionOptions: List<AnimationDirection> = listOf(
    AnimationDirection.Normal,
    AnimationDirection.Reverse,
    AnimationDirection.Alternate,
    AnimationDirection.AlternateReverse
)

private val fillModeOptions: List<AnimationFillMode> = listOf(
    AnimationFillMode.None,
    AnimationFillMode.Forwards,
    AnimationFillMode.Backwards,
    AnimationFillMode.Both
)

fun UiScope.renderAnimationsSection(window: ShowcaseWindow, contentWidth: Int, contentHeight: Int) {
    val duration = window.animationsDurationMs.toInt().coerceIn(200, 6000)
    val customBezier = cubicBezier(
        window.animationsBezierX1.toFloat() / 100f,
        window.animationsBezierY1.toFloat() / 100f,
        window.animationsBezierX2.toFloat() / 100f,
        window.animationsBezierY2.toFloat() / 100f
    )
    val dynamicEasingOptions = easingOptions + listOf(
        "custom(${window.animationsBezierX1},${window.animationsBezierY1},${window.animationsBezierX2},${window.animationsBezierY2})" to customBezier
    )
    val easingIndex = window.animationsEasingIndex.coerceIn(0, dynamicEasingOptions.lastIndex)
    val directionIndex = window.animationsDirectionIndex.coerceIn(0, directionOptions.lastIndex)
    val fillIndex = window.animationsFillModeIndex.coerceIn(0, fillModeOptions.lastIndex)
    val easing = dynamicEasingOptions[easingIndex].second
    val easingName = dynamicEasingOptions[easingIndex].first
    val direction = directionOptions[directionIndex]
    val fillMode = fillModeOptions[fillIndex]
    val playState = if (window.animationsPaused) AnimationPlayState.Paused else AnimationPlayState.Running
    val iterations = if (window.animationsUseInfinite) IterationCount.Infinite else IterationCount.Count(3)

    div(
        ComponentProps(
            key = "section.animations",
            width = contentWidth,
            height = contentHeight,
            gap = 4
        ).asFlexColumn()
    ) {
        text(TextProps("Transforms + Transitions + Keyframes"))
        text(
            TextProps("Transforms are layout-neutral; hit testing follows transformed geometry.").apply {
                color = DEMO_MUTED
            }
        )

        div(ComponentProps(gap = 3).asFlexRow()) {
            button(
                ButtonProps(if (window.animationsToggle) "Retarget: ON" else "Retarget: OFF").apply {
                    onMouseClick = {
                        window.animationsToggle = !window.animationsToggle
                        window.appendInfo("Animation retarget toggle=${window.animationsToggle}")
                    }
                }
            )
            button(
                ButtonProps(if (window.animationsPaused) "Play" else "Pause").apply {
                    onMouseClick = {
                        window.animationsPaused = !window.animationsPaused
                    }
                }
            )
            button(
                ButtonProps(if (window.animationsUseInfinite) "Iterations: inf" else "Iterations: 3").apply {
                    onMouseClick = {
                        window.animationsUseInfinite = !window.animationsUseInfinite
                    }
                }
            )
            button(
                ButtonProps("Easing: $easingName").apply {
                    onMouseClick = {
                        window.animationsEasingIndex = (window.animationsEasingIndex + 1) % dynamicEasingOptions.size
                    }
                }
            )
        }

        div(ComponentProps(gap = 3).asFlexRow()) {
            button(
                ButtonProps("Dir: ${direction.name.lowercase()}").apply {
                    onMouseClick = {
                        window.animationsDirectionIndex = (window.animationsDirectionIndex + 1) % directionOptions.size
                    }
                }
            )
            button(
                ButtonProps("Fill: ${fillMode.name.lowercase()}").apply {
                    onMouseClick = {
                        window.animationsFillModeIndex = (window.animationsFillModeIndex + 1) % fillModeOptions.size
                    }
                }
            )
            input(
                InputProps(
                    org.dreamfinity.dsgl.core.dom.elements.InputType.Range(
                        value = duration.toLong(),
                        min = 200,
                        max = 6000,
                        step = 50
                    )
                ).apply {
                    key = "animations.duration.slider"
                    width = 120
                    onInput = { event ->
                        val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: duration.toLong()
                        window.animationsDurationMs = next.coerceIn(200, 6000)
                    }
                }
            )
            text(TextProps { "duration=${window.animationsDurationMs}ms" }.apply { color = DEMO_MUTED })
        }

        div(ComponentProps(gap = 3).asFlexRow()) {
            text(TextProps("Bezier x1=${window.animationsBezierX1}").apply { color = DEMO_MUTED })
            input(
                InputProps(
                    org.dreamfinity.dsgl.core.dom.elements.InputType.Range(
                        value = window.animationsBezierX1,
                        min = 0,
                        max = 100,
                        step = 1
                    )
                ).apply {
                    key = "animations.bezier.x1"
                    width = 72
                    onInput = { event ->
                        val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: window.animationsBezierX1
                        window.animationsBezierX1 = next.coerceIn(0, 100)
                    }
                }
            )
            text(TextProps("y1=${window.animationsBezierY1}").apply { color = DEMO_MUTED })
            input(
                InputProps(
                    org.dreamfinity.dsgl.core.dom.elements.InputType.Range(
                        value = window.animationsBezierY1,
                        min = 0,
                        max = 100,
                        step = 1
                    )
                ).apply {
                    key = "animations.bezier.y1"
                    width = 72
                    onInput = { event ->
                        val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: window.animationsBezierY1
                        window.animationsBezierY1 = next.coerceIn(0, 100)
                    }
                }
            )
        }

        div(ComponentProps(gap = 3).asFlexRow()) {
            text(TextProps("Bezier x2=${window.animationsBezierX2}").apply { color = DEMO_MUTED })
            input(
                InputProps(
                    org.dreamfinity.dsgl.core.dom.elements.InputType.Range(
                        value = window.animationsBezierX2,
                        min = 0,
                        max = 100,
                        step = 1
                    )
                ).apply {
                    key = "animations.bezier.x2"
                    width = 72
                    onInput = { event ->
                        val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: window.animationsBezierX2
                        window.animationsBezierX2 = next.coerceIn(0, 100)
                    }
                }
            )
            text(TextProps("y2=${window.animationsBezierY2}").apply { color = DEMO_MUTED })
            input(
                InputProps(
                    org.dreamfinity.dsgl.core.dom.elements.InputType.Range(
                        value = window.animationsBezierY2,
                        min = 0,
                        max = 100,
                        step = 1
                    )
                ).apply {
                    key = "animations.bezier.y2"
                    width = 72
                    onInput = { event ->
                        val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: window.animationsBezierY2
                        window.animationsBezierY2 = next.coerceIn(0, 100)
                    }
                }
            )
        }

        div(
            ComponentProps(
                key = "animations.cards",
                width = (contentWidth - 8).coerceAtLeast(120),
                padding = 4,
                gap = 6,
                backgroundColor = 0xFF222B37.toInt()
            ).asFlexRow().apply {
                style = {
                    display = Display.Flex
                    alignItems = org.dreamfinity.dsgl.core.style.AlignItems.Center
                    justifyContent = org.dreamfinity.dsgl.core.style.JustifyContent.Start
                    border(1, 0xFF3F4D5E.toInt())
                }
            }
        ) {
            div(
                ComponentProps(
                    key = "animations.transition.card",
                    width = 120,
                    height = 52,
                    backgroundColor = 0xFF2E3C4F.toInt()
                ).apply {
                    onMouseEnter = { window.animationsHover = true }
                    onMouseLeave = { window.animationsHover = false }
                    style = {
                        transition {
                            property(StyleAnimProps.transform, 220, easing = Easings.EASE_IN_OUT)
                            property(StyleAnimProps.opacity, 200, easing = Easings.EASE_OUT)
                            property(StyleAnimProps.color, 260, easing = Easings.EASE_IN)
                        }
                        val tx = if (window.animationsToggle) 20f else 0f
                        val lift = if (window.animationsHover) -8f else 0f
                        val scale = if (window.animationsToggle) 1.08f else 1f
                        transform {
                            translate(tx, lift)
                            scale(scale)
                            rotate(if (window.animationsToggle) 8f else 0f)
                        }
                        transformOrigin(0.5f, 0.5f)
                        opacity = if (window.animationsToggle) 0.65f else 1f
                        foregroundColor(if (window.animationsToggle) 0xFFA4F0C2.toInt() else 0xFFEAF3FF.toInt())
                        border(1, 0xFF56677A.toInt())
                        padding(4)
                    }
                }.asFlexColumn()
            ) {
                text(TextProps("Transition card"))
                text(TextProps("hover + toggle").apply { color = DEMO_MUTED })
            }

            div(
                ComponentProps(
                    key = "animations.keyframes.card",
                    width = 120,
                    height = 52,
                    backgroundColor = 0xFF31313C.toInt()
                ).apply {
                    style = {
                        animation {
                            animation(
                                name = "showcase.spinFade",
                                durationMs = duration,
                                easing = easing,
                                iterationCount = iterations,
                                direction = direction,
                                fillMode = fillMode,
                                playState = playState
                            )
                        }
                        transformOrigin(0.5f, 0.5f)
                        border(1, 0xFF5F5F72.toInt())
                        padding(4)
                    }
                }.asFlexColumn()
            ) {
                text(TextProps("Keyframes card"))
                text(TextProps("spin + fade + color").apply { color = DEMO_MUTED })
            }

            div(
                ComponentProps(
                    key = "animations.nested.parent",
                    width = 110,
                    height = 52,
                    backgroundColor = 0xFF2A3442.toInt(),
                    padding = 4
                ).apply {
                    style = {
                        transform {
                            rotate(if (window.animationsToggle) 12f else 0f)
                        }
                        transformOrigin(0.5f, 0.5f)
                        transition {
                            property(StyleAnimProps.transform, 260, easing = Easings.EASE_IN_OUT)
                        }
                        border(1, 0xFF4C6077.toInt())
                    }
                }
            ) {
                div(
                    ComponentProps(
                        key = "animations.nested.child",
                        width = 64,
                        height = 22,
                        backgroundColor = 0xFF3F5571.toInt()
                    ).apply {
                        style = {
                            transform {
                                translate(if (window.animationsToggle) 10f else 0f, if (window.animationsToggle) 4f else 0f)
                            }
                            transition {
                                property(StyleAnimProps.transform, 220, easing = Easings.EASE_OUT)
                            }
                            border(1, 0xFF7593B8.toInt())
                        }
                    }
                ) {
                    text(TextProps("Nested").apply { color = 0xFFEAF3FF.toInt() })
                }
            }
        }

        text(
            TextProps {
                val debug = StyleAnimationEngine.debugSnapshotForKey("animations.keyframes.card")
                val transitionDebug = debug?.activeTransitions?.joinToString(", ").orEmpty().ifBlank { "-" }
                val keyframesDebug = debug?.activeKeyframes?.joinToString(", ").orEmpty().ifBlank { "-" }
                val transformDebug = debug?.effectiveTransform?.let {
                    "tx=${it.translateX},ty=${it.translateY},sx=${it.scaleX},sy=${it.scaleY},rot=${it.rotateDeg}"
                } ?: "-"
                "debug: hover=${window.animationsHover} toggle=${window.animationsToggle} " +
                    "easing=$easingName direction=${direction.name} fill=${fillMode.name} " +
                    "play=${playState.name} iterations=${if (window.animationsUseInfinite) "infinite" else "3"} " +
                    "activeTransitions=$transitionDebug activeKeyframes=$keyframesDebug " +
                    "effectiveOpacity=${debug?.effectiveOpacity ?: 1f} transform={$transformDebug}"
            }.apply {
                color = DEMO_MUTED
            }
        )
    }
}
