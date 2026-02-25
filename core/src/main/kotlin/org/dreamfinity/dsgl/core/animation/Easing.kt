package org.dreamfinity.dsgl.core.animation

import kotlin.math.abs

fun interface Easing {
    fun map(t: Float): Float
}

object Easings {
    val LINEAR: Easing = Easing { t -> t.coerceIn(0f, 1f) }
    val EASE: Easing = cubicBezier(0.25f, 0.1f, 0.25f, 1f)
    val EASE_IN: Easing = cubicBezier(0.42f, 0f, 1f, 1f)
    val EASE_OUT: Easing = cubicBezier(0f, 0f, 0.58f, 1f)
    val EASE_IN_OUT: Easing = cubicBezier(0.42f, 0f, 0.58f, 1f)

    fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float): Easing {
        return CubicBezierEasing(x1, y1, x2, y2)
    }
}

fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float): Easing {
    return Easings.cubicBezier(x1, y1, x2, y2)
}

private class CubicBezierEasing(
    private val x1: Float,
    private val y1: Float,
    private val x2: Float,
    private val y2: Float
) : Easing {
    override fun map(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        if (clamped <= 0f) return 0f
        if (clamped >= 1f) return 1f

        var lower = 0.0
        var upper = 1.0
        var u = clamped.toDouble()
        repeat(18) {
            val x = sampleCurveX(u)
            if (abs(x - clamped.toDouble()) <= 1e-5) return@repeat
            if (x < clamped.toDouble()) {
                lower = u
            } else {
                upper = u
            }
            u = (lower + upper) * 0.5
        }
        return sampleCurveY(u).toFloat().coerceIn(0f, 1f)
    }

    private fun sampleCurveX(t: Double): Double {
        val omt = 1.0 - t
        return 3.0 * omt * omt * t * x1 +
                3.0 * omt * t * t * x2 +
                t * t * t
    }

    private fun sampleCurveY(t: Double): Double {
        val omt = 1.0 - t
        return 3.0 * omt * omt * t * y1 +
                3.0 * omt * t * t * y2 +
                t * t * t
    }
}

