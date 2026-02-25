package org.dreamfinity.dsgl.core.animation

import org.dreamfinity.dsgl.core.style.UiTransform

val Int.ms: Int
    get() = this

class TransitionBuilder internal constructor() {
    private val specs: MutableList<TransitionPropertySpec> = ArrayList()

    fun property(
        property: AnimatedStyleProperty,
        durationMs: Int,
        delayMs: Int = 0,
        easing: Easing = Easings.EASE
    ) {
        specs += TransitionPropertySpec(
            property = property,
            durationMs = durationMs.coerceAtLeast(0),
            delayMs = delayMs.coerceAtLeast(0),
            easing = easing
        )
    }

    internal fun build(): TransitionSpec = TransitionSpec(specs.toList())
}

class AnimationListBuilder internal constructor() {
    private val specs: MutableList<AnimationSpec> = ArrayList()

    fun animation(
        name: String,
        durationMs: Int,
        delayMs: Int = 0,
        easing: Easing = Easings.LINEAR,
        iterationCount: IterationCount = IterationCount.Count(1),
        direction: AnimationDirection = AnimationDirection.Normal,
        fillMode: AnimationFillMode = AnimationFillMode.None,
        playState: AnimationPlayState = AnimationPlayState.Running
    ) {
        specs += AnimationSpec(
            name = name,
            durationMs = durationMs.coerceAtLeast(0),
            delayMs = delayMs.coerceAtLeast(0),
            easing = easing,
            iterationCount = iterationCount,
            direction = direction,
            fillMode = fillMode,
            playState = playState
        )
    }

    internal fun build(): List<AnimationSpec> = specs.toList()
}

fun keyframes(
    name: String,
    block: KeyframesBuilder.() -> Unit
) {
    val definition = KeyframesBuilder(name).apply(block).build()
    KeyframesRegistry.register(definition)
}

fun transform(block: UiTransformBuilder.() -> Unit): UiTransform {
    return UiTransformBuilder().apply(block).build()
}

