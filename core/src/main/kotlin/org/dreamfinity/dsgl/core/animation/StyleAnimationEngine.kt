package org.dreamfinity.dsgl.core.animation

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.style.ComputedStyle
import org.dreamfinity.dsgl.core.style.UiTransform
import kotlin.math.floor

object StyleAnimationEngine {
    data class DebugSnapshot(
        val activeTransitions: List<String>,
        val activeKeyframes: List<String>,
        val effectiveTransform: UiTransform,
        val effectiveOpacity: Float,
        val effectiveColor: Int?,
    )

    private data class TransitionState<T : Any>(
        val property: AnimatedStyleProperty,
        val from: T,
        val to: T,
        val startSec: Double,
        val delaySec: Double,
        val durationSec: Double,
        val easing: Easing,
        val animatable: Animatable<T>,
    ) {
        fun valueAt(nowSec: Double): T {
            if (durationSec <= 0.0) return to
            val local = nowSec - startSec
            if (local <= delaySec) return from
            val t = ((local - delaySec) / durationSec).toFloat().coerceIn(0f, 1f)
            val eased = easing.map(t)
            return animatable.interpolate(from, to, eased)
        }

        fun isFinishedAt(nowSec: Double): Boolean {
            if (durationSec <= 0.0) return true
            val local = nowSec - startSec
            if (local <= delaySec) return false
            return ((local - delaySec) / durationSec).toFloat() >= 1f
        }
    }

    private data class RunningKeyframe(
        val spec: AnimationSpec,
        var startedAtSec: Double,
        var pausedElapsedSec: Double? = null,
        var previousPlayState: AnimationPlayState = spec.playState,
    )

    private data class NodeAnimationState(
        var baseTransform: UiTransform = UiTransform.IDENTITY,
        var baseOpacity: Float = 1f,
        var baseColor: Int = 0xFFFFFFFF.toInt(),
        var transitions: MutableMap<AnimatedStyleProperty, TransitionState<out Any>> = linkedMapOf(),
        var keyframeSpecs: List<AnimationSpec> = emptyList(),
        var keyframes: MutableList<RunningKeyframe> = mutableListOf(),
        var effectiveTransform: UiTransform = UiTransform.IDENTITY,
        var effectiveOpacity: Float = 1f,
        var effectiveColor: Int? = null,
        var lastSeenFrame: Long = 0L,
    )

    private val states: MutableMap<Any, NodeAnimationState> = linkedMapOf()
    private var nowSec: Double = 0.0
    private var frameCounter: Long = 0L
    private val errorRateLimitByKey: MutableMap<String, Double> = linkedMapOf()

