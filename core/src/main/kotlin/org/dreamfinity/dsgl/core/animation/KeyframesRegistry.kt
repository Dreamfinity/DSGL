package org.dreamfinity.dsgl.core.animation

import org.dreamfinity.dsgl.core.style.UiTransform
import java.util.concurrent.ConcurrentHashMap

object KeyframesRegistry {
    private val definitions: MutableMap<String, KeyframesDefinition> = ConcurrentHashMap()

    fun register(definition: KeyframesDefinition) {
        definitions[definition.name] = definition.normalized()
    }

    fun get(name: String): KeyframesDefinition? = definitions[name]

    fun clear() {
        definitions.clear()
    }
}

class KeyframesBuilder internal constructor(
    private val name: String
) {
    private val frames: MutableList<Keyframe> = ArrayList()

    fun at(percent: Float, block: KeyframeScope.() -> Unit) {
        val normalized = (percent / 100f).coerceIn(0f, 1f)
        val scope = KeyframeScope()
        scope.block()
        frames += Keyframe(normalized, scope.build())
    }

    fun atPercent(percent: Float, block: KeyframeScope.() -> Unit) {
        at(percent, block)
    }

    internal fun build(): KeyframesDefinition {
        require(frames.isNotEmpty()) { "Keyframes '$name' must define at least one frame." }
        return KeyframesDefinition(name, frames.toList()).normalized()
    }
}

class KeyframeScope internal constructor() {
    var transform: UiTransform? = null
    var opacity: Float? = null
    var color: Int? = null

    fun transform(block: UiTransformBuilder.() -> Unit) {
        transform = UiTransformBuilder().apply(block).build()
    }

    internal fun build(): KeyframeValue {
        return KeyframeValue(
            transform = transform,
            opacity = opacity?.coerceIn(0f, 1f),
            color = color
        )
    }
}

class UiTransformBuilder internal constructor() {
    private var tx: Float = 0f
    private var ty: Float = 0f
    private var sx: Float = 1f
    private var sy: Float = 1f
    private var rotate: Float = 0f

    fun translate(x: Float, y: Float) {
        tx += x
        ty += y
    }

    fun scale(x: Float, y: Float = x) {
        sx *= x
        sy *= y
    }

    fun rotate(deg: Float) {
        rotate += deg
    }

    fun build(): UiTransform {
        return UiTransform(translateX = tx, translateY = ty, scaleX = sx, scaleY = sy, rotateDeg = rotate)
    }
}

internal fun KeyframesDefinition.normalized(): KeyframesDefinition {
    val normalizedFrames = frames
        .sortedBy { it.fraction }
        .map { frame ->
            frame.copy(fraction = frame.fraction.coerceIn(0f, 1f))
        }
    return copy(frames = normalizedFrames)
}

