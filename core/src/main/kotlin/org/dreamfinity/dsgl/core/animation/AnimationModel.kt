package org.dreamfinity.dsgl.core.animation

import org.dreamfinity.dsgl.core.style.UiTransform

enum class AnimatedStyleProperty {
    Transform,
    Opacity,
    Color,
}

object StyleAnimProps {
    val transform: AnimatedStyleProperty = AnimatedStyleProperty.Transform
    val opacity: AnimatedStyleProperty = AnimatedStyleProperty.Opacity
    val color: AnimatedStyleProperty = AnimatedStyleProperty.Color
}

data class StylePropertyKey<T : Any>(
    val id: String,
    val property: AnimatedStyleProperty,
    val animatable: Animatable<T>,
)

interface Animatable<T : Any> {
    fun interpolate(from: T, to: T, t: Float): T
}

object FloatAnimatable : Animatable<Float> {
    override fun interpolate(from: Float, to: Float, t: Float): Float = from + (to - from) * t
}

object ColorAnimatable : Animatable<Int> {
    override fun interpolate(from: Int, to: Int, t: Float): Int {
        val a0 = (from ushr 24) and 0xFF
        val r0 = (from ushr 16) and 0xFF
        val g0 = (from ushr 8) and 0xFF
        val b0 = from and 0xFF

        val a1 = (to ushr 24) and 0xFF
        val r1 = (to ushr 16) and 0xFF
        val g1 = (to ushr 8) and 0xFF
        val b1 = to and 0xFF

        val a = (a0 + ((a1 - a0) * t)).toInt().coerceIn(0, 255)
        val r = (r0 + ((r1 - r0) * t)).toInt().coerceIn(0, 255)
        val g = (g0 + ((g1 - g0) * t)).toInt().coerceIn(0, 255)
        val b = (b0 + ((b1 - b0) * t)).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}

object TransformAnimatable : Animatable<UiTransform> {
    override fun interpolate(from: UiTransform, to: UiTransform, t: Float): UiTransform {
        val rotateDelta = shortestAngleDelta(from.rotateDeg, to.rotateDeg)
        return UiTransform(
            translateX = from.translateX + (to.translateX - from.translateX) * t,
            translateY = from.translateY + (to.translateY - from.translateY) * t,
            scaleX = from.scaleX + (to.scaleX - from.scaleX) * t,
            scaleY = from.scaleY + (to.scaleY - from.scaleY) * t,
            rotateDeg = from.rotateDeg + rotateDelta * t,
        )
    }

    private fun shortestAngleDelta(fromDeg: Float, toDeg: Float): Float {
        var delta = (toDeg - fromDeg) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }
}

val TransformKey: StylePropertyKey<UiTransform> =
    StylePropertyKey("transform", AnimatedStyleProperty.Transform, TransformAnimatable)
val OpacityKey: StylePropertyKey<Float> =
    StylePropertyKey("opacity", AnimatedStyleProperty.Opacity, FloatAnimatable)
val ColorKey: StylePropertyKey<Int> =
    StylePropertyKey("color", AnimatedStyleProperty.Color, ColorAnimatable)

data class TransitionPropertySpec(
    val property: AnimatedStyleProperty,
    val durationMs: Int,
    val delayMs: Int = 0,
    val easing: Easing = Easings.EASE,
)

data class TransitionSpec(
    val properties: List<TransitionPropertySpec>,
) {
    companion object {
        val NONE: TransitionSpec = TransitionSpec(emptyList())
    }

    fun forProperty(property: AnimatedStyleProperty): TransitionPropertySpec? =
        properties.lastOrNull {
            it.property == property
        }
}

enum class AnimationDirection {
    Normal,
    Reverse,
    Alternate,
    AlternateReverse,
}

enum class AnimationFillMode {
    None,
    Forwards,
    Backwards,
    Both,
}

enum class AnimationPlayState {
    Running,
    Paused,
}

sealed class IterationCount {
    data class Count(
        val value: Int,
    ) : IterationCount()

    data object Infinite : IterationCount()

    fun isInfinite(): Boolean = this is Infinite

    fun finiteValueOrNull(): Int? = (this as? Count)?.value
}

data class AnimationSpec(
    val name: String,
    val durationMs: Int,
    val delayMs: Int = 0,
    val easing: Easing = Easings.LINEAR,
    val iterationCount: IterationCount = IterationCount.Count(1),
    val direction: AnimationDirection = AnimationDirection.Normal,
    val fillMode: AnimationFillMode = AnimationFillMode.None,
    val playState: AnimationPlayState = AnimationPlayState.Running,
)

data class KeyframeValue(
    val transform: UiTransform? = null,
    val opacity: Float? = null,
    val color: Int? = null,
)

data class Keyframe(
    val fraction: Float,
    val value: KeyframeValue,
)

data class KeyframesDefinition(
    val name: String,
    val frames: List<Keyframe>,
) {
    init {
        require(frames.isNotEmpty()) { "Keyframes '$name' must contain at least one frame." }
    }

    val transformFrames: List<Keyframe> = frames.filter { it.value.transform != null }
    val opacityFrames: List<Keyframe> = frames.filter { it.value.opacity != null }
    val colorFrames: List<Keyframe> = frames.filter { it.value.color != null }
}