    fun onComputedStyleApplied(node: DOMNode, previous: ComputedStyle?, current: ComputedStyle) {
        val token = animationToken(node)
        val state = states.getOrPut(token) { NodeAnimationState() }
        state.baseTransform = current.transform
        state.baseOpacity = current.opacity
        state.baseColor = current.foregroundColor
        state.lastSeenFrame = frameCounter

        val transitions = node.transitionSpec
        if (previous != null) {
            maybeRetargetTransition(
                state = state,
                transitions = transitions,
                property = AnimatedStyleProperty.Transform,
                previousValue = previous.transform,
                currentValue = current.transform,
            )
            maybeRetargetTransition(
                state = state,
                transitions = transitions,
                property = AnimatedStyleProperty.Opacity,
                previousValue = previous.opacity,
                currentValue = current.opacity,
            )
            maybeRetargetTransition(
                state = state,
                transitions = transitions,
                property = AnimatedStyleProperty.Color,
                previousValue = previous.foregroundColor,
                currentValue = current.foregroundColor,
            )
        }

        val nextSpecs = node.animationSpecs
        if (state.keyframeSpecs != nextSpecs) {
            state.keyframeSpecs = nextSpecs
            state.keyframes = nextSpecs.map { RunningKeyframe(spec = it, startedAtSec = nowSec) }.toMutableList()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun tickAndApply(root: DOMNode, dtSeconds: Double, partialTicks: Float?): Boolean {
        val safeDt = dtSeconds.coerceAtLeast(0.0)
        nowSec += safeDt
        frameCounter += 1
        val visualsChanged = applyRecursive(root)
        cleanupStaleStates()
        return visualsChanged
    }

    fun clear() {
        states.clear()
        errorRateLimitByKey.clear()
        nowSec = 0.0
        frameCounter = 0L
    }

    fun debugSnapshot(node: DOMNode): DebugSnapshot? {
        val token = animationToken(node)
        val state = states[token] ?: return null
        return debugSnapshot(state)
    }

    fun debugSnapshotForKey(key: Any?): DebugSnapshot? {
        if (key == null) return null
        val state = states[key] ?: return null
        return debugSnapshot(state)
    }

    private fun debugSnapshot(state: NodeAnimationState): DebugSnapshot {
        val transitions =
            state.transitions.values.map { transition ->
                val local = (nowSec - transition.startSec - transition.delaySec).coerceAtLeast(0.0)
                val progress =
                    if (transition.durationSec <=
                        0.0
                    ) {
                        1.0
                    } else {
                        (local / transition.durationSec).coerceIn(0.0, 1.0)
                    }
                "${transition.property.name.lowercase()}:${(progress * 100.0).toInt()}%"
            }
        val keyframes =
            state.keyframes.map { running ->
                val elapsedSec = resolveElapsed(running, nowSec)
                val delaySec =
                    running.spec.delayMs
                        .coerceAtLeast(0) / 1000.0
                val durationSec =
                    running.spec.durationMs
                        .coerceAtLeast(1) / 1000.0
                val active = (elapsedSec - delaySec).coerceAtLeast(0.0)
                val rawProgress = if (durationSec <= 0.0) 1.0 else (active % durationSec) / durationSec
                "${running.spec.name}:${(rawProgress * 100.0).toInt()}%:${running.spec.playState.name.lowercase()}"
            }
        return DebugSnapshot(
            activeTransitions = transitions,
            activeKeyframes = keyframes,
            effectiveTransform = state.effectiveTransform,
            effectiveOpacity = state.effectiveOpacity,
            effectiveColor = state.effectiveColor,
        )
    }

    private fun applyRecursive(node: DOMNode): Boolean {
        var changed = false
        val token = animationToken(node)
        val state = states[token]
        if (state == null) {
            if (node.applyAnimationVisuals(transform = null, opacity = null, color = null)) {
                changed = true
            }
        } else {
            state.lastSeenFrame = frameCounter
            runCatching {
                val transitionTransform = transitionValue<UiTransform>(state, AnimatedStyleProperty.Transform)
                val transitionOpacity = transitionValue<Float>(state, AnimatedStyleProperty.Opacity)
                val transitionColor = transitionValue<Int>(state, AnimatedStyleProperty.Color)

                var resolvedTransform: UiTransform? = transitionTransform
                var resolvedOpacity: Float? = transitionOpacity
                var resolvedColor: Int? = transitionColor

                state.keyframes.forEachIndexed { index, running ->
                    val definition = KeyframesRegistry.get(running.spec.name) ?: return@forEachIndexed
                    val sampled = sampleKeyframe(definition, running)
                    if (sampled.transform != null) {
                        resolvedTransform = sampled.transform
                    }
                    if (sampled.opacity != null) {
                        resolvedOpacity = sampled.opacity
                    }
                    if (sampled.color != null) {
                        resolvedColor = sampled.color
                    }
                }

                if (node.applyAnimationVisuals(
                        transform = resolvedTransform,
                        opacity = resolvedOpacity,
                        color = resolvedColor,
                    )
                ) {
                    changed = true
                }
                state.effectiveTransform = node.effectiveTransform()
                state.effectiveOpacity = node.effectiveOpacity()
                state.effectiveColor = node.animationColorOverride()
            }.onFailure { error ->
                if (node.applyAnimationVisuals(transform = null, opacity = null, color = null)) {
                    changed = true
                }
                state.effectiveTransform = node.effectiveTransform()
                state.effectiveOpacity = node.effectiveOpacity()
                state.effectiveColor = node.animationColorOverride()
                logRateLimited(
                    key = "anim:${node.key ?: node.styleType}",
                    message = "[DSGL-Anim] ${node.key ?: node.styleType}: ${error.message}",
                )
            }
        }

        node.children.forEach { child ->
            if (applyRecursive(child)) {
                changed = true
            }
        }
        return changed
    }

    private fun cleanupStaleStates() {
        val iterator = states.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.lastSeenFrame != frameCounter) {
                iterator.remove()
            }
        }
    }

