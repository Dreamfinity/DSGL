package org.dreamfinity.dsgl.mc1710.demo.sections

import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.animation.*
import org.dreamfinity.dsgl.core.dom.elements.InputType
import org.dreamfinity.dsgl.core.style.AlignItems
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.FlexDirection
import org.dreamfinity.dsgl.core.style.JustifyContent
import org.dreamfinity.dsgl.core.useState
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

fun UiScope.animationsSection(onInfo: (String) -> Unit) {
    var animationsToggle by useState(false)
    var animationsHover by useState(false)
    var animationsPaused by useState(false)
    var animationsDurationMs by useState(1400L)
    var animationsUseInfinite by useState(true)
    var animationsEasingIndex by useState(0)
    var animationsDirectionIndex by useState(0)
    var animationsFillModeIndex by useState(0)
    var animationsBezierX1 by useState(17L)
    var animationsBezierY1 by useState(67L)
    var animationsBezierX2 by useState(83L)
    var animationsBezierY2 by useState(67L)

    val duration = animationsDurationMs.toInt().coerceIn(200, 6000)
    val customBezier = cubicBezier(
        animationsBezierX1.toFloat() / 100f,
        animationsBezierY1.toFloat() / 100f,
        animationsBezierX2.toFloat() / 100f,
        animationsBezierY2.toFloat() / 100f
    )
    val dynamicEasingOptions = easingOptions + listOf(
        "custom($animationsBezierX1,$animationsBezierY1,$animationsBezierX2,$animationsBezierY2)" to customBezier
    )
    val easingIndex = animationsEasingIndex.coerceIn(0, dynamicEasingOptions.lastIndex)
    val directionIndex = animationsDirectionIndex.coerceIn(0, directionOptions.lastIndex)
    val fillIndex = animationsFillModeIndex.coerceIn(0, fillModeOptions.lastIndex)
    val easing = dynamicEasingOptions[easingIndex].second
    val easingName = dynamicEasingOptions[easingIndex].first
    val direction = directionOptions[directionIndex]
    val fillMode = fillModeOptions[fillIndex]
    val playState = if (animationsPaused) AnimationPlayState.Paused else AnimationPlayState.Running
    val iterations = if (animationsUseInfinite) IterationCount.Infinite else IterationCount.Count(3)

    div({
        key = "section.animations"
        style = {
            gap = 4.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text("Transforms + Transitions + Keyframes")
        text(
            "Transforms are layout-neutral; hit testing follows transformed geometry.",
            { style = { color = DEMO_MUTED } }
        )

        div({
            style = {
                gap = 3.px
                display = Display.Flex
                flexDirection = FlexDirection.Row
            }
        }) {
            button(
                if (animationsToggle) "Retarget: ON" else "Retarget: OFF",
                {
                    onMouseClick = {
                        animationsToggle = !animationsToggle
                        onInfo("Animation retarget toggle=$animationsToggle")
                    }
                }
            )
            button(if (animationsPaused) "Play" else "Pause", {
                onMouseClick = {
                    animationsPaused = !animationsPaused
                }
            })
            button(if (animationsUseInfinite) "Iterations: inf" else "Iterations: 3", {
                onMouseClick = {
                    animationsUseInfinite = !animationsUseInfinite
                }
            })
            button("Easing: $easingName", {
                onMouseClick = {
                    animationsEasingIndex = (animationsEasingIndex + 1) % dynamicEasingOptions.size
                }
            })
            button("Dir: ${direction.name.lowercase()}", {
                onMouseClick = {
                    animationsDirectionIndex = (animationsDirectionIndex + 1) % directionOptions.size
                }
            })
            button("Fill: ${fillMode.name.lowercase()}", {
                onMouseClick = {
                    animationsFillModeIndex = (animationsFillModeIndex + 1) % fillModeOptions.size
                }
            })
        }

        div({
            style = {
                gap = 3.px
                display = Display.Flex
                flexDirection = FlexDirection.Column
            }
        }) {
            text("duration=$animationsDurationMs ms", { style = { color = DEMO_MUTED } })
            input(
                InputType.Range(
                    value = duration.toLong(),
                    min = 200,
                    max = 6000,
                    step = 50
                ),
                {
                    key = "animations.duration.slider"
                    style = { width = 100.percent }
                    onInput = { event ->
                        val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: duration.toLong()
                        animationsDurationMs = next.coerceIn(200, 6000)
                    }
                }
            )
        }

        bezierSliderRow("Bezier x1=$animationsBezierX1", "animations.bezier.x1", animationsBezierX1) { next ->
            animationsBezierX1 = next
        }
        bezierSliderRow("Bezier y1=$animationsBezierY1", "animations.bezier.y1", animationsBezierY1) { next ->
            animationsBezierY1 = next
        }
        bezierSliderRow("Bezier x2=$animationsBezierX2", "animations.bezier.x2", animationsBezierX2) { next ->
            animationsBezierX2 = next
        }
        bezierSliderRow("Bezier y2=$animationsBezierY2", "animations.bezier.y2", animationsBezierY2) { next ->
            animationsBezierY2 = next
        }

        div({
            key = "animations.cards"
            style = {
                width = 100.percent
                padding = 4.px
                gap = 6.px
                backgroundColor = 0xFF222B37.toInt()
                display = Display.Flex
                flexDirection = FlexDirection.Row
                alignItems = AlignItems.Center
                justifyContent = JustifyContent.Start
                border(1.px, 0xFF3F4D5E.toInt())
            }
        }) {
            div({
                key = "animations.transition.card"
                onMouseEnter = { animationsHover = true }
                onMouseLeave = { animationsHover = false }
                style = {
                    width = 120.px
                    height = 52.px
                    backgroundColor = 0xFF2E3C4F.toInt()
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
                    transition {
                        property(StyleAnimProps.transform, 220, easing = Easings.EASE_IN_OUT)
                        property(StyleAnimProps.opacity, 200, easing = Easings.EASE_OUT)
                        property(StyleAnimProps.color, 260, easing = Easings.EASE_IN)
                    }
                    val tx = if (animationsToggle) 20f else 0f
                    val lift = if (animationsHover) -8f else 0f
                    val scale = if (animationsToggle) 1.08f else 1f
                    transform {
                        translate(tx, lift)
                        scale(scale)
                        rotate(if (animationsToggle) 8f else 0f)
                    }
                    transformOrigin(0.5f, 0.5f)
                    opacity = if (animationsToggle) 0.65f else 1f
                    foregroundColor(if (animationsToggle) 0xFFA4F0C2.toInt() else 0xFFEAF3FF.toInt())
                    border(1.px, 0xFF56677A.toInt())
                    padding(4.px)
                }
            }) {
                text("Transition card")
                text("hover + toggle", { style = { color = DEMO_MUTED } })
            }

            div({
                key = "animations.keyframes.card"
                style = {
                    width = 120.px
                    height = 52.px
                    backgroundColor = 0xFF31313C.toInt()
                    display = Display.Flex
                    flexDirection = FlexDirection.Column
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
                    border(1.px, 0xFF5F5F72.toInt())
                    padding(4.px)
                }
            }) {
                text("Keyframes card")
                text("spin + fade + color", { style = { color = DEMO_MUTED } })
            }

            div({
                key = "animations.nested.parent"
                style = {
                    width = 110.px
                    height = 52.px
                    backgroundColor = 0xFF2A3442.toInt()
                    padding = 4.px
                    transform {
                        rotate(if (animationsToggle) 12f else 0f)
                    }
                    transformOrigin(0.5f, 0.5f)
                    transition {
                        property(StyleAnimProps.transform, 260, easing = Easings.EASE_IN_OUT)
                    }
                    border(1.px, 0xFF4C6077.toInt())
                }
            }) {
                div({
                    key = "animations.nested.child"
                    style = {
                        width = 64.px
                        height = 22.px
                        backgroundColor = 0xFF3F5571.toInt()
                        transform {
                            translate(
                                if (animationsToggle) 10f else 0f,
                                if (animationsToggle) 4f else 0f
                            )
                        }
                        transition {
                            property(StyleAnimProps.transform, 220, easing = Easings.EASE_OUT)
                        }
                        border(1.px, 0xFF7593B8.toInt())
                    }
                }) {
                    text("Nested", { style = { color = 0xFFEAF3FF.toInt() } })
                }
            }
        }

        text({
            val debug = StyleAnimationEngine.debugSnapshotForKey("animations.keyframes.card")
            val transitionDebug = debug?.activeTransitions?.joinToString(", ").orEmpty().ifBlank { "-" }
            val keyframesDebug = debug?.activeKeyframes?.joinToString(", ").orEmpty().ifBlank { "-" }
            val transformDebug = debug?.effectiveTransform?.let {
                "tx=${it.translateX},ty=${it.translateY},sx=${it.scaleX},sy=${it.scaleY},rot=${it.rotateDeg}"
            } ?: "-"
            "debug: hover=$animationsHover toggle=$animationsToggle " +
                    "easing=$easingName direction=${direction.name} fill=${fillMode.name} " +
                    "play=${playState.name} iterations=${if (animationsUseInfinite) "infinite" else "3"} " +
                    "activeTransitions=$transitionDebug activeKeyframes=$keyframesDebug " +
                    "effectiveOpacity=${debug?.effectiveOpacity ?: 1f} transform={$transformDebug}"

            style = { color = DEMO_MUTED }
        })
    }
}

private fun UiScope.bezierSliderRow(
    label: String,
    key: String,
    value: Long,
    onChange: (Long) -> Unit
) {
    div({
        style = {
            gap = 3.px
            display = Display.Flex
            flexDirection = FlexDirection.Column
        }
    }) {
        text(label, { style = { color = DEMO_MUTED } })
        input(
            InputType.Range(
                value = value,
                min = 0,
                max = 100,
                step = 1
            ),
            {
                this.key = key
                style = { width = 100.percent }
                onInput = { event ->
                    val next = (event.parsedValue as? Long) ?: event.value.toLongOrNull() ?: value
                    onChange(next.coerceIn(0, 100))
                }
            }
        )
    }
}