    private fun maybeRetargetTransition(
        state: NodeAnimationState,
        transitions: TransitionSpec,
        property: AnimatedStyleProperty,
        previousValue: UiTransform,
        currentValue: UiTransform,
    ) {
        if (previousValue == currentValue) return
        val spec =
            transitions.forProperty(property) ?: run {
                state.transitions.remove(property)
                return
            }
        val currentVisual = transitionValue<UiTransform>(state, property) ?: previousValue
        state.transitions[property] =
            TransitionState(
                property = property,
                from = currentVisual,
                to = currentValue,
                startSec = nowSec,
                delaySec = spec.delayMs.coerceAtLeast(0) / 1000.0,
                durationSec = spec.durationMs.coerceAtLeast(0) / 1000.0,
                easing = spec.easing,
                animatable = TransformAnimatable,
            )
    }

    private fun maybeRetargetTransition(
        state: NodeAnimationState,
        transitions: TransitionSpec,
        property: AnimatedStyleProperty,
        previousValue: Float,
        currentValue: Float,
    ) {
        if (previousValue == currentValue) return
        val spec =
            transitions.forProperty(property) ?: run {
                state.transitions.remove(property)
                return
            }
        val currentVisual = transitionValue<Float>(state, property) ?: previousValue
        state.transitions[property] =
            TransitionState(
                property = property,
                from = currentVisual,
                to = currentValue,
                startSec = nowSec,
                delaySec = spec.delayMs.coerceAtLeast(0) / 1000.0,
                durationSec = spec.durationMs.coerceAtLeast(0) / 1000.0,
                easing = spec.easing,
                animatable = FloatAnimatable,
            )
    }

    private fun maybeRetargetTransition(
        state: NodeAnimationState,
        transitions: TransitionSpec,
        property: AnimatedStyleProperty,
        previousValue: Int,
        currentValue: Int,
    ) {
        if (previousValue == currentValue) return
        val spec =
            transitions.forProperty(property) ?: run {
                state.transitions.remove(property)
                return
            }
        val currentVisual = transitionValue<Int>(state, property) ?: previousValue
        state.transitions[property] =
            TransitionState(
                property = property,
                from = currentVisual,
                to = currentValue,
                startSec = nowSec,
                delaySec = spec.delayMs.coerceAtLeast(0) / 1000.0,
                durationSec = spec.durationMs.coerceAtLeast(0) / 1000.0,
                easing = spec.easing,
                animatable = ColorAnimatable,
            )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> transitionValue(state: NodeAnimationState, property: AnimatedStyleProperty): T? {
        val transition = state.transitions[property] as? TransitionState<T>? ?: return null
        val value = transition.valueAt(nowSec)
        if (transition.isFinishedAt(nowSec)) {
            state.transitions.remove(property)
        }
        return value
    }

    private fun sampleKeyframe(definition: KeyframesDefinition, running: RunningKeyframe): KeyframeValue {
        val spec = running.spec
        if (spec.durationMs <= 0) {
            return sampleDefinition(definition, progress = 1f, easing = spec.easing)
        }

        val elapsedSec = resolveElapsed(running, nowSec)
        val delaySec = spec.delayMs.coerceAtLeast(0) / 1000.0
        val durationSec = spec.durationMs.coerceAtLeast(1) / 1000.0
        val iterationCount = spec.iterationCount.finiteValueOrNull()
        val activeSec = elapsedSec - delaySec

        if (activeSec < 0.0) {
            return if (spec.fillMode == AnimationFillMode.Backwards || spec.fillMode == AnimationFillMode.Both) {
                val startProgress =
                    when (spec.direction) {
                        AnimationDirection.Reverse,
                        AnimationDirection.AlternateReverse,
                        -> 1f
                        AnimationDirection.Normal,
                        AnimationDirection.Alternate,
                        -> 0f
                    }
                sampleDefinition(definition, startProgress, spec.easing)
            } else {
                KeyframeValue()
            }
        }

        val totalDuration = iterationCount?.let { durationSec * it.toDouble() }
        if (totalDuration != null && activeSec >= totalDuration) {
            val shouldFill = spec.fillMode == AnimationFillMode.Forwards || spec.fillMode == AnimationFillMode.Both
            if (!shouldFill) return KeyframeValue()
            val lastIterationIndex = (iterationCount - 1).coerceAtLeast(0)
            val reversed = isIterationReversed(spec.direction, lastIterationIndex)
            return sampleDefinition(definition, if (reversed) 0f else 1f, spec.easing)
        }

        val iterationIndex = floor(activeSec / durationSec).toInt().coerceAtLeast(0)
        val iterationTime = (activeSec - iterationIndex * durationSec).toFloat()
        var progress = (iterationTime / durationSec.toFloat()).coerceIn(0f, 1f)
        if (isIterationReversed(spec.direction, iterationIndex)) {
            progress = 1f - progress
        }
        return sampleDefinition(definition, progress, spec.easing)
    }

    private fun resolveElapsed(running: RunningKeyframe, nowSec: Double): Double {
        if (running.spec.playState == AnimationPlayState.Paused) {
            if (running.previousPlayState != AnimationPlayState.Paused) {
                running.pausedElapsedSec = nowSec - running.startedAtSec
                running.previousPlayState = AnimationPlayState.Paused
            }
            return running.pausedElapsedSec ?: 0.0
        }

        if (running.previousPlayState == AnimationPlayState.Paused) {
            val frozen = running.pausedElapsedSec ?: 0.0
            running.startedAtSec = nowSec - frozen
            running.pausedElapsedSec = null
            running.previousPlayState = AnimationPlayState.Running
        }
        return nowSec - running.startedAtSec
    }

    private fun sampleDefinition(definition: KeyframesDefinition, progress: Float, easing: Easing): KeyframeValue =
        KeyframeValue(
            transform = sampleTransform(definition, progress, easing),
            opacity = sampleOpacity(definition, progress, easing),
            color = sampleColor(definition, progress, easing),
        )

    private fun sampleTransform(definition: KeyframesDefinition, progress: Float, easing: Easing): UiTransform? =
        sampleProperty(
            frames = definition.transformFrames,
            progress = progress,
            easing = easing,
            valueOf = { it.transform },
            interpolate = { from, to, t -> TransformAnimatable.interpolate(from, to, t) },
        )

    private fun sampleOpacity(definition: KeyframesDefinition, progress: Float, easing: Easing): Float? =
        sampleProperty(
            frames = definition.opacityFrames,
            progress = progress,
            easing = easing,
            valueOf = { it.opacity },
            interpolate = { from, to, t -> FloatAnimatable.interpolate(from, to, t).coerceIn(0f, 1f) },
        )

    private fun sampleColor(definition: KeyframesDefinition, progress: Float, easing: Easing): Int? =
        sampleProperty(
            frames = definition.colorFrames,
            progress = progress,
            easing = easing,
            valueOf = { it.color },
            interpolate = { from, to, t -> ColorAnimatable.interpolate(from, to, t) },
        )

    private inline fun <T : Any> sampleProperty(
        frames: List<Keyframe>,
        progress: Float,
        easing: Easing,
        valueOf: (KeyframeValue) -> T?,
        interpolate: (T, T, Float) -> T,
    ): T? {
        if (frames.isEmpty()) return null
        if (progress <= frames.first().fraction) {
            return valueOf(frames.first().value)
        }
        if (progress >= frames.last().fraction) {
            return valueOf(frames.last().value)
        }
        val leftIndex = surroundingLeftIndex(frames, progress)
        if (leftIndex < 0) {
            return valueOf(frames.last().value)
        }
        val left = frames[leftIndex]
        val right = frames[leftIndex + 1]
        val t = normalizedProgress(left.fraction, right.fraction, progress)
        val eased = easing.map(t)
        return interpolate(valueOf(left.value)!!, valueOf(right.value)!!, eased)
    }

    private fun surroundingLeftIndex(frames: List<Keyframe>, progress: Float): Int {
        for (index in 0 until frames.lastIndex) {
            if (progress >= frames[index].fraction && progress <= frames[index + 1].fraction) {
                return index
            }
        }
        return -1
    }

    private fun normalizedProgress(left: Float, right: Float, value: Float): Float {
        val span = (right - left).coerceAtLeast(1e-6f)
        return ((value - left) / span).coerceIn(0f, 1f)
    }

    private fun isIterationReversed(direction: AnimationDirection, iterationIndex: Int): Boolean =
        when (direction) {
            AnimationDirection.Normal -> false
            AnimationDirection.Reverse -> true
            AnimationDirection.Alternate -> iterationIndex % 2 == 1
            AnimationDirection.AlternateReverse -> iterationIndex % 2 == 0
        }

    private fun animationToken(node: DOMNode): Any = node.key ?: node

    private fun logRateLimited(key: String, message: String) {
        val last = errorRateLimitByKey[key]
        if (last != null && nowSec - last < 1.0) return
        errorRateLimitByKey[key] = nowSec
        println(message)
    }
}
